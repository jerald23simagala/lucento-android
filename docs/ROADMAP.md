# Lucento Roadmap

Principle: each milestone ships a working, tested APK. Money stays in `Long` minor units; every calculation gets
JVM tests; every Room schema change ships a `Migration` and a migration test.

## Done — M0 Foundation (v0.1.0)
- Gradle (AGP 8.7.3, Kotlin 2.0.21, KSP, Compose BOM 2024.12.01), wrapper 8.11.1, GitHub Actions CI → debug APK artifact.
- Design system: palette, Inter typography, shapes/spacing, components (buttons, cards, empty states, amount text,
  currency picker, brand mark), light/dark themes, splash screen, adaptive + themed launcher icon.
- Onboarding (welcome → principles → main currency), DataStore preferences, bottom navigation.
- Wallets CRUD (Room v1) + net worth per currency. Unit tests for Money, MoneyParser, MoneyFormatter, NetWorthCalculator.

## M1 — Transactions (income & expense)  ← next
- Room v2: `categories`, `transactions` (amount minor, type, wallet_id, category_id, occurred_at, note, created_at,
  client_uuid for idempotency); migration 1→2 + test. Wallet balance = opening balance + Σ transactions (SQL aggregate, tested).
- Default category set (editable), add/edit/delete transaction sheet with numeric keypad, Activity tab with
  day-grouped history, search (note/category/amount) and filters (date range, wallet, category, type).
- Home: this month's income / spending.

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
- Lottie is not yet integrated (planned M11); current motion uses Compose animation APIs.
- No instrumented/UI tests yet; Room DAO tests arrive with M1 (Robolectric or emulator in CI).
- Debug APK signing key varies per CI runner (see README).
