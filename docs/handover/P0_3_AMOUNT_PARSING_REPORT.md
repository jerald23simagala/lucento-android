# P0-3 — Global monetary amount parsing

**Date:** 2026-11-01 · **Branch:** `feature/ai-development-workflow` (uncommitted) · **Environment:** `lucento-test` only;
no production access, no schema change, no stored record modified.
**Evidence:** `artifacts/p0-3-amount-parsing/` (device log and screenshots B-80…B-89). Unit tests in
`app/src/test/.../core/money/MoneyParserTest.kt`, `domain/capture/CaptureAmountParsingTest.kt`,
`data/ai/RemoteCandidatesTest.kt`.

## 1. Root cause of the original defect

`MoneyParser.parse` (the only parser for typed amounts) took the phone's decimal separator as fact and **deleted every
occurrence of the other separator** as a "thousands separator", wherever it was and however many digits followed.

| Phone | Typed | Read as | Stored |
|---|---|---|---|
| en-US | `12,50` | `1250` | $1,250.00 (the reported defect, O-13) |
| de-DE | `12.50` | `1250` | €1,250.00 |
| de-DE | `1.23` | `123` | €123.00 |
| any | `12 50` | `1250` | ×100 |

No grouping position was checked and no ambiguity existed as a result.

**Two further defects found while mapping.** Both are fixed:
- The composer's clause splitter treated a comma between digits as a clause break unless exactly three digits followed
  it. So `coffee 12,50` became **two rows**, 12 and 50.
- The AI path accepted any `BigDecimal` the model returned, with no format check and no cross-check against the
  message.

**Confirmed existing data affected (QA data only).** On `lucento-test`, QA account A's wallet **QA-Cash** has an
opening balance of **$1,250.00**. This is the O-13 entry, where `12,50` was typed. It was **not modified**, as required.

## 2. Parsing architecture before P0-3

| Path | Parser | Note |
|---|---|---|
| Transaction and transfer editor, wallet create/edit and opening balance, balance adjust, budgets, goals, debts, schedules, guided setup | `MoneyParser` | the defect above |
| Composer (the only way to add transactions) | `QuickEntryParser.normalizeNumber`, then `BatchReview` re-parses with `MoneyParser` | its own, locale-blind rules |
| Receipt OCR | `ReceiptParser.interpret`, then `BatchReview` | own, currency-aware heuristics |
| Smarter reading (Gemini) | `BigDecimal(model output)`, then `BatchReview` | unchecked |

Every path ends in `MoneyParser` before anything is saved, so the central parser is the one boundary that must be
right.

## 3. New strategy (one rule set, in `core/money/MoneyParser.kt`)

`parse(input, currencyCode, decimalSeparator, locale)` returns one of: `Success(Money)`, `Empty`, `Invalid`,
`TooManyDecimals(allowed)`, `TooLarge`, **`Ambiguous(options)`** or **`WrongCurrency(walletCurrency)`**.
`readNumber(...)` is the same rule set without currency marks, shared with the composer.

**How a lone `.` or `,` is read.** Here `k` is the number of digits after the mark and `p` the number before it.

| Case | Result |
|---|---|
| The mark is the **phone's decimal mark**, `k ≤` the currency's decimals | decimal |
| … `k >` decimals and the extra digits are zeros, and `k = 3` with `p` 1–3 (`1.000`) | **Ambiguous** (1.00 or 1,000) |
| … `k >` decimals, zeros, not a thousands shape (`12.5000`) | decimal |
| … `k >` decimals, non-zero | `TooManyDecimals`. **Never re-read as thousands.** |
| The **other** mark: thousands only if `k = 3`, `p` 1–3, no leading zero; decimal only if `k ≤` decimals (no zero dropping) | one valid → that reading; both → **Ambiguous**; neither → `TooManyDecimals` |

**Other rules:**
- **Both marks** (`1,234.56`, `1.234,56`): the last one is the decimal mark and must appear once; the other is
  validated grouping.
- **A repeated mark** is grouping. It is validated as Western groups of three or Indian lakh grouping (`1,23,456`).
  The phone's own decimal mark can never repeat.
- **Space, no-break space, narrow no-break space, thin space and apostrophe** are grouping only, and validated. A single
  `.` or `,` alongside them is the decimal mark. Zeros past the precision are dropped only after the phone's own mark.
- **Unicode digits** are accepted (`Character.digit`). The Arabic decimal mark `٫` is the decimal mark; `٬` is grouping.
- **Rejected:** a leading `+` is allowed once; `-`, `−` and parentheses give `Invalid`.
- **Limits:** more than 24 digits, or more than 1,000,000,000,000 major units, gives `TooLarge`. Digits are counted
  before any `BigDecimal` is built. Conversion is `movePointRight` + `longValueExact`, with no rounding.

**Outcomes under these rules:**
- `12,50` and `12.50` are **12.50 on every phone**.
- en-US `1,000` USD is 1000; fr-FR `1.000` EUR is 1000.
- en-US `1.000` USD and de-DE `1,000` EUR are **Ambiguous**.
- en-US `1,234` BHD is Ambiguous. BHD has three decimals, so both readings are valid.
- de-DE `1,234` EUR is `TooManyDecimals`, never 1234.
- en-US `0,500` is `TooManyDecimals`, never 500.

