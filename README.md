# Lucento

**Financial clarity without the complexity.**

Lucento is a native Android personal finance app — income & expenses, budgets, goals, manual wallets,
and AI-assisted entry — built local-first: your data stays on your device and every core feature works offline.

> Status: **v0.4.0 — financial-integrity hardening, authentic Room schema history, a stricter CI pipeline,
> mockup-led UI refinements and an original Lottie motion system.** v0.4.0 has **not yet been compiled** — see
> [docs/QUALITY.md](docs/QUALITY.md) for exactly what was and wasn't verified. Also: [docs/ROADMAP.md](docs/ROADMAP.md),
> [docs/DESIGN.md](docs/DESIGN.md), [docs/CALCULATIONS.md](docs/CALCULATIONS.md), [CHANGELOG.md](CHANGELOG.md).

## What works today (0.5.14)
- **Navigation**: a persistent dock — **Home · Transactions · (+) · Budget · Wallets** — whose central **+** opens the
  **transaction composer**; Insights, Savings goals, Scheduled transactions and Debts & loans open from Home's Quick
  links, Categories from the Budget tab; the Home bell lists scheduled payments that are overdue or due this week; a **profile** button (optional name and photo — from the phone, or the Google account's picture on request — appearance, Settings, Enabled features).
- **Onboarding**: welcome → focus → features → **choose your spending categories** (≈90 presets in 10 groups,
  search, custom categories; required) → review → currency & appearance → confirmation. On a new phone,
  **I already have an account** (when sync is available) reminds you to sync the old phone first, signs in, downloads
  the account — records and settings — and goes straight to Home: no story, no guided setup; an empty account continues
  with the normal setup.
- **Home**: a working header (sync status, month selector, profile); the **Budget pace** card — % of budget used,
  budget used vs month elapsed, a verdict in words and Lucento, the mascot, whose look follows it; **net worth** as
  assets − liabilities with shortcuts to goals, scheduled transactions, debts and categories; the savings goal closest
  to done; **scheduled transactions** (tap to review and mark paid).
- **Wallets** include **investments** (stocks, funds, MP2, time deposits and more, each with a kind and an icon):
  money moves in and out with transfers, and **Update value** records growth or loss as a balance adjustment, never as
  income or spending. **Property & valuables** (home, car, jewellery) are valued by hand and never used for
  transactions. Both count in net worth.
- **Safe to spend** is the lower of what the budget allows and what your **spending money** allows: the wallets you
  count as spending money (cash, bank and e-wallet by default; savings and investments when you switch them on; part of
  a card's credit if you choose), less bills, loan and card payments and transfers out due this month. An (i) shows
  both figures line by line. See [docs/CALCULATIONS.md](docs/CALCULATIONS.md).
- **Budget**: the total is always the **sum of category amounts**; per-category spent/left/status; spending outside
  the budget shown transparently; amounts per month and currency; manage categories (add, rename, reorder,
  deactivate, restore). Existing users keep their categories and can personalise them from presets.
- **Insights**: the Ask Lucento card and the Reports list, then income and expenses over 3/6/12 months (transfers
  excluded, with the change against the period before; 6 and 12 months are Pro, previewed with the figures hidden on
  Free), where your money went, and net worth over time.
- **Ask Lucento** (from Insights): questions about your money, typed or spoken, and six statements — wallet statement,
  cash flow, net worth, budget vs actual, debts & loans, top spending categories — calculated on the phone, saved or
  shared as PDFs. AI answers (Gemini through Lucento's
  proxy) for a signed-in account, confirmed before the first question is sent: the question with a summary of your
  finances, including recent transactions; "Keep answers on this phone" turns them off. See [docs/ASK.md](docs/ASK.md).
- **Transactions**: search, filters (date, type incl. transfers, wallet, category), day groups, edit/delete.
- **Transfers** between your own accounts (two linked legs, atomic, never income or spending). From and To open one
  account picker — grouped into Everyday money, Set aside and growing, Things you own and What you owe, with balances,
  and search and recent accounts once there are more than six — also used by the transaction editor and the composer.
- **Wallets**: cash, bank, e-wallet, savings, investment, credit card, loan, money owed to you and property; a brand-blue net-worth card (assets above liabilities),
  assets and liabilities in separate cards (Debts & loans linked from Liabilities), each wallet shown as a bank-style card, warnings for balances that can't be right, and
  **Adjust balance** (the gap is recorded as a visible Balance adjustment, never income or spending); account detail with history.
  A wallet can show its bank or provider's logo and colours: 409 institutions in 48 countries are bundled offline (36 in
  the Philippines, including all seven BSP-licensed digital banks), chosen by country and kind or found by name, former
  name or abbreviation (`tools/institutions/`; display only — Lucento never connects to a bank).
- **Savings goals**, **Debts & loans** (optional interest rate; loans: one-time or monthly amortization with an
  instalment bar and payment reminders; credit cards: credit limit, available credit, statement and due days with an
  amount-due reminder that is never counted in Safe to spend),
  **Scheduled transactions** (bills, income, debt payments split into principal and interest, and transfers between
  your own accounts such as a monthly MP2 contribution; every bill and income has a category; mark paid / skip / pause —
  never auto-posted). The Scheduled page lists every dated occurrence with
  Transactions-style filters and a From–To range, summed up in the Transactions page's cards (income, expenses and net
  flow, with card and loan transfers apart). All schedules has a Select mode: change the day or amount of several
  plans, pause or resume them, or delete them, in one step; the editor warns when a card or loan already has a
  payment plan.
