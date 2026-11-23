# Lucento financial definitions

All money is `Long` minor units with an ISO 4217 code. Every figure below is computed for one currency: the **main
currency**, chosen once and locked (v13, owner 2026-11-05). Wallets in other currencies keep their own currency, and
each of their movements carries an explicit, stored value in the main currency; the totals use those values (see
"Exchange rates" below). Nothing is converted with a guessed or hidden rate. A foreign wallet with no rate yet stays in
its own currency and is listed apart (the *focus currency* rule still names which one the page shows). Implementations
are pure Kotlin in `domain/` with JVM tests.

## Exchange rates (multicurrency, v13)

Docs: docs/MULTICURRENCY.md. Code: `domain/fx/FxLedger.kt` (`FxLedger`, `FxTranslation`), `FxValue.kt` (`FxQuotes`,
`FxTransfer`), `core/money/FxRate.kt`. Tests: `FxLedgerTest`, `FxRateTest`, `MulticurrencyRepositoryTest`.

| Figure | Definition | Code |
|---|---|---|
| Rate | 1 unit of the wallet's currency = *r* units of the main currency, an exact decimal (12 significant digits, never floating point). A conversion rounds once, half-even, to the target's minor units | `FxRate` |
| A movement's value | its amount in the main currency, stored with the movement (`base_amount_minor`, `base_currency`, `fx_source`): the day's market rate (MARKET), a typed rate (ENTERED), offline the wallet's last rate (LAST_RATE) or its average (AVERAGE), or — between currencies — the other side's real amount (TRANSFER). A main-currency movement has none | `FxQuotes.quote`, `FxTransfer.legValues` |
| Opening rate | a foreign wallet's opening balance is valued at its `fx_basis_rate`, set when the wallet is created (or later, for a wallet from before v13). Without one the wallet is *unvalued* | `Wallet.fxBasisRate` |
| Carrying value (average method) | replay the wallet's movements by date, then entry: an **increase** (money in; on a card or loan, more owed) adds its own value; a **decrease** takes off its share of the carrying value (carrying × amount ÷ held). The average rate = carrying ÷ held. A movement without a value of its own (AVERAGE, or written before v13) follows the average; while nothing is held, the opening rate (or the last rate seen) | `FxLedger.build` |
| Realized FX gain / loss | on each decrease: its own value − the carrying value taken off (on a card or loan: the reverse — paying less than it was carried at is a gain). AVERAGE movements never make one. Anything still carried when the wallet reaches exactly zero is settled then (only an overdraft can leave any) | `FxLedger.build` → `FxRealized` |
| Where FX results count | **gains as income, losses as expenses** under "FX gains" / "FX losses" in Insights, trends and Ask Lucento reports, and in net worth (through carrying values). **Never** in budgets, Spent, Safe to spend, Home's "Where it went" or the Transactions page totals — they are not spending (owner, 2026-11-05) | `TranslatedLedger.withFxResults`, `BudgetPlanner.plan` skips `fxResult` |
| Totals in the main currency | net worth, assets and liabilities: valued foreign wallets at their carrying value; income, expenses, budgets and safe to spend: foreign movements at their own value | `FxTranslation.translate` (Home, Wallets, Budget, Transactions, Insights, Ask) |
| Transfer between currencies | two legs, each in its wallet's currency; the amount received is required — the two real amounts are the rate. A leg in the main currency is the other leg's value; between two foreign wallets both legs carry the source's value (day rate, else last, else average) | `TransactionRepository.saveTransfer`, `FxTransfer.legValues` |
| Editing a movement | keeps the rate it was saved at, rescaled to the new amount (a market rate moves to the new day's when that can be had); a movement following the average keeps doing so | `TransactionEditorViewModel.fxFor` |
| Month-end revaluation | not yet: `fx_revalue` is stored and synced, but nothing uses it (stage 2, NEXT_TASKS MC-2) | — |

| Figure | Definition | Code |
|---|---|---|
| Wallet balance (asset) | opening + income + transfers in + adjustments in − expenses − transfers out − adjustments out | `WalletBalanceCalculator` |
| Wallet balance (credit card, loan) | amount owed = opening + expenses + transfers out + adjustments out − income − transfers in − adjustments in | `WalletBalanceCalculator` |
| Balance flag | cash / e-wallet / savings below zero, a bank account below zero (overdrawn?), or a card / loan below zero (owed to you). Shown as a warning with a reconcile note; never changes a figure | `Wallet.balanceFlag` |
| Net worth | assets − liabilities, per currency | `NetWorthCalculator` |
| Display precision (Settings › Money › **Show cents**, tester round 2026-11-17) | on by default: amounts show their currency’s minor units. Off: every amount people read is rounded **half-up on its absolute value** to whole units (−₱12.50 → −₱13; a figure that rounds to zero shows no sign), so rounded lines may not add up by one unit. Display only: storage, parsing, sync, exports and every calculation stay exact. Home, Budget, Insights, Wallets, lists, Ask Lucento’s answers, report PDFs and the widget follow it; amount inputs and editors, the single-transaction view, the composer’s rows and totals and the parser’s questions always show cents. Currencies without a minor unit are unchanged | `MoneyFormatter.wholeUnits`, `LocalWholeUnits`, `AmountsShown`, `AmountsExact` |
| Income / Expenses | sum of INCOME / EXPENSE in the period. **Transfers and balance adjustments never count.** | `TransactionQueries.totalsByCurrency` |
| Net flow | income − expenses for the month; compared with last month when last month has records | `CashflowCalculator.netFlow` |
| Burn rate | actual: expenses this month ÷ days elapsed (incl. today), half-up. Projection (labelled): expenses × days in month ÷ days elapsed. Comparison: last month's expenses ÷ its length | `CashflowCalculator.burnRate` |
| **Monthly budget (v0.5)** | **Σ category allocations** of active expense categories in one currency for the calendar month. Never stored or edited on its own, so it cannot disagree with the categories. Archived categories, income categories, zero amounts and legacy "everything" rows (≤ v0.4) never count. Currencies are never summed together | `BudgetPlanner.plan` |
| Guided setup budget total (preview) | while amounts are typed: Σ of the entries that parse (`MoneyParser`, as saving does) and are above zero; blank or unclear entries add nothing yet. Shown under the rows as "Monthly budget — the sum of these amounts"; once saved, the budget is the allocations' sum above | `GuidedSetup.draftTotal` |
| Spent (budget) | the month's actual money out: **every** EXPENSE in the month and currency — **spent = budgeted + unbudgeted** (next row). Remaining = budget − spent (negative when over); percentage spent, health, pace and the daily allowance all use it. Home and the Budget tab both call it "Spent" (Home said "Used" until 2026-11-04) and both show the split. It always equals the Transactions page's Expenses as the page opens (this month, nothing else filtered), Insights' month and "Where it went" (`MonthSpendingReconciliationTest`, and on a device `ReconciliationTest`) | `BudgetPlan.spent / remaining` |
| Focus currency | the currency the month's figures are shown in: the main currency when a wallet or entry uses it, else the first wallet's. Home's budget card, the Budget page (by default) and the Transactions totals (listed first) all use it, so they describe the same money; other currencies are never converted (2026-11-04: the Budget page used the main currency even when nothing was held in it) | `FocusCurrency.of` |
| Budgeted / unbudgeted | budgeted = EXPENSE in an allocated category (the Budgeted section's "x of total"); unbudgeted = every other EXPENSE (uncategorised, unallocated or archived categories). Unbudgeted is counted in Spent, never reassigned; the Budget hero always reads "Spent = budgeted + unbudgeted" | `BudgetPlan.budgetedSpending / unbudgetedSpending` |
| Budget health | NOT_SET when the budget is 0; EXCEEDED when spent ≥ budget (exhausted or over); LOW when remaining × 100 ≤ budget × 10; APPROACHING when spent × 100 ≥ budget × 80; else ON_TRACK. Exact integers. The Home hero (Sun happy vs. supportively concerned = LOW/EXCEEDED), burn bar and Budget tab all use it | `BudgetPlanner.health` |
| Burn-rate bar | consumed = spent (all expenses) ÷ budget (display fraction); elapsed = day of month ÷ days in month; level NORMAL / APPROACHING (APPROACHING or LOW) / EXHAUSTED; no percentage without a budget. Daily allowance = remaining ÷ days left incl. today, rounded down, only when positive | `BudgetPlanner.burn` |
| Budget pace | used % (as burn-rate bar) vs elapsed % (day of month ÷ days in month, rounded down; 100 % for a past month). AHEAD when used > elapsed + 10, BEHIND when used < elapsed − 10, else ALIGNED; none without a budget. Home's pace card and its wording read it | `BudgetBurn.pace` |
| Featured savings goal | the unreached goal with the highest percent (ties: earlier target date, then older); when all are reached, the newest goal | `GoalsSummary.overview().top` |
| Manually valued assets | `OTHER_ASSET` wallets ("Property & valuables" since the tester round, 2026-11-17): balance = the valuation the user enters (no movements; repositories refuse transactions, transfers and schedules on them). Counted once as an asset in net worth | `WalletType.isValuedManually` |
| Investments (tester round, 2026-11-17) | `INVESTMENT` wallets: an asset money moves into and out of — contributions and withdrawals are transfers — whose value also changes on its own. **Update value** (the wallet page's adjust card) records the change as a balance adjustment, so a gain is never income and a loss never spending. Counted once as an asset in net worth; not spending money unless the person says so; counts towards the Free plan's money wallets like savings | `WalletType.INVESTMENT` |
| Savings level | Σ min(max(saved, 0), target) ÷ Σ targets for goals in one currency; closest = unreached goal with the highest percent. A view only: never added to net worth or budgets | `GoalsSummary.overview` |
| Net worth composition | assets and liabilities grouped by wallet type, per currency; totals equal `NetWorthCalculator` | `NetWorthComposer.compose` |
| Income/expense trend | INCOME and EXPENSE per month (zero-filled); transfers never appear | `Insights.incomeExpenseTrend` |
| Upcoming payments | next unpaid occurrence of each active schedule due within 30 days (overdue flagged), date order, max 4. Listing never records anything | `RecurringMath.upcomingPayments` |
| Safe to spend (tester round, 2026-11-17) | with a budget, the **lower** of two figures: what the budget allows (next row) and what the spending money allows (**money on hand**, below; the app calls it "spending money"). `limitedByMoney` says when the money set it; both stay on the result (`budgetAllows`, `moneyOnHand`) for the "How Safe to spend works" sheet (opened from the Safe to spend panel, spoken "How Safe to spend is worked out", and from Budget remaining). The spending money takes part only once money has been **recorded** in a spending wallet (`MoneyOnHand.recorded`: a starting balance, income, a transfer in or a balance adjustment, or a card whose credit is counted) — a ledger of expenses alone has balances that only ever go down, so it follows the budget, and the sheet says why (QA review, 2026-11-17). Without a budget, the money on hand alone (an estimate, labelled). Safe per day = this figure ÷ days left | `CashflowCalculator.safeToSpend` |
| Money on hand | **Sources**: every wallet in the currency that is spending money (`Wallet.isSpendingMoney`: the person's choice, else the type's default — cash, bank and e-wallet yes; savings, investments and cards no; never loans, receivables or valued assets) adds its balance (below zero when overdrawn); a card the person chose adds the credit they named **less what this month has put on it and not paid back** — its charges and money moved off it this month, less whatever its payments and refunds this month leave over once they have settled what was owed when the month opened (a statement already set aside, or last month's balance) — capped at what is still available (limit − owed, never below zero); below zero once more went on the card than was counted; nothing without a limit. When this month's statement is due by month end and paid from spending money (the account that paid the card last, or a schedule to it), the month opens the day after the statement instead: the charges before it are that payment's, set aside below, so they never count twice; a card paid from savings keeps them. Spending on the card uses the counted credit up; paying the card, a bill from it marked paid, or a refund never moves the figure the wrong way (`MoneyOnHandCalculator.creditUsed`; QA review 2026-11-18). **Deductions**, unpaid and due by month end (overdue included), from those wallets only: bills (one line), loan and card payments planned as schedules (principal **and** interest — the cash leaves either way — but never more than the debt still owes, and the line then says so), a card's payment due from its billing days when the account that usually pays it is spending money (skipped when a scheduled payment or transfer to that card is due this month), and scheduled transfers into a wallet that isn't spending money (a transfer between two spending wallets stays on hand). A schedule paid from a foreign wallet that has a rate is valued at that wallet's average rate, as its balance is (`FxTranslation.valueSchedules`; Home, the Budget tab and Ask Lucento alike). Expected income is never added. Allows = sources − deductions; may be negative | `MoneyOnHandCalculator.compute`, `MoneyOnHand` |
| Safe to spend — with category allocations (what the budget allows) | (a bill paid from a foreign wallet with a rate counts in bills due at that wallet's rate, as its spending counts in Spent: `FxTranslation.valueSchedules`, tester round 2026-11-17) budget − spent (**every** expense this month in the currency — budgeted + unbudgeted, as "Spent (budget)" above; corrected 2026-11-03, the code always did this) − **bills due**: unpaid scheduled expenses due by month end (overdue included), **whatever their category** — budgeted, unbudgeted or none — because once paid each one is spending and lands in Spent (owner, 2026-11-03; until then only budgeted categories counted, which left Bills due at zero for most people). A debt payment counts only its interest (the rest is a transfer); credit card payments never count. Current month only; the Recurring module switched off means no bills are subtracted. Home's Monthly budget card shows it (only this basis) | `CashflowCalculator.safeToSpend` |
| Home budget card and the Budget tab (owner's redesign, 2026-11-18) | Three parts, read from one `SafeToSpend` (`SpendingCapacity.of`); nothing is worked out again, so the parts always add up to their figures. **A** (Home): a half gauge of the share of the budget spent, the amount spent "spent of" the budget, "N% spent", and "Budgeted ₱X • Unbudgeted ₱Y" (Spent = budgeted + unbudgeted). **B**, a subtle grid of two cells side by side (stacked in a panel narrower than 280dp or from 130 % text), set out alike so their lines balance (owner, 2026-11-18): the name and the figure centred (one figure size for both), then under a rule two parts as a small ledger, the description on the left and the amount aligned on the right (one size for all four, smaller when the widest needs it; below 85 % each description goes over its amount). **Budget remaining** = budget − spent − upcoming expenses (`SafeToSpend.budgetAllows`): "Unspent ₱X" (budget − spent, with its minus sign once over) and "Upcoming −₱Y" (upcoming expenses: unpaid bills due by month end, as above). **Spendable wallets** = what the spending money allows (`MoneyOnHand.allows`): "In wallets ₱X" (the sources) and "Due −₱Y" (everything due from them: bills, loan and card payments, transfers out of spending money). It shows "—" with "No spending wallets" when none counts, or "Not counted yet" until money is recorded in one (QA, 2026-11-18). **C**, the verdict: **Safe to spend** = the lower of the two (`SafeToSpend.amount`), never shown below 0: green with a check above zero, red with an alert at 0, and one sentence on which limit set it, "Limited by your remaining budget.", "Limited by your available spending wallets." or "Both your budget and wallet availability are limiting your spending.", or at 0 what took it ("You’re over budget this month.", "This month’s budget is used up." when nothing was left before the bills either, "Bills due take what’s left of your budget.", "What’s due takes everything in your spending wallets.", "Your spending wallets show no money."). A second line appears only when the wallets don't take part, saying why only the budget is used. Budget remaining and the verdict open "How Safe to spend works" (sections "From your budget" and "From your spending wallets", each ending on the card's figure); Spendable wallets opens the choice of wallets (the explanation on a Partner's phone). **Bills come off both limits on purpose** (owner decision 2026-11-18, OT-40): each limit has to cover them on its own, and the verdict takes the lower, never the sum, so nothing is taken twice. Leaving them out of the wallet figure (the brief subtracted only transfers that aren't expenses) would promise money the bills are about to take: ₱20,000 in the spending wallets, ₱6,000 of bills and ₱4,000 to savings due leave ₱10,000, which the wallets show; without the bills they would read ₱16,000, and with ₱15,000 of budget left Safe to spend would say ₱15,000. The Budget tab shows B and C under its hero this month, from the same calculation and inputs (`budgetSafeToSpend`), with Safe per day under the verdict; its hero reads "Unspent of your ₱X budget" and ends on "Spent ₱X = ₱Y budgeted + ₱Z unbudgeted". A past month keeps the Budget hero with Spent and the daily average, and Home's card closes on Remaining / Overspent. The home-screen widget gives short forms of the verdict's sentences (`BudgetFormula.caption`: "Limited by your spending wallets", "What’s due takes your spending wallets", "Your spending wallets show no money", "You’re over budget this month", "This month’s budget is used up", "Bills due take what’s left") | `SpendingCapacity.of`, `SpendingCapacityPanel`, `budgetSafeToSpend`; widget: `BudgetFormula.caption` |
| Safe to spend — without a budget (estimate, labelled) | the money on hand (above): spending wallets' balances and chosen card credit − bills, loan and card payments and scheduled transfers out due by month end. By default that is cash + bank + e-wallet − what is paid from them, as before the tester round | same |
| Safe per day | safe to spend ÷ days left incl. today, rounded down; shown only when positive. On the Budget tab it is the line under Safe to spend ("₱X a day for N days"; owner's redesign 2026-11-18, the hero's Safe per day tile until then, decision D1, 2026-11-03), so it always equals Home's safe to spend ÷ days left; with nothing left it isn't shown (the verdict reads 0); a past month's hero shows its daily average instead. Both read the translated ledger, so spending in other currencies counts at its own value (`budgetDailyFigure` takes the `TranslatedLedger`; until 2026-11-16 the Budget tab passed the untranslated lists and left foreign-wallet spending out) | same; `budgetSafeToSpend`, `dailyFigure` |
| Net worth trend (Wallets) | net worth at the end of each of the last 6 months (`BalanceHistory.lastMonths`; the last point is today's net worth); the change pill compares last month with this one only, and only when both measure the same wallets and last month was above zero (`comparableTrendPercent`) | `WalletsOverview.trend` |
| Assets / liabilities split (Wallets) | assets ÷ (assets + owed) in whole percent, half-up; liabilities take the rest (always 100 together); no bar when either side is zero or below | `WalletsOverview.assetShare` |
| Category spent | EXPENSE in the calendar month, budget currency, matching category. No rollover; refunds recorded as income are not netted | `BudgetCalculator` |
| Budget status | over when spent > limit; near when spent × 100 ≥ limit × 80 (exact, no rounding) | `BudgetProgress.status` |
| Goal saved | sum of contributions (earmarks; wallets unchanged, so savings are never double-counted) | `GoalCalculator` |
| Goal projection (labelled estimate) | remaining ÷ average daily pace since first contribution (min. 30 days) | `GoalCalculator.projectCompletion` |
| Required per month | remaining ÷ started months until target date, rounded up | `GoalCalculator.requiredPerMonth` |
| Recurring occurrence *k* | start + k × interval × frequency (month-end anchors kept: Jan 31 → Feb 28 → Mar 31) | `RecurringMath` |
| Recurring idempotency key | `recurring-<schedule id>-on-<due epoch day>` (v0.4). Keyed by due date, not index, so editing a schedule's timing (which restarts the index) never makes a new payment look like a duplicate of an old one. v0.3 keys (`recurring-<id>-<index>`) cannot collide | `RecurringSchedule.occurrenceKey` |
| Goal milestone | halfway / reached, when saved × 100 crosses target × 50 / × 100 because of a contribution (never a withdrawal) | `GoalMilestones` |
| Archived goal | only a reached goal (saved ≥ target) can be archived; it keeps its contributions but is left out of every goal total (Goals hero, Home's savings card, featured goal) and takes no money in or out until restored. It still counts for the Goal Setter milestone | `GoalRepository.archive` / `restore` |
| Budget month kept | the previous calendar month: it had a budget from its 1st (an amount in the main currency set on or before that day; a row without a creation date can't show it), its plan (every amount it has, as on the Budget tab, one set while planning it in the check-in's first days included) is positive, and that month's Spent (every expense, budgeted or not, as on the Budget tab; other currencies at their own value from the translated ledger) is > 0 and ≤ that budget. Acknowledged once (persisted). Until 2026-11-16 it read the untranslated transactions, so foreign-wallet spending could not stop it; until 2026-11-23 only allocations created on or before the 1st counted, so an amount set in the check-in's first days was left out of the budget while its spending counted | `BudgetMilestones` |

## Budget model (v0.5)
- The overall budget is derived — the sum of category amounts — for one currency and one calendar month. There is no
  separately editable overall amount, and nothing is double-counted: each expense belongs to at most one category.
- Pre-0.5 "budget for everything" rows (category NULL) are kept in the database but ignored by every calculation. The
  Budget tab explains this once and offers "Remove the old limit" (`BudgetRepository.removeLegacyOverall`).
- Savings-goal contributions are earmarks, not expenses; transfers and debt principal repayments are transfers. None
  of them count as budget spending unless the user records an actual EXPENSE.
- Categories: a fresh install seeds only income categories. The short first run then applies the recommended
  expense presets (`FirstRunDefaults.categoryPresetKeys`) without asking. The same picker stays in Settings and on
  the Budget screen. Applying a selection (`CategorySelection`)
  reuses categories by normalised name (case, spacing, "&" = "and"), restores archived matches with their ids,
  creates the rest, and deactivates unchosen ones (archived when used, deleted only when unused). No schema change
  was needed for this step.

### Budget when categories are combined (P1-8)
Sync folds live categories of the same kind and name into one keeper (the copy with the smallest sync id,
`CategoryDedup.plan`). When both copies had a budget in the same currency, the keeper ends with
(`CategoryDedup.mergedLimit`, owner decision **keep the latest edit**):
1. the set amount, if only one of them is set (an unset 0 never replaces a set amount);
2. otherwise the amount whose budget row was edited most recently (`updated_at`);
3. on an exact tie, the amount of the row with the smaller sync id.
The same rows give the same result on every device, in either order. The other amount is not kept; the Settings
combine dialog says "the most recently changed amount is kept". Only rows of the same scope are combined (see "Budget
lifecycle"): a month-only amount and a recurring amount are different budgets.

### Two budgets for one category (sync QA C-3)
Two phones that each give the same category an amount in the same currency create two budget rows. Without a rule each
phone showed the row it stored first, so two devices could show different budgets. Sync now folds them
(`SyncRepository.mergeDuplicateBudgets`): every device keeps the row with the smallest sync id, with the amount chosen by
the same rule as above (`CategoryDedup.winner`: a set amount beats 0, then the latest edit, then the smaller sync id,
also for three or more rows, in any order), and deletes the others; the deletions sync. Rows are folded only within one
scope (category, currency, `month`, `effective_from`): two phones changing November alone end with one November amount,
and the usual amount is untouched. Budgets in other currencies are never touched. When the amount this phone showed changes, the "changed on another device" notice counts it (P2-6).

### Budget lifecycle (schema v10)
A normal change to a category's amount affects **only the month being viewed**. Changing future months is a separate,
explicit choice, and earlier months always keep the amount that applied to them, so history is never rewritten.

Each budget row has one scope (`BudgetEntity.month` / `effective_from`, "yyyy-MM"; at most one is set):
- **Month-only** (`month` set): the amount for exactly that month. 0 means "no amount this month".
- **Recurring** (`effective_from` set): the amount from that month onward. 0 means "stopped from that month".
- **Every month** (both null): every row written before v10. It behaves as it always did.

The amount for category C in currency X in month M (`BudgetPlanner.effectiveAllocation`):
1. the month-only row for M, if there is one;
2. otherwise the recurring/every-month row with the latest start no later than M (every-month counts as the earliest);
3. otherwise nothing.
Within one tier, the first stored row with an amount wins, else the first stored row (the pre-v10 duplicate rule). A
category whose resolved amount is 0 is not budgeted that month. The overall budget is still the sum (`BudgetPlanner.plan`).

What each action writes (`BudgetRepository`):
| Action (Budget sheet) | Write |
| --- | --- |
| "Only <month>": save | `setMonthAmount`: upsert the month-only row |
| "Only <month>": remove | `clearMonth`: a 0 month-only row (deleted instead when no recurring amount applies) |
| "Use the usual amount" | `setMonthAmount(…, null)`: delete the month-only row |
| "<month> onward": save or remove (confirmed) | `setFrom`: delete M's month-only row, delete recurring rows starting after M, upsert the recurring row from M (a 0 row when removing while an earlier amount exists, else delete) |
| First amount (onboarding, guide, a category with no amount) | `setAllocation` = recurring from the current month |

Months changed on their own after M keep their amounts when "onward" is used, and the confirmation says so. Past months
can only be corrected for themselves. The Budget tab reaches up to 12 months ahead (`MONTHS_AHEAD`, owner 2026-11-23): a
month that hasn't started shows its planned total (the same sum), how it compares with the month before, and each
category's amount with where it comes from ("Only December · usually ₱2,000", or "Every month"). Nothing is spent in it
yet, so it shows no pace, no Safe to spend and no per-day figure. Spending is counted by each transaction's calendar date, so a month starts on the
1st in the user's local calendar. Budget periods other than calendar months (payday, weekly, biweekly) are planned for
Pro after launch and not built; any implementation must follow the rules in `docs/handover/NEXT_TASKS.md` › "Custom
budget periods" (new tables only, never new scope columns on `budgets`).

### Home: total balance and "Where it went" (redesign Part E)
- **Total balance** is net worth in the dashboard currency (assets minus what is owed, `NetWorthComposition.netWorth`);
  other currencies are listed separately and never converted.
- **Where it went** (`spendingBreakdown`): the shown month's EXPENSE rows in the dashboard currency, grouped by
  category, largest first; the top four are listed and the rest summed as "Other". Transfers, adjustments, goal
  contributions and income never count. Shares are of that month's total spending in that currency.
- **Vs last month** (wallet card, `BalanceHistory`): net worth at the end of the previous month, rebuilt from
  opening balances and transactions dated up to then (wallets created later are left out), against the shown month.
  The percentage appears only when the previous month's balance is above zero and no wallet in that currency was added
  in between (`comparableTrendPercent`): a new wallet's opening balance is not growth. The spoken comparison states the
  signed amount difference.

### Milestones / awards (redesign Part D)
Worked out from stored records (`Achievements.earned`), never from counters: Money Keeper (an asset wallet), Budget
Builder (a category with an amount this month, lifecycle rules), First Step (an expense or income recorded), Getting
Consistent (expenses or income on 7 different days, any order — no streak), Financial Explorer (Insights opened; a
local flag), Goal Setter (a savings goal, archived ones included), Secured (app lock turned on at some point; a local
flag). The progress awards (owner, 2026-11-23) recognise results rather than setup:
- **Steady Month**: a whole finished month, at most 24 back, that stayed within its budget by the budget moment's rules
  (`BudgetMilestones.keptWithin`: a budget from the month's 1st, real spending, Spent at or under the total, in the main
  currency on the translated ledger). So the recap's verdict and the award agree: a month whose budget began later says
  so under its verdict ("Your budget began on November 10"), and until the first whole month
  (`BudgetMilestones.firstWholeMonth`: the month after the first amount, or its own month when set on a 1st) has ended,
  the cabinet names it under the award ("Your first whole month is December.").
- **Goal Reached**: a goal (archived ones too) whose contributions, added up in date order, reached its target at some
  point (`Achievements.everReached`); taking money back later doesn't undo it.
- **Loan Cleared**: a loan (`WalletType.LOAN`, not a card: paying a card in full is routine) owing nothing, with at
  least one payment (a transfer in) recorded on it.

Each is acknowledged once, after the write that earned it; the first run with milestones records what is already earned
without showing anything. Awards an update adds are recorded the same quiet way (`Achievements.newToThisPhone`, from the
`achievements_known` record; a phone without one knew the first seven), so an update never brings ceremonies for the
past. The cabinet shows what the records earn plus every award already presented (`shownEarned`): none is taken away. Progress (`Achievements.progress`) comes from the
same facts, so it never disagrees with what is earned: `current` of `target`, capped at the target; only Getting
Consistent has a target above 1 and shows a count ("3 of 7 days"). Awards earned together (`toAcknowledgeAll`: a
restored backup, a first sync, one write that earns two) are acknowledged together and presented in one ceremony.

### Monthly check-in (owner, 2026-11-23)
A card on Home asks to plan a month: budgets differ from month to month (a birthday, an event, a yearly bill). Pure rules
in `MonthCheckIn`:
- **When**: from the last 3 days of a month (planning next month) to the 7th of the next (planning that month, and
  offering last month's recap). Outside those days there is no card. It shows only while Lucento is open: it is never a
  notification, and it never opens over the composer or a widget.
- **Plan part**: Budgets on, the phone may change the budget (not a Partner's), and the budget is settled: it has an
  amount this month and its first amount is at least 14 days old (`budgetSettled`; rows without a creation date count
  as settled). It goes once the month is planned (the plan page's first saved change, or "Done planning") or dismissed.
  Only the month the card asks about next, or an earlier one, is recorded as planned (`countsAsPlanned`): planning March
  from November keeps March's amounts but leaves the December, January and February check-ins to come.
- **Recap part**: the 1st to the 7th, when last month has income or expenses recorded; it goes once the recap is opened
  (from the card or on the Milestones page) or dismissed.
- **Markers**: `budget_month_planned` and `recap_month_seen` ("yyyy-MM", the latest month done or dismissed) follow the
  account like the budget moment (docs/SYNC.md "Settings": ledger settings, never moving back to an earlier month). They
  aren't in the JSON backup (docs/BACKUP.md), like the one-time notices.
- **The plan page** (`MonthPlanScreen`) is the Budget tab's own plan for that month: every change starts as that month's
  only (`BudgetChangeContext.planning`; "onward" is one tap away), and "Add something" sets a month-only amount on any
  category or a new one. From the 1st, the card says how the recap month's budget ended: "₱18,200 of ₱20,000 spent",
  or "₱1,200 over budget" (in a month's last days this month's figures are on the budget card below it).
- **Hints** (`MonthPlanHints`, facts only, at most 4, at most 2 of each kind, in this order): scheduled expenses that don't
  come every month (yearly, every few months; not weekly, monthly, linked or paused ones) with an occurrence in the
  month; the same month last year where a category's spending was at least 10 % above its usual amount for this month
  (an amount set for the month alone doesn't count), or it had none; last month's budgeted categories that went over (so
  far, in a month's last days). A hint opens its category's amount with a suggestion, said where it comes from
  (`hintSuggestion`): a bill due adds its amount to the category's usual monthly amount (`PlannedSource.usual`, never an
  amount already raised for the month); last year's month proposes what it cost then; last month's overspending proposes
  what was spent in that category (owner, 2026-11-23: what was spent, labelled, rather than the usual amount). Nothing
  is saved until the person saves it, and each save says what it did and the month's new total ("Restaurants: $235.00
  for December only. December’s budget: $2,775.00."). Once the month has its own amount for a
  hint's category, the hint says so ("$235.00 set for December", with "Change") and opens at that amount, with no
  suggestion: a hint never adds to what the person has already set.
- **Add something** on a category that already has an amount asks for the extra ("Extra for December") and saves the
  amount plus the extra for that month only ("Personal care becomes $260.00 for December ($60.00 planned + $200.00)").
- **The page's order**: the intro and "each change is saved as you make it" first; the hints; Add something; the
  budgeted categories with this month's own amounts first, folded after five as on the Budget tab (a usual amount has no
  subtitle: only what differs gets one); categories without an amount; "Done planning".
- **Reaching the plan page**: from the card; from the Budget tab on any month ahead ("Plan <month>"); and on the Budget
  tab's running month from the 1st to the 7th, so the hints never depend on the card.
- **Closing the card** ("Not this month") hides both parts and offers Undo, which puts the two months back as they were
  (the one time they move back). The message names what stops: "We won’t ask about December again", or "November’s
  recap hidden" (the recap stays on the Milestones page).

### Milestones recap (owner, 2026-11-23)
The Milestones page ("Progress" until the same day; from Home's Quick links) opens with an Awards button (the award
cabinet has its own page) and tells a month's story and the calendar year's budget, from the same calculators as the rest of the app (`MonthRecaps`), in the main currency on the translated ledger (FX results left
out, as on Home and Budget):
- **Kept** = income − expenses (`CashflowCalculator.netFlow`), signed. Transfers, adjustments and goal contributions
  never count.
- **Budget**: the Budget tab's plan for the month (`BudgetPlanner.plan`): spent of the total, categories within, and the
  ones that went over, by how much. Null when Budgets is off or the month had no budget. When the first amount in the
  currency was set after the month's 1st (`RecapBudget.since`; unknown when an amount has no date), the line says when
  the budget began, since such a month can't be a Steady Month.
- **Where it went**: the 3 largest expense categories, with their share of the month's expenses.
- **Biggest change**: the category whose spending moved most since the month before (by size, either way; ties to the
  larger amount, then category id), only when the month before has records. A month still running is compared with the
  same days of the month before ("at this point in October", up to its last day if shorter), never with all of it.
- **Net worth**: at the month's end (today's for the current month), and its change since the month before ended
  (`BalanceHistory`), when wallets existed then.
- **Goals**: contributions less withdrawals in the month for goals in this currency (archived included), and goals whose
  savings crossed the target in it. **Debts**: payments (transfers in) to cards and loans in the month. Both only when
  their module is on.
- **Days recorded**: days with income or expenses (any currency) of the month's days so far.
- **The budget year** (`MonthRecaps.calendarYear`): January to December of one year (this one first; earlier years
  with its switcher, back to the year of the first records), one square per month (owner: "square months 3 x 4 or
  4 x 3", flat colours). A month with records and a budget compares the Budget tab's plan total with what was spent
  (`MonthBudgetResult`, so far for the running month): the share used as the Budget tab rounds it
  (`Insights.percentOf`, half-up) and its level (`BudgetPlanner.health`): on track below 80 %, getting close from 80 %
  or with 10 % or less left, over from 100 % (said "fully used" when spent to the cent, as on the Budget tab). A month
  recorded without a budget is grey, one with nothing recorded an outline with a dash (never 0 % used), one still to come
  an empty outline, and the squares show only with Budgets on. Each square says its month and share in words and figures, so colour is never the only cue; only
  months with records open their recap.
- **The year's totals** (up to today's month): finished budgeted months within budget (the Budget tab's verdict:
  spent ≤ total), what was kept, income and expenses, the months where more came in than went out, and net worth's
  change from the end of the year before to the end of its last month so far. A year still running says "so far"; when
  records start after January it is told from the first month with records ("kept since September"). The budget year
  is Lucento Pro, gated like Insights' 12 months (`ProGates.insightsPeriodAllowed(12)`, docs/MONETIZATION.md §3.4),
  previewed on Free as the person's own months in grey with every result hidden; each month's recap is free.
- Last month kept within budget is celebrated once, on the Milestones page or the Budget tab, whichever comes first (the
  same `acknowledged_budget_month`).

### Spendable now (Home, P2-1)
The summed balances of cash, bank and e-wallet accounts in the dashboard currency (`NetWorthComposition.available`).
Savings, other assets and debts are excluded; other currencies are never converted. It is part of the same balances as
net worth and is shown beside it only when the two differ (some savings, other asset or debt exists).

## Debt rules
- Credit-card purchase: EXPENSE on the card (raises owed). Paying the card: TRANSFER from bank to card (lowers owed;
  not spending, so the purchase is not counted twice).
- Loan proceeds: TRANSFER out of the loan into a bank account (raises owed; not income).
- Principal repayment: TRANSFER into the loan. Interest and fees: EXPENSE on the loan.
- Lucento does not split a single payment into principal and interest automatically: the user enters the split.

## Debt terms (v8)
Optional per card or loan (`debt_terms`, local only): a yearly interest rate (basis points, parsed exactly from
"12.5" / "7,25 %", 0–100 %, at most two decimals), repayment ONE_TIME or MONTHLY, and for monthly the term in months
and the monthly amortization. The rate and amortization are shown on the wallet card; nothing is fetched.
- Suggested amortization (a hint the user can apply): standard annuity `P·r·(1+r)^n / ((1+r)^n − 1)` with monthly rate
  `r = yearly ÷ 12`, in `BigDecimal`, rounded half-up to the minor unit; without interest `P ÷ n` rounded **up** so the
  instalments never fall short (`DebtMath.monthlyInstallment`).
- Monthly interest estimate: owed × yearly ÷ 12, half-up (`DebtMath.monthlyInterest`). Used only by Ask Lucento
  ("<debt> interest a month on what's owed now, at that rate (estimate)", `domain/ask/Ask.kt`); it is not shown beside
  a payment and never applied. The interest in a payment always comes from the statement, entered when it is marked
  paid (see "Debt payments (recurring)" below). Corrected 2026-11-08: this line used to say the estimate appears beside a payment.
- Creating a card or loan creates (or restores) the expense category "Debt interest" (`CategoryRepository.ensure`).
- A payment reminder date opens a prefilled recurring debt payment: "Monthly Payment - <debt>" for the amortization,
  first due on that date and ending after the term; "One-time Payment - <debt>" for what is owed, one date only.

- v11: for a **credit card** the editor no longer offers one-time / monthly or a reminder date; it asks for the credit
  limit and billing days instead (next section). A card that already had a monthly plan keeps it. Loans are unchanged.
- The yearly rate is appended to the wallet card's title: "Car loan (24% / yr)".

## Credit cards (v11)
Optional per card (`debt_terms.credit_limit_minor`, `statement_day`, `due_day`; synced on the wallet row).
- **Billing days** are days of the month, 1–31, entered together; a shorter month uses its last day (31 → Feb 28/29).
  The current statement closed on the latest statement day on or before today; its payment is due on the first due
  day after that (`CreditCardMath.lastStatementDate / dueDateAfter`).
- **Statement balance** = owed now − charges dated after the statement + payments and credits dated after it (what was
  owed at the end of the statement day). **Amount due** = statement balance − payments and credits since = owed now −
  charges since, never below zero. Charges after the statement belong to the next one. Only the card's own recorded
  transactions count: Lucento has no bank access (`CreditCardMath.cycle`).
- **Available credit** = credit limit − owed now; negative when over the limit (`CreditCardMath.available`).
- **Payment reminder**: with billing days set, the due date becomes a reminder on Home (Upcoming card and the bell)
  showing the amount due, while there is something to pay. It is derived every time, never stored as a recurring
  payment, and **never counted as spending** (budget, Bills due): the card's purchases were already counted as spending,
  and paying the card is a transfer (Debt rules above). Since the tester round (2026-11-17) Safe to spend's money on
  hand sets it aside from the paying account when that account is spending money (above). A tap (the bell, Upcoming, or Payment on the card) opens that transfer filled
  in: the amount due, today, the note "<card> payment", to the card, from the **paying account** — the account that paid
  the card last (the source of the latest transfer into it), otherwise the cash, bank or e-wallet account in the card's
  currency with the most money (savings only when there is no everyday account), never another card or loan or another
  currency (`CreditCardMath.dues` / `payingWallet`). Every field stays editable; nothing is saved until the user saves.
- The wallet card shows Credit limit, Available and Amount due ("by Nov 5", or "Overdue · Nov 5").
- **Amount due at setup** (tester round 3): a new card with billing days may say how much of its opening balance the
  latest statement asks for ("Amount due now", 0 ≤ due ≤ owed; empty = all of it). The card opens at that amount and the
  rest is written in the same database transaction as **one** ADJUSTMENT_OUT dated the setup day, noted "Not yet due at
  setup". The card still owes the full balance; the cycle above treats the setup-day row as a charge after the
  statement, so the first reminder asks only for the amount due and the rest joins the next statement by itself. It is
  never spending. No field is stored for it (`CardSetup.split`, `WalletRepository.create(unbilledAtSetup)`).

## Receivables (owner round 10)
A **Receivable** wallet (`WalletType.RECEIVABLE`) is money someone owes the user — a loan in reverse.
- It is an **asset**: its balance (what is still owed to the user) counts towards net worth and Assets, never towards
  Safe to spend or spendable money (not spendable), and it is never offered as the account a card or loan is paid from
  (`CreditCardMath.payingWallet`).
- It carries a loan's optional terms (`DebtTerms`: yearly rate, one-time or monthly with a term and instalment), stored
  and synced in the same columns as a loan's; the editor suggests the instalment with `DebtMath.monthlyInstallment`.
- **Collections** are recurring schedules of type INCOME whose `to_wallet_id` is the receivable and whose wallet is the
  money account it is paid into. Marking one received writes, in one database transaction keyed by the occurrence: any
  interest as INCOME on the receivable (category "Interest received"; it is earned and raises what is owed), then a
  TRANSFER of principal + interest from the receivable into the account. Net: the receivable falls by the principal,
  the account rises by the total, income rises by the interest only. Principal is never income; on the Scheduled page
  a collection is a transfer, so it never changes net flow, Bills due or Safe to spend.
- **Record payment** on the receivable's page opens a transfer from it into the account the last instalment went to
  (`CreditCardMath.receivingWallet`), otherwise the spendable account in its currency with the most money.
- Progress (`LoanMath.progress`) reads as a loan's from the other side: Principal (owed to the user at start), Received
  (principal − remaining, never below zero), Remaining, and instalments received = transfers out of the receivable.

## Instalment loan progress (v11 display)
For a loan repaid monthly with a term (`LoanMath.progress`):
- **Principal** = owed at start (the opening balance); **Remaining** = owed now; **Paid** = principal − remaining, never
  below zero — the same "principal repaid" as the loan history's note, so the three always add up.
- **Instalments paid** = recorded repayments (transfers into the loan), at most the term; a loan owed down to zero
  counts as fully paid. Interest and fees (expenses on the loan) and balance adjustments are not instalments.
- The card draws one segment per instalment, paid ones green, and says "3 of 24 installments paid" in words.

## Scheduled outlook (Scheduled page, Home's Upcoming and the bell)
`ScheduleOutlook` (pure, unit-tested). Nothing here is recorded: only "Mark paid" writes a transaction.
- **Flow** of a scheduled item: **income**; **expense** (a bill: spending); **transfer** — a debt payment (to a card or
  loan), a card's payment due (`CreditCardMath`), a collection from a receivable (owner round 10), or a **scheduled
  transfer** between two of the person's money accounts (tester round, 2026-11-17: type TRANSFER_OUT, `to_wallet_id`
  the destination, no category; e.g. "₱1,000 into MP2 every month"). Marking one done records one transfer, keyed by the
  occurrence; it never changes net flow, Bills due or the budget, and Safe to spend's money on hand sets it aside when
  it leaves spending money. Amounts are signed
  from the user's side (`signedOccurrence`): income and collections +, expenses, debt payments and card dues −. Ink:
  income green, expenses red, transfers neutral (owner, 2026-11-03); the sign always carries the meaning too. The bell's
  "outgoing this week" adds only the − entries, so a collection never lowers it.
- **Occurrences** in a range `from..to`: every unpaid occurrence of each active schedule dated in the range (a weekly
  plan appears every week, not only once); paused and finished schedules have none. When the range starts on or before
  today, every **overdue** occurrence is included and flagged — it is still owed. Card payments: each card's *current*
  statement only (later statements aren't known yet). Ranges: This month (default: overdue, then today to the month's
  end), Next 30 days, Next 3 months, or a custom From–To of at most 366 days.
- **Totals** per flow are in one currency (the main one), never converted; other currencies are listed, not added.
  **Net flow** = income − expenses. **Transfers** (card and loan payments from the user's own money) are totalled on
  their own line and never change net flow (owner, 2026-11-03, round 4; round 3 subtracted them). The page's summary,
  under the filters in the Transactions page's cards, covers the chosen range and follows the kind / wallet / category
  filters; each month's header shows that month's net flow. (Round 3's month hero and three-month projection were
  removed in round 4: too much at once.)
- A debt payment's total (principal + any planned interest) is the transfer; its interest part becomes spending only when
  it is marked paid (see below), so Bills due counts just that interest.
- **Category required**: a scheduled bill or income must have a category (`RecurringRepository.SaveResult.CategoryRequired`,
  owner 2026-11-03); a debt payment gets the shared "Debt interest" category for its interest. Older schedules without
  one keep working and ask for one when edited.
- **Changing several at once** (Scheduled › All schedules, Select mode; tester round, 2026-11-18; `ScheduleBulk`, pure,
  unit-tested). Each change is one database transaction (`RecurringRepository.moveDay`, `setAmount`, `setPaused`,
  `delete`): every plan selected takes it, or none does, and a plan that can't take it is skipped and counted.
  - **Change day**: weekly plans take a weekday; every other plan, one-time ones included, a day of the month. The
    occurrence still waiting moves to that day in the same month (weekly: the same week, as the phone's calendar starts
    it, so a Sunday plan moved to Monday goes to the next day where weeks start on Sunday), so none is gained or lost.
    One moved before today is then overdue, and an overdue one moved after today no longer is; the preview marks both.
    The plan restarts there: its first date is the moved date and the count starts again, as when the editor changes
    the timing. A plan already on that day isn't rewritten. A month shorter than the day uses its last day and later
    months keep the day (Nov 30, then Dec 31): the plan is anchored on an earlier occurrence month that has the day,
    with the occurrences before the waiting one counted as handled (a yearly February plan moved to the 29th or 30th
    gets Feb 29 in leap years). A plan with an end date keeps the same number of dates left: the end date stays when
    they fit, and otherwise becomes the new last date (the preview says "ends …"); with one date left that moves past
    the end, the end date becomes the day before the date after it, so the plan isn't stored as one-time. Recorded
    transactions never change. The waiting date's idempotency key is dated, and it stays in the month or week whose
    date was still waiting, so it can't match a payment the plan recorded for an earlier month or week.
  - **Change amount**: one amount for plans in one currency; plans in another, or ended, are skipped. A debt payment's
    amount is its whole payment, as the editor plans it, so an older plan's interest part is cleared (the split is
    entered at Mark paid).
  - **Pause / Resume** skips ended plans and plans already that way. **Delete** keeps every transaction the plans
    recorded.
- **A second payment plan for one debt** (`ScheduleBulk.paymentPlansFor`): any unfinished plan paying that card or loan,
  or collecting from that receivable, paused ones included (resuming one would remind twice). The scheduled-payment
  editor warns while one exists, listing them: "Adjust existing" opens All schedules filtered to that debt with those
  plans selected; "Add another" carries on. Nothing blocks a second plan.

## Ask Lucento reports
*Since tester round 3 these built-in reports are listed on Insights, under Ask Lucento; an Ask answer can still show one. The wallet statement is described above.*

`domain/ask/Reports.kt` (`ReportBuilder`, pure, unit-tested); one currency per report, others named and never converted;
the same `Report` is drawn on screen and written to the PDF. See docs/ASK.md for what the AI may see.
- **Net worth statement** (a month): each wallet's balance at the month's end (today for the current month), rebuilt as
  `BalanceHistory.walletsAt` does; assets, liabilities (amounts owed) and net worth = assets − liabilities; change =
  net worth − net worth at the end of the month before (omitted when no wallet existed then).
- **Cash flow statement** (a month; the current month runs to today): income and expenses by category
  (`Insights.categoryBreakdown`), each with its share of its total; net flow = income − expenses. Transfers (the
  outgoing legs) and balance adjustments (signed) are listed apart, never in net flow.
- **Budget vs actual** (a month): `BudgetPlanner.plan` — for each budgeted category its budget, spent and left; spending
  without a budget (unbudgeted categories, uncategorised); the month's total budget, everything spent, and what's left
  (the Budget screen's figures).
- **Debts & loans summary** (today): each card and loan in the currency with what's owed, its rate, a loan's
  instalments (`LoanMath`), a card's amount due (`CreditCardMath`) and the next unpaid occurrence of a schedule paying it;
  total owed.
- **Top spending categories** (a month): the top N (default 5, at most 20) expense categories with their share of all
  spending, then "everything else".
- **AI answers' figures** (docs/ASK.md; `AskSnapshotBuilder`, all on the phone): *projected spending* this month =
  spending so far ÷ days so far × days in the month (`CashflowCalculator.burnRate`); *averages* = totals over the last
  full months on record (up to three, never the current month) ÷ their number, half-up; *share of income kept* = (income −
  expenses) ÷ income over those months, whole percent; *money on hand* = cash, bank and e-wallet balances (Ask's own figure, by type; Safe to spend's spending money above follows the person's choices); goals use
  `GoalCalculator` (still to save, needed a month by the target date, projected date at the pace so far); a debt's
  *monthly interest* = owed × yearly rate ÷ 12 (`DebtMath.monthlyInterest`). **Transaction patterns** (expenses, one
  currency; the payee is the note, lower-cased, punctuation and 4+-digit runs dropped): *top payees* over 90 days;
  *monthly repeats* = a payee in 3+ of the last 6 months within 15% of its median, or in 2 consecutive months within 2%;
  *unusual* = this month's expense ≥ 2.5 × its category's median over the 90 days before the month (4+ earlier expenses
  needed); *possible duplicates* = same account, day, amount and payee within 60 days. **Calculations in answers**
  (`AskCalc`): `[[= expression ]]` over figure ids, numbers, + − × ÷ and brackets, in exact decimals; amount ± amount
  (same currency), amount × or ÷ number, amount ÷ amount = number; anything else (mixed currencies, ÷ 0, a text figure,
  an unknown id) shows "—". Amounts round half-up to the currency's minor unit; numbers show at most one decimal.
  Round 10 (request version 3): an amount from the question or assumed by the model, written with its ISO code
  ("PHP 5000", "5000 PHP", "PHP 5,000"); a percentage ("7%" = 0.07, plain numbers only); and a power `^` (plain numbers,
  whole exponents −1200…1200, right to left; a sign binds looser, so −2^2 = −4). Future value of a monthly saving s for
  n years at yearly rate r, compounded yearly on each year's total: s × 12 × ((1 + r)^n − 1) ÷ r. Rates written in an
  answer ("7.03%") are not counted as amounts the AI wrote itself.
- **Purchase check** (owner test round 2, 2026-11-09; `PurchaseCheck`, on the phone, from the AI figures above): a
  purchase question ("Should I buy…", "Can I afford…") answered without Gemini (AI off, or Gemini unreachable).
  *Balances* = money on hand + savings balances; *cushion* = 3 × average monthly expenses. A price (main currency only,
  read by `AskScope.price`; never converted) ≤ this month's safe to spend → fits this month, leaving safe − price; else
  balances < price → not yet; else balances − price ≥ cushion → can come from savings, leaving (balances − price) ÷
  average expenses months (one decimal, rounded down); else → wait. A wait gives the months to close the gap at the
  average net flow, rounded up (only when net flow > 0): gap = cushion − (balances − price), or price − balances +
  cushion when the balances can't cover the price. Without a price it lists safe to spend, balances (and the months of
  spending they cover), net worth and what's owed, with the same rule in words.
- **Insights change chip**: `Insights.changePercent(net, previous net)` — the period's net flow against the equal period
  before it, as a whole percentage of the previous amount's size (half-up); omitted when the earlier period has no
  records or a zero net. **Chart axis**: `Insights.axisTicks` — round gridlines (1, 2, 2.5 or 5 × a power of ten), at
  most five intervals, the top at or above the largest bar; bars scale to the top gridline.

## Debt payments (recurring)
A recurring schedule with `to_wallet_id` set pays a card or loan from a money account (same currency). It is planned
as one amount (the payment, e.g. the monthly amortization; `interest_minor` stays 0). The split is entered when an
occurrence is marked paid: the payment and "of which interest" from the statement; principal = payment − interest
(interest may not exceed the payment). Marking it paid writes, in one database transaction and keyed by the occurrence: the interest as an EXPENSE on the debt (category
"Debt interest"; it is spending and raises what is owed), then a TRANSFER of principal + interest from the paying wallet
to the debt. Net: the debt falls by the principal, the paying wallet by the total, spending rises by the interest only.
Safe to spend counts the whole payment against balances, but only its interest against a budget
(`RecurringMath.committedExpenses(spendingOnly)`). A debt with a payment schedule can't be deleted.
On the debt's own page, history is split into Principal and Interest (a card: Payments and Charges) and shown as the
change in what is owed — a payment reads "−", interest "+" (display only). Its note: owed at start − principal repaid
(start − current balance) = current balance; the interest charged is paid within the payments, so it nets out.

## Balance adjustments
Lucento never syncs with a bank, so a balance only changes when something is recorded. When the user can't find what
is missing, Edit wallet › Adjust balance sets the real balance (amount owed for a card or loan) at the end of a day,
today or earlier. The tracked balance that day (opening ± that wallet's movements dated on or before it) is compared
with the actual figure and the gap is written as **one** row: ADJUSTMENT_IN when it raises the money in the wallet
(lowers what a card owes), ADJUSTMENT_OUT otherwise. Nothing already recorded is rewritten, and later transactions
still apply on top. Adjustments have no category, appear in Transactions as "Balance adjustment" (warning tone), are
never income, spending, budgeted spending or net flow, can be deleted but not edited as income/expense, and are
idempotent on the sheet's client key. Equal balances write nothing. Manually valued assets are revalued in their
editor instead. Code: `BalanceAdjustment.plan`, `WalletBalanceCalculator.balanceOn`, `TransactionRepository.adjustBalance`.

### Withdrawals and external payments (tester round 3)
Money that moves between one account and the world outside Lucento without being income, spending or a transfer
between tracked wallets (`OutsideMovement`, `TransactionRepository.recordOutsideMovement`):
- **Withdrawal** (cash, bank, e-wallet, savings): money taken out that isn't spending and doesn't go to a tracked
  wallet. **One** ADJUSTMENT_OUT; the balance drops by exactly the amount.
- **External payment** (credit card, loan): the debt paid from outside the person's wallets, e.g. a family member
  settling it as a gift. **One** ADJUSTMENT_IN; what is owed drops by exactly the amount. A card's amount due falls with
  it (an inflow after the statement), and a loan counts it as an instalment paid (`LoanMath.progress`).
Both are dated today or earlier, noted "<label> · <what for>" — the reason is **required** (≤ 200 characters) because
the wallet statement lists it — and, like every adjustment, never income, spending, budgeted spending or net flow.
Transactions and the detail page show them by their label. Receivables and valued assets take neither. Idempotent on
the sheet's client key.

## Wallet statement (tester round 3)
One wallet's month in its own currency (`ReportBuilder.walletStatement`; on Insights › Reports and the wallet's page):
- **Balance at the start** = `WalletBalanceCalculator.balanceOn` at the end of the day before the month (a wallet added
  during the month starts at its opening balance); **balance at the end** = `balanceOn` at the month's end, or today.
- In between, every movement dated in the month, as its effect on the balance (for a card or loan, on what is owed):
  income and expenses by category; then transfers in and out by the wallet on the other side (the other leg of the
  transfer group); withdrawals or external payments, each with what it was for; and balance corrections.
- **Start + every movement = end**, exactly (the summary's lines add up to the ending balance; tested).

## Transfers
Two legs (TRANSFER_OUT on the source, TRANSFER_IN on the destination) sharing `transfer_group`, written in one Room
transaction, idempotent on the client key. Same currency only. Deleting or editing either leg affects both.

## Record integrity (v0.4)
- **Wallet type** (`WalletChangePolicy`): an asset wallet (cash, bank, e-wallet, savings) and a liability wallet
  (credit card, loan) compute balances in opposite directions, so switching between the two sides is refused while any
  transaction (including transfer legs) or recurring schedule references the wallet. Same-side changes (cash → bank,
  card → loan) keep the formula and are allowed. Nothing is rewritten; a conversion would be a separate, explicit
  operation (not implemented).
- **Wallet currency**: refused while transactions *or recurring schedules* reference the wallet (a schedule stores its
  currency; changing the wallet would make it unpayable). Never converted.
- **Category removal** (`CategoryRepository.remove`): deleted only when nothing references it. A category used by
  transactions, budgets or recurring schedules is **archived** (hidden from pickers, still labels history; its budget
  keeps working; `restore` brings it back). This avoids the budgets foreign key's `ON DELETE CASCADE` and the
  schedules' `SET NULL` ever firing through the app.
- **Writes**: transfer legs are inserted inside one Room transaction with the duplicate check repeated inside it; a
  unique-key race returns the row that won. Screens use `saveOrNull` / `saveTransferOrNull` and the Plan view model
  catches failures, so a refused write shows an error and never a success confirmation.

## JSON backup
A backup is one JSON document (`schemaVersion`, app version, export time) of the Room ledger plus the preferences
listed in `docs/BACKUP.md`. Amounts are integer minor units with an ISO 4217 code — the same representation as the
database, never floating point. Calendar dates are ISO-8601 (`YYYY-MM-DD`); instants are ISO-8601 UTC. Stable ids are
each row's `client_uuid`. Restored rows are marked dirty with no base revision, so the next sync uploads them and a
conflict with the server is decided by `updatedAt`, not by claiming to be based on a server revision the file does
not know.

## CSV export
RFC 4180 quoting (commas, quotes, CR/LF) for every field. User-entered text (account, category, note) whose first
meaningful character — after leading whitespace, control characters or a BOM — is `=`, `+`, `-`, `@` (or the
full-width forms), or that starts with a tab/CR/LF, gets a leading apostrophe (OWASP CSV-injection guidance).
Generated values (date, type, amount, currency, transfer id) are not altered, so negative amounts stay numeric. This
reduces, but cannot guarantee against, every spreadsheet's import behaviour (e.g. an importer that strips the
apostrophe).

## Receipt amounts
**Which line is the total**, best first: "Grand total"; "Amount due" / "Total due" / "Balance due" / "Amount payable" /
"Amount to pay" / "Amount sent" / "Refund amount"; "Total amount" / "Net total" / "Net amount" / "Total" / "Fuel sale";
"Amount paid" / "Total paid" / "You paid" / "You sent" / a line starting "Amount" — and their equivalents in 20 languages
(SUMME, ZU ZAHLEN, TOTAL TTC, NET À PAYER, TOTAL A PAGAR, TOTALE, TE BETALEN, TOTAL BAYAR, 合計, 합계 …;
`ReceiptParser.totalLabels`). Never "Subtotal", tax lines, "GST included in total", the total row of a tax table,
change, cash tendered, savings, points, suggested tips, item counts or "Minimum amount due". When the only label is
"Amount paid" and a change line is printed, the total is paid − change (`ReceiptParser.findTotal`). A label alone on its
line takes the next line's amount only when that line holds nothing else. Abbreviations count ("Amt due", "Total amt",
"TTL", "Amt paid"), and a "VAT inclusive" / "incl. VAT" / "TTC" / "inkl. MwSt" qualifier doesn't make a total line a tax
line. With no total line, cash tendered − change is the total only when that amount is also printed on the receipt;
otherwise nothing is pre-filled, and the amounts offered leave out cash tendered, change, tax lines, discounts, savings
and points (cash − change, when printed, is offered first). With a tip, items + tax + tip are checked against the total.
A total printed negative, or refund words on its line or a title line, makes the receipt a refund (income).
**Currency** (`DocumentCurrency`): the one printed (or clearly dominating); a shared symbol (`$`, `¥`, `kr`, `Rs`) settled
by the document's country (`DocumentLocale`), else by the person's only account in such a currency (the row says "check
the currency"); nothing printed and a firm country → that country's currency; otherwise unknown and the account's.
Amounts are read with that currency's minor-unit digits, else the default wallet's, else 2. A group of three after a
single separator is thousands (`1,250`, `12.500`) except for 3-decimal currencies with a `.` (`12.500` KWD); Indian
`1,23,456.00` is one number. Plain integers count on a line labelled as the total, or with a currency beside them
(`¥348`, `348円`). Dates, times, percentages, phone/TIN/reference/account numbers, quantities with units, meter readings
and written dates are masked first. **Date**: a labelled bill/invoice/transaction date wins; due dates, periods and
expiry dates are passed over; day/month order by another date on the receipt, else the country, else marked unclear.
The result is always a suggestion on an editable review screen; missing values stay missing. More: docs/SCAN.md.

## Statement scans
`domain/capture/StatementParser.kt` (pure, unit-tested; `ScanOcrTest` and `DocumentIntelligenceDeviceTest` run it on
real on-device OCR). A scan is a **statement** (one transaction per line) when two or more lines each carry a date and an
amount; or dates head (or sit under) groups of at least two different amounts, with +/− marks or statement words, and no
more receipt words than statement words; or three or more lines carry +/− marks and nothing reads like a receipt. A bill
or receipt with at most three dated lines, no running balance and its own total to pay stays a **receipt**, unless it
reads like a card statement.
- **Lines**: opening/closing balances, totals, minimum due, limits, rewards, balance labels, daily-balance and APR tables,
  fee summaries, prose and declined lines are never transactions; an opening balance anchors the balance check. A
  wrapped second line joins the dated line above it. On a statement whose lines carry their own dates, an undated line
  counts only with a running balance, under a date line, or as a card's interest or fee. At most 200 lines.
- **Amount**: with columns known from positions, the amount under Debit/Credit (or Amount) and the Balance column's;
  with a balance column (a header naming "balance", or most amount lines carrying two amounts) the first amount is the
  transaction and the last the running balance; otherwise the last amount. Bare integers count only with a currency or
  a sign beside them (or on a dated line in a currency without decimals); digits glued to letters are codes.
- **Direction** (money in or out of the account), strongest first: the running balance (previous − amount = this
  balance → out; previous + amount → in, reversed on a page that plainly is a card statement, whose balance is owed),
  checked oldest-first and newest-first and the better fit kept; the column; a mark beside the amount (−, +, ( ), CR,
  DR, (Cr), (Dr); a trailing minus or a minus is a credit on a card statement and a debit on a bank's); a bank's
  convention (debits printed with a minus: unsigned amounts are money in); the section heading; the words
  (`FinancialTerms`, by account type); on a card statement, a charge unless it says payment, credit or refund. Anything
  else is marked for the user to check.
- **Dates**: a day above 12 settles day/month order for the whole statement; otherwise the order that keeps its dates
  within about two months, then the document's country's order, then the phone's order, and the dates are marked as
  unclear. A year not printed is the latest one that keeps the date on or before the statement's newest full date (or
  today). Future dates and dates more than five years back are dropped (the row's date is then marked). European
  `dd.mm` lines and month names in ten languages are read.
- **Rows** (`StatementCandidates`): all in the statement's account (the person's wallet at the bank named in the heading,
  else the account in its currency, else the active wallet); out → expense, in → income, except a refund (income, on a
  card too), money into a card or loan (a payment: a transfer whose source the user picks), money added to an e-wallet
  (a transfer in), a line naming another of the user's same-currency accounts (a transfer between them), and ATM
  withdrawals and cash advances (a transfer to the one cash wallet).

## Statement check
`StatementReconciler` compares a statement's rows with the saved ledger of its account; nothing saved is changed.
**Saved already**: same account, amount and type within 3 days (closest date, then closest words; one saved transaction
per line) → held back, offered with "Add anyway". **Possibly saved**: the same amount in the account within 7 days or as
another type, or in another account within 3 days → added, with a note. **Printed twice**: same day, amount, direction
and (nearly) the same words → added, marked. A line repeating a row already waiting in the batch is held back. **Not on
the statement**: saved in the account within the statement's period (whole statements only: a period, or both balances,
printed). **Balance**: the statement's closing balance against `WalletBalanceCalculator.balanceOn` on its last day plus
every row offered (a card's balance is what's owed); skipped for a wallet created after that day. **Adds up**: opening ±
posted lines = closing. Refunds and reversals are paired with a charge of the same amount sharing a word, and a refund
with a saved purchase of the same amount within 120 days; fees and interest are totalled.