## 4. Locale and currency handling

- **Locale sets the convention:** callers pass `MoneyFormatter.decimalSeparator()`. The phone's decimal mark is
  trusted; the other mark has to earn its reading.
- **Currency sets precision:** `CurrencyRules.fractionDigits` (ISO 4217 through `java.util.Currency`; 0 for JPY, 3 for
  BHD; unknown codes fall back to 2).
- **Currency marks:**
  - A code or symbol is stripped only if it belongs to the wallet's currency, as any locale writes it (`$`, `US$`,
    `€`, `₱`, `¥`, `￥`, `kr.`, `R$`…).
  - Another currency's mark gives `WrongCurrency`, with a message naming the wallet's currency.
  - Nothing is ever inferred or converted from a symbol.
- **Prefilled edit text** (`MoneyFormatter.formatPlain`) now uses ASCII digits and the same decimal mark the parser
  receives, so a prefill always parses back to itself, including in ar/fa/hi locales.

## 5. Ambiguity resolution and UI

- **Every amount screen** (transaction, transfer, wallet, balance adjust, budgets, goals, schedules, debt instalment,
  guided budgets, composer review) shows **one** shared message from `MoneyParser.clarification`. For example:
  *"This could be $1.00 or $1,000.00. Type “1” or “1000” to say which."* Each suggested rewrite parses to exactly one
  value. The typed text is kept, nothing is saved, and the field is marked as an error in words, not colour alone.
  At 200% font the message wraps under the field (B-83).
- **Composer:** a number with two readings becomes the existing one-tap choice ("Use 1.5 / Use 1500"), and saving is
  blocked until one is picked (B-86).
- **The composer never guesses:**
  - A space-grouped number on a phone that doesn't group with spaces is offered both ways.
  - A number it can't read ("12.345" on a 2-decimal account, "2.5L") keeps its row and never lets another number take
    its place.

## 6. Financial precision safeguards

- Storage is unchanged: `Long` minor units with an ISO code, with no `Float` or `Double` (BigDecimal only for decimal
  conversion).
- No silent rounding or truncation: decimals beyond the currency's precision are rejected unless they are zeros after
  the phone's own decimal mark.
- Overflow: digit cap, 1e12 major-unit cap, `longValueExact`. 1e12 in a 3-decimal currency is 1e15 minor units, well
  within a `Long`.
- Unchanged: Room schema, sync, calculations and stored data. No migration.

## 7. Entry points covered

| Entry point | Parse path | Ambiguity shown | Device |
|---|---|---|---|
| Composer, typed or spoken (the only add path) | `QuickEntryParser.scanAmounts` → `MoneyParser.readNumber`, then `BatchReview` → `MoneyParser.parse` | one-tap choice / row message | ✓ B-86, B-87 |
| Smarter reading (AI) | canonical `^\d{1,13}(\.\d{1,3})?$` only; cross-checked with the device's own readings of the message | AMOUNT_AMBIGUOUS / "check the amount" | unit |
| Receipt OCR | `ReceiptParser` (unchanged except leading-zero fix), then `BatchReview` | row message | unit |
| Transaction editor (edit), transfer editor | `MoneyParser.parse` | `AmountUnclear` | unit |
| Wallet create/edit opening balance | `MoneyParser.parse` | `AmountUnclear` | ✓ B-80…B-83, B-88 (de-DE) |
| Balance adjust | `MoneyParser.parse` | `AmountUnclear` | unit |
| Debt instalment | `MoneyParser.parse` | `InstallmentUnclear` | unit |
| Budgets, goals, schedules (`parsePositive`, `parseInterest`) | `MoneyParser.parse` | `AmountError.Unclear` | unit |
| Guided-setup budgets | `MoneyParser.parseOrNull` + clarification | message | ✓ B-85 |
| CSV/JSON import | Backup restore reads stored minor units, not typed text | n/a | — |

The input filters that silently dropped characters (the transaction editor and `MoneyField`) now only limit length.
They had turned "۱۲٫۵" into 125.

## 8. Regression results

