# Changelog

## 0.4.0 — 2026-10-22 (local, not yet built by CI)

### Financial integrity
- Wallets: switching between an asset and a liability type, or changing currency, is refused while transactions
  (incl. transfer legs) or recurring schedules use the wallet; the editor explains why (`WalletChangePolicy`).
- Categories: removing one that transactions, budgets or recurring schedules use archives it instead of deleting
  (previously a category with only a budget was deleted and the budget cascade-deleted). `restore()` added.
- Recurring: idempotency key now uses the occurrence's due date. Fixes a bug where, after editing a schedule's
  start/frequency, "Mark paid" silently matched an older payment and recorded nothing.
- Transactions/transfers: duplicate check repeated inside the database transaction; a unique-key race returns the
  winning row; failed writes return an error instead of crashing, and never show a success confirmation.
- CSV: formula neutralisation for account, category and note (leading whitespace/control/BOM/full-width aware),
  generated amounts untouched.
- Receipts: amounts read with 0/2/3 decimals by detected or expected currency, integer totals on total lines, dates,
  times, phones, IDs, percentages and quantities masked; detected currency picks a matching wallet, never converts.
- 69 new JVM tests (215 total, all passing in the local harness).

### Database and CI
- Authentic Room schemas 1, 2, 3 committed (from CI artifacts of `11e2f3d`, `b55d7a2`, `6eec35b`).
- `tools/check_migrations.py` replays migrations on SQLite against the schemas (both steps match).
- Instrumented `MigrationTest` seeds representative wallets/categories/transactions and checks FKs, uniqueness and
  that the real `LucentoDatabase` opens the migrated file.
- CI: lint errors block; schema drift check; release APK + App Bundle with `aapt2` checks (id, not debuggable, no
  network permission); optional signing from secrets; emulator migration job; no `continue-on-error`.

### Design and motion
- Reference mockup added (`docs/design-reference/lucento-mock.png`) and applied: category hues, Home quick actions
  (Add · Transfer · Scan · Speak · More) and split net-flow card, Transactions type tabs, empty states with actions.
- Lottie (`lottie-compose` 6.6.7) with 17 original assets: Sun/Moon mascot expressions, onboarding mark, four empty
  states, receipt scanning, saved check, milestone, loading dots. Reduced motion shows finished stills.
- Confirmations after committed saves (transactions, transfers, recurring payments); goal halfway/reached and a month
  kept within budget (once) get a calm milestone.
- Version 0.4.0 (code 4).
