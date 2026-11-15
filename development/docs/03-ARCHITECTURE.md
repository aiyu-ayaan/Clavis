# 03 — Architecture

Mirrors the sibling app Rewire (`../Rewire`) so tooling, patterns and review habits carry over.

## Stack

| Layer | Choice | Note |
|-------|--------|------|
| Language | Kotlin 2.x | Same as Rewire. |
| UI | Jetpack Compose, `androidx.compose.material3` 1.5.x alpha (Expressive) | Rewire pins `1.5.0-alpha26` because `MaterialExpressiveTheme` and expressive components are not in the stable 1.4 line. Re-check the stable status when scaffolding. |
| Navigation | `navigation-compose` | Single activity. |
| DI | Hilt + KSP | As in Rewire. |
| Storage | Room | Encrypted payload columns, see `02-SECURITY.md`. |
| Settings | DataStore Preferences | Non-sensitive only. |
| Crypto | Android Keystore + Tink AEAD + Argon2id lib | No `security-crypto`. |
| Biometric | `androidx.biometric` | `BIOMETRIC_STRONG` with `CryptoObject`. |
| Camera | CameraX | Preview + ImageAnalysis. |
| OCR | ML Kit Text Recognition v2 **bundled** model | Works offline and needs no Play-services download. |
| Splash | `core-splashscreen` | |
| Image share | `android.graphics.Canvas`/`Bitmap` + FileProvider | No library. |
| Min / target SDK | min 28, target 36 | 28 gives StrongBox and `clearPrimaryClip`. Rewire notes Play requires target 36. |
| Package | `com.aiyu.clavis` | Dev flavor `com.aiyu.clavis.dev`, as Domus does with `.dev`. |

Scanning alternative considered: Google's Payment Card Recognition API in `play-services-wallet`. It needs no camera permission and no OCR code, but it ties scanning to Play services and a Google account on the device, and its on-device claim is Google's, not ours. Rejected for v1 to keep the "no external dependency, verifiable offline" story. Revisit as an optional path.

## Modules

Three Gradle modules, scaffolded and building (`./gradlew :app:assembleDebug`):

| Module | Namespace | Holds | Depends on |
|--------|-----------|-------|------------|
| `:app` | `com.aiyu.clavis` | `MainActivity`, manifest, navigation host, feature screens and view models (packages `feature.*`), DI wiring. Debug build installs as `com.aiyu.clavis.dev`. | `:core`, `:ui-common` |
| `:ui-common` | `com.aiyu.clavis.ui` | Theme (`ClavisTheme`, Color/Shape/Type), shared components, **every `strings.xml`**. Exposes Compose and Material3 as `api`. | none |
| `:core` | `com.aiyu.clavis.core` | Pure domain and infrastructure: `card` (Luhn, network, BIN), `crypto`, `data`, `settings`. No UI, no string resources. | none |

Rules:

- All user-visible strings go in `ui-common/src/main/res/values/strings.xml` (and its locale folders). `:app` and `:core` never add `strings.xml`. Non-text resources (launcher icon, splash theme, backup rules) stay in `:app`.
- `:core` stays UI-free so crypto and card logic are testable on the JVM. When `:core` must report text to the user it returns a typed error and `:app` maps it to a string.
- Add a module only when a boundary pays for itself. Candidates: `:feature-scan` (CameraX + ML Kit, heaviest dependencies), `:core-crypto` if the crypto layer needs stricter visibility.

Target layout inside the modules:

```
core/   com.aiyu.clavis.core
        ├── crypto   KeyManager, VaultCipher, PinKdf, AuthGate
        ├── data     Room db, CardDao, CardEntity, repositories
        ├── model    Card, Issuer
        ├── card     CardNetwork, CardType, Luhn (done), NetworkDetector, BinLookup
        └── settings DataStore wrapper
ui-common/ com.aiyu.clavis.ui
        ├── theme       Color, Type, Shape, Motion
        └── components  CardSurface, PinPad, SecureField, CopyChip, ScreenHeader
app/    com.aiyu.clavis
        ├── MainActivity, ClavisApp, NavHost
        └── feature  onboarding, lock, cards, addcard, share, settings
```

## Data model

```
CardEntity (Room)
  id: String (PK, random UUID; part of the AEAD AAD, see D20)
  createdAt, updatedAt: Long
  sortKey: Int
  payload: ByteArray        // AEAD(CardRecord)
  cvvPayload: ByteArray?    // AEAD(cvv), separate subkey

CardRecord (in memory, serialized with kotlinx.serialization CBOR/JSON)
  nickname: String?
  holderName: String
  pan: String               // digits only
  expiryMonth: Int, expiryYear: Int
  network: CardNetwork      // VISA, MASTERCARD, RUPAY, AMEX, DISCOVER, DINERS, JCB, UNIONPAY, MAESTRO, UNKNOWN
  type: CardType            // CREDIT, DEBIT, PREPAID, UNKNOWN
  issuer: String?           // bank name
  country: String?          // ISO 3166-1 alpha-2
  colorSeed: Int            // card tint picked from issuer/network or user
  notes: String?
```

## Runtime state

```
VaultState: Locked | Unlocking | Unlocked(dek, authAt)
```

- `VaultSession` (singleton) owns the DEK and the last-auth timestamp. Everything that decrypts asks it. Locked means no DEK in memory.
- `AuthGate.requireFresh(maxAgeMs = 60_000)` suspends until the user passes biometric or PIN. Reveal, copy-CVV and share all call it.
- Lifecycle observer locks on `ON_STOP` after the timeout, and a screen-off receiver locks immediately.

## Navigation

```
Splash → Onboarding (first run only) → SecuritySetup (first run only)
       → Lock → Home (cards) → CardDetail → Share
                     ├─ AddCard (manual | scan → review)
                     └─ Settings
```

Back stack is cleared on lock. Predictive back is enabled on all inner screens (Rewire has `PredictiveBack.kt` to copy the approach from).

## Key flows

**First run**: intro pager → create PIN (enter + confirm) → optional biometric enable (BiometricPrompt enroll of `K_bio`) → vault created (random DEK, wrapped) → Home.

**Unlock**: biometric prompt or PIN pad → `VaultSession` receives DEK → Home. Wrong PIN increments throttle counter.

**Add by scan**: camera permission → CameraX preview → ML Kit frames → parser extracts PAN/expiry/name with Luhn check → user sees review form prefilled → save.

**Add by hand**: form with live grouping, network icon, Luhn status; issuer and type auto-fill from `BinLookup`; user can override all.

**Copy / share**: see `05-SHARE-AND-COPY.md`.

## Testing

- JVM unit tests for `core.card`, `core.crypto` (with Tink), throttle logic.
- Robolectric or instrumented tests for Room DAO with ciphertext.
- Compose UI tests for lock and add-card flows. Screenshots tests optional.
- Baseline profile later, as Rewire does.
- Test data: card numbers from `development/TempCards.md` only; test PIN `134679` on dev builds and emulators (never on a phone with real cards).

## Build and flavors

- `dev` and `prod` flavors, side by side, with a visible DEV badge on dev (pattern from Domus).
- Version file + changelog convention from Rewire (`VERSION`, `CHANGELOG.md`).
- Release signing via untracked `keystore.properties`.
