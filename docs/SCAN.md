# Lucento Scan — financial document intelligence

Scan turns financial documents — receipts, bills, invoices, payment confirmations, refunds, tax receipts, bank, card
and e-wallet statements — into reviewable rows in the composer. Everything below runs **on the phone**: no document,
image, text or financial data leaves it (Privacy Policy › "Scanning documents"). Every value is a suggestion the person
reviews; nothing is saved without their tap, and nothing already saved is ever changed.

Roadmap and audit: Claude Docs "Lucento Scan — financial document intelligence audit" (2026-11-21); phases 0–3 approved
and built 2026-11-22. Phase 4 (optional AI reading) and phase 5 (receipt line items) are not built (see the end).

## Pipeline

```
Document Scanner pages · camera photo · picked images · PDF
        │  feature/capture: ReceiptOcr (ML Kit bundled Latin model) · PdfDocumentReader (text layer, else OCR)
        ▼
ScanPage (rows + positioned fragments)  ── DocumentLayout.upright (quarter turns) · ReceiptParser.layoutRows (tilt)
        ▼
DocumentAssembler.assemble  ── split receipts photographed together · join a statement's pages (overlap removed)
        │                       · a summary page heads its statement · a receipt continued over two photos joined
        ▼
per document: StatementParser.classify ─┬─ RECEIPT  → ReceiptCandidates (one row)
                                         └─ STATEMENT → StatementParser.parseRows → StatementCandidates → StatementReconciler
        ▼
Interpretation.Candidates (rows, held lines, StatementReport) → composer review (BatchReview) → saveBatch
```

`LocalTransactionInterpreter.interpretDocument` is the entry point; `interpretScan(lines)` reads one page of text the
same way. Smarter reading (Gemini) never reads scans.

## Capture

| Input | How | Notes |
|---|---|---|
| **Scan pages** | ML Kit Document Scanner (`play-services-mlkit-document-scanner`), up to 10 pages, gallery import allowed | Flattens, straightens and cleans paper. Runs in Google Play services, on the device; downloaded once by Play services. Needs ≥ 1.7 GB RAM and Play services; otherwise the camera opens instead. No camera permission. Its JPEGs land in Lucento's cache and are deleted once read. |
| **Take a photo** | System camera (`TakePicture`) | The fallback; the photo is deleted once read. |
| **Choose images** | Photo picker, up to 10 | Read as pages of one scan. Picked images are the person's own files and are not deleted. |
| **Import a PDF** | `OpenDocument("application/pdf")`, up to 20 pages | Read where it is, never copied. Android 15+ (and 12–14 with SDK extension 13): each page's text layer with positions; locked PDFs ask for the password, used once, never stored. Older phones and scanned PDFs: each page drawn at ~2,400 px and read by OCR; locked PDFs can't be opened and the person is told how to save an unlocked copy. |

Pages are put upright by quarter turns when most lines' angles say so (`DocumentLayout.upright`), and a slight tilt is
straightened when rows are rebuilt (`ReceiptParser.pageTilt`). Perspective correction is the Document Scanner's.

## Several pages and several documents (`DocumentAssembler`)

- **Receipts photographed together** are split by a clear vertical gutter (side by side) or a wide gap (one above the
  other) only when each part reads on its own as a receipt with a labelled total — so one receipt's item and price
  columns never split. At most 6 per photo.
- **A statement's pages** join into one statement. Where photographed pages overlap, the longest run of lines that ends
  one page and starts the next is kept once — at least two lines, or one carrying a running balance — compared with
  OCR noise allowed (same amounts; one or two misread characters on long lines). A PDF's pages never overlap and are
  never trimmed.
- **A summary page** (balances, minimum due, limits) before statement pages joins the statement.
- **A receipt continued** over two photos (no total on the first) is joined; each complete receipt stays its own
  document, so five receipts scanned in one go are five rows.

## Country, currency and dates

