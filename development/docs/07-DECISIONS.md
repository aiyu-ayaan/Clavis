# 07 — Decisions, open questions, research log

Format: decision, status, reason. Status is **Decided** (default chosen, change on request), **Open** (needs owner input) or **Verify** (a fact to confirm before relying on it).

## Decisions

| # | Decision | Status | Reason |
|---|----------|--------|--------|
| D1 | Android only, Kotlin + Compose, min SDK 28, target 36 | Decided | Matches Rewire; StrongBox and `clearPrimaryClip` from 28. |
| D2 | No `INTERNET` permission | Decided | Strongest verifiable privacy claim. Rules out online BIN lookup, Play Integrity, crash SDKs. |
| D3 | PIN mandatory, biometric optional | Decided | UPI-style, PIN is the recoverable base. |
| D4 | Biometric accepts `BIOMETRIC_STRONG` only, bound via `CryptoObject` | Decided | UI bypass yields no key. |
| D5 | Per-record AEAD (Tink) on Room, no SQLCipher | Decided | Fewer native deps; dataset is small. Revisit if metadata leak matters. |
| D6 | CVV stored optionally, separate subkey, fresh auth to reveal | Decided | Honors the request while limiting exposure. See D14. |
| D7 | Bundled offline BIN table | Decided | Online lookup would leak card prefixes. |
| D8 | ML Kit bundled OCR + CameraX, not Play-services card recognition | Decided | No Play-services dependency. |
| D9 | Same stack as Rewire (Hilt, Room, DataStore, Navigation Compose) | Decided | Reuse habits and tooling. |
| D10 | No account, no cloud sync, no backup in v1 | Decided | Smaller attack surface. |
| D11 | Share defaults to CVV off, resets every time | Decided | Avoid sticky unsafe choices. |
| D12 | Always-on `FLAG_SECURE` | Decided | Simplest and safest. |
| D13 | No PIN recovery | Decided | Any recovery path weakens the vault. |
| D14 | PCI DSS 3.3.1 stance: private vault of own cards is not a covered entity, warn on CVV save | **Open** | Owner should confirm comfort; legal advice if distributed widely. |
| D15 | KDF: PBKDF2-HMAC-SHA256, 600,000 iterations (platform `SecretKeyFactory`) | Decided (dev default, owner may switch to Argon2id) | No native dependency; the KDF output is also wrapped under the hardware `K_device`, so offline guessing needs the device. Iteration count is stored per vault, so a later upgrade can re-wrap on next unlock. |
| D16 | BIN data source and license | **Open** | No clearly permissive 8-digit dataset found. Choose: build own table, license commercial data, or accept 6-digit community data with a curated Indian layer. |
| D17 | App name and package: `Clavis`, `com.aiyu.clavis` | **Open** | Confirm. Check Play Store name conflicts. |
| D18 | Encrypted export/import of the vault (passphrase) in v2 | Open | Lets users migrate phones without cloud. |
| D19 | Autofill service for payment cards in v2 | Open | Removes clipboard need. |
| D20 | Card row id is a random UUID string, not an auto-increment Long | Decided | The id is part of the AEAD associated data, so it must exist before the first encryption. |
| D21 | Dependency: `com.google.crypto.tink:tink-android` | Decided | AEAD + HKDF for card fields. Google-maintained, no network behavior. |

## Facts to verify before implementation

| # | Fact | Status |
|---|------|--------|
| V1 | `ClipDescription.EXTRA_IS_SENSITIVE` exists from API 33 with value `android.content.extra.IS_SENSITIVE`. | Confirmed by Android docs (secure clipboard handling). |
| V2 | `androidx.security:security-crypto` is deprecated; Tink + DataStore or direct Keystore is the usual replacement. | Confirmed by multiple 2025–2026 sources; Google statement is in the Android cryptography docs. Re-read before coding. |
| V3 | Exact API for Android 15 content sensitivity (`View.setContentSensitivity`, sensitive-content protection during screen share). | Verify against Android 15 reference. Not found in searches. |
| V4 | `material3` Expressive APIs still need the 1.5 alpha line. | Verify at scaffold time; Rewire pins `1.5.0-alpha26`. |
| V5 | Network IIN ranges in `04-CARD-INTELLIGENCE.md`, especially RuPay and Discover `65`. | Verify against scheme docs. Cover with tests. |
| V6 | Payment Card Recognition (Play services) device requirements and current status. | Only needed if D8 is revisited. |
| V7 | Google Play policy for apps storing payment card data. | Read Financial Services and User Data policies before first upload. |
| V8 | Network logo usage rules (Visa, Mastercard, RuPay). | Check brand guidelines before using official marks. |
| V9 | Pull intro: interpreted as a full-screen onboarding pager. | Confirm with owner. |

## Research log

- Card scanning: Google Pay offers a Payment Card Recognition API that returns PAN and expiry via Play services without app camera permission; ML Kit Text Recognition v2 is a general OCR and needs custom parsing. Source: [Google Pay card recognition docs](https://developers.google.com/pay/payment-card-recognition/debit-credit-card-recognition), [ML Kit text recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2).
- Secure storage: `EncryptedSharedPreferences` is deprecated; guides recommend Tink with Proto DataStore or direct Keystore. Sources: [Include Security](https://blog.includesecurity.com/2026/08/encryptedsharedpreferences-is-dead-heres-what-you-should-use-instead/), [ProAndroidDev migration guide](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a).
- Clipboard: flag sensitive data with `EXTRA_IS_SENSITIVE`. Source: [Android secure clipboard handling](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling).
- BIN data: community datasets such as [binlist/data](https://github.com/binlist/data) and `smartbindb` exist but license and 8-digit coverage are unclear; commercial options (BinDB, BinBase) are paid.
- UI reference: Rewire theme files and version catalog listed in `06-UX-DESIGN.md`.
- Hardware and OS facts (StrongBox from API 28, `clearPrimaryClip` from API 28, `setRecentsScreenshotEnabled` from API 33) are from Android platform knowledge and should be re-checked against the SDK when scaffolding.
