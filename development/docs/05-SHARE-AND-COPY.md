# 05 — Smart copy and share

## Smart copy

Goal: get a card into a checkout form in a few taps, without leaving the number sitting on the clipboard.

### Behaviors

1. **Tap a field to copy it.** Number, expiry, holder, CVV. Each field row has a copy affordance and a ripple confirmation.
2. **Format choice.** Number copies as digits only by default (what most payment fields accept). Long-press offers "with spaces". Expiry offers `MM/YY` and `MM/YYYY`.
3. **Copy sequence ("guided paste").** One button on the card: "Copy step by step". A persistent notification-free bottom sheet shows the 4 fields in order; each tap copies the next and marks the previous done. Lets the user alternate between this app and a checkout page without retyping.
4. **Copy all as text.** Multi-line block (see share text below) for pasting into a note or message. Subject to the same warnings as share.
5. **Gating.** Number and CVV copies need fresh auth (see `02-SECURITY.md`). Holder and expiry copy within the unlocked session.
6. **Clipboard hygiene.** Sensitive flag on every clip, auto-clear after the configured delay, an undo-style snackbar "Copied number • clears in 30 s" with a "Clear now" action. No value text in the snackbar.

### Not in v1

- Android Autofill service for payment cards. It would remove the clipboard entirely, but a filling service has its own trust surface and requires user enablement in system settings. Strong v2 candidate.
- Floating overlay copy buttons. Overlay permission is a security and review risk.

## Share

### Flow

```
Card detail → Share
  → fresh auth
  → Share options sheet
       [ ] Include CVV            (off by default)
       [ ] Include holder name    (on)
       Image style: Card front | Minimal
       Preview (masked? no: shows exactly what will be sent)
  → warning line: "Anyone who receives this can use the card."
  → [Share]
  → system share sheet with image + text
  → cache cleaned when it returns
```

The user's request, restated: on share, ask whether to include the CVV, then produce an image and send both the image and text.

### Text payload

```
Card: HDFC Bank Visa Debit
Name: A B Sharma
Number: 4242 4242 4242 4242
Expiry: 08/29
CVV: 123          ← only if the option was chosen
```

Labels are plain, locale-independent, and the format avoids anything a chat app might auto-link badly. If CVV is off the line is absent, not blank.

### Image payload

- Rendered off-screen with `Canvas` onto an ARGB bitmap, 1080 px wide, card ratio 1.586:1, using the same card design as the in-app surface (network mark, issuer tint, chip glyph, number, expiry, holder). CVV, if included, is drawn on a "back" panel in the same image (side by side or stacked) so the card is one self-contained picture.
- PNG written to `cache/shared/clavis-card-<random>.png`. Random name, no card data in the filename.
- Exposed with `FileProvider` (`authorities = ${applicationId}.fileprovider`) and `FLAG_GRANT_READ_URI_PERMISSION`. Not saved to the gallery.
- Sent as `ACTION_SEND` with `EXTRA_STREAM` (the image) and `EXTRA_TEXT` (the text), `type = image/png`, wrapped in `Intent.createChooser`. Mixed image + text works in most targets but not all: some apps drop the text or use it as the caption (WhatsApp and Telegram use it as caption, some email clients attach only the image). Provide a secondary "Share text only" action so the user can send what they intended.
- `ChooserTargets` and sharing shortcuts are not customized. The chooser's own "copy" action is a clipboard path outside our control; the warning dialog mentions it.

### Safety rules

- Fresh auth before the options sheet and before the final Share tap if more than 60 s passed.
- Default for CVV is off, remembered **never** (reset on every share), so a past choice cannot silently apply.
- Delete the image from cache when the chooser returns, on lock, on app start, and on process death recovery.
- Warn that recipients' chat backups and the share target's retention are outside the app's control.
- Disable sharing entirely if the user turns on a "Never share" setting (optional hardening toggle).

### Accessibility

The generated image has no alt text, so the text payload is the accessible form. In-app, the share options sheet announces "CVV will be included" when toggled on.