- **Transaction composer** (replaces the old Manual/Scan/Speak/Type it sheet): type, speak or scan, and every
  transaction in the message appears at once as an editable row ("Lunch 250, groceries 800, salary 35000" → three
  rows). Type, category, date and account are inferred; fix anything inline; save the batch with one tap (atomic,
  idempotent). Interpretation runs on the phone, with no network; Smarter reading (Pro, switched on in Settings)
  sends the message to Gemini through Lucento's proxy instead. See [docs/CAPTURE.md](docs/CAPTURE.md).
- **Lucento Scan** (2026-11-22): scan paper with the Document Scanner (several pages, flattened), take a photo, pick
  images or import a PDF (its text, or OCR; locked PDFs ask for the password once). Receipts, bills, invoices,
  payment confirmations, refunds and bank, card and e-wallet statements from the Philippines, the United States and
  20 other countries become rows: the business paid (never the payment app), the document's own currency and dates,
  the account that paid, the person's usual category. Statements are checked against the ledger — lines already
  saved are held back, near matches marked, the balance compared — and nothing saved is ever changed. All on the
  phone. See [docs/SCAN.md](docs/SCAN.md).
- **Home-screen widgets**: **Quick add** (the composer's own scan · mic · field bar — one tap into Type, Speak or Scan)
  and **Safe to spend** (Home's Monthly budget card in brief, with Add). Same figures as Home, in its words; they follow
  Hide amounts, App lock and the chosen theme; home screen only; no permission, no network. Add them from Settings ›
  Home-screen widgets or the launcher's widget list. See [docs/WIDGETS.md](docs/WIDGETS.md).
- **Appearance** (profile menu): five curated themes — Sunrise (the brand), Midnight (always dark), Grove, Mono and
  Aurora — each with its own landscape, every one contrast-checked (WCAG AA) with colour-blind-safe chart colours, plus
  System / Light / Dark. Switching is instant; money colours (income, spending, warnings) never change between themes.
  Home's five shortcut tiles take each theme's own hues, measured to stay clear of those money colours.
- **Settings**: main currency, **Show cents** (off rounds every amount you read to whole units; where you type an
  amount, and in a transaction's own view, it stays exact), Enabled features, CSV export, JSON backup and restore,
  privacy facts, an
  optional app lock (fingerprint, face or screen lock) and Hide in recent apps. Signed in, **Delete account** removes the
  account and its cloud data (docs/ACCOUNT_DELETION.md); the Terms of Use and Privacy Policy open from Settings ›
  Privacy & security (docs/LEGAL.md).
- **Motion**: the mascot's gentle float and pose cross-fades; original Lottie net-flow glyphs, onboarding mark,
  empty states, receipt scan beam, saved confirmation, milestones; awards as medals presented by the mascot — bundled and
  reduced-motion aware.
- Exact money math in minor units; more than 1,500 JVM unit tests; an emulator screenshot tour in CI.
- **Optional cloud sync** (Google Sign-In + Supabase, row-level security): off until you connect an account; every
  core feature works offline without it, and signing out keeps local data. Your settings and customisations (theme,
  features, name, default wallet…) follow the account too; consents and the app lock stay on each phone. See
  [docs/SYNC.md](docs/SYNC.md) — sign-in needs the Google OAuth setup listed there.
- **Lucento Shared**: one person's finances shared with a Partner who signs in with their own account. Both see everything and add, edit or delete transactions; the Owner
  manages wallets, budgets, goals, categories and schedules. Built on cloud sync; needs its Supabase migrations on the
  project. See [docs/shared/](docs/shared/).
- Android cloud backup disabled.

## Not yet
Reminder notifications and budget periods other than calendar months. See the roadmap.

## Tech
Kotlin · Jetpack Compose · Material 3 · Room · DataStore · ViewModel · Coroutines/Flow · Navigation Compose (type-safe).
JDK 17, compileSdk and targetSdk 36, minSdk 26 (Android 8.0+). Application ID `com.lucento.app` (debug: `com.lucento.app.debug`).

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
   Gradle must run on **JDK 17–21** (CI uses 17; the toolchain is AGP 9.5, Gradle 9.6 and Kotlin 2.4, built locally on
   JBR 21). JDK 25, which recent Android Studio versions bundle, isn't verified with it, so set *Settings › Build Tools ›
   Gradle › Gradle JDK* to a 17 or 21 JDK (or `JAVA_HOME` on the command line). With Gradle 8.11, JDK 25 failed at once
   with only `25.0.3` as the error. Optionally
   `./gradlew updateDaemonJvm
   --jvm-version=21` writes a per-machine `gradle/gradle-daemon-jvm.properties` (gitignored) that picks an installed 21.
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
Lucento's own code, designs and animations: © 2026 Jerald Simagala, all rights reserved; see [LICENSE](LICENSE).

Plus Jakarta Sans © The Plus Jakarta Sans Project Authors, SIL Open Font License 1.1 — see `docs/licenses/PlusJakartaSans-OFL.txt`.
Airbnb Lottie, Apache License 2.0. All animation files are original to Lucento (`docs/design/ANIMATION_ASSETS.md`).
