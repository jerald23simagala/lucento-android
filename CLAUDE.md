# Lucento — Engineering Instructions

Lucento is a native Android personal finance app. Philosophy: **"Financial clarity without the complexity."**
These instructions are permanent. Read them before every change.

## Role
Act as principal Android engineer, product designer, architect, QA engineer and tech lead. Work autonomously,
make sensible decisions, and ship real functionality — not plans, mockups or scaffolding.

## Stack (do not substitute)
- Kotlin, Jetpack Compose, Material 3. **Never** a WebView, Flutter or React Native.
- Room (local source of truth), DataStore (preferences), ViewModel + Coroutines/Flow, Navigation Compose (type-safe routes).
- Versions live in `gradle/libs.versions.toml`. Upgrade deliberately and keep them mutually compatible
  (AGP ↔ Gradle ↔ Kotlin ↔ KSP ↔ Compose compiler).
- JDK 17. compileSdk/targetSdk 35, minSdk 26. Application ID `com.lucento.app` (debug builds add `.debug`).

## Architecture
```
app/src/main/java/com/lucento/app/
  core/money/          Money (Long minor units), MoneyParser, MoneyFormatter — no floating point, ever
  core/designsystem/   theme (Color, Type, Dimens, Theme) + reusable components
  data/local/          Room entities, DAOs, LucentoDatabase
  data/preferences/    DataStore-backed UserPreferencesRepository
  data/repository/     Repositories mapping entities <-> domain, enforcing validation
  domain/              Pure Kotlin models and calculators (unit-tested, no Android deps)
  feature/<name>/      Screen + ViewModel per feature
  navigation/          LucentoRoot, routes, NavigationDock (Home · Transactions · Wallets · Insights · Budget + More menu),
                       app-wide + Add sheet (Manual · Scan · Speak · Type it), profile menu (Settings lives there)
```
- Dependency wiring: `AppContainer` in `LucentoApplication` (manual DI). ViewModels use `containerFactory`.
- Keep financial logic in `domain/` or `core/money/` as pure functions so it is testable on the JVM.

## Money rules (non-negotiable)
- Store amounts as `Long` minor units with an ISO 4217 code. Use `CurrencyRules.fractionDigits` (JPY 0, BHD 3).
- Never convert between currencies implicitly. Totals are per currency.
- Use `Math.addExact`-style arithmetic; overflow must throw, not wrap.
- Parse user input only through `MoneyParser`.
- Transfers are a single atomic DB transaction producing two linked legs (`transfer_group`); never double-count.
- Financial definitions (budget, burn rate, net flow, goals, net worth, debts) live in `docs/CALCULATIONS.md`.
- **The overall budget is always the sum of category allocations** (`BudgetPlanner`), per currency and calendar month.
  Never add a separately editable overall amount; legacy "everything" rows are ignored. Home, Budget and Insights read
  the same `BudgetPlan`.
- Guard against duplicate submissions (in-flight flags, idempotency keys for imports).

## Database
- `exportSchema = true`; schemas land in `app/schemas/` and **are committed** (1–3 recovered from CI artifacts).
  CI fails if the exported schema differs from the committed one. Never hand-write a schema JSON.
- Wallet type/currency edits go through `WalletChangePolicy`; categories in use are archived, never deleted.
- Fresh installs seed income categories only; expense categories come from the mandatory onboarding step
  (`CategoryPresets` + `CategorySelection`). `DefaultCategories.all` is frozen (MIGRATION_1_2 depends on it).
- Every version bump needs an explicit `Migration` plus a migration test. Never enable destructive fallback.

## Privacy & cost
- Core features work fully offline. No mandatory paid APIs, accounts, cloud, or bank integrations.
- The app has **no INTERNET permission**; the manifest strips network permissions merged by libraries (ML Kit).
  Adding network access requires an explicit, opt-in feature (e.g. optional remote AI) with clear user consent
  before any financial data leaves the device. OCR uses ML Kit's bundled on-device model.
- Never hardcode API keys or secrets. Never commit keystores. Android auto-backup is disabled; Lucento
  provides its own explicit backup/restore.

## Design standards
- Palette: brand blue `#2563EB` primary, sun-yellow accent (sparingly), warm cream / deep navy neutrals, midnight-navy
  dark theme. No dynamic colour. Tokens, components and rules are in `docs/DESIGN.md`; never hardcode colours.
- Brand: blue L + yellow leaf mark; original Sun (light) / Moon (dark) mascot (`LucentoMascot`), small on dashboards.
- Optional modules are switched through the typed `FeatureSet` (DataStore). Disabling a module hides it and never
  deletes data; core transactions/accounts are never optional.
- Amounts are always signed via `MoneyFormatter.formatSigned` so meaning never depends on colour alone.
- Typography: Inter (body) + Inter Display (headings), bundled under OFL. Amounts use tabular figures (`AmountStyles`).
- Flat surfaces with hairline borders; no heavy gradients, glassmorphism, or decorative charts.
- Every screen has one clear purpose. **No buttons that do nothing. No fake financial data.**
- Haptics through `rememberHaptics()`; structural motion via Compose animation APIs, short and purposeful.
- Lottie (`lottie-compose`, version catalog) only through `LucentoLottieAnimation` / `LucentoMascot` /
  `ConfirmationHost`; assets are original, bundled in `res/raw`, generated by `tools/lottie`, and registered in
  `docs/design/ANIMATION_ASSETS.md`. Success/milestone animations play only after a committed write.
- `docs/design-reference/lucento-mock.png` is the visual reference; implement it through tokens and shared components.
- Accessibility: 48dp touch targets, content descriptions, headings semantics, sufficient contrast in both themes.

## Quality
- Write JVM unit tests for every financial calculation and critical workflow (`app/src/test`).
- Run `./gradlew testDebugUnitTest assembleDebug` before pushing. Without the Android SDK, `tools/jvm-harness/run.sh`
  compiles and runs the Android-free code and all JVM tests (a fallback, not a replacement for CI).
- `ScreenshotTourTest` (androidTest, run by CI on an emulator) walks the main flows and saves screenshots for review.
- Never claim a build or test passed without seeing it pass (locally or in GitHub Actions).
- Fix defects instead of hiding them. Keep `docs/ROADMAP.md` and `README.md` current.

## Build & delivery
- CI: `.github/workflows/android.yml` — tests, debug build, schema drift, offline migration check, lint (errors
  block), release APK + AAB with permission checks, emulator migration tests. See `docs/QUALITY.md`.
- Debug signing only for now. Release signing will use GitHub encrypted secrets — never committed files.
