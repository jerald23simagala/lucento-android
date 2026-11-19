# Lucento Pro in-app experience: audit, decisions and what was built (2026-11-19)

Owner brief: "Implement Lucento Pro In-App Monetization Experience" (Discover → Explore → Understand Value → Upgrade).
Audited against `main` at #109 (Free limits in setup, one Pro dialog at the tap). The owner then decided four questions
(below), and `feature/pro-repeat-taps-previews` built the result.

## 1. What existed (the audit)

### Entitlements
Centralised; no change was needed.
- **Plan model.** `ProFeature` has 14 features, each needing one `Capability`: LOCAL_PRO, CLOUD, AI_PRO or SHARED.
- **Limits.** `PlanCatalog` writes every limit for each plan and generates the Free / Pro / Lifetime comparison from them.
- **Checks.**
  - `EntitlementEvaluator` answers Allowed, Locked or LimitReached.
  - `ProGates` holds the helpers the screens use.
- **Data-layer gates.**
  - `WalletRepository`: counts per kind (retypes included since #109) and wallets in other currencies.
  - `GoalRepository`: active goals and restores.
- **Server gates.**
  - The AI proxy enforces `plan_limits`.
  - Sync writes need CLOUD (security audit F-01).
  - Lucento Shared invitations are decided on the server.
- **Plan state.** `EntitlementManager` merges Play, the server and grants into a sealed cache with a `ready` flag. A failed check never grants Pro (`PurchaseInterpreter` tests).

### Billing
Untouched.
- Play Billing, with server purchase checks (`PurchaseVerifier`).
- Restore and Manage subscription on the plan page.
- A test-build plan simulator.
- Prices come only from Google Play, and the products aren't created yet (OT-16).

### Entry points
- The profile menu: a Lucento Pro card on Free; otherwise the plan row.
- The Settings hub: a plan row showing the plan's status.
- Every wall's dialog.
- The Pro welcome after a purchase.
- Nothing on Home (owner 2026-11-09, option G not chosen; re-confirmed 2026-11-19).

### Visibility
- Nothing is hidden by plan. Modules are the person's own toggles.
- Pro marks:
  - themes;
  - wallet types (their TalkBack label states the plan fact);
  - "6 months · Pro";
  - "Save as PDF · Pro".

## 2. Classification matrix (from `PlanCatalog` and the gates)

Gating UX:
- **A:** a contextual dialog.
- **B:** a locked preview.
- **C:** free up to a limit, then Pro at the limit.
- **D:** fully free.

| Feature | Access (Free / Pro) | Gating UX | Preview |
|---|---|---|---|
| Transactions and composer, categories, budgets, scheduled, debt tracking, statements on screen, Ask's on-phone answers, backup file, widgets, receipts (OCR), exchange rates | Free, unlimited | D | — |
| Money wallets (cash, bank, e-wallet, savings, investment) | 3 / unlimited | C: Add → type sheet → dialog | No |
| Cards and loans | 2 / unlimited | C | No |
| Savings goals | 2 / 20 | C | No |
| AI adviser questions | 10 a month / 50 a day | C: dialog at the limit | No |
| Receivables | — / Pro | A, at the type | No |
| Property & valuables | — / Pro | A, at the type | No |
| Wallets in other currencies | main currency only / Pro | A, at the currency | No |
| Insights 6 and 12 months | 3 months / Pro | **B** | **Yes:** your own months, figures hidden |
| PDF statements | on screen / Pro | C: "· Pro" marker + dialog | No |
| Themes Grove, Mono, Aurora | Sunrise, Midnight / Pro | **B** | **Yes:** a try-on, never saved |
| Cloud sync, restore from account | backup file / Pro | A | No |
| Smarter reading | — / Pro | A | No |
| Lucento Shared (inviting a Partner) | joining / Pro | A | No |

No free feature is hidden, and no classification or limit changed. "Advanced budgeting" (the brief's example) doesn't
exist yet: custom budget periods are a future Pro feature (NEXT_TASKS).

## 3. The owner's decisions (2026-11-19)

| Question | Choice |
|---|---|
| What the dialog leads with | **The plan fact, as built** ("The Free plan includes 3 money wallets"; See Lucento Plan / Not now). The brief's "Upgrade to Pro / Maybe later" and value-first lines were not chosen |
| Repeat taps | **A short message after the first time, per app session**: the first tap on a feature in a session is the dialog; later taps are "{plan fact} · Unlock". In memory only |
| A Lucento Pro card on Home | **No** (re-confirms 2026-11-09) |
| Previews | **Insights 6 and 12 months** (your own data, figures hidden) and **a theme try-on** (never saved) |

## 4. What was built

- **Repeat taps.**
  - `ProWallMemory` lives in the app container and is held in memory for the session.
  - `ProWallDialog` decides for itself whether it's the first tap, so no call site changed.
  - A repeat shows the plan fact with **Unlock**, which does what See Lucento Plan does.
  - The message appears where the person is. `MessageHosts` routes it to the newest open sheet's own snackbar (every `LucentoBottomSheet` has one), and otherwise to the app's.
- **Insights preview** (`LongPeriodPreview`).
  - Picking 6 or 12 months on Free shows your own income and expenses for that period, softened (blurred on Android 12+), with the axis blank (the chart's hidden-amounts mode) and one spoken line that says the figures are hidden.
  - Below it: the plan fact, See Lucento Plan, and Back to 3 months.
  - With nothing recorded there is no picture, only the plan fact.
- **Theme try-on** (`ThemeTrial`, in the container and in memory).
  - Tapping a Pro theme shows Lucento in it at once. A note below it says the plan fact, that nothing is saved and what the theme goes back to, with See Lucento Plan and Done.
  - The trial ends on Done, on picking a theme of your own, or when the profile sheet closes.
  - `AppViewModel.setThemeId` still never saves a locked theme.
- **Unchanged:**
  - billing, entitlements, limits, the data model and the database;
  - Settings, whose plan row and plan page already cover the benefits, comparison, status, prices, restore and manage;
  - Home.

## 5. Tests

- **JVM:**
  - `ProWallMemoryTest`: the first tap is the dialog and later taps the message, per feature; a new session starts again.
  - `ThemeTrialTest`.
  - `MessageHostsTest`: the newest sheet wins, otherwise the app.
- **Emulator** (local capture `ProExperienceShotsTest`):
  - the first tap's dialog, the second tap's message, and Unlock opening the plan page;
  - the same inside the wallet editor's sheet;
  - the Insights preview with seven months of real income and expenses;
  - the theme try-on and its end.
- **CI:** `ScreenshotTourTest` now leaves the locked period with "Back to 3 months".
- **Not runnable here:** a real purchase, expiry and restore against Google Play (no products yet, OT-16). The simulator and the `PurchaseInterpreter` tests cover the plan logic.
