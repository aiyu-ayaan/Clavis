# 04 — Card intelligence (scan and metadata)

## Entry methods

1. **Manual typing.** Live formatting, Luhn validation, network icon appears after the first digits.
2. **Scan.** Camera OCR, then the same review form. The user always confirms before saving. OCR output is a suggestion.

## Scan pipeline

```
CameraX Preview + ImageAnalysis (back camera, 720p-1080p, STRATEGY_KEEP_ONLY_LATEST)
  → ML Kit TextRecognizer (Latin, bundled model)
  → CardTextParser
  → stability filter (same PAN in N consecutive frames)
  → Review form
```

Design notes:

- Guide frame in ID-1 card ratio (85.60 × 53.98 mm, about 1.586:1). Only text inside the frame is parsed, which cuts false matches.
- `CardTextParser` rules:
  - PAN: digit runs, spaces or dashes removed, length 13–19, must pass Luhn. Prefer 16, then 15 (Amex), then 19.
  - Expiry: `MM/YY` or `MM/YYYY` with month 01–12; accept `VALID THRU`, `EXP`, `GOOD THRU` neighbors; ignore "member since" dates; reject already-expired values with a soft warning.
  - Name: uppercase letter line below the PAN, 2–26 chars, excluding bank/network words.
  - CVV is never read by OCR. Printed CVV is on the back and is always typed.
- Embossed or low-contrast cards are the main failure mode. Provide a torch toggle and a "type instead" button always visible.
- Frames are processed in memory and never saved. The camera surface is covered by `FLAG_SECURE` like the rest of the app.
- Stability filter: accept when the same Luhn-valid PAN appears in 3 of the last 5 frames. This is the biggest accuracy lever for the effort.

## Network detection

Pure function on the digit prefix. Rules below are the commonly published ranges. Verify against each scheme's current public documentation before shipping and keep them in a data-driven table with tests.

| Network | Prefix (IIN) | Length | CVV length |
|---------|--------------|--------|------------|
| Visa | 4 | 13, 16, 19 | 3 |
| Mastercard | 51–55, 2221–2720 | 16 | 3 |
| American Express | 34, 37 | 15 | 4 |
| RuPay | 60, 65 (6521, 6522), 81, 82, 508 | 16 | 3 |
| Discover | 6011, 644–649, 65, 622126–622925 | 16–19 | 3 |
| Diners Club | 300–305, 36, 38, 39 | 14–19 | 3 |
| JCB | 3528–3589 | 16–19 | 3 |
| UnionPay | 62 | 16–19 | 3 |
| Maestro | 50, 56–69 (not claimed by the above) | 12–19 | 3 |

Ambiguity: RuPay and Discover share `65`. Order of rules matters; resolve with the longer, more specific prefix first, then fall back to BIN lookup data. Co-badged cards will report one network; the user can override.

Validation: Luhn on every PAN. Length checked per network. CVV length follows the network.

## Issuer, type and country (bank metadata)

The network comes from the prefix alone. **Bank name, debit/credit and country need a BIN (IIN) table.** BINs are the first 6 digits, and since 2022 up to 8 digits (ISO/IEC 7812), so the table must support both lengths with longest-prefix match.

Options:

| Option | Offline | Privacy | Cost | Verdict |
|--------|---------|---------|------|---------|
| Bundled BIN table in the APK | yes | no leak | data license | **Chosen** |
| Public lookup API (e.g. binlist.net) | no | sends first 6–8 digits to a third party and needs `INTERNET` | free tier, rate limits | Rejected: breaks the no-network guarantee. |
| Commercial BIN database | yes | no leak | paid | Option if accuracy is critical. |
| Scheme APIs (Mastercard BIN lookup) | no | needs network and onboarding | | Rejected. |

Findings from research:

- Community datasets exist (`binlist/data` on GitHub, `smartbindb` on PyPI with an MIT license). None found with a clearly permissive license **and** reliable 8-digit coverage. The binlist repo itself says it is not an official register and use is at the user's risk. Check licenses before bundling.
- Indian cards matter for this user base: RuPay, SBI, HDFC, ICICI, Axis, etc. Coverage of Indian issuers in free sets is uneven. A small curated, hand-verified Indian issuer table layered over the general table is a pragmatic fix.

Chosen (D16): **BIN List Data** (CC BY 4.0), 6-digit rows with brand, debit/credit/prepaid, issuer and country. `tools/build_bin_table.py` turns the CSV into `core/src/main/assets/bins.bin` (8 bytes per BIN plus issuer and country dictionaries, about 3.4 MB). `BinLookup` binary-searches it in memory. In the add form the bank, type, country and network follow the first six digits until the user edits them. A prepaid category wins over the debit/credit column.

Packaging the table:

- Build step converts the CSV to a compact sorted binary (`bins.bin`) with a prefix index, shipped under `assets/` or `res/raw`.
- Lookup: binary search on prefix, longest match wins (8 → 6 digits).
- Record: `prefix, network, type, issuerName, countryCode`. Expected size: a few MB at most after compression.
- Update path: ship a new table with each app release. No in-app downloads.
- Unknown prefix: leave issuer and type empty and let the user type them. Never block saving.
- Always show metadata as "detected", editable. Wrong guesses are easy to fix, silent wrong data is not.

## Card art (D25)

No card images are fetched or bundled. Wallet apps get real card art through network token services, which need onboarding and send the card to a server, and apps that fetch art by BIN leak the prefix (D7). Bank logos are trademarks (V8).

Instead, `core/card/CardArt.kt` maps the issuer to a base colour:

- `IssuerBrand.of(issuer)` matches whole words in the normalised issuer name (BIN table or typed), first match wins, so "State Bank of India" and "Union Bank of India" come before "Bank of India". About 30 banks: the largest Indian issuers in `bins.bin` plus major US/UK/global ones. Each holds only its public brand colour.
- No match: one base colour per network (`networkArt`).
- Several cards from one bank: each theme has `ART_VARIANTS` (4) variants (classic, deep, mist, graphite), each with its own ring pattern. A new card gets the variant least used in its family (same bank theme, else same issuer text, else same network); a fifth card reuses one and its last four still tell it apart. The variant is stored in the encrypted record (`CardRecord.artVariant`); null keeps the older app-theme tint (`colorSeed`), so existing cards do not change until edited.
- The bank's name shows as text and the network as the text mark. No logos.

## Network marks

Network logos (Visa, Mastercard, etc.) are trademarks with brand-use rules. For a personal tool, prefer simple text or neutral glyph treatments, and check each scheme's brand guidance before using official marks in a published Play listing.

## Test data

Use the published test PANs from payment processors' docs in unit tests and manual QA. The list lives in `development/TempCards.md` (PayPal's test numbers: Amex, Diners, Discover, JCB, Mastercard, Visa including the 13-digit `4222222222222`, Dankort, Switch/Solo); `4242 4242 4242 4242` is also fine. Never commit real card numbers.
