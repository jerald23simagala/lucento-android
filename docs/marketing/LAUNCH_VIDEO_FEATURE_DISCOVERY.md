# Lucento launch film: feature discovery

**App version:** 0.5.8 (versionName), `main` at `0679ccf`. **Date:** 2026-11-14.
**Method:** every feature below was verified in the Kotlin source (`app/src/main/java/com/lucento/app/`), not taken
from the README, roadmap or changelog, which are partly out of date (see section 6). The screens used in the film were
captured from the same build on the API 35 emulator on 2026-11-14 with the demo month the website and Play listing
already use (`DemoMonth.seed`, pesos ×10).

Companion documents live in [`launch-film/`](launch-film/): the feature-to-visual map, the storyboard, the voiceover
script, the asset inventory, the cut-downs and future video topics.

---

## 1. What Lucento really is

Lucento is a complete personal-finance workspace that runs on the phone. Money in and out, every account the person
holds (cash, bank, e-wallet, savings, cards, loans, things they own, money owed to them), budgets, goals, bills,
debts, reports and an assistant all live in one ledger. That ledger works fully offline and can be shared with one
partner. It is not an expense tracker with extras. The strongest evidence is how the pieces connect:

- Safe to spend sets upcoming bills aside before it tells you what's left.
- A loan payment splits into principal and interest.
- A credit card knows its statement cycle.
- Ask Lucento answers from the same ledger, and every figure in an answer is calculated on the phone.

**Strongest differentiators, in order:**

1. **Type or say it like a message.** One sentence ("Lunch 250, groceries 800, salary 35000") becomes several
   reviewed transactions, saved in one tap. Voice works the same way, and a bank statement screenshot becomes a month
   of rows. All of it is read on the phone, with no AI service needed.
2. **One clear view of everything.** Dashboard, net worth (assets above, liabilities below), Safe to spend, bills due.
3. **A whole financial life, not just spending.** Credit card cycles, loan amortization, things you own, money owed
   to you, and multiple currencies at your own rates.
4. **Ask Lucento.** Plain-language questions are answered offline from your own records, and optional AI advice never
   invents a number.
5. **Lucento Shared.** Two people, their own phones, one ledger.
6. **Private by design.** No bank login, works offline, Hide amounts, App lock.

## 2. How to read the inventory

- **Status:** `Full` = built and reachable in the UI. `Partial` = built but limited (stated). `Not built` = absent or
  planned only; never market these.
- **Plan:** `Free` or `Pro` exactly as `domain/entitlement/PlanCatalog.kt` and `ProGates.kt` enforce it. Pro
  Lifetime keeps the on-phone Pro features for good; its online services (sync, AI allowance, Shared) are included
  for the first year, then need the Lucento Cloud add-on.
- **Film:** `Hero` (dedicated demo), `Support` (meaningful moment), `Montage` (quick beat), `Later` (its own video),
  `No` (keep out, with reason).

Paths are relative to `app/src/main/java/com/lucento/app/`.

## 3. Feature inventory

### 3.1 Dashboard (Home)

| Feature | Where | Status | Plan | User benefit | Marketing potential | Film |
|---|---|---|---|---|---|---|
| Net wallet balance card, drawn as a bank card with the theme's landscape: Wallets vs Debt & Loans, change vs last month | `feature/home/HomeScreen.kt` | Full | Free | Your position at a glance | High: the signature visual | Hero (S02) |
| Shortcut row: Goals, Insights, Awards, Debts, Scheduled; switched-off modules offer "Turn on…" | `feature/home/HomeScreen.kt` | Full | Free | Everything one tap away | Medium | Hero (S02) |
| Monthly budget card: verdict chip (Ahead of pace / On track…), % spent ring, "To watch" categories | `feature/home/HomeScreen.kt` | Full | Free | Know if you're on pace before it's too late | High | Hero (S02) |
| **Safe to spend**: budget − spent − bills still due this month; never shown below zero | `feature/home/HomeState.kt` | Full | Free | A number you can act on today | High (also a widget) | Support (S02) |
| Upcoming (scheduled items and card payments) and Recent transactions | `feature/home/HomeScreen.kt` | Full | Free | What's next and what just happened | Medium | Montage |
| Payments bell: overdue and next 7 days, "To pay by …" total, one-tap card payment | `feature/home/HomeScreen.kt` (`PaymentsBell`) | Full | Free | Bills in one place, in the app | Medium (in-app only, see 5) | Montage |
| Month selector, up to 24 months back | `feature/home/HomeViewModel.kt` | Full | Free | Look back at any month | Low | No |
| Hide amounts: one tap shows ₱•••• everywhere, including the widget; showing them again needs fingerprint, face or screen lock | `feature/home`, `data/preferences` | Full | Free | Check your money on a bus | High | Support (S08) |
| Sync chip: "On device" / "Synced · 5m" / "3 to sync" | `feature/home/HomeScreen.kt` | Full | Free | Always know where your data is | Low | Montage ("On device" in the Dashboard header) |
| Dashboard customisation (reorder or hide cards) | — | Not built (cards follow Settings › Enabled features only) | — | — | — | No |
| Goals or spending-breakdown card on Home | — | Not built (computed, never drawn) | — | — | — | No |

