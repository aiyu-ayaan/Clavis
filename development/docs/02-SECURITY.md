# 02 — Security design

Goal: as secure as an offline Android app can be. This document is the contract. Any implementation that departs from it needs an entry in `07-DECISIONS.md`.

## Threat model

| ID | Threat | In scope | Mitigation |
|----|--------|----------|------------|
| T1 | Lost or stolen unlocked device | yes | App lock independent of device lock, short auto-lock, lock on screen-off. |
| T2 | Lost device, attacker reads app storage (root, forensic image) | yes | Field-level AES-256-GCM, keys wrapped by hardware-backed Keystore, PIN-derived key. Storage alone is useless. |
| T3 | Offline brute force of a weak PIN | yes | PIN KDF output is additionally bound to a non-exportable Keystore key, so the attack needs the device. Attempt throttling in app. |
| T4 | Shoulder surfing | yes | Masked by default, reveal timer, no auto-reveal. |
| T5 | Screenshots, screen recording, recents thumbnail | yes | `FLAG_SECURE`, recents screenshot disabled, content-sensitivity flag where supported. |
| T6 | Clipboard snooping by other apps or keyboards | yes | Sensitive clip flag, short auto-clear, never copy silently. |
| T7 | Malicious app on the same device | partial | Sandbox, no exported components except launcher, no IPC surface, accessibility-service overlay risk documented. |
| T8 | Data exfiltration by the app itself or a dependency | yes | No `INTERNET` permission, no analytics or crash SDKs with network, dependency allowlist. |
| T9 | Backup and device-transfer leaks | yes | `allowBackup=false`, data-extraction rules exclude everything. |
| T10 | Malware with root and live memory access | no | Out of scope. Reduce window: keep plaintext in memory only while needed, zero buffers. |
| T11 | Coerced unlock | no | Out of scope. |
| T12 | Tampered or repackaged APK | partial | Play App Signing, release-only signing; Play Integrity needs network so it is not used. Local debugger/root detection is warn-only. |

## Key hierarchy

```
Android Keystore (hardware-backed, StrongBox when available, non-exportable)
 ├─ K_device   AES-256-GCM, no user-auth requirement, wraps the PIN-path secret
 └─ K_bio      AES-256-GCM, setUserAuthenticationRequired(true), BIOMETRIC_STRONG,
               invalidated on new enrollment, used through BiometricPrompt.CryptoObject

DEK  (data encryption key, 256-bit random, generated once at setup)
 ├─ wrapped by KEK_pin   = Argon2id(PIN, salt) -> encrypted under K_device
 └─ wrapped by K_bio     (only if biometric enabled)
```

- The DEK never touches disk unwrapped. It lives in memory only while the vault is unlocked.
- PIN path: derive from the PIN with a memory-hard KDF, then encrypt the result under `K_device`. An attacker who copies the app data cannot test PIN guesses without the physical Keystore.
- Biometric path: BiometricPrompt with a `CryptoObject` unlocks `K_bio`, which unwraps the DEK. Without a successful cryptographic unlock, the app UI alone cannot reach the DEK. A bypass of the prompt UI gives nothing.
- If the biometric set changes, `K_bio` is invalidated. The user falls back to PIN and re-enables biometric, which re-wraps the DEK.
- Argon2id is not in the platform or Tink. Use a vetted library (libsodium binding or Bouncy Castle). Fallback if a native dependency is unwanted: PBKDF2-HMAC-SHA256 with at least 600,000 iterations (OWASP). Decision tracked in `07-DECISIONS.md`.
- Keep the old `androidx.security:security-crypto` out. It is deprecated. Use Keystore directly plus Tink AEAD for the data layer.

## Data at rest

- Room (SQLite) stores one row per card: `id`, `created_at`, `updated_at`, `sort_key`, and `payload` (BLOB).
- `payload` is an AEAD ciphertext (AES-256-GCM via Tink, 96-bit random nonce) of a serialized record: nickname, holder, PAN, expiry, network, type, issuer, color, notes.
- CVV lives in a **second** column `cvv_payload`, encrypted under a distinct derived subkey (HKDF from the DEK with a `"cvv"` label). Reading it requires a fresh-auth token (see below). This is a software gate in addition to the cryptographic one.
- AEAD associated data = `cardId || fieldLabel || schemaVersion`. Moving ciphertext between rows fails to decrypt.
- Nothing about a card is stored in plaintext columns, so the list screen decrypts after unlock and holds the result in memory. Search and sorting run in memory.
- DataStore (Preferences) holds only non-sensitive settings: theme, lock timeout, clipboard delay, onboarding done. Wrapped KDF salt, wrapped DEK blobs and attempt counters live in a small encrypted-or-integrity-protected file.
- No SQLCipher in v1. Whole-database encryption adds a native dependency and little over per-record AEAD for this dataset size. Revisit if metadata leakage (row count, timestamps) matters.

