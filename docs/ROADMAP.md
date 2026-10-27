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

## Done — v0.3.0 design-led refactor (pending CI)
- Identity: blue/yellow/cream palette + midnight dark theme (AA-checked), L+leaf mark and launcher icon, original
  vector Sun/Moon mascots. Navigation: Home · Transactions · Add · Insights · More.
- Room v3 (`MIGRATION_2_3`, additive): `transactions.transfer_group`, `budgets`, `goals`, `goal_contributions`,
  `recurring`. Instrumented migration tests for 2→3 and 1→3 written (need schemas + emulator; not run).
- Transfers (M2, same currency), loans as liability accounts, Budgets (M3, monthly, no rollover), Goals (M4),
  Recurring (M8, user-confirmed; no WorkManager auto-posting), Insights (M9 subset), CSV export (M10 part).
- Natural-language entry (M6, offline English parser) + system speech recogniser; receipt OCR (M7, ML Kit bundled
  model, system camera / photo picker — no camera permission).
- Feature toggles + personalised onboarding. Safe to spend / burn rate / net flow per `docs/CALCULATIONS.md`.

## Done — v0.4.0 integrity, schemas, CI, mockup UI, Lottie (not yet compiled — see docs/QUALITY.md)
- Integrity: `WalletChangePolicy` (asset ↔ liability and currency locks incl. recurring schedules), category archive
  instead of delete when budgets/schedules/transactions use it (+ `restore`), date-keyed recurring idempotency (fixes a
  swallowed payment after editing a schedule's timing), race-safe transaction/transfer inserts, crash-free write
  failures, OWASP-style CSV formula neutralisation, currency-aware receipt amounts (0/2/3 decimals, integer totals,
  masking dates/phones/IDs/quantities, currency detection that never converts). 69 new JVM tests (215 total).
- Room: authentic schemas 1–3 committed from CI artifacts; offline migration checker; stronger instrumented tests.
- CI: blocking lint policy, schema drift check, release APK + AAB with `aapt2` permission/debuggable checks, optional
  signing from secrets, emulator migration job. `tools/verify.ps1` for local runs.
- UI from the reference mockup: category hues, Home actions/net-flow layout, Transactions type tabs, richer empty
  states; Lottie: mascot expressions, onboarding, empty states, scan, saved, milestones (goal halfway/reached, month
  kept within budget — once).

## Done — v0.5.0 UI/UX refinement (JVM-tested; compile pending CI)
Dock navigation + More + profile menu, + Add sheet (Manual/Scan/Speak/Type it), budget hero with Sun moods, net flow
glyph, burn bar, savings level, upcoming payments, category-derived budget, preset category onboarding, Budget tab
with category management, focused Insights, screenshot tour in CI.

## Done — conversational transaction composer (JVM-tested; compile pending CI)
One composer replaces the Add sheet and the Scan / Speak / Type it screens: several transactions per message,
inferred type/category/date/account, inline correction, receipts and speech through the same review, atomic batch
save. Chat layout with a pinned wallet bar (default wallet, picker, one-tap transfers) and all-or-nothing saving.
Next: on-device learning from corrections (M5), optional opt-in remote interpreter (docs/CAPTURE.md).

## In progress — optional cloud sync (Supabase + Google Sign-In; built and tested locally, see docs/SYNC.md)
Done: Room v6 sync bookkeeping (emulator-tested migration), push/pull/merge engine with tombstones and
last-write-wins, WorkManager scheduling, Settings → Cloud sync, Supabase schema + RLS applied and verified.
Remaining: Google OAuth clients + Supabase Google provider (blocks real sign-in), an end-to-end two-device test,
recurring-schedule sync, syncing preferences.

## In progress — dashboard refresh (`feature/dashboard-refresh`)
- Home: working header (brand, sync status, disabled notifications placeholder, profile, month selector); Budget pace
  card (budget used vs month elapsed, verdict in words, mascot); Net worth card (assets − liabilities with shortcuts);
  featured savings goal; Scheduled transactions.
- Manually valued assets (`OTHER_ASSET`): valuation only, counted in net worth, refused by every money write path.
- App-wide hierarchy (`Hierarchy`, `SectionHeader`, `CardHeader`, `FieldHeader`); rounded floating dock
  Home · Transactions · (+) · Insights · Budgets; Wallets, goals, scheduled transactions and debts open from Home's Quick links; categories from Budgets.
- Wallets redesign: brand-blue Net worth hero (net worth left; Assets on sunlight / Liabilities on tangerine pills
  right), Assets and Liabilities as separate tinted sections of carded rows (Debts & loans beside Liabilities), each
  wallet page opening on a bank-card style face per type (`walletCard` tokens), balance flags with tooltips and a reconcile note (Scan shortcut), and Adjust balance from
  Edit wallet (ADJUSTMENT_IN/OUT rows; no schema change — `type` is TEXT).
- Debts & loans: optional interest rate, one-time / monthly repayment (months + amortization with a suggestion) and a
  payment reminder that opens a prefilled recurring debt payment; payments split (user input) into principal (transfer)
  and interest (expense under the auto-created "Debt interest"); Recurring payments redesigned (brand hero, carded rows,
  Bill / Income / Debt payment). DB v8 (`debt_terms`, `recurring.to_wallet_id` / `interest_minor`), not synced yet.
- Sync v9: recurring schedules (incl. debt payments) and debt terms sync (`public.recurring`, wallet term columns);
  email + password accounts beside Google; onboarding asks to connect sync (first sync after setup). Budgets, Goals and
  Insights adopt the brand hero and carded sections.
- Polish: rising-logo launch animation (the approved symbol's own paths, staged) and the same mark as the sign-in
  indicator; 30 s sync request timeout with one retry; loan history shows each payment's principal and its interest
  apart (`DebtPaymentLink`); New wallet live card preview; type icons on every wallet pill; compact goal cards;
  smaller dashboard hero and raised Quick links; themed onboarding text in dark mode.
- Open: notifications/reminders behind the header bell; older installs read `OTHER_ASSET` as Cash after a sync pull. Older installs also read balance adjustments as expenses after a sync pull.

## M2 — Transfers between wallets (same-currency done in v0.3.0)
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
- ~~Lottie animations for key moments~~ — done in v0.4.0 (original assets, reduced-motion aware).
- Accessibility audit (TalkBack, font scale 200%, contrast), large-screen layouts, baseline profiles, R8 release build,
  stable signing via GitHub secrets, Play-ready release workflow.

## Next recommended milestone
1. Push v0.4.0 (or run `tools/verify.ps1`), fix any compile errors CI reports, confirm the migration job is green.
2. Device QA against `docs/design-reference/lucento-mock.png`: light/dark, small phones, font scale 200%, TalkBack,
   reduced motion; Lottie on lottie-android; receipts in PHP/JPY/KWD/EUR; speech without Google services.
3. Category management screen on top of the tested archive/restore rules; full backup/restore; opt-in reminders.

## Known gaps
- The authoring environment (cloud + device VM) has no Android SDK or Maven access, so Compose/Android code is
  verified only by GitHub Actions (or a local run). Never treat a change as working until CI is green.
- No UI/screenshot tests; DAO behaviour is tested with in-memory fakes, real SQLite only in the migration job.
- Debug APK signing key varies per CI runner (see README); release signing needs repository secrets.
