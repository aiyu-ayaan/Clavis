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
| D13 | PIN recovery only through the phone's screen lock, or erase. "Forgot PIN?" on the lock screen offers both | Decided (owner, 2026-11-15; replaces "no recovery") | Owner asked for WhatsApp-style recovery. Trade-off: anyone who knows the phone's screen-lock PIN can reset the Clavis PIN and see the cards. K_recovery is an RSA Keystore key bound to the device credential; the DEK is wrapped with its public half on each unlock. Removing the phone lock invalidates it, leaving erase as the only path. |
| D14 | PCI DSS 3.3.1 stance: private vault of own cards is not a covered entity, warn on CVV save | **Open** | Owner should confirm comfort; legal advice if distributed widely. |
| D15 | KDF: PBKDF2-HMAC-SHA256, 600,000 iterations (platform `SecretKeyFactory`) | Decided (dev default, owner may switch to Argon2id) | No native dependency; the KDF output is also wrapped under the hardware `K_device`, so offline guessing needs the device. Iteration count is stored per vault, so a later upgrade can re-wrap on next unlock. |
| D16 | BIN data: BIN List Data (github.com/venelinkochev/bin-list-data), CC BY 4.0, 6-digit, bundled as `core/src/main/assets/bins.bin` | Decided (owner asked for free auto-detection, 2026-11-15) | Free and permissively licensed; 374,788 BINs incl. ~12k Indian (RuPay, HDFC, ICICI, SBI, Axis). Attribution shown in Settings › About. Not used: `binlist/data` (no license). No 8-digit rows; curated Indian layer still open. |
| D17 | App name and package: `Clavis`, `com.aiyu.clavis` | **Open** | Confirm. Check Play Store name conflicts. |
| D18 | Encrypted export/import of the vault (passphrase) in v2 | Open | Lets users migrate phones without cloud. |
| D19 | Autofill service for payment cards in v2 | Open | Removes clipboard need. |
| D20 | Card row id is a random UUID string, not an auto-increment Long | Decided | The id is part of the AEAD associated data, so it must exist before the first encryption. |
| D22 | ML Kit bundled OCR ships Google's `datatransport` telemetry, which adds `INTERNET` and `ACCESS_NETWORK_STATE`. Both are removed with `tools:node="remove"`; the module stays (ML Kit references it, excluding it crashes) | Decided (owner, 2026-11-15) | Keeps scan offline. Without `INTERNET` the uploader cannot open a socket. CI must keep checking the merged manifest for `INTERNET`. Revisit if a telemetry-free OCR appears. |
| D21 | Dependency: `com.google.crypto.tink:tink-android` | Decided | AEAD + HKDF for card fields. Google-maintained, no network behavior. |
| D23 | Logo: **Sleeve**, a card rising out of its sleeve with a thumb notch, one colour (pale blue `#D1E4FF` on ink `#1E3A5F`). It replaces the placeholder key glyph | Decided (owner, 2026-11-15) | Chosen from three rounds of concepts (`development/brand/logo/`). It says "your card, put away until you pull it out" without lock or shield clichés. A trademark search is still to do. |
| D24 | In-app updater from GitHub releases (Rewire design): `INTERNET` + `REQUEST_INSTALL_PACKAGES` added, one `GET api.github.com/repos/aiyu-ayaan/Clavis/releases`, APK download, system installer. `network_security_config.xml` trusts system CAs only for `github.com` and `githubusercontent.com`; every other host fails TLS validation, so ML Kit telemetry (D22) still cannot send | Decided (owner, 2026-11-15; supersedes the "no INTERNET, ever" rule) | Single build, no offline flavor (owner chose it over two flavors). Toggle in Settings; no background worker. Nothing about cards or the user is sent. CI keeps checking the merged manifest and the network config. |
| D25 | Card art: bank colour themes, offline. `IssuerBrand` maps the issuer to its public brand colour (about 30 banks), else one colour per network; 4 variants per bank so cards from the same bank differ, least-used variant picked by default and stored in the record. No images, logos or fetching | Decided (owner, 2026-11-15) | Real card art needs network token services or a server lookup by BIN, which breaks D7 and D24. Logos raise V8. Brand colours live as data in `:core`; the face colours are derived in the `ui-common` theme layer. |

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