### 3.2 Wallets and financial position

| Feature | Where | Status | Plan | User benefit | Marketing potential | Film |
|---|---|---|---|---|---|---|
| Wallet types: Cash, Bank account, E-wallet, Savings, Credit card, Loan | `domain/Wallet.kt`, `feature/wallets/` | Full | Free: 3 money wallets + 2 cards/loans; Pro: no limit | Every account in one place | High | Hero (S03) |
| Receivable (money owed to you) and Other asset (home, car, investments; value typed in by you) | `domain/Wallet.kt` | Full | Pro | A true net worth, not just bank balances | High | Support (S03) |
| Net worth hero: "+3% vs Oct", "You owe X% of what you have", 6-month trend, assets/liabilities split bar | `feature/wallets/WalletsScreen.kt` | Full | Free | Understand your position, not just your balance | High | Hero (S03) |
| Card view and row view (remembered) | `feature/wallets/WalletsScreen.kt` | Full | Free | Personal, tactile overview | High (card wall) | Hero (S03) |
| Live card editor: "Build it below and watch it take shape" | `feature/wallets/WalletEditorSheet.kt`, `WalletCard.kt` | Full | Free | Setting up feels good | Medium | Later |
| 409 bank and e-wallet faces in 48 countries (36 Philippine), bundled offline; display only | `assets/banks/institutions.json`, `feature/wallets/InstitutionPicker.kt` | Full | Free | Your cards look like your cards | High, but trademarks (see 5) | No (bank logos); mention only |
| Wallet colour picker | — | Not built (faces come from the type or the bank) | — | — | — | No |
| Adjust balance: the gap is saved as a visible adjustment, never income or spending | `feature/wallets/AdjustBalanceSheet.kt` | Full | Free | Match reality without faking history | Medium | Montage |
| Transfer, Withdrawal ("Not spending"), monthly statement per wallet, "1 balance looks off" warnings | `feature/wallets/WalletDetailScreen.kt` | Full | Free | Honest records | Medium | Later |
| Credit cards: limit, available credit, statement and due days, amount due, one-tap payment from the bell | `feature/plan/DebtsScreen.kt`, `domain/Debt.kt` | Full | Free (counts as card/loan) | Never lose track of a card cycle | High | Montage (S03) |
| Loans: interest rate, amortization suggestion with **Use**, "3 of 24 installments paid", pre-filled monthly payment schedule, principal/interest split when paid | `feature/plan/DebtsScreen.kt`, `domain/DebtProgress.kt` | Full | Free (counts as card/loan) | See the end of a loan | High | Montage (S02 Debt & Loans, S05 instalment) |

### 3.3 Multicurrency

| Feature | Where | Status | Plan | User benefit | Marketing potential | Film |
|---|---|---|---|---|---|---|
| Main currency chosen once and locked; wallets in other currencies with their own rate | `data/fx/FxRates.kt`, `domain/fx/FxLedger.kt`, `feature/fx/FxRateField.kt` | Full | Pro | Live in more than one currency | High (OFWs, travellers, expats) | Hero (S03) |
| Today's rate filled in online, editable ("Change it to the rate you actually got") | `feature/fx/FxRateField.kt` | Full | Pro | Your real rate, not a guess | Medium | Later |
| Foreign amounts stay in their own currency with "≈ ₱…" beneath; totals in the main currency by average cost; FX gains and losses | `domain/fx/FxDisplay.kt` | Full | Pro | Honest totals | Medium | Support (S03) |
| Cross-currency transfers ("They receive…") in the Transfer sheet | `feature/transactions/TransferEditorSheet.kt` | Full | Pro (needs a foreign wallet) | Move money between currencies correctly | Medium | Later |
| Exchange-rate lookups: on by default, send only two currency codes and a date; offline uses last or average rate | `feature/settings/SettingsScreen.kt` | Full | Free setting | Works offline | Low | No |
| Month-end revaluation; cross-currency rows from the composer | — | Not built | — | — | — | No |

