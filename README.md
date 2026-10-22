# Lucento

**Financial clarity without the complexity.**

Lucento is a native Android personal finance app — income & expenses, budgets, goals, manual wallets,
and AI-assisted entry — built local-first: your data stays on your device and every core feature works offline.

> Status: **foundation (v0.1.0)**. Onboarding, design system, navigation, wallets with net worth, and settings
> are implemented. See [docs/ROADMAP.md](docs/ROADMAP.md) for what comes next.

## What works today
- First-run onboarding with main-currency selection (all ISO 4217 currencies, searchable).
- Wallets: create, edit and delete cash, bank, e-wallet, savings and credit-card wallets, each in its own currency.
- Home: net worth per currency (credit cards count as amounts owed), assets vs owed, wallet overview.
- Settings: light / dark / system theme, main currency, privacy statement.
- Exact money math in minor units with JVM unit tests.
- No internet permission; Android cloud backup disabled for financial data.

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
   On Windows use `gradlew.bat`.

## Project layout
See [CLAUDE.md](CLAUDE.md) for architecture, money rules and engineering standards.

## Licenses
Inter typeface © The Inter Project Authors, SIL Open Font License 1.1 — see `docs/licenses/Inter-OFL.txt`.
"# lucento-android-app" 
"# lucento-android" 