- **Country** (`DocumentLocale.country`): weighted cues — US state + ZIP, sales tax, toll-free numbers; PH "VAT REG
  TIN", BIR/PTU/MIN, VATable, city names; Canadian postcodes and GST/HST; UK postcodes; ABN; GSTIN/CGST; TRN; MwSt,
  TVA, IVA, BTW, NIF/CIF, SIRET; Nordic org.nr/MVA/CVR; scripts (kana → JP, hangul → KR, Han only → CN); names of
  single-country merchants and providers from the dictionaries. Named only when it clearly outweighs any other.
- **Currency** (`DocumentCurrency.read`), never the person's default by assumption:
  1. printed — an ISO code or a symbol that names one currency (₱ € £ ₹ ₩ US$ C$ A$ S$ HK$ NZ$ NT$ MX$ R$ RM Rp KD …,
     "en euros"), or the one that clearly dominates when another appears (a foreign line on a card statement);
  2. a shared symbol — `$`, `¥`, `kr`, `Rs` — settled by the document's country;
  3. else the person's only account in such a currency, **marked "check the currency"**;
  4. nothing printed and a firm country: that country's currency; otherwise unknown, and the row's account applies.
  A row in a currency its account doesn't use is blocked until the person picks another account (never converted).
- **Amounts**: 0/2/3-decimal currencies (`CurrencyRules.fractionDigits`); `1.234,56`, `1'234.50`, Indian `1,23,456.00`;
  space-grouped `2 145,80` on statements; currency-marked integers (`¥348`, `₩15000`, `348円`); negatives as `-`, `( )`,
  trailing `-`, `CR`/`DR`, `(Cr)`/`(Dr)`. Reference numbers, phone numbers, account numbers in hyphenated groups,
  quantities with units (gallons, kWh), meter readings and years in written dates are never amounts.
