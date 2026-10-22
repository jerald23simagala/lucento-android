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
  navigation/          LucentoRoot, routes, bottom navigation (Home · Transactions · Wallets; Settings pushed)
```
- Dependency wiring: `AppContainer` in `LucentoApplication` (manual DI). ViewModels use `containerFactory`.
- Keep financial logic in `domain/` or `core/money/` as pure functions so it is testable on the JVM.

## Money rules (non-negotiable)
- Store amounts as `Long` minor units with an ISO 4217 code. Use `CurrencyRules.fractionDigits` (JPY 0, BHD 3).
- Never convert between currencies implicitly. Totals are per currency.
- Use `Math.addExact`-style arithmetic; overflow must throw, not wrap.
- Parse user input only through `MoneyParser`.
- Transfers (future) must be a single atomic DB transaction producing two linked legs; never double-count.
- Guard against duplicate submissions (in-flight flags, idempotency keys for imports).

## Database
- `exportSchema = true`; schemas land in `app/schemas/` (CI uploads them as an artifact — commit them).
- Every version bump needs an explicit `Migration` plus a migration test. Never enable destructive fallback.

## Privacy & cost
- Core features work fully offline. No mandatory paid APIs, accounts, cloud, or bank integrations.
- The app currently requests **no INTERNET permission**. Adding it requires an explicit, opt-in feature
  (e.g. optional remote AI) with clear user consent before any financial data leaves the device.
- Never hardcode API keys or secrets. Never commit keystores. Android auto-backup is disabled; Lucento
  provides its own explicit backup/restore.

## Design standards
- Palette: deep pine primary, single brass accent (sparingly), warm ivory/ink neutrals. No dynamic colour.
  Tokens, components and rules are documented in `docs/DESIGN.md`; never hardcode colours in features.
- Amounts are always signed via `MoneyFormatter.formatSigned` so meaning never depends on colour alone.
- Typography: Inter (body) + Inter Display (headings), bundled under OFL. Amounts use tabular figures (`AmountStyles`).
- Flat surfaces with hairline borders; no heavy gradients, glassmorphism, or decorative charts.
- Every screen has one clear purpose. **No buttons that do nothing. No fake financial data.**
- Haptics through `rememberHaptics()`; motion via Compose animation APIs, short and purposeful.
- Accessibility: 48dp touch targets, content descriptions, headings semantics, sufficient contrast in both themes.

## Quality
- Write JVM unit tests for every financial calculation and critical workflow (`app/src/test`).
- Run `./gradlew testDebugUnitTest assembleDebug` before pushing.
- Never claim a build or test passed without seeing it pass (locally or in GitHub Actions).
- Fix defects instead of hiding them. Keep `docs/ROADMAP.md` and `README.md` current.

## Build & delivery
- CI: `.github/workflows/android.yml` runs tests, builds the debug APK, uploads `lucento-debug-apk`.
- Debug signing only for now. Release signing will use GitHub encrypted secrets — never committed files.
