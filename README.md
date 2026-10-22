# Lucento

**Financial clarity without the complexity.**

Lucento is a native Android personal finance app — income & expenses, budgets, goals, manual wallets,
and AI-assisted entry — built local-first: your data stays on your device and every core feature works offline.

> Status: **v0.4.0 — financial-integrity hardening, authentic Room schema history, a stricter CI pipeline,
> mockup-led UI refinements and an original Lottie motion system.** v0.4.0 has **not yet been compiled** — see
> [docs/QUALITY.md](docs/QUALITY.md) for exactly what was and wasn't verified. Also: [docs/ROADMAP.md](docs/ROADMAP.md),
> [docs/DESIGN.md](docs/DESIGN.md), [docs/CALCULATIONS.md](docs/CALCULATIONS.md), [CHANGELOG.md](CHANGELOG.md).

## What works today
- **Onboarding**: welcome → financial focus → features → currency & appearance → confirmation. Skippable; choices
  only set the initial modules.
- **Home**: greeting with the Sun (light) / Moon (dark) mascot; **Safe to spend** (budget-based, or a labelled
  estimate from spendable balances, minus bills still due); quick actions (expense, income, transfer, scan, speak);
  **Net flow** with income/expenses and **burn rate** (actual, projected, vs last month); budget and bill alerts;
  recent transactions.
- **Transactions**: search, filters (date, type incl. transfers, wallet, category), day groups, edit/delete.
- **Add tab**: Scan receipt, Speak or type, Manual entry, recent categories.
- **Transfers** between your own accounts (two linked legs, atomic, never income or spending).
- **Wallets** (via More): cash, bank, e-wallet, savings, credit card and **loan**; account detail with history and
  the right actions for assets vs debts.
- **Plan** hub: **Budgets** (overall + category, monthly), **Savings goals** (contributions, projection,
  required per month), **Debts & loans**, **Recurring payments** (mark paid / skip / pause — never auto-posted).
- **Insights**: net flow by period, spending/income breakdown donut, 6-month spending, budget performance, net worth.
- **Receipt scanning**: system camera or photo picker → on-device ML Kit OCR (bundled model) → editable suggestions.
- **Voice & text entry**: system speech recogniser (prefers offline) or typing → offline parser → editable review.
- **Settings**: theme, main currency, **Enabled features** (turn modules off/on without losing data), CSV export,
  privacy facts.
- **Motion**: original Lottie Sun/Moon mascot (idle, curious, success, celebrate), onboarding mark, four empty-state
  illustrations, receipt-scanning beam, saved confirmation, calm milestones — all bundled, reduced-motion aware.
- **Data safety**: wallet type/currency locked while history or schedules depend on it; used categories are archived,
  never cascade-deleting budgets; CSV export neutralises spreadsheet formulas; currency-aware receipt amounts.
- Exact money math in minor units; 215 JVM unit tests.
- No internet permission (network permissions merged by libraries are stripped); Android cloud backup disabled.

## Not yet
Full backup/restore (CSV export only), reminder notifications, category management screen (the safe rules exist and
are tested), cross-currency transfers, automatic principal/interest split. See the roadmap.

## Tech
Kotlin · Jetpack Compose · Material 3 · Room · DataStore · ViewModel · Coroutines/Flow · Navigation Compose (type-safe).
JDK 17, compileSdk 35, minSdk 26 (Android 8.0+). Application ID `com.lucento.app` (debug: `com.lucento.app.debug`).

## Get the APK (no local build needed)
1. Open the repository's **Actions** tab → **Android CI** → the latest green run on `main`.
2. Under **Artifacts**, download **lucento-debug-apk** (a `.zip`) and unzip it to get `Lucento-debug-<sha>.apk`.
3. Copy the APK to your phone (or download it there directly while signed in to GitHub in the browser).
4. Open it, allow *Install unknown apps* for your browser/file manager when prompted, and install.
   Updates install over the previous debug build as long as they come from the same CI signing key — see note below.

> Debug APKs are signed with the CI runner's debug key, which can differ between runs. If Android refuses an update
> with "App not installed", uninstall the previous Lucento (debug) first. A stable signing key will be added via
> GitHub secrets before beta.

## Build locally (Android Studio on Windows/macOS/Linux)
1. `git clone` the repo and open the folder in Android Studio (Ladybug or newer). Let Gradle sync.
2. Run the `app` configuration on a device/emulator, or:
   ```bash
   ./gradlew testDebugUnitTest   # unit tests
   ./gradlew assembleDebug       # APK at app/build/outputs/apk/debug/
   ```
   On Windows use `gradlew.bat`, or run everything CI runs with
   `powershell -ExecutionPolicy Bypass -File tools\verify.ps1` (writes `build\verify-summary.txt`).

## Project layout
See [CLAUDE.md](CLAUDE.md) for architecture, money rules and engineering standards.

## Licenses
Inter typeface © The Inter Project Authors, SIL Open Font License 1.1 — see `docs/licenses/Inter-OFL.txt`.
Airbnb Lottie, Apache License 2.0. All animation files are original to Lucento (`docs/design/ANIMATION_ASSETS.md`).