## Authentication and lock

| Aspect | Design |
|--------|--------|
| PIN length | 6 digits default, 4–8 allowed. Reject trivial PINs (000000, 123456, repeated, sequential). |
| Biometric | Optional. Class 3 (BIOMETRIC_STRONG) only. Device credential (pattern/passcode) is **not** accepted as the app unlock, since the user wants app-level separation. |
| Throttling | 5 free attempts, then 30 s, 1 min, 5 min, 15 min, 1 h. Counter persisted and tied to elapsed realtime to resist clock changes. Optional "erase after 10 failures" setting, off by default. |
| Session | Unlocked session ends on: app backgrounded longer than the timeout (default 30 s, choices: immediately, 30 s, 1 min, 5 min), screen off, or user lock. |
| Fresh auth | Revealing a full PAN or CVV, copying CVV, and sharing require authentication within the last 60 s. If the session auth is older, prompt again (biometric or PIN). |
| Forgot PIN | No recovery. This is a feature. The only path is wiping the vault. The UI states this during setup. |
| Change PIN | Requires the old PIN. Re-wraps the DEK. Data is not re-encrypted. |

## Screen and surface protection

- `FLAG_SECURE` on the single activity. No toggle in v1 (always on).
- `setRecentsScreenshotEnabled(false)` (API 33+) and a blurred splash on background for older versions.
- Android 15 sensitive-content APIs (`View.setContentSensitivity`) on card views, wrapped in an SDK check. Confirm exact API against the Android 15 docs when implementing.
- Card number is masked as `•••• •••• •••• 4242` with grouping matched to the network (Amex 4-6-5).
- Reveal is time-boxed: auto-mask after 20 s or on leaving the screen.
- Keyboard: card inputs set `IME_FLAG_NO_PERSONALIZED_LEARNING` and `inputType` that disables suggestions, so IMEs do not learn digits.
- No `autofill`: card input fields use `importantForAutofill="no"`.

## Clipboard

- Every copy sets `ClipDescription.EXTRA_IS_SENSITIVE` (API 33). Below 33 use the raw key `android.content.extra.IS_SENSITIVE`.
- Auto-clear after N seconds (default 30, choices 10/30/60/never). Implemented with `clearPrimaryClip()` (API 28) on a timer owned by a short-lived foreground-safe component. If the process is killed before the timer, the clip persists. Documented limitation.
- Android 13+ shows its own confirmation toast, so the app's snackbar avoids duplicating the value.
- Clipboard content is never logged.

## Share

- Share is the one deliberate exit path. See `05-SHARE-AND-COPY.md` for the flow.
- The generated image is written to `cache/shared/` and exposed via `FileProvider` with `FLAG_GRANT_READ_URI_PERMISSION`, never public storage or MediaStore.
- Cache is deleted after the share sheet returns, on lock, and on next app start.
- The image and text are built from the user's choice on the share sheet: CVV on or off. Default off.
- A warning dialog states that the recipient and their device will hold the data.

## Build and platform hardening

- Manifest: no `INTERNET`, `android:allowBackup="false"`, `dataExtractionRules` and `fullBackupContent` exclude all, `android:usesCleartextTraffic="false"`, `android:debuggable` false in release, only `MainActivity` exported.
- R8 minify and resource shrinking on release. Strip logging with R8 rules (`Log.*` removal) in release.
- `CAMERA` permission requested only on first scan, with rationale. No other dangerous permissions.
- No third-party SDKs that phone home (Firebase Analytics, Crashlytics, ads). Crash reports are local-only and user-exportable.
- Dependency policy: each new dependency needs a line in `07-DECISIONS.md` (purpose, maintainer, network behavior). Prefer AndroidX and Google libraries already used in the sibling Rewire app.
- Memory hygiene: keep PAN and CVV as `CharArray` or `ByteArray` in the crypto layer where practical, zero after use. Compose UI strings are unavoidable; keep their lifetime to the reveal window.
- Compose test tag and semantics must not contain card data.

## Verification plan

- Unit tests: AEAD round-trip, AAD mismatch rejection, wrong-key rejection, KDF determinism, throttle schedule.
- Instrumented tests: lock on background, fresh-auth gating, clipboard flag present and cleared, `FLAG_SECURE` set.
- Manual: inspect `/data/data/<pkg>` on a rooted emulator after saving a card and confirm no plaintext; `adb backup` returns nothing; screenshot returns black; recents shows blank.
- Static: `apkanalyzer manifest print` shows no `INTERNET`; dependency tree has no network SDKs.
