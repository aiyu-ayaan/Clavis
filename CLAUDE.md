# Clavis

Android-only, offline card vault. Stores the owner's cards (number, expiry, holder, optional CVV) behind a mandatory PIN and optional biometric, with smart copy and a safe share flow (image + text). Material 3 Expressive UI.

Status: scaffold builds, no features yet. Design is complete and lives in `development/docs/`. Task list is `development/TODO.md`.

## Read first

| Need | File |
|------|------|
| Scope, non-goals, open sign-offs | `development/docs/01-PRODUCT.md` |
| Threat model, keys, storage, auth | `development/docs/02-SECURITY.md` |
| Modules, data model, flows | `development/docs/03-ARCHITECTURE.md` |
| Scan, network/BIN detection | `development/docs/04-CARD-INTELLIGENCE.md` |
| Copy and share behavior | `development/docs/05-SHARE-AND-COPY.md` |
| Theme, screens, a11y | `development/docs/06-UX-DESIGN.md` |
| Decisions, facts to verify, sources | `development/docs/07-DECISIONS.md` |

Update the matching doc and `TODO.md` in the same commit as any change that alters them.

## Modules

- `:app` (`com.aiyu.clavis`): activity, manifest, nav, feature screens and view models.
- `:ui-common` (`com.aiyu.clavis.ui`): theme, shared components, **all `strings.xml`**.
- `:core` (`com.aiyu.clavis.core`): card logic, crypto, data. No UI, no string resources.

## Hard rules

1. **No `INTERNET` permission, ever.** No analytics, crash, ads or any networking SDK. A change that needs the network needs an entry in `07-DECISIONS.md` first.
2. **All user-visible strings live in `ui-common/src/main/res/values/strings.xml`** (plus its locale folders). Never add `strings.xml` to `:app` or `:core`. Reference with `com.aiyu.clavis.ui.R`.
3. **Card data never touches disk, logs, backups or test fixtures in plaintext.** Only AEAD ciphertext is persisted. Tests use published processor test PANs (e.g. `4242424242424242`), never real cards.
4. **No `security-crypto` (deprecated).** Use Android Keystore + Tink. Key hierarchy in `02-SECURITY.md` is a contract.
5. **Reveal, copy-CVV and share require fresh auth** (within 60 s). Share defaults to CVV off and resets each time.
6. **Every clipboard write sets the sensitive flag** and schedules auto-clear.
7. `FLAG_SECURE` stays on. `allowBackup` stays false.
8. No hardcoded colors in components; use `MaterialTheme.colorScheme`. Follow Rewire's theme pattern (`../Rewire/app/src/main/java/com/aiyu/rewire/ui/theme/`).
9. No PIN recovery. Do not add one.

## Stack

Kotlin, Compose Material3 Expressive (`1.5.0-alpha26`, pinned in `gradle/libs.versions.toml`), min SDK 28, target 36, compile 37. Planned: Hilt, Room (encrypted payload columns), DataStore, Navigation Compose, CameraX, ML Kit bundled text recognition, `androidx.biometric`, Tink. Versions mirror the sibling app `../Rewire`.

## Commands

```bash
./gradlew :app:assembleDebug          # debug APK, installs as com.aiyu.clavis.dev
./gradlew :core:testDebugUnitTest     # JVM unit tests
./gradlew --offline ...               # works when Rewire already cached the dependencies
```

Local `local.properties` (SDK path) and `keystore.properties` (release signing) are gitignored.

## Conventions

- Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`). One logical stage per commit; check `git log` before committing.
- Code reference apps: `../Rewire` (Compose, theme, nav, release flow), `../domus` (design-system docs, dev/prod flavors).
- CodeGraph is available when a `.codegraph/` index exists; none does here yet.
- Open owner decisions are marked **Open** in `07-DECISIONS.md` (CVV stance, KDF, BIN data source, name/package). Do not silently pick; record the choice.
