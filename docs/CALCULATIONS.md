# Lucento financial definitions

All money is `Long` minor units with an ISO 4217 code. Nothing is ever converted between currencies; every figure
below is computed for one currency (the dashboard's *focus currency*: the main currency when used, otherwise the
first wallet's). Implementations are pure Kotlin in `domain/` with JVM tests.

| Figure | Definition | Code |
|---|---|---|
| Wallet balance (asset) | opening + income + transfers in + adjustments in − expenses − transfers out − adjustments out | `WalletBalanceCalculator` |
| Wallet balance (credit card, loan) | amount owed = opening + expenses + transfers out + adjustments out − income − transfers in − adjustments in | `WalletBalanceCalculator` |
| Balance flag | cash / e-wallet / savings below zero, a bank account below zero (overdrawn?), or a card / loan below zero (owed to you). Shown as a warning with a reconcile note; never changes a figure | `Wallet.balanceFlag` |
| Net worth | assets − liabilities, per currency | `NetWorthCalculator` |
| Income / Expenses | sum of INCOME / EXPENSE in the period. **Transfers and balance adjustments never count.** | `TransactionQueries.totalsByCurrency` |
| Net flow | income − expenses for the month; compared with last month when last month has records | `CashflowCalculator.netFlow` |
| Burn rate | actual: expenses this month ÷ days elapsed (incl. today), half-up. Projection (labelled): expenses × days in month ÷ days elapsed. Comparison: last month's expenses ÷ its length | `CashflowCalculator.burnRate` |
| **Monthly budget (v0.5)** | **Σ category allocations** of active expense categories in one currency for the calendar month. Never stored or edited on its own, so it cannot disagree with the categories. Archived categories, income categories, zero amounts and legacy "everything" rows (≤ v0.4) never count. Currencies are never summed together | `BudgetPlanner.plan` |
| Spent (budget) | the month's actual money out: **every** EXPENSE in the month and currency, budgeted or not. Remaining = budget − spent (negative when over); percentage used, health, pace and the daily allowance all use it | `BudgetPlan.spent / remaining` |
| Budgeted / outside the budget | budgeted = EXPENSE in an allocated category (the Budgeted section's "x of total"); outside = every other EXPENSE (uncategorised, unallocated or archived categories), shown on the Budget page and counted in Spent, never reassigned | `BudgetPlan.budgetedSpending / unbudgetedSpending` |
| Budget health | NOT_SET when the budget is 0; EXCEEDED when spent ≥ budget (exhausted or over); LOW when remaining × 100 ≤ budget × 10; APPROACHING when spent × 100 ≥ budget × 80; else ON_TRACK. Exact integers. The Home hero (Sun happy vs. supportively concerned = LOW/EXCEEDED), burn bar and Budget tab all use it | `BudgetPlanner.health` |
| Burn-rate bar | consumed = spent (all expenses) ÷ budget (display fraction); elapsed = day of month ÷ days in month; level NORMAL / APPROACHING (APPROACHING or LOW) / EXHAUSTED; no percentage without a budget. Daily allowance = remaining ÷ days left incl. today, rounded down, only when positive | `BudgetPlanner.burn` |
| Budget pace | used % (as burn-rate bar) vs elapsed % (day of month ÷ days in month, rounded down; 100 % for a past month). AHEAD when used > elapsed + 10, BEHIND when used < elapsed − 10, else ALIGNED; none without a budget. Home's pace card and its wording read it | `BudgetBurn.pace` |
| Featured savings goal | the unreached goal with the highest percent (ties: earlier target date, then older); when all are reached, the newest goal | `GoalsSummary.overview().top` |
| Manually valued assets | `OTHER_ASSET` wallets: balance = the valuation the user enters (no movements; repositories refuse transactions, transfers and schedules on them). Counted once as an asset in net worth | `WalletType.isValuedManually` |
| Savings level | Σ min(max(saved, 0), target) ÷ Σ targets for goals in one currency; closest = unreached goal with the highest percent. A view only: never added to net worth or budgets | `GoalsSummary.overview` |
| Net worth composition | assets and liabilities grouped by wallet type, per currency; totals equal `NetWorthCalculator` | `NetWorthComposer.compose` |
| Income/expense trend | INCOME and EXPENSE per month (zero-filled); transfers never appear | `Insights.incomeExpenseTrend` |
| Upcoming payments | next unpaid occurrence of each active schedule due within 30 days (overdue flagged), date order, max 4. Listing never records anything | `RecurringMath.upcomingPayments` |
| Safe to spend — with category allocations | budget − budgeted spending − unpaid recurring expenses in budgeted categories due by month end (overdue included) | `CashflowCalculator.safeToSpend` |
| Safe to spend — without a budget (estimate, labelled) | cash + bank + e-wallet balances − unpaid recurring expenses due by month end that are paid from those accounts. Savings, liabilities, and bills paid from them are excluded | same |
| Safe per day | safe to spend ÷ days left incl. today, rounded down; shown only when positive | same |
| Category spent | EXPENSE in the calendar month, budget currency, matching category. No rollover; refunds recorded as income are not netted | `BudgetCalculator` |
| Budget status | over when spent > limit; near when spent × 100 ≥ limit × 80 (exact, no rounding) | `BudgetProgress.status` |
| Goal saved | sum of contributions (earmarks; wallets unchanged, so savings are never double-counted) | `GoalCalculator` |
| Goal projection (labelled estimate) | remaining ÷ average daily pace since first contribution (min. 30 days) | `GoalCalculator.projectCompletion` |
| Required per month | remaining ÷ started months until target date, rounded up | `GoalCalculator.requiredPerMonth` |
| Recurring occurrence *k* | start + k × interval × frequency (month-end anchors kept: Jan 31 → Feb 28 → Mar 31) | `RecurringMath` |
| Recurring idempotency key | `recurring-<schedule id>-on-<due epoch day>` (v0.4). Keyed by due date, not index, so editing a schedule's timing (which restarts the index) never makes a new payment look like a duplicate of an old one. v0.3 keys (`recurring-<id>-<index>`) cannot collide | `RecurringSchedule.occurrenceKey` |
| Goal milestone | halfway / reached, when saved × 100 crosses target × 50 / × 100 because of a contribution (never a withdrawal) | `GoalMilestones` |
| Budget month kept | the previous calendar month: allocations that existed all month (created on/before its 1st) sum to a positive budget in the main currency, budgeted spending > 0 and ≤ that budget. Acknowledged once (persisted) | `BudgetMilestones` |

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
  was needed, so the database stays at version 3.

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
- Interest estimate shown beside a debt payment: owed × yearly ÷ 12, half-up (`DebtMath.monthlyInterest`). Never applied.
- Creating a card or loan creates (or restores) the expense category "Debt interest" (`CategoryRepository.ensure`).
- A payment reminder date opens a prefilled recurring debt payment: "Monthly Payment - <debt>" for the amortization,
  first due on that date and ending after the term; "One-time Payment - <debt>" for what is owed, one date only.

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

## CSV export
RFC 4180 quoting (commas, quotes, CR/LF) for every field. User-entered text (account, category, note) whose first
meaningful character — after leading whitespace, control characters or a BOM — is `=`, `+`, `-`, `@` (or the
full-width forms), or that starts with a tab/CR/LF, gets a leading apostrophe (OWASP CSV-injection guidance).
Generated values (date, type, amount, currency, transfer id) are not altered, so negative amounts stay numeric. This
reduces, but cannot guarantee against, every spreadsheet's import behaviour (e.g. an importer that strips the
apostrophe).

## Receipt amounts
Amounts are read with the minor-unit digits of the currency printed on the receipt (unambiguous symbols/ISO codes
only; `$` and `¥` are ambiguous and ignored), else the default wallet's currency, else 2. A group of three after a
single separator is thousands (`1,250`, `12.500`) except for 3-decimal currencies with a `.` (`12.500` KWD). Plain
integers count only on a line labelled as the total. Dates, times, percentages, phone/TIN/reference numbers and
quantities are masked first. The result is always a suggestion on an editable review screen; missing values stay
missing.