| Check | Result |
|---|---|
| `testDebugUnitTest` | **671 passed, 0 failed, 0 errors** (634 before P0-3; 5 old parser tests replaced by 27 in `MoneyParserTest`; +12 `CaptureAmountParsingTest`; +3 `RemoteCandidatesTest`) |
| Matrix covered | `0`, `1`, `12`, `12.5`, `12.50`, `12,50`, `1234`, `1,234.56`, `1.234,56`, `1 234,56`, `1 234.56` across en-US, en-GB, en-PH, de-DE, fr-FR, es-ES, pt-BR and en-IN; 0-, 2- and 3-decimal currencies (JPY, USD/EUR/PHP/BRL/INR, BHD/KWD); repeated, invalid and leading/trailing separators; negatives; currency marks; whitespace, NBSP, NNBSP and thin space; Arabic-Indic, Persian and Devanagari digits; huge values; excess precision; empty and malformed input; the critical `12,50` on every locale; round trips input → minor units → `formatPlain` → minor units and `format` → minor units across those locales and currencies; and the composer's review round trip |
| `lintDebug` | 0 errors, 9 warnings (unchanged; none in the changed code) |
| `assembleDebug`, `assembleRelease` (`-x uploadCrashlyticsMappingFileRelease`) | Both succeed |
| Project separation and secrets | Debug APK references `lucento-test` only; release APK references production only; no `sb_secret_`, `service_role` or JWT-like strings in either |
| Device (API 36.1 emulator) | O-13 re-run `12,50` → stored 1250; `1.000` → clarification, nothing saved, then `1000` → 100000; 200% font; guided budgets 1250 and 100000; composer `coffee 12,50 and lunch 1.500` → 1250 plus a choice → 150000; de-DE `12.50` → 1250. Final build: `groceries 1,500php` → $1,500.00 (B-89). 0 crashes. |

## 9. Independent QA

- **Architect review (before implementation):** approved with changes. Changes adopted:
  - no zero-dropping for the other mark;
  - Ambiguous for `x.000` on the phone's own mark;
  - `plain()` strips trailing zeros;
  - ASCII `formatPlain`;
  - the composer delegates to the central rules, using the account's precision.
- **QA attack (after implementation): FAIL, four blockers, all fixed:**

| Finding | Fix |
|---|---|
| **B1** The composer cut space or apostrophe grouping, so "loyer 1 000,50" stored 0.50 | The number pattern accepts validated grouping; a plain space on a phone that doesn't group with spaces is offered both ways |
| **B2** Letters after a number made the pattern backtrack, so "1,500php" stored 1 | Possessive pattern; only k/m or a currency word may follow; anything else makes the number unreadable, never half-read |
| **B3** Field filters dropped `٫`, so "۱۲٫۵" stored 125 | The filters only limit length now |
| **B4** A leading zero passed as a thousands group, so "0,500" stored 500 | Groups may not start with 0, in the parser and in `ReceiptParser` |

  Non-blocking notes fixed as well:
  - an AI amount that picks one reading of an ambiguous number now asks;
  - space grouping with the other mark keeps every digit;
  - choices offer all readings;
  - the debt instalment shows the clarification;
  - every locale's symbol for the wallet currency is accepted;
  - an unreadable number's clause is kept.
- **QA re-check:** **PASS WITH NOTES, nothing blocking.** Two notes, both addressed:
  - Case-insensitive matching of one-letter symbols would read `2k` in a kina, kwacha or kyat wallet as "2 K", storing
    2 for 2,000. Symbols of one or two letters now match exactly (test added).
  - On a space-grouping phone, "déjeuner 250 180" reads as 250180, by that locale's convention. It now carries a
    non-blocking "check the amount" note when the first group has two or more digits.

## 10. Known limitations

| Item | Note |
|---|---|
| Existing data | Inflated amounts entered before P0-3 can't be identified reliably, because the typed text isn't stored. One known QA instance (QA-Cash $1,250.00). **Proposal (not started, needs authorization):** a one-off "check your balances" review listing manually entered opening balances and transactions that are whole multiples of 100 in major units and were created before the update. It would be shown to the user, who confirms each one. Never an automatic rewrite. |
| Lakh/crore and other scales ("2.5L") | Not read; the composer asks instead of guessing |
| `ReceiptParser` | Still has its own OCR heuristics (leading-zero fix only); every receipt row is reviewed through `BatchReview` |
| Header previews in the wallet sheet | Show $0.00 while the amount is ambiguous or invalid (pre-existing); the field's message explains |
| fr-FR and other space-grouping phones | "2 150" in the composer is one number (2150), by that locale's convention |
| Composer separator | Fixed when the composer opens; a locale change applies the next time it opens |
| Device coverage | Wallet, guide and composer paths on the emulator only; the other screens are unit-tested through the same call sites |

## 11. Release-readiness recommendation

**P0-3: PASS.** Every completion criterion is met:

| Criterion | Status |
|---|---|
| `12,50` defect resolved | PASS (unit tests on 8 locales; device en-US and de-DE) |
| All monetary entry points use safe validation | PASS (one rule set; composer, AI and receipt end in `MoneyParser`; filters no longer alter text) |
| Ambiguous values never silently misread | PASS (`Ambiguous` result, one-tap choices, AI cannot settle them) |
| Currency precision respected | PASS (0-, 2- and 3-decimal currencies) |
| Integer minor-unit storage preserved | PASS (no schema or calculation change) |
| Overflow and invalid precision handled | PASS |
| Existing calculations correct | PASS (all 634 pre-existing tests still pass) |
| Automated tests pass | PASS (671/671) |
| Independent QA finds no blocking defect | PASS (after fixes; re-check PASS WITH NOTES) |
| Debug and release builds succeed | PASS |

**Not part of this pass; it needs your authorization:** reviewing amounts that may have been inflated before this fix
(§10). It is a separate, user-confirmed process, never an automatic rewrite.
