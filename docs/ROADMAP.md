# Lucento Roadmap

Principle: each milestone ships a working, tested APK. Money stays in `Long` minor units; every calculation gets
JVM tests; every Room schema change ships a `Migration` and a migration test.

## Done — M0 Foundation (v0.1.0)
- Gradle (AGP 8.7.3, Kotlin 2.0.21, KSP, Compose BOM 2024.12.01), wrapper 8.11.1, GitHub Actions CI → debug APK artifact.
- Design system: palette, Inter typography, shapes/spacing, components (buttons, cards, empty states, amount text,
  currency picker, brand mark), light/dark themes, splash screen, adaptive + themed launcher icon.
- Onboarding (welcome → principles → main currency), DataStore preferences, bottom navigation.
- Wallets CRUD (Room v1) + net worth per currency. Unit tests for Money, MoneyParser, MoneyFormatter, NetWorthCalculator.

## M1 — Transactions (income & expense)  ← done (`b55d7a2`, CI verified)
Implemented:
- Room v2: `categories`, `transactions` with explicit `MIGRATION_1_2` (also seeds 20 default categories on upgrade and,
  via a database callback, on fresh install). Foreign keys: wallet `RESTRICT`, category `SET NULL`.
- Design decision: `occurred_on` is an **epoch day** (calendar date), not a timestamp, so a transaction never shifts
  day when the user changes time zone. `client_uuid` has a unique index → idempotent creates (double-tap safe).
- Wallet balance = opening balance ± transactions (SQL aggregate; credit cards track amount owed). Wallets with
  transactions cannot be deleted, and their currency is locked.
- Activity tab (now **Transactions**): day-grouped history, search (note / category / wallet / amount), filters (date preset, type, wallet,
  category), add / edit / delete sheet with date picker. Home shows this month's income and spending per currency.
- Tests: `WalletBalanceCalculator`, `TransactionQueries` (ranges, filters, grouping, totals), default categories,
  and repository rules via in-memory DAO fakes (validation, idempotency, balances, wallet/category guards).

Still open in M1:
- Category management UI (the repository supports create / rename / archive and is tested; no screen yet).
- Custom numeric keypad (the system decimal keyboard is used for now).
- Instrumented migration test (`app/src/androidTest/.../MigrationTest.kt`) exists but needs `app/schemas/**/1.json` and
  `2.json` committed, and an emulator run (`connectedDebugAndroidTest`). It has not been run.
- Room DAO tests against a real database (Robolectric or emulator).
- Filtering is done in memory over all transactions; revisit with paging/SQL if lists reach tens of thousands.

## Done — UI/UX redesign (v0.2.0, pending CI)
Presentation-layer only: no schema, entity, DAO or repository changes. See `docs/DESIGN.md`.
- Refined pine/brass/ivory palette with semantic tokens (positive, negative, statement panel, chart, wallet tones),
  AA-checked in light and dark; editorial overline labels; tabular amount scale; signed amounts everywhere.
- Navigation: Home · Transactions · Wallets; Settings moved behind Home's gear (pushed screen, bottom bar hidden).
  One app-wide transaction editor (activity-scoped `TransactionEditorViewModel`) with snackbar confirmation.
- Home: first-run setup panel; pine net-worth statement; Expense/Income quick actions; this month's in/out with
  spend-to-income ratio; recent transactions; 6-month spending bars; top categories this month.
- Transaction editor: amount-first (large, auto-focused, shrinking), Expense/Income toggle, wallet pills,
  category icon grid, Today/Yesterday/Other date, note. Validation unchanged.
- Transactions: filled search, filter pills, in/out/net for what's shown, sticky day headers with daily net,
  animated rows.
- Wallets: net worth split (have / owe + ratio), Assets and Liabilities sections with per-currency subtotals,
  wallet-type tiles; editor with type grid.
- Settings: theme previews, main currency, honest privacy facts (and the current lack of backup), about.
- New pure logic with JVM tests: `Insights` (monthly totals, category breakdown, daily net, percentages),
  `TransactionQueries.totalsByCurrency` (unbounded), `MoneyFormatter.formatSigned` / `fractionSpan`,
  `HomeStateBuilder`.

## M2 — Transfers between wallets
- Transfer = one DB transaction writing two linked legs (`transfer_group_id`); excluded from income/expense totals.
- Same-currency transfers first; cross-currency transfers record both amounts explicitly (user-entered rate, no guessing).
- Tests: balances conserved, no double counting, deleting one leg deletes both.

## M3 — Budgets
- Monthly overall + per-category limits, rollover option, progress bars, "safe to spend per day", 80%/100% states.
- Deterministic period math (calendar months in user time zone, custom start day). Tests for edge months.

## M4 — Goals
- Target amount/date, contributions (optionally linked to a savings wallet), progress, projected completion date
  from contribution pace. Tests for projections.

## M5 — Local categorization rules
- Merchant/keyword → category rules learned from user edits; deterministic priority order; tested.

## M6 — Natural-language entry (offline first)
- Deterministic on-device parser: "coffee 4.50 yesterday from cash" → amount, date, wallet, category (via M5 rules).
  Always shows an editable preview before saving. Extensive parser tests across locales/decimal separators.
- Optional remote LLM provider behind explicit opt-in, user-supplied key stored in EncryptedSharedPreferences/Keystore;
  adds INTERNET permission only in that build flavor or with clear consent.

## M7 — Receipt scanning (on-device OCR)
- CameraX + ML Kit Text Recognition (bundled model, works offline). Extract total, date, merchant with heuristics;
  editable result screen; images never leave the device and are discarded after save unless the user keeps them.

## M8 — Recurring transactions
- Rules (daily/weekly/monthly/yearly, end date/count); WorkManager materializes due items idempotently
  (unique key per occurrence) so reboots or double runs never duplicate.

## M9 — Insights
- Local analytics: month-over-month by category, top merchants, unusual spend, cash-flow trend. Only charts that answer
  a question; accessible text equivalents.

## M10 — Export, backup & restore
- CSV export (RFC 4180, ISO dates, minor→decimal exact) via Storage Access Framework.
- Full backup: versioned JSON (or DB copy) + checksum; optional password encryption; restore with schema-version checks
  and a dry-run summary before overwriting.

## M11 — Polish & release readiness
- Lottie animations for key moments (first transaction, goal reached) — bundled assets, reduced-motion aware.
- Accessibility audit (TalkBack, font scale 200%, contrast), large-screen layouts, baseline profiles, R8 release build,
  stable signing via GitHub secrets, Play-ready release workflow.

## Known gaps in the foundation
- The authoring environment (cloud + device VM) has no Android SDK or Maven access, so code changes are verified
  only by GitHub Actions. Never treat a change as working until CI is green.
- Lottie is not yet integrated (planned M11); current motion uses Compose animation APIs and honours "Remove animations".
- No instrumented/UI tests yet; Room DAO tests arrive with M1 (Robolectric or emulator in CI).
- Debug APK signing key varies per CI runner (see README).