### 3.4 Getting transactions in

| Feature | Where | Status | Plan | User benefit | Marketing potential | Film |
|---|---|---|---|---|---|---|
| **Composer, "Add transactions"**: write naturally; every transaction in the message becomes an editable row. Type, category, date and wallet are inferred; competing amounts appear as one-tap chips; one tap saves the batch atomically (one idempotency key per row) | `feature/capture/TransactionComposerScreen.kt`, `domain/capture/TransactionInterpreter.kt` | Full | Free | Log a whole day in one sentence | Very high | Hero (S04) |
| On-device reading: amounts ("1.5k", "₱250", number words), dates ("yesterday", "last friday", "October 12"), wallets ("using my cash wallet"), transfers, fees. English, rules, **not AI** | `domain/capture/QuickEntryParser.kt`, `BatchEntryParser.kt`, `NumberWords.kt` | Full | Free | Works offline, no account | High (truthfully framed) | Hero (S04) |
| **Many at once**: up to 30 rows per message; rows from several messages and scans stack into one save | `domain/capture/BatchEntryParser.kt` (`MAX_ROWS`) | Full (limit not shown in UI) | Free | No repeated forms | Very high | Hero (S04) |
| **Voice entry**: "Listening…", live words, listens through pauses (each pause becomes a row), stops after 3.5 s of quiet or on Done; offline by default, "Try online voice" opt-in; no recording kept | `feature/capture/VoiceCapture.kt`, `VoiceSession.kt`, `ListeningSheet.kt` | Full | Free | Hands busy? Just say it | Very high | Hero (S04) |
| Receipt scan: one expense for the amount due, shop name, date, currency; ML Kit bundled on the phone | `feature/capture/ReceiptOcr.kt`, `domain/capture/ReceiptParser.kt` | Full | Free | Snap the receipt, done | High | Later (own video) |
| **Statement / e-wallet history scan**: one row per line (up to 60), money in/out checked against the running balance, likely duplicates flagged | `domain/capture/StatementParser.kt` | Full | Free | A month of entries from one screenshot | Very high (overlooked) | Later (own video) |
| Smarter reading (Gemini, through Lucento's proxy): better at messy messages; every reply labelled "Read by Gemini" or "Read on this phone" | `data/ai/HybridTransactionInterpreter.kt` | Full | Pro, opt-in, signed in | Handles the messiest messages | Medium | No (keeps the AI story clean) |
| Philippine vocabulary: Jollibee, GrabFood, Meralco, PLDT, jeepney, MRT/LRT, "sweldo", "palengke", "merienda", "load" | `domain/capture/QuickEntryParser.kt` | Full | Free | It speaks your language | High (PH launch) | Montage (S04: "Grab", "load") |
| Home-screen widgets: **Quick add** (scan · mic · field) and **Safe to spend**; follow Hide amounts and App lock | `feature/widget/` | Full | Free | Add or check without opening the app | High (overlooked) | Later |
| Transactions page: search (notes, categories, wallets, amounts), filters, day groups, Income / Expenses / Net inflow cards, view, edit, delete | `feature/transactions/` | Full (no undo) | Free | Find anything | Medium | Montage (S07) |
| Transfers between your own wallets: two linked legs, never income or spending | `data/repository/TransactionRepository.kt` | Full | Free | Totals you can trust | Low | No |
| Saved confirmation ("4 transactions saved"), only after the write succeeds; haptics | `core/designsystem/component/Feedback.kt` | Full | Free | Confidence | Medium | Hero (S04) |

### 3.5 Planning

| Feature | Where | Status | Plan | User benefit | Marketing potential | Film |
|---|---|---|---|---|---|---|
| Budget = the sum of category amounts, per month and currency; status words, pace, "Spent", "unbudgeted" spending shown openly | `feature/budget/BudgetScreen.kt`, `domain/BudgetPlan.kt` | Full | Free | A budget that's always honest | High | Support (S05) |
| **Preset categories**: 82 in 10 groups, 12 recommended already ticked at first run ("We've started you with everyday essentials"), searchable, custom names, a review step | `domain/CategoryPresets.kt`, `feature/onboarding/` | Full | Free | A budget in seconds instead of an afternoon | High (brief's ask) | Support (S05) |
| Budget conveniences: "Only Nov" or "Nov onward", "Use the usual ₱X", Safe per day, a one-time "You stayed within October's budget" | `feature/budget/BudgetChange.kt`, `BudgetDaily.kt` | Full | Free | Less fiddling every month | Medium | Later |
| Category management: add, from presets, rename, reorder, deactivate (archived, history kept), restore | `feature/budget/CategoryScreens.kt` | Full (no custom icons/colours) | Free | Make it yours without losing history | Medium | Montage |
| Savings goals: 8 suggestions, automatic icons, your own photo, add money / take out, "₱X a month", "At your pace: <date>", milestones | `feature/plan/GoalsScreen.kt`, `domain/Goal.kt` | Full | Free 2 active; Pro 20 | Watch the trip come closer | High (emotional) | Support (S05) |
| Scheduled: bills, income, debt payments, collections; once/weekly/monthly/yearly; Mark paid (editable amount), Skip, Pause; never posted automatically | `feature/plan/RecurringScreen.kt`, `domain/Recurring.kt` | Full | Free | See every bill coming | High | Support (S05) |
| Reminders | `feature/home` (bell, Upcoming) | Partial: in-app only. **No notifications** (no POST_NOTIFICATIONS; Settings says "Reminder notifications aren't available yet.") | — | — | Don't imply alerts | No push imagery |
| Budget suggestions from spending; a short first run with categories applied automatically | — | Not built (reverted 2026-10-29) | — | — | — | No |

### 3.6 Financial intelligence

| Feature | Where | Status | Plan | User benefit | Marketing potential | Film |
|---|---|---|---|---|---|---|
| Insights, "Income and expenses": money in/out, "You kept ₱X · N% of what came in", monthly bars | `feature/insights/InsightsScreen.kt` | Full | Free 3 months; Pro 6 and 12 months | See your money flow | High | Support (S06) |
| "Where your money went" donut and "Net worth over time" line | `feature/insights/InsightsScreen.kt` | Full | Free | Understand the shape of your spending | High | Support (S06) |
| Reports: Wallet statement, Cash flow, Net worth, Budget vs actual, Debts & loans, Top spending categories; one month at a time (24 back); "How this was calculated" | `feature/reports/ReportViews.kt`, `domain/ask/Reports.kt` | Full | Free on screen; PDF save/share Pro | Real statements without a spreadsheet | High | Support (S06) |
| **Ask Lucento, on the phone**: "Ask me about your money"; five report questions, purchase check ("Can I afford…?" → "Yes, it fits" / "You can, but from savings" / "I'd wait"), off-topic questions refused offline | `feature/ask/AskLucentoScreen.kt`, `domain/ask/Ask.kt` | Full | Free, unlimited, offline | Answers in plain words, in seconds | Very high | Hero (S06) |
| Ask Lucento AI answers (Gemini through Lucento's server): advice chips, consent sheet, "Answered by Gemini · bold figures worked out on this phone" | `data/ai/AskLucentoClient.kt` | Full | Opt-in, signed in; Free 10 a month, Pro 50 a day | Advice that can't make up numbers | High, needs careful copy | Later (own video) |

### 3.7 Togetherness

| Feature | Where | Status | Plan | User benefit | Marketing potential | Film |
|---|---|---|---|---|---|---|
| **Lucento Shared**: one Partner on their own phone; both see every wallet, budget, goal and transaction; the Partner adds, edits and deletes transactions; the Owner manages the structure; "Added by" on the detail page; invite through the share sheet, 7-day expiry | `feature/sharedspace/SharedSpaceScreen.kt`, `domain/SharedSpace.kt` | Full | Inviting is Pro; being a Partner is free; needs an account and sync | Decide together, from the same numbers | Very high (emotional) | Hero (S07) |

### 3.8 Personalisation

| Feature | Where | Status | Plan | User benefit | Marketing potential | Film |
|---|---|---|---|---|---|---|
| Five themes, each with its own landscape: Sunrise, Midnight (free); Grove, Mono, Aurora (Pro). System / Light / Dark | `core/designsystem/theme/Themes.kt`, `feature/profile/ThemePicker.kt` | Full | Free 2, Pro 5 | It feels like yours | High | Hero (S08) |
| Profile name and photo (or the Google photo), kept on the phone | `feature/profile/` | Full | Free | Personal | Low | No |
| Enabled features: 8 modules on or off; "Turning one off never deletes anything" | `feature/settings/` | Full | Free | Only what you need | Medium | Later |
| App lock (fingerprint, face or screen lock; after 1 minute away), Hide in recent apps (also blocks screenshots) | `feature/lock/` | Full | Free | Privacy | Medium | Montage (S08) |
| Default wallet; wallets as rows or cards | `feature/settings/`, `feature/wallets/` | Full | Free | Fewer taps | Low | No |
| Awards: 7 earned from your own records, no streaks to break; a Pro medal | `domain/Achievements.kt`, `feature/awards/` | Full | Free | Encouragement without guilt | Medium (overlooked) | Later |

### 3.9 Reliability and data

| Feature | Where | Status | Plan | User benefit | Marketing potential | Film |
|---|---|---|---|---|---|---|
| Offline-first: every core feature works with no connection and no account | whole app; Room is the source of truth | Full | Free | Works anywhere | High | Support (S08) |
| Cloud sync (Google or email sign-in): syncs on open and after changes, plus about every 30 min; a newer local edit wins a conflict | `data/sync/`, `docs/SYNC.md` | Full | Pro (free for a Partner) | Same money on every phone | Medium | Later |
| Restore from your account on a new phone | `feature/onboarding/` | Full | Pro | Switch phones safely | Low | No |
| JSON backup and restore: Merge or Replace with a preview; a bad file changes nothing; not encrypted (the app warns) | `data/backup/` | Full | Free | Your data, portable | Medium | Later |
| CSV export (transactions) | `feature/settings/` | Full | Free | Take it to a spreadsheet | Low | No |
| Sign-out safety: "Sync first" / "Save a backup" / "Keep them here" or "Remove and sign out" | `feature/account/` | Full | Free | Never lose data by accident | Low | No |
| Delete account (typed confirmation) | `feature/account/` | Full | Free | Control | Low | No |

### 3.10 Onboarding and setup

| Feature | Where | Status | Plan | Benefit | Film |
|---|---|---|---|---|---|
| 8 screens: Welcome (with "I already have Lucento", "I was invited", "Restore a backup file") → Keep your money safe (No bank login · Works offline · Private by default) → What should Lucento help with? → How do you pay? → What do you spend on? (presets) → Review → Currency and look → Lucento is personalized | `feature/onboarding/OnboardingScreen.kt` | Full | Free | A tailored start | Support (presets, S05) |
| Guided setup: Add your accounts → Any cards or loans? → Set your monthly budget → Save for something → Type it like a message → You're all set | `feature/guide/GuideScreen.kt` | Full | Free | Set up with real data, guided | Later |

### 3.11 Free and Pro

The film shows Pro features (multicurrency, Shared, three themes, 6/12-month Insights). The end card says "Some
features require Lucento Pro." That matches the Play listing, which labels Multicurrency and Shared "Pro".
Prices are not in the app (Google Play supplies them) and are not confirmed live, so the film shows none.

| | Free | Pro |
|---|---|---|
| Money wallets / cards and loans | 3 / 2 | No limit |
| Money owed to you, things you own, wallets in other currencies | — | ✓ |
| Active savings goals | 2 | 20 |
| Insights periods | 3 months | 6 and 12 months |
| PDF statements | — | ✓ |
| Themes | 2 | All 5 |
| Cloud sync and restore | — | ✓ |
| AI adviser questions | 10 a month | 50 a day |
| Smarter reading | — | ✓ |
| Lucento Shared | — | 1 Partner |

## 4. Features the original brief didn't mention

These are commercially valuable and the brief didn't list them:

1. **Statement and e-wallet history scanning:** a month of entries from one screenshot, checked against the running
   balance. It's free and on the phone. This is the strongest "less manual work" story after the composer.
2. **Safe to spend and Safe per day:** bills are set aside first, so the number is safe to act on. Also a free widget.
3. **Home-screen widgets:** Quick add (scan · mic · type) and Safe to spend.
4. **Purchase check in Ask Lucento:** "Can I afford it?" answered offline from your own records.
5. **AI that can't invent numbers:** every figure in an AI answer is calculated on the phone.
6. **Philippine vocabulary:** Meralco, jeepney, sweldo, palengke and more, understood out of the box.
7. **Hide amounts with a fingerprint to reveal**, App lock, and screenshots blocked when hidden from recents.
8. **Awards with no streaks to break:** encouragement without guilt.
9. **Credit card statement cycles and loan amortization**, including a suggested monthly payment with one-tap "Use".
10. **Honest reconciliation:** Adjust balance, Withdrawal ("Not spending") and External payment keep history true.
11. **A monthly statement for every wallet**, and every report says how it was calculated.
12. **409 bank and e-wallet faces bundled offline** in 48 countries (display only; no bank connection).
13. **Free backup and restore**, even from the welcome screen; Pro limits never delete anything when Pro ends.
14. **Budget changes for one month or onward**, and "Use the usual" amounts.

## 5. Claims to keep out of all marketing

| Don't say | Why (in code) | Say instead |
|---|---|---|
| "AI reads your messages / receipts" (default experience) | The composer uses on-device rules; ML Kit only extracts text. AI is Smarter reading (Pro, opt-in) | "Type it like a message." "Read on your phone." |
| "Free AI entry" | Smarter reading is Pro | — |
| "Automatic categorisation / auto-logging / bills paid automatically" | Every row is reviewed and saved by you; schedules never post themselves | "Lucento fills in the details; you confirm." |
| Bank sync, bank connections, SMS/CSV import | None exist; bank faces are display only | "No bank login." |
| Push notifications, bill alerts, reminder pop-ups | No notification permission or code; reminders are the in-app bell and Upcoming card | "See every bill coming." Owner decision (2026-11-14): "Never miss a bill" may describe Scheduled transactions (also the app's own onboarding wording), without notification imagery |
| "Nothing ever leaves your phone" | Exchange rates are on by default (two codes and a date); Google Play billing diagnostics; purchase checks; opt-ins | "Works offline." "No bank login." Scope privacy lines to "your entries" and link the Privacy Policy |
| "Your voice never leaves the phone" | Online voice (opt-in) may send audio to Google | "Lucento never records or keeps your voice." |
| Any language | Reading is English; scanning reads Latin-alphabet text | — |
| "Unlimited items per message", undo | 30 rows per message; no undo | — |
| Customisable dashboard, custom category icons or colours, wallet colour picker, budget rollover | Not built | — |
| Live valuations of a home, car or investments; automatic rates everywhere | Values are typed in; rates are suggested and editable | "Count everything you own." |
| Sharing with a family or group | One Partner only | "Two people. One ledger." |
| Free cloud sync | Sync is Pro (a Partner syncs free on the Owner's plan) | — |
| Prices, trial | Not in the app; not confirmed in Google Play | — |
| Real bank or e-wallet logos in ads | Third-party trademarks; showing them in advertising can imply partnership | Use the demo wallets ("Everyday bank", "Cash", "Savings") |

## 6. Issues found during discovery (for the owner, outside the film)

1. **Free users can spend their Ask Lucento allowance on Smarter reading.** In the local copy of the website repo the
   interpret proxy counts a request before rejecting non-Pro users (`src/pages/api/interpret.ts` around lines 154–166
   with `consume_ai_request`). A free user who switches Smarter reading on may use up their 10 monthly questions and
   get no Gemini reading. Verify against what's deployed.
2. Home's info tooltip still says "Nothing is converted between currencies." (`feature/home/HomeScreen.kt:1409`),
   which is no longer true with multicurrency.
3. The Ask Lucento hero types "What's my top 5 spending categories?"; the chip reads "What are my top 5…".
4. The AI counter says "N left today" even though Free's allowance is per month.
5. Onboarding's "Never miss a bill" leans on in-app reminders only (no notifications). The owner accepts this wording
   (2026-11-14) and the film uses it for Scheduled transactions.
6. Onboarding's "Sync with an account (Recommended)" and the summary's "Synced with your account" don't say sync is
   Pro; on Free, sync stays off after setup.
7. The plan page says debts are free "with no limits", but Free tracks 2 cards or loans.
8. The editor allows "No category" while the composer requires one.
9. Rows beyond 30 in one message are dropped without a message.
10. README and CHANGELOG are stale: the "Short first run" was reverted on 2026-10-29; Lucento Shared is merged; Ask's AI
    answers include the last 60 days of transactions (consent v2); Insights no longer shows net worth by account type.
