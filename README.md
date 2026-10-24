# Lucento

**Financial clarity without the complexity.**

Lucento is a native Android personal finance app — income & expenses, budgets, goals, manual wallets,
and AI-assisted entry — built local-first: your data stays on your device and every core feature works offline.

> Status: **v0.4.0 — financial-integrity hardening, authentic Room schema history, a stricter CI pipeline,
> mockup-led UI refinements and an original Lottie motion system.** v0.4.0 has **not yet been compiled** — see
> [docs/QUALITY.md](docs/QUALITY.md) for exactly what was and wasn't verified. Also: [docs/ROADMAP.md](docs/ROADMAP.md),
> [docs/DESIGN.md](docs/DESIGN.md), [docs/CALCULATIONS.md](docs/CALCULATIONS.md), [CHANGELOG.md](CHANGELOG.md).

## What works today (0.5.0)
- **Navigation**: a persistent dock — **Home · Transactions · Wallets · Insights · Budget** — plus a **More** menu
  (Savings goals, Recurring payments, Debts & loans, Categories) and a **profile** button (optional name,
  appearance, Settings, Enabled features). An app-wide **+** opens the **transaction composer**.
- **Onboarding**: welcome → focus → features → **choose your spending categories** (≈90 presets in 10 groups,
  search, custom categories; required) → review → currency & appearance → confirmation.
- **Home**: budget hero with Lucento, the mascot, whose torch light and look follow the month's budget (five wellness tiers),
  compact net flow with an animated trend glyph, burn-rate bar against the calendar, savings goals level, upcoming
  recurring payments (tap to review and mark paid).
- **Budget**: the total is always the **sum of category amounts**; per-category spent/left/status; spending outside
  the budget shown transparently; amounts per month and currency; manage categories (add, rename, reorder,
  deactivate, restore). Existing users keep their categories and can personalise them from presets.
- **Insights**: income and expenses over 3/6/12 months (transfers excluded) and net-worth composition by account type.
- **Transactions**: search, filters (date, type incl. transfers, wallet, category), day groups, edit/delete.
- **Transfers** between your own accounts (two linked legs, atomic, never income or spending).
- **Wallets**: cash, bank, e-wallet, savings, credit card and loan; account detail with history.
- **Savings goals**, **Debts & loans**, **Recurring payments** (mark paid / skip / pause — never auto-posted).
- **Transaction composer** (replaces the old Manual/Scan/Speak/Type it sheet): type, speak or scan, and every
  transaction in the message appears at once as an editable row ("Lunch 250, groceries 800, salary 35000" → three
  rows). Type, category, date and account are inferred; fix anything inline; save the batch with one tap (atomic,
  idempotent). Interpretation runs on the phone — no AI service, no network. See [docs/CAPTURE.md](docs/CAPTURE.md).
- **Settings**: theme, main currency, Enabled features, CSV export, privacy facts.
- **Motion**: the mascot's gentle float and pose cross-fades; original Lottie net-flow glyphs, onboarding mark,
  empty states, receipt scan beam, saved confirmation, milestones — bundled and reduced-motion aware.
- Exact money math in minor units; 325 JVM unit tests; an emulator screenshot tour in CI.
- **Optional cloud sync** (Google Sign-In + Supabase, row-level security): off until you connect an account; every
  core feature works offline without it, and signing out keeps local data. See [docs/SYNC.md](docs/SYNC.md) —
  sign-in needs the Google OAuth setup listed there.
- Android cloud backup disabled.

## Not yet
Full backup/restore (CSV export only), reminder notifications, cross-currency transfers, automatic principal/interest
split, budget periods other than calendar months. See the roadmap.

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
Plus Jakarta Sans © The Plus Jakarta Sans Project Authors, SIL Open Font License 1.1 — see `docs/licenses/PlusJakartaSans-OFL.txt`.
Airbnb Lottie, Apache License 2.0. All animation files are original to Lucento (`docs/design/ANIMATION_ASSETS.md`).
