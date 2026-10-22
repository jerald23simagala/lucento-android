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

## Debt rules
- Credit-card purchase: EXPENSE on the card (raises owed). Paying the card: TRANSFER from bank to card (lowers owed;
  not spending, so the purchase is not counted twice).
- Loan proceeds: TRANSFER out of the loan into a bank account (raises owed; not income).
- Principal repayment: TRANSFER into the loan. Interest and fees: EXPENSE on the loan.
- Lucento does not split a single payment into principal and interest automatically.

## Transfers
Two legs (TRANSFER_OUT on the source, TRANSFER_IN on the destination) sharing `transfer_group`, written in one Room
transaction, idempotent on the client key. Same currency only. Deleting or editing either leg affects both.
