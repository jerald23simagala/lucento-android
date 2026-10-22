# Lucento financial definitions

All money is `Long` minor units with an ISO 4217 code. Nothing is ever converted between currencies; every figure
below is computed for one currency (the dashboard's *focus currency*: the main currency when used, otherwise the
first wallet's). Implementations are pure Kotlin in `domain/` with JVM tests.

| Figure | Definition | Code |
|---|---|---|
| Wallet balance (asset) | opening + income + transfers in − expenses − transfers out | `WalletBalanceCalculator` |
| Wallet balance (credit card, loan) | amount owed = opening + expenses + transfers out − income − transfers in | `WalletBalanceCalculator` |
| Net worth | assets − liabilities, per currency | `NetWorthCalculator` |
| Income / Expenses | sum of INCOME / EXPENSE in the period. **Transfers never count.** | `TransactionQueries.totalsByCurrency` |
| Net flow | income − expenses for the month; compared with last month when last month has records | `CashflowCalculator.netFlow` |
| Burn rate | actual: expenses this month ÷ days elapsed (incl. today), half-up. Projection (labelled): expenses × days in month ÷ days elapsed. Comparison: last month's expenses ÷ its length | `CashflowCalculator.burnRate` |
| Safe to spend — with an overall monthly budget | budget − expenses this month − unpaid recurring expenses due by month end (overdue included) | `CashflowCalculator.safeToSpend` |
| Safe to spend — without a budget (estimate, labelled) | cash + bank + e-wallet balances − unpaid recurring expenses due by month end. Savings and liabilities excluded | same |
| Safe per day | safe to spend ÷ days left incl. today, rounded down; shown only when positive | same |
| Budget spent | EXPENSE in the calendar month, budget currency, matching category (or all for overall). No rollover; refunds recorded as income are not netted | `BudgetCalculator` |
| Budget status | over when spent > limit; near when spent × 100 ≥ limit × 80 (exact, no rounding) | `BudgetProgress.status` |
| Goal saved | sum of contributions (earmarks; wallets unchanged, so savings are never double-counted) | `GoalCalculator` |
| Goal projection (labelled estimate) | remaining ÷ average daily pace since first contribution (min. 30 days) | `GoalCalculator.projectCompletion` |
| Required per month | remaining ÷ started months until target date, rounded up | `GoalCalculator.requiredPerMonth` |
| Recurring occurrence *k* | start + k × interval × frequency (month-end anchors kept: Jan 31 → Feb 28 → Mar 31) | `RecurringMath` |
| Recurring idempotency key | `recurring-<schedule id>-on-<due epoch day>` (v0.4). Keyed by due date, not index, so editing a schedule's timing (which restarts the index) never makes a new payment look like a duplicate of an old one. v0.3 keys (`recurring-<id>-<index>`) cannot collide | `RecurringSchedule.occurrenceKey` |
| Goal milestone | halfway / reached, when saved × 100 crosses target × 50 / × 100 because of a contribution (never a withdrawal) | `GoalMilestones` |
| Budget month kept | the previous calendar month, overall budget in the main currency that existed all month, real spending, spent ≤ limit. Acknowledged once (persisted) | `BudgetMilestones` |

## Debt rules
- Credit-card purchase: EXPENSE on the card (raises owed). Paying the card: TRANSFER from bank to card (lowers owed;
  not spending, so the purchase is not counted twice).
- Loan proceeds: TRANSFER out of the loan into a bank account (raises owed; not income).
- Principal repayment: TRANSFER into the loan. Interest and fees: EXPENSE on the loan.
- Lucento does not split a single payment into principal and interest automatically.

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
