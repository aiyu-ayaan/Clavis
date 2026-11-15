# Changelog

## [0.0.1-alpha.2](https://github.com/aiyu-ayaan/Clavis/compare/v0.0.1-alpha.1...v0.0.1-alpha.2) (2026-11-15)

### Features

* Acknowledgements page under About
* Separate Updates screen with alpha/beta/stable channel, About screen with developer section
* Card faces in bank colours, with a bank/app colour picker
* Bank colour themes for card art, with distinct variants for cards from the same bank
* View options with cards, list and wallet layouts, grouping and a contacts-style index rail
* Card grouping and layout preferences for the home view

### Bug fixes

* No entrance replay when returning from a card, so the shared card does not flicker on landing
* Full-width sticky headers hide cards scrolling under them; wallet card collapses on empty-space tap or back
* No entrance replay on fast scroll (wallet gaps), new layout or grouping starts at the top

### Other changes

* Release next alpha
* Add launch video and README gif
* Dev and prod flavors, DEV badge on home, CI releases prodRelease
* Test PANs from TempCards.md and test PIN 134679 for QA

## 0.0.1-alpha.1 (2026-11-15)

### Features

* In-app updater from GitHub releases, fenced to GitHub by a network security config (D24)
* Forgot PIN resets with the phone's screen lock or erases the vault
* Sleeve logo, adaptive launcher icon and part-by-part animated splash
* Read card number, expiry and name with CameraX and on-device OCR, network permissions stripped
* Detect bank, network and credit/debit type offline from a bundled BIN table
* Add landing, PIN setup, lock, cards, detail, copy, share and settings with shared transitions
* Add brand palette, card surface, PIN pad, morphing mark and shared-transition helpers
* Add vault crypto, card network detection, encrypted card store and settings
* Scaffold Android project with app, ui-common and core modules

### Bug fixes

* Keep card list scroll position across reloads and screen round-trips
* Status bar icons follow the app theme, no crash when locking from home, clearer change-PIN prompt
* Keep card number on one line, stop preview padding past 16 digits, adapt form and PIN pad to dense screens and large fonts
* Dark-mode stripe colors and delete the shared image when the share sheet returns

### Other changes

* Check networkSecurityConfig in the manifest, not the renamed res file
* First version is 0.0.1-alpha.1
* Logo in brand colors (pale blue on ink)
* Release branch is master
* Release signing from env/keystore.properties, smaller APK (arm64, compressed libs, en only), keep ML Kit registrars under R8
* Add README and MIT license
* Add reasoning contract bytecode for decision rule processing
* Add logo concept explorations (rounds 1-3) with generator scripts
* Wire Hilt, Room, Navigation, Tink, biometric dependencies
* Write project CLAUDE.md
* Add phased TODO
* Add decision log and docs index
* Add share/copy flows and Material 3 Expressive UX design
* Add architecture and card intelligence research
* Add product scope and security design
* Scaffold repo tooling and agent config

