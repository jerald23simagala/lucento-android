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
- JDK 17. compileSdk/targetSdk 36 (Play requires 36 since 31 Aug 2026), minSdk 26. Application ID `com.lucento.app` (debug builds add `.debug`).

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
  navigation/          LucentoRoot, routes, NavigationDock (Home · Transactions · (+) · Budget · Wallets; the centre +
                       opens the transaction composer in feature/capture; Insights, goals, scheduled and debts open from
                       Home's Quick links, categories from Budget), profile menu (Settings lives there); a new user's
                       guided setup (feature/guide) follows onboarding; Lucento Pro's welcome (feature/pro/welcome:
                       a tour over the real screens via Modifier.spotlightTarget) plays after a confirmed plan
```
This tree and the navigation notes above describe the current implementation. They are
not mandatory UX or information-architecture constraints. Claude may propose and implement
a better user-facing structure while preserving sound engineering boundaries.

- Dependency wiring: `AppContainer` in `LucentoApplication` (manual DI). ViewModels use `containerFactory`.
- Keep financial logic in `domain/` or `core/money/` as pure functions so it is testable on the JVM.

## Money rules (non-negotiable)
- Store amounts as `Long` minor units with an ISO 4217 code. Use `CurrencyRules.fractionDigits` (JPY 0, BHD 3).
- Never convert between currencies implicitly. The main currency is chosen once and locked; a movement in another
  currency carries an explicit, stored value in it (rate source recorded), and totals use those values by the average
  method (docs/CALCULATIONS.md "Exchange rates", docs/MULTICURRENCY.md). Rates are exact decimals (`FxRate`), never
  floating point. A wallet with no rate stays in its own currency.
- Use `Math.addExact`-style arithmetic; overflow must throw, not wrap.
- Parse user input only through `MoneyParser`.
- Transfers are a single atomic DB transaction producing two linked legs (`transfer_group`); never double-count.
- Financial definitions (budget, burn rate, net flow, goals, net worth, debts) live in `docs/CALCULATIONS.md`.
- **The overall budget is always the sum of category allocations** (`BudgetPlanner`), per currency and calendar month.
  Never add a separately editable overall amount; legacy "everything" rows are ignored. Home, Budget and Insights read
  the same `BudgetPlan`.
- Guard against duplicate submissions (in-flight flags, idempotency keys for imports).
- New transactions are added only through the composer (`feature/capture`): `TransactionInterpreter` → `BatchReview`
  → `TransactionRepository.saveBatch` (atomic, one idempotency key per row). The full-page editor (`TransactionEditorScreen`, opened from
  Edit on a history row) is for editing existing transactions. Never present local parsing as AI; a remote interpreter needs the opt-in described in docs/CAPTURE.md.

## Database
- `exportSchema = true`; schemas land in `app/schemas/` and **are committed** (1–3 recovered from CI artifacts).
  CI fails if the exported schema differs from the committed one. Never hand-write a schema JSON.
- Wallet type/currency edits go through `WalletChangePolicy`; categories in use are archived, never deleted.
- Fresh installs seed income categories only; expense categories come from the mandatory onboarding step
  (`CategoryPresets` + `CategorySelection`). `DefaultCategories.all` is frozen (MIGRATION_1_2 depends on it).
- Every version bump needs an explicit `Migration` plus a migration test. Never enable destructive fallback.

## Privacy & cost
- Core features work fully offline. No mandatory paid APIs, accounts, cloud, or bank integrations.
- Network use is only what the person turns on, plus two defaults. The in-app Privacy Policy
  (`feature/legal/LegalDocuments.kt`, published at the website's /privacy) must list every path, and any summary of it
  (website, onboarding, store listing) must either list them all or be scoped (for example "your entries") and link to it.
  - **Opt-ins, each off until chosen:** cloud sync (docs/SYNC.md): nothing leaves the device until the user signs in
    (Google, or an email account) and keeps sync on; Room stays the source of truth. A signed-in account also gets account emails (verify, welcome, password reset code on request, Pro confirmation; docs/EMAILS.md); the app's only part is one welcome request with the account's token. The Gemini features through
    Lucento's proxy: Smarter reading sends the typed or spoken message (docs/CAPTURE.md); Ask Lucento's AI answers send
    the question with a summary of the person's finances, including recent transactions, under their own consent
    (docs/ASK.md). Anonymous usage counts (event names and timings with a random install id, kept 90 days). Crash
    reports via Firebase Crashlytics, stacks without amounts or notes (docs/QUALITY.md); production releases include
    it (owner decision 2026-11-08; CI writes `app/google-services.json` from the `GOOGLE_SERVICES_JSON` secret).
    Online voice (owner decision 2026-11-13; docs/CAPTURE.md): the mic asks the phone's speech service for offline
    only; after a failed try, and only with consent, it may go online, so the recording may reach Google. Lucento gets
    only the text. Lucento holds `RECORD_AUDIO` (asked on first use) only for the composer's own listening sheet; it
    keeps no recording, and without the permission the speech service's own window is used.
  - **On by default:** exchange rates (docs/MULTICURRENCY.md, owner decision 2026-11-06): fetched only when something
    is recorded in another currency, sending two currency codes and a date (never an amount); off in Settings › Money.
    In release builds, Google Play's billing component sends Google its own diagnostics. When the person buys Pro,
    Lucento's server checks the purchase with Google (docs/MONETIZATION.md). After that, signed in or not, the app
    sends Play's signed purchase record to `/api/billing/check` every few hours while online, so a refund ends Pro
    (owner decision 2026-11-13; SERVER_CONTRACT §3b). Mandatory sign-in was declined in its place.
  - Signing out, and switching to a different account, wipes this phone’s ledger and sync binding so the next account
    starts clean and one user’s data is never uploaded into another account; unsynced changes require confirmation
    before that wipe. The one exception (owner decision 2026-11-12): records no account or Shared Space ever claimed
    are the phone’s own, so signing out asks whether to keep them or remove them (`AccountBoundary.signOutKeepingLedger`,
    docs/SYNC.md). The action is called "Sign out" everywhere. Any other network use needs its own explicit opt-in with clear consent. CI allow-lists release
    permissions. OCR uses ML Kit's bundled on-device model.
- Supabase: schema changes ship as new files in `supabase/migrations/` (never edit applied DDL); every user-owned
  table has RLS (`auth.uid() = user_id`) and grants to `authenticated` only. Only the publishable key may reach the
  APK, via `local.properties`/CI env — never a service-role key or OAuth client secret.
- Never hardcode API keys or secrets. Never commit keystores. Android auto-backup is disabled; Lucento
  provides its own explicit backup/restore.

## Design principles

These principles protect product coherence and accessibility; they are not a frozen visual
specification or a requirement to preserve the current UI.

- Lucento should communicate financial clarity without unnecessary complexity.
- The current Sunrise Energy palette, Plus Jakarta Sans typography, Rising Crest logo,
  mascot, and existing design tokens are useful brand assets and starting points—not
  restrictions on future design exploration.
- Use the design system and shared components where they improve consistency. Claude may
  evolve the visual system, typography, colors, spacing, surfaces, components, iconography,
  and motion when a coherent improvement is justified.
- Do not hardcode design tokens or create one-off styles that fragment the experience.
- Protect original logo and mascot source files. Do not destructively overwrite approved
  artwork; if a new brand-art direction is warranted, develop it as a proposal or separate
  asset so the original can be recovered.
- Avoid generic fintech templates, repetitive card grids, decorative data visualizations,
  excessive borders, and visual effects that do not improve comprehension.
- Every screen and interaction must have a clear purpose. No buttons that do nothing and
  no fake financial data.
- Accessibility is a product requirement: provide appropriate semantics and descriptions,
  sufficient contrast, and touch targets of at least 48dp.
- Amounts must always communicate positive/negative meaning through signed formatting,
  not color alone.
- Use haptics through `rememberHaptics()`. Use Compose animation APIs for purposeful,
  restrained motion; success or milestone animations play only after a committed write.
- Keep Lottie usage through `LucentoLottieAnimation`, `LucentoMascot`, or
  `ConfirmationHost`; follow `docs/design/ANIMATION_ASSETS.md` for asset registration.
- `docs/DESIGN.md` and `docs/design-reference/lucento-mock.png` document the current
  design system and a historical reference. Consult them for context, but do not treat
  them as mandatory screen specifications or acceptance criteria.

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

## Agent Coordination
- Main Claude Code session owns implementation, debugging, integration, and delivery.
- `lucento-architect` reviews architecture and technical decisions.
- `lucento-qa` reviews quality, regression risks, and test coverage.
- Specialists are read-only unless explicitly authorized.
- Delegate only work that benefits from independent analysis.
- Avoid duplicate investigations.
- Main Claude Code remains accountable for all changes.

## Git Workflow
- Never commit changes directly to `main`.
- Develop on appropriately named feature branches.
- Inspect `git status` before modifying files.
- Never discard or overwrite unrelated user changes.
- Never commit, push, or merge without explicit authorization.
- Verify the active branch before committing.

## Token Efficiency
- Read existing documentation before rediscovering established decisions.
- Inspect only files relevant to the current task.
- Avoid unnecessary repository-wide scans and repeated file reads.
- Do not regenerate existing reports or screenshots without reason.
- Do not use multiple agents for simple tasks.
- Prefer concise progress updates and focused findings.
- Reuse existing components, utilities, and architecture.

## Implementation Discipline
- Understand existing code before modifying it.
- Prefer targeted changes over broad rewrites.
- Preserve existing functionality unless explicitly instructed otherwise.
- Identify dependencies and regression risks before changing shared components.
- Explain the necessity of new dependencies.
- Never fabricate test results or completion status.

## Handover Protocol
For substantial development sessions, maintain concise documentation in `docs/handover/`.

- `CURRENT_STATUS.md`: Verified implementation state and progress.
- `NEXT_TASKS.md`: Prioritized outstanding work.
- `KNOWN_ISSUES.md`: Confirmed defects, limitations, and unresolved risks.

Record completed changes, relevant file paths, actual test results, important decisions, unfinished work, and the next recommended action.
Avoid copying entire conversations or generating unnecessarily large reports.


## UI/UX Creative Authority

Claude has broad creative authority to independently evaluate, improve, and redesign
Lucento's user experience. The goal is to create an excellent, coherent global personal
finance app—not merely to polish the existing screens or reproduce previous design work.

### Start from first principles

- Treat the current app, screenshots, mockups, design explorations, and previous audit
  reports as historical context, not mandatory specifications.
- Do not assume existing layouts, navigation, onboarding, information architecture,
  component arrangements, color treatments, or interaction patterns are optimal.
- Do not let previous Grok/Claude proposals, approved screenshots, or historical design
  baselines become implicit acceptance criteria.
- Inspect the actual source code and running application to understand current behavior,
  capabilities, constraints, and implementation before proposing changes.
- Evaluate the product with fresh eyes. Consider substantially different approaches when
  they produce a clearer, simpler, more useful experience.

### Creative authority

Claude may independently:
- Redesign one screen or the entire application.
- Rework navigation, information architecture, and feature discoverability.
- Reimagine onboarding, setup, empty states, and first-run experiences.
- Restructure dashboards and the presentation of wallets, assets, liabilities, budgets,
  transactions, goals, recurring items, debts, and insights.
- Create or replace visual systems, design tokens, reusable components, and interaction
  patterns.
- Simplify, combine, relocate, or remove UI elements that create friction or add no value.
- Introduce thoughtful transitions, animations, haptics, and micro-interactions.
- Challenge established design decisions and implement justified improvements without
  seeking approval for every routine design choice.

### What good design means

Optimize for:
- Immediate comprehension and a strong visual hierarchy.
- Low cognitive load and fewer unnecessary steps.
- Clear financial context, useful insights, and confidence in actions.
- A cohesive product identity rather than a collection of unrelated screens.
- Excellent usability across small and large devices, font scaling, light and dark themes,
  and populated, empty, loading, validation, and error states.
- Accessibility, predictable interaction, and appropriate feedback.
- A polished, distinctive consumer-fintech experience without relying on fashionable
  effects or generic dashboard conventions.

### Product and engineering guardrails

Creative freedom does not authorize breaking the product:
- Preserve financial correctness, money-handling rules, data integrity, security, privacy,
  offline-first behavior, and user consent requirements.
- Preserve established capabilities unless there is a clear product reason to change,
  consolidate, or retire them. Do not silently remove functionality.
- Keep Room as the local source of truth and respect the existing architecture and
  engineering constraints. UX and navigation may evolve without casually compromising
  sound data/domain boundaries.
- Do not introduce fake data, misleading financial representations, nonfunctional controls,
  or unnecessary network/API dependencies.
- Protect original brand source assets and existing user data. Make changes reversible
  where practical.

### Design implementation and validation

For substantial UX changes:
- Explain the user problem, the proposed direction, and the expected benefit briefly.
- Consider the end-to-end journey and how the change affects related screens and features.
- Implement the experience rather than stopping at recommendations or mockups, unless the
  task explicitly requests an audit or proposal only.
- Validate relevant screen sizes, font scaling, themes, accessibility semantics, and
  important application states.
- Run appropriate tests and report exactly what was and was not validated.
- Capture updated screenshots when useful, but treat them as evidence of the new
  implementation—not as permanent constraints on future design work.

Claude does not need prior screenshot or mockup approval for ordinary design improvements.
For unusually high-impact product decisions—such as removing a major capability, changing
a core financial concept, or introducing a materially different product direction—surface
the rationale and trade-offs before making an irreversible change.

The standard is not fidelity to the existing UI. The standard is a demonstrably better
Lucento experience, delivered without compromising product integrity or engineering quality.