- **Dates**: ISO and `年/년` forms; numeric dates settled by another date on the document with a day above 12, else by
  the country's order (US, PH month first; JP/KR/CN year first; most others day first), else marked "check the date";
  month names in English, Filipino, Spanish, Portuguese, French, German, Italian, Dutch, Indonesian/Malay and the Nordic
  languages (other languages' month words need a four-digit year). A receipt's date prefers a labelled bill/invoice/
  transaction date and passes over due dates, billing periods and expiry dates.

## Receipts (`ReceiptParser`, `ReceiptCandidates`)

- **Total**: labels ranked — amount due/payable outranks total, which outranks paid — in 20 languages (SUMME, TOTAL TTC,
  TOTAL A PAGAR, TOTALE, TE BETALEN, TOTAL BAYAR, 合計, 합계 …) and printer abbreviations (AMT DUE, TTL). Subtotals,
  tax lines, "GST included in total", a VAT table's own total row, cash tendered, change, savings, points, suggested
  tips and item counts never count. A label alone takes the next line only when that line holds nothing but an amount.
  Cash tendered less change is the total when it matches a printed amount. Without a label, the payable amounts are
  offered as one-tap choices and nothing is pre-filled.
- **Tips**: the total after a filled-in tip is what was paid; items, tax and tip are checked against it.
- **Refunds**: a total printed negative, or refund words on the total or title line → income, Refunds category.
- **Who and how** (`MerchantRecognizer.receipt`): the payee is a labelled biller or merchant ("Biller: MERALCO", "You paid
  … to PG&E"), else the business heading the receipt, else (only when the top names nobody) the business named most —
  **never the payment provider**. The instrument is the app that issued a confirmation, else the card scheme, wallet or
  rail on a tender line (with its last four digits). A person paid reads "Sent to Juan Dela Cruz"; a confirmation moving
  money between the person's own accounts ("Cash In from BPI") is a transfer, in the right direction.
- **Account**: the one that paid (the person's GCash wallet, their Amex card — `WalletMatcher`, by wallet name or its
  bank's registry names, never a generic word like "Cash"), else the account in the receipt's currency, else their usual
  account for this merchant (unless they picked one), else the active wallet.
- **Category**: the person's habit for this merchant, else the dictionary's, else the receipt's own words
  (`ReceiptCues`: a tip line and a server → dining; gallons and a pump → fuel; labour and aircon → repairs; two cues
  must agree), else the category words in the header.

## Statements (`StatementParser`, `StatementCandidates`)

- **Lines**: a row with a date and an amount; a date heading or under groups (app histories); on a statement whose lines
  carry their own dates, an undated row is a transaction only with a running balance (UK same-day lines), under a date
  row, or as a card's interest or fee. Summaries, section totals, limits, rewards, balance labels ("Venmo balance"),
  daily-balance and APR tables, fee summaries, prose ("…you may have to pay a late fee of up to $41.00") and declined
  lines are never transactions. Up to 200 lines per document.
- **Direction**, strongest first: the running balance (a card's is what's owed, read so only when the page plainly is a
  card statement); the column (Debit/Credit, Withdrawals/Deposits, from positions); a sign or mark (on a card a minus is
  a credit); a bank's convention (debits printed with a minus → unsigned is money in); the section ("DEPOSITS AND
  ADDITIONS", "ELECTRONIC WITHDRAWALS", "Payments and Credits", "New Charges"); the words (`FinancialTerms`, by account
  type); a card's lines are charges by default. Anything unclear is marked.
- **Meaning** (`FinancialTerms`): purchase, payment to a card, bill payment, transfer, deposit, payroll, ATM, check, fee,
  interest charged, interest earned, refund (incl. statement credits and cash-back rewards), reversal (incl. returned
  payments, Rückbuchung), cash advance, cash in/out — English plus Spanish, Portuguese, French, German, Italian, Dutch and
  Indonesian terms (PRLV, Lastschrift, Gutschrift, Dauerauftrag, abono, cargo, accredito, bijschrijving …); pending and
  declined status.
- **Rows**: a refund is income (never a payment, on a card too); money into a card or loan is a payment from another
  account; a line naming another of the person's accounts is a transfer; money added to an e-wallet is a transfer in
  (source picked when not named); ATM and cash advances move money to the one cash wallet; payroll → Salary, fees →
  Bank fees, interest → its category. The description is the merchant when recognised ("Starbucks" for "PURCHASE
  AUTHORIZED ON 10/05 STARBUCKS STORE 12345 SEATTLE WA", "Blue Bottle Coffee" for "SQ *BLUE BOTTLE COFFEE").
- **Account**: the person's wallet at the bank, card issuer or app named in the heading (last four digits pick between
  cards), else the account in the statement's currency, else the active wallet. The heading is the lines above the
  first amount, so a bank named in a line ("Cash In via BPI") is a counterpart, not the statement's own bank.
- **Facts** (`StatementSummary`): opening and closing balances (by label, or by position for "Kontostand am …",
  "Solde au …"), minimum due, due date, credit limit, available credit, period. `addsUp` checks opening ± lines = closing.

## Checking a statement against the ledger (`StatementReconciler`)

Nothing saved is ever changed. For the statement's account:

| Finding | Rule | What the person sees |
|---|---|---|
| Saved already | Same account, amount and type, dated within 3 days (closest first, then the closest words); one saved transaction answers one line | **Held back, not added**; listed with "Add anyway" |
| Possibly saved | Same amount in the account within 7 days or as another type, or in another account within 3 days | Added, with "Possibly saved already …" on the row |
| Printed twice | Same day, amount, direction and (nearly) the same words | Added, "appears twice on the statement" |
| Already waiting | A line repeating a row from an earlier scan in this batch | Held back, "Already among the rows above" |
| Not on the statement | Saved in the account within the statement's period but on no line (whole statements only) | Listed, nothing done |
| Balance | Closing balance vs Lucento's balance on the last day (`WalletBalanceCalculator.balanceOn`) after saving every row offered; skipped for a wallet created after that day | "Matches" or the difference, explained; never adjusted |
| Refund / reversal | A refund or reversal and a charge of the same amount sharing a word on the statement; a refund of a saved purchase within 120 days | Notes on both rows |
| Fees | Fees and interest charged | Total and count |
| Card figures | Minimum due, due date, credit limit, available credit | One line |

## Merchant dictionaries (`assets/merchants`)

Seven files, one per region — `us`, `ph`, `global`, `europe`, `asia_pacific`, `americas`, `middle_east_south_asia` — with
about 1,950 entities, all loaded on every phone (a brand lives once, in its home market's file, listing its other
countries). Schema:

```json
{"region": "US", "version": 1, "entities": [
  {"name": "Walmart", "kind": "MERCHANT", "aliases": ["walmart", "wal-mart"], "weakAliases": [], "category": "groceries", "countries": ["US", "CA", "MX"]},
  {"name": "Square", "kind": "PROCESSOR", "aliases": ["squareup"], "prefixes": ["sq *"], "countries": ["US"]}
]}
```

- `kind`: MERCHANT, BILLER, BANK, EWALLET, CARD_NETWORK, PROCESSOR. `category`: a category-icon key (payees only),
  mapped to the person's own categories by `CategoryIntents` (exact icon, then broader: cafe → dining, fuel → car →
  transport…). `weakAliases`: common words ("target", "shell") that count only at the start of a descriptor or a
  receipt's header line. `prefixes`: a processor's descriptor prefix, stripped to find the real merchant (a whole
  merchant name opening the descriptor wins: "SP SERVICES" is a utility, not Shopify's "SP ").
- Matching is accent- and case-insensitive, whole words, longest first. Every alias must name one entity:
  `MerchantIntelligenceTest.theBundledDictionariesAreSound` fails the build on a collision, a bad category or a missing
  name from the owner's brief.
- **Adding a merchant**: add an entity to its home region's file and bump that file's `version`; no code changes.
  Remote dictionary updates would be a new network path (Privacy Policy + opt-in) and are not built.

## Learning from the person's ledger (`MerchantMemory`)

At read time, from the person's saved income and expenses of the last ~13 months (at most 3,000): per payee (dictionary
name, else the first two meaningful words), the most-chosen category and account. Used only when chosen at least twice
and for at least 60 % of that payee's transactions; a habit's account never overrides an account the person picked.
Nothing is stored: editing or deleting transactions changes the suggestion, and signing out or switching accounts —
which wipes the ledger (docs/SYNC.md) — leaves nothing of one person's habits for the next.

## Benchmark (`ScanCorpusBenchmarkTest`)

An independent synthetic corpus in `app/src/test/resources/scan-corpus` — 112 documents (`dev` 73, `holdout` 39) from the
US, the Philippines and 20 other countries, written by a separate agent that never saw the parser, with ground truth
checked by script (totals, balances, running balances). `dev` is the set rules were tuned on; `holdout` was only
measured. Reports: `app/build/reports/scan-benchmark/{dev,holdout}.md`, by type and by country. The test asserts the
owner's targets on both sets: statement recall ≥ 95 %, currency on unambiguous documents ≥ 95 %, statement direction
≥ 95 %, and no line read twice without a mark.

They are OCR-like **text**, not photos. Real photos (glare, creases, fading, misreads) score lower; the device test
(`DocumentIntelligenceDeviceTest`) runs real ML Kit OCR on drawn images and a generated PDF, and a consented photo
corpus is the next gate (docs/handover/NEXT_TASKS.md).

## Not built (by decision)

- **AI reading (phase 4)**: the Gemini proxy given the image (paid tier, JSON schema) would read messy photos, unusual
  layouts and CJK photos; it is a new send path needing its own consent, a Privacy Policy and website update and the
  paid tier (OT-31). Gemini Nano runs on flagships only (none of Transsion's ~37 % PH share).
- **Line items (phase 5)**: one payment stays one transaction; items as detail need Room and Supabase migrations.
- **CJK photo OCR**: Lucento bundles ML Kit's Latin model only; Japanese, Korean and Chinese *photos* lose their words
  (amounts and `¥`/`₩` still read). PDFs with a text layer read CJK fine. Adding the script models is an APK-size or
  Play-services-download decision for the owner.
