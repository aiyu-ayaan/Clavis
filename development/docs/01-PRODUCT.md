# 01 — Product

## Vision

Clavis is an Android-only, offline card vault. It stores a person's own debit and credit cards (number, holder, expiry, CVV, notes) behind a PIN and/or the system biometric, and makes them fast to copy and share without leaving the device unprotected.

Tagline (working): **Every card, locked tight.**

## Principles

1. **Offline for your data.** The only network use is the GitHub update check (D24), fenced to GitHub hosts by the network security config. Card data can leave the device only through an explicit user share.
2. **Secure by default, convenient on demand.** Every sensitive reveal is gated. Convenience (copy, share) works inside that gate, never around it.
3. **No surprises.** Anything that creates an unprotected copy (clipboard, shared image) is announced, scoped and auto-cleaned where Android allows.
4. **Simple surface.** Material 3 Expressive, few screens, large touch targets.

## Users

A single owner on a single device who wants their cards at hand without keeping them in notes apps or chat history. Not a shared-device or family vault in v1.

## Feature scope (v1)

| # | Feature | Summary |
|---|---------|---------|
| F1 | Onboarding | Full-screen pager intro, then mandatory security setup. |
| F2 | App lock | PIN is mandatory. Biometric is optional and layered on top, as UPI apps do. |
| F3 | Add card — manual | Form with live formatting, Luhn check, network detection. |
| F4 | Add card — scan | Camera OCR of number, expiry and name, on-device only. |
| F5 | Card intelligence | Network (Visa, Mastercard, RuPay, Amex, ...), type (debit/credit/prepaid), issuer bank, country, from the card prefix, offline. |
| F6 | Card list and detail | Card-shaped surfaces, masked by default, tap to reveal after auth. |
| F7 | Smart copy | One tap per field, copy-all, guided copy-paste sequence, auto-clearing clipboard. |
| F8 | Share | Choose with/without CVV, generate a card image plus text, send via the system share sheet. |
| F9 | Settings | Lock timeout, biometric toggle, change PIN, theme, clipboard clear delay, wipe vault. |

## Non-goals (v1)

- Making payments, NFC, tokenization or Google Wallet integration.
- Cloud sync, account system, online backup.
- Multi-user, sharing a vault, caregivers.
- Storing non-card secrets (passwords, IDs). Possible later.
- iOS, desktop, web.

## Key product decisions needing the owner's sign-off

These are the places where the request and good practice pull apart. Defaults are chosen; change them in `07-DECISIONS.md`.

1. **CVV storage.** The request is to store CVV. PCI DSS 4.0 requirement 3.3.1 forbids retaining sensitive authentication data (the CVV) after authorization, for entities that handle card payments. A private, offline vault for one's own cards is generally not a merchant or service provider, so the standard does not directly bind it. It is still the single most sensitive field. Default: **CVV is optional per card, stored in its own encrypted record, and revealed only after a fresh authentication.** Users see a one-line warning when they first save a CVV.
2. **Cards belonging to other people.** The app is positioned for the owner's own cards. Store listing and onboarding copy must say so.
3. **Share creates unprotected copies.** An image of a card in a chat is outside our control. Default: share requires fresh auth, defaults to "without CVV", and shows a warning sheet before every share.
4. **Google Play policy.** A financial-data app must complete the Data safety form (no data collected or shared is the accurate answer for an offline app) and avoid claims such as "bank-grade". Review Play's Financial Services and User Data policies before the first upload.

## Success criteria

- Cold start to card copied in under 5 seconds with biometric.
- No plaintext PAN or CVV ever written to disk, logs, clipboard history flagged non-sensitive, or backups.
- Passes an internal checklist of the threats in `02-SECURITY.md`.
