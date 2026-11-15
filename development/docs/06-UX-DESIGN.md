# 06 — UX and Material 3 Expressive design

Reference apps: **Rewire** (`../Rewire`, Compose + `MaterialExpressiveTheme`) for the Android implementation patterns. **Domus** (`../domus`, `design-system/domus`) for design-system documentation style. Clavis follows Rewire's theme setup closely.

## Design language

Material 3 Expressive: bold shape contrast, springy motion, tonal color, large type for hero content. Calm and trustworthy for a security product: expressive in motion and shape, restrained in color.

### Theme (copy the Rewire structure)

| Piece | Rewire reference | Clavis choice |
|-------|------------------|---------------|
| Entry | `ui/theme/Theme.kt` `RewireTheme` → `MaterialExpressiveTheme` | `ClavisTheme` with `MotionScheme.expressive()`. |
| Color | `Color.kt` brand schemes + dynamic color from wallpaper on API 31+ | Brand seed in deep indigo/teal family (security, calm). Dynamic color **on by default**, toggle in Settings. Dark/Light/System. |
| Shape | `Shape.kt` increased corner scale: 8/12/20/28/36 dp | Same scale. Cards use `extraLarge` (36 dp). Buttons are pill/rounded; selected state morphs shape (expressive button groups). |
| Type | `Type.kt`: heavier display/headline weights, tighter tracking | Same. Add a tabular-figures style (`fontFeatureSettings = "tnum"`) for card numbers so digits align. Card numbers use a monospace-feel style with 4-4-4-4 grouping. |
| Motion | `Motion.kt` + `rememberReducedMotion()` | Reuse. Respect "Remove animations". |
| Accents | `AccentTheme` per tab | Optional: tint card detail with the card's own color. |

Do not hardcode colors in components. Use `MaterialTheme.colorScheme` roles only. Verify contrast of text on every card tint in both modes.

### Expressive components to use

- `LargeFlexibleTopAppBar` / collapsing large header on Home.
- `FloatingActionButtonMenu` or `ExtendedFloatingActionButton` on Home for **Add card** (menu items: Scan card, Enter manually).
- `ButtonGroup` / connected button group for network/type pickers and share image-style choice.
- `LoadingIndicator` (expressive) for OCR processing and unlock.
- `MorphingShape` pattern from Rewire's `components/MorphingShape.kt` for the splash/lock icon morph.
- `Switch` with icon thumb, `SegmentedButton`, `ModalBottomSheet` for share options, `Snackbar`.
- Wavy progress is not needed.
- Navigation: single-level, so no bottom bar needed in v1. Top bar with Settings icon.

Expressive APIs are alpha. If an API shifts, fall back to the stable component with the same role.

## Screens

### 1. Splash
System splash (`core-splashscreen`) with the app mark, hands off to Intro on first run, else Lock.

### 2. Intro (landing pager) — "pull intro"
Full-screen `HorizontalPager`, 3 pages, large expressive illustration (morphing shapes), title, one line of copy, page indicator, Skip and Next/Get started.

1. **Your cards, one safe place.** Everything stays on this device.
2. **Locked by you.** PIN plus fingerprint. No account, no cloud, no internet permission.
3. **Fast when you need it.** Tap to copy, share safely.

Finishes into Security setup. Skip also lands there; setup cannot be skipped.

### 3. Security setup
Step 1: create PIN (6 dots, `PinPad`, shake on mismatch). Step 2: confirm PIN. Step 3: "Use fingerprint too?" with system biometric prompt. Copy states plainly: "If you forget your PIN, your cards cannot be recovered."
Progress shown as a three-step stepper. The UPI-app convention applies: PIN is the base, biometric is the shortcut.

### 4. Lock
Centered mark, 6 PIN dots, number pad, biometric button that prompts automatically on entry (user setting). Lockout state shows a countdown. No card data, no hints.

### 5. Home (cards)
- Large collapsing top bar "Cards" with Settings action.
- Vertical stack of `CardSurface` (the list is a wallet, not a table). Each shows network mark, nickname, masked number, expiry, issuer tint. Overlapping stack when collapsed is optional polish.
- Tap opens detail with a shared-element transition.
- Empty state: expressive illustration, "Add your first card", primary button.
- FAB menu: Scan card, Enter manually.

### 6. Add card
**Manual**: card preview at top that updates live as the user types; below it fields: Number, Expiry, CVV (optional), Holder, Nickname, plus "Detected" chips for Bank, Network, Type (tap to edit). Primary Save. Luhn error shown inline, not blocking until Save.
**Scan**: full-bleed camera, ID-1 guide frame, torch, "Enter manually" text button, haptic and morph on lock-in, goes to the manual form prefilled for review.

### 7. Card detail
Large card surface (masked). Actions row: **Reveal** (auth), **Copy**, **Share**, **Edit**, **Delete**. Below: field rows with copy buttons (number, expiry, holder, CVV), detected metadata, notes. Reveal auto-masks after 20 s with a thin countdown. Delete asks for confirmation and fresh auth.

### 8. Share sheet
`ModalBottomSheet` described in `05-SHARE-AND-COPY.md`. Live preview of the image that will be sent, CVV switch, image style button group, warning text, Share button.

### 9. Settings
Sections: **Security** (change PIN, biometric toggle, auto-lock delay, wipe vault), **Privacy** (clipboard auto-clear, never-share toggle), **Appearance** (theme, dynamic color), **About** (version, open-source licenses, "This app has no internet permission").

## Card surface spec

- Aspect ratio 1.586:1, `extraLarge` shape, tonal gradient from the issuer or network color derived through the color scheme (no raw hex in components).
- Content: network mark (top right), chip glyph, grouped number, `VALID THRU` and expiry, holder. Nickname above the number when set.
- Masked state: groups 1–3 as bullets, group 4 shown. Amex groups 4-6-5.
- Elevated only on press. Uses the surface-container tonal system, not heavy shadows.

## Interaction and feedback

- Haptics: light tick on digit entry, confirm on copy, reject on wrong PIN.
- Every sensitive action gets visible confirmation (snackbar or inline check).
- Shake plus color change on wrong PIN, also announced for screen readers.
- Predictive back on inner screens; back from Home does not exit to a revealed state: it locks first if configured.

## Accessibility

- Touch targets at least 48 dp. PIN keys 72 dp.
- TalkBack: card contents are announced masked ("Visa ending 4242"). Reveal announces the state change. Never expose the full number in `contentDescription` while masked.
- `FLAG_SECURE` also hides content from screen readers that rely on screenshots; TalkBack uses accessibility nodes so it still works. Verify.
- Dynamic type to 200%: card surface scales text, layout reflows to a taller card.
- Color is never the only signal (Luhn error has icon and text).
- Reduced motion: replace springs and morphs with fades.

## Adaptive layout

Phone first. On large screens (foldables, tablets) use list-detail (Rewire has `components/ListDetail.kt`): cards list on the left, detail on the right, with the same security rules.

## Reference to copy from Rewire

`ui/theme/{Theme,Color,Shape,Type,Motion}.kt`, `ui/components/{ScreenHeader,InnerScreen,MorphingShape,PredictiveBack,ListDetail,Common}.kt`, `ui/MainScreen.kt` and `RewireNavHost.kt` for nav structure, the `libs.versions.toml` version catalog, and its `CLAUDE.md` for how product docs are phrased.
