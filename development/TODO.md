# Clavis TODO

Legend: `[x]` done, `[ ]` open, **(owner)** needs a decision from the owner. Design lives in `development/docs/`. Decision IDs (D#, V#) refer to `docs/07-DECISIONS.md`.

## Phase 0 — Research and scaffold

- [x] Product scope, security design, architecture, card intelligence, share/copy, UX docs
- [x] Gradle project with `:app`, `:ui-common`, `:core`; strings only in `:ui-common`
- [x] `isLuhnValid` + unit test
- [ ] **(owner)** Confirm D14 (CVV stance), D15 (KDF, PBKDF2 chosen as default), D17 (name/package), V9 (intro meaning)
- [ ] Verify V3, V4, V5, V7, V8 from `07-DECISIONS.md`

## Phase 1 — Foundation

- [x] Brand: color seed (navy #1E3A5F + emerald tertiary), tonal palette
- [ ] Brand: launcher icon (adaptive + monochrome), splash mark
- [x] `ui-common`: `Motion.kt` + `rememberReducedMotion`, `InnerScreen`, predictive back, shared-transition helpers (port from Rewire)
- [x] Add Hilt, Room, DataStore, Navigation Compose to the catalog use sites
- [ ] `dev` / `prod` flavors, DEV badge
- [x] `NavHost` skeleton with all routes from `03-ARCHITECTURE.md`
- [x] `FLAG_SECURE`, `setRecentsScreenshotEnabled(false)` (API 33+)
- [ ] Sensitive-content flag (after V3)
- [ ] CI: assemble, unit tests, lint, manifest check that `INTERNET` is absent

## Phase 2 — Vault and lock

- [x] `KeyManager`: Keystore `K_device`, `K_bio` (StrongBox when available)
- [x] `PinKdf` (Argon2id or PBKDF2 per D15) + tests
- [x] DEK generation, wrap/unwrap for PIN and biometric paths
- [x] `VaultSession` (Locked/Unlocked), auto-lock on stop timeout and screen off
- [x] `AuthGate.requireFresh(60 s)`
- [x] Throttle schedule persisted against elapsed realtime + tests
- [ ] Optional wipe after 10 failures
- [x] Biometric enroll/invalidations handling (fallback to PIN, re-enable flow)
- [x] Change PIN (re-wrap only), erase vault
- [x] Tink AEAD wrapper with AAD = id + field + schema version + tests (round trip, AAD mismatch)

## Phase 3 — Onboarding and lock UI

- [x] Intro pager (3 pages, expressive illustrations)
- [x] Security setup stepper: create PIN, confirm PIN, optional biometric
- [x] `PinPad` component (72 dp keys, haptics, shake, a11y)
- [x] Lock screen with lockout countdown and auto biometric prompt

## Phase 4 — Cards

- [x] Room schema, `CardEntity`, `CardDao`, repository (ciphertext in, `CardRecord` out)
- [x] `CardRecord` serialization + schema versioning
- [x] Home: collapsing large top bar, `CardSurface` list, empty state, FAB menu
- [x] Add card (manual): live preview, grouping per network, Luhn, expiry validation, CVV optional with warning
- [x] Card detail: mask/reveal with 20 s timer, edit, delete with fresh auth
- [ ] Large-screen list-detail layout

## Phase 5 — Card intelligence

- [x] `NetworkDetector` data-driven table + tests with published test PANs (V5)
- [x] BIN table build script (CSV → `bins.bin`), `BinLookup` (6-digit; 8-digit when data exists) + tests
- [ ] Curated Indian issuer layer
- [x] Detected chips (bank, network, type) editable in the form
- [ ] Scan: CameraX preview, ML Kit bundled OCR, `CardTextParser`, 3-of-5 stability filter, torch, guide frame
- [ ] Parser tests from recorded text fixtures (no real card numbers)

## Phase 6 — Copy and share

- [x] Field copy with sensitive clip flag, auto-clear timer, snackbar with "Clear now"
- [x] Copy-all text, step-by-step copy sheet
- [x] Share options sheet (CVV off by default, resets each time), preview
- [x] Bitmap renderer (front + optional back panel), PNG to `cache/shared/`, `FileProvider`
- [x] `ACTION_SEND` image + text, text-only fallback, cache cleanup on return / lock / start
- [x] Optional "disable sharing" setting

## Phase 7 — Settings, polish, hardening

- [x] Settings screens per `06-UX-DESIGN.md`
- [x] Theme (system/light/dark), dynamic color toggle
- [ ] Locale folders in `ui-common` (hi, etc.), RTL check
- [ ] TalkBack pass: masked descriptions, reveal announcements
- [ ] R8 release, strip `Log` calls, verify `adb backup` empty, rooted-emulator storage inspection
- [ ] Security checklist from `02-SECURITY.md` verification plan
- [ ] Baseline profile

## Phase 8 — Release

- [ ] Play listing: privacy policy (no data collected), Data safety form, financial-policy review (V7)
- [ ] Signing config, `VERSION` + `CHANGELOG.md` flow (Rewire pattern)
- [ ] Internal testing track, then production

## Later (v2 candidates)

- [ ] Android Autofill service for payment cards (D19)
- [ ] Encrypted passphrase export/import (D18)
- [ ] Non-card secrets (IDs, bank accounts)
- [ ] Optional Play-services card recognition path
