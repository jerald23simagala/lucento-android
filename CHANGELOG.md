# Changelog

## 0.5.0 — 2026-10-22 (UI/UX refinement; JVM-tested locally, awaiting CI compile)

### Navigation
- Persistent dock: Home · Transactions · Wallets · Insights · Budget + More menu (goals, recurring, debts, categories).
  Hidden only for focused workflows (scan, speak/type, category setup, settings). Tab state preserved.
- Profile button on every primary screen → profile menu (optional device-only name, appearance, Settings, Enabled
  features). Settings is no longer a primary destination.
- App-wide + opens Add Transaction: Manual, Scan, Speak (listens on arrival), Type it (focuses the sentence field).
  Removed: Add tab, Quick Add screen, Plan hub, More screen.

### Home
- Budget hero (brand panel, ~30 % shorter) with remaining/total/spent and the Sun mascot: happy within budget,
  supportively concerned at ≤ 10 % left or exceeded (new `concerned` Lottie marker + vector still).
- Compact net-flow card with an animated trend glyph (green / red / neutral at zero — new `net_flow_indicator` Lottie).
- Burn-rate consumption bar with a today marker; same thresholds as the hero; no percentage without a budget.
- Savings goals level; Upcoming recurring payments replace Recent transactions (tap → review / mark paid).
- Action-button row removed.

### Budget
- The overall budget is derived: the sum of category allocations per currency and month (`BudgetPlanner`). No
  separate "budget for everything"; legacy rows are ignored, explained once and removable.
- New Budget tab: summary, budgeted / not-budgeted categories, spending outside the budget, amount sheet with a
  live new-total preview, category management (add, rename, reorder, deactivate, restore).
- Mandatory onboarding step: choose spending categories from a grouped preset library (≈90 presets, 10 groups,
  search, recommended defaults, custom names) and review before confirming. Fresh installs seed income categories
  only. Existing users are never sent back through onboarding: a one-time card offers the same picker or keeping
  their categories; users with no categories get "Finish setting up your budget".
- Budgets module on by default.

### Insights
- Focused on income/expense trends (3/6/12 months, transfers excluded) and net-worth composition (assets and
  liabilities by account type on a shared scale). Category donut and budget performance removed from Insights.

### Quality
- 38 new JVM tests (253 total, passing in `tools/jvm-harness/run.sh`, now committed).
- `ScreenshotTourTest` (emulator) + CI upload of screenshots; CI also runs on `feature/**` and `release/**` pushes.
- No Room schema change (database stays v3).

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
