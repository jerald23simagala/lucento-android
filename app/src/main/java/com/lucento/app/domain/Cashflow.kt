package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * "How much can I safely spend for the rest of this month?" — in one currency, never converted.
 *
 * Two bases, chosen by the data that exists, and always disclosed to the user:
 *
 * - [Basis.BUDGET] (an overall monthly budget exists in this currency):
 *   `limit − spent this month − unpaid recurring expenses due by month end`.
 * - [Basis.BALANCES] (no overall budget): `spendable balances − unpaid recurring expenses due by month end`.
 *   Spendable balances are cash, bank and e-wallet accounts only; savings and liabilities are excluded,
 *   so money set aside is never presented as spendable. This is an estimate and is labelled as one.
 *
 * Transfers never count as spending. The amount may be negative (over budget / overcommitted) and is
 * shown honestly rather than clamped.
 */
data class SafeToSpend(
    val basis: Basis,
    val amount: Money,
    val month: YearMonth,
    /** Days remaining in the month including today. */
    val daysLeft: Int,
    /** [amount] spread evenly over [daysLeft]; null when nothing is left to spread. */
    val perDay: Money?,
    /** Expenses recorded this month in this currency. */
    val spentThisMonth: Money,
    /** Unpaid recurring expenses due on or before the last day of the month (overdue included). */
    val committed: Money,
    /** The overall budget's limit for [Basis.BUDGET]. */
    val budgetLimit: Money?,
    /** Sum of spendable balances in this currency; null when the user has no spendable account in it. */
    val spendableBalance: Money?,
) {
    enum class Basis { BUDGET, BALANCES }

    val currencyCode: String get() = amount.currencyCode

    /** Share of the budget already spent or committed (display only); null without a budget. */
    val budgetUsedFraction: Float?
        get() = budgetLimit?.let { Insights.fraction(spentThisMonth + committed, it) }

    val budgetUsedPercent: Int?
        get() = budgetLimit?.let { Insights.percentOf(spentThisMonth + committed, it) }
}

/** How quickly money is being spent this month, in one currency. */
data class BurnRate(
    val month: YearMonth,
    val spentThisMonth: Money,
    /** Days counted so far this month, including today. */
    val daysElapsed: Int,
    /** Actual average daily spending so far this month (rounded half-up to the minor unit). */
    val dailyAverage: Money,
    /** Projection: month-end spending if the current pace continues. Labelled as projected in the UI. */
    val projectedMonthSpend: Money,
    /** Last month's actual average daily spending; null when last month had no spending. */
    val previousDailyAverage: Money?,
    /** Change of [dailyAverage] versus [previousDailyAverage] in whole percent; null without a comparison. */
    val changePercent: Int?,
)

/** Income, expenses and their difference for a period. Transfers are excluded. */
data class NetFlow(
    val month: YearMonth,
    val income: Money,
    val expense: Money,
    val previousNet: Money?,
) {
    val net: Money get() = income - expense

    /** Difference between this month's net flow and last month's; null without last month's data. */
    val changeVsPrevious: Money? get() = previousNet?.let { net - it }
}

object CashflowCalculator {

    fun safeToSpend(
        currencyCode: String,
        today: LocalDate,
        transactions: List<Transaction>,
        wallets: List<Wallet>,
        budgets: List<Budget>,
        schedules: List<RecurringSchedule>,
    ): SafeToSpend? {
        val month = YearMonth.from(today)
        val monthEnd = month.atEndOfMonth()
        val daysLeft = (ChronoUnit.DAYS.between(today, monthEnd) + 1).toInt()
        val spent = expensesIn(transactions, currencyCode, month)
        val committed = RecurringMath.committedExpenses(schedules, currencyCode, monthEnd)
        val spendableWallets = wallets.filter { it.type.isSpendable && it.currencyCode == currencyCode }
        val spendable = if (spendableWallets.isEmpty()) null else Money.sum(currencyCode, spendableWallets.map { it.balance })
        val budget = BudgetCalculator.overallFor(budgets, currencyCode)

        val (basis, amount) = when {
            budget != null -> SafeToSpend.Basis.BUDGET to (budget.limit - spent - committed)
            spendable != null -> SafeToSpend.Basis.BALANCES to (spendable - committed)
            else -> return null
        }
        return SafeToSpend(
            basis = basis,
            amount = amount,
            month = month,
            daysLeft = daysLeft,
            perDay = if (amount.amountMinor > 0L) divide(amount, daysLeft.toLong(), RoundingMode.DOWN) else null,
            spentThisMonth = spent,
            committed = committed,
            budgetLimit = budget?.limit,
            spendableBalance = spendable,
        )
    }

    fun burnRate(currencyCode: String, today: LocalDate, transactions: List<Transaction>): BurnRate {
        val month = YearMonth.from(today)
        val elapsed = today.dayOfMonth
        val spent = expensesIn(transactions, currencyCode, month)
        val daily = divide(spent, elapsed.toLong())
        val projected = Money(
            BigDecimal.valueOf(spent.amountMinor)
                .multiply(BigDecimal.valueOf(month.lengthOfMonth().toLong()))
                .divide(BigDecimal.valueOf(elapsed.toLong()), 0, RoundingMode.HALF_UP)
                .longValueExact(),
            currencyCode,
        )
        val previousMonth = month.minusMonths(1)
        val previousSpent = expensesIn(transactions, currencyCode, previousMonth)
        val previousDaily = if (previousSpent.isZero) null else divide(previousSpent, previousMonth.lengthOfMonth().toLong())
        val change = previousDaily?.takeIf { it.amountMinor > 0L }?.let { prev ->
            BigDecimal.valueOf(daily.amountMinor - prev.amountMinor)
                .multiply(BigDecimal(100))
                .divide(BigDecimal.valueOf(prev.amountMinor), 0, RoundingMode.HALF_UP)
                .toInt()
        }
        return BurnRate(month, spent, elapsed, daily, projected, previousDaily, change)
    }

    fun netFlow(currencyCode: String, month: YearMonth, transactions: List<Transaction>): NetFlow {
        val income = totalIn(transactions, currencyCode, month, TransactionType.INCOME)
        val expense = totalIn(transactions, currencyCode, month, TransactionType.EXPENSE)
        val previous = month.minusMonths(1)
        val hasPrevious = transactions.any {
            !it.isTransfer && it.currencyCode == currencyCode && YearMonth.from(it.date) == previous
        }
        val previousNet = if (hasPrevious) {
            totalIn(transactions, currencyCode, previous, TransactionType.INCOME) -
                totalIn(transactions, currencyCode, previous, TransactionType.EXPENSE)
        } else {
            null
        }
        return NetFlow(month, income, expense, previousNet)
    }

    fun expensesIn(transactions: List<Transaction>, currencyCode: String, month: YearMonth): Money =
        totalIn(transactions, currencyCode, month, TransactionType.EXPENSE)

    private fun totalIn(transactions: List<Transaction>, currencyCode: String, month: YearMonth, type: TransactionType): Money =
        Money.sum(
            currencyCode,
            transactions.asSequence()
                .filter { it.type == type && it.currencyCode == currencyCode && YearMonth.from(it.date) == month }
                .map { it.amount }
                .asIterable(),
        )

    /** Exact division of money by a positive count, rounded to the currency's minor unit. */
    fun divide(money: Money, by: Long, rounding: RoundingMode = RoundingMode.HALF_UP): Money {
        require(by > 0) { "Divisor must be positive" }
        val minor = BigDecimal.valueOf(money.amountMinor)
            .divide(BigDecimal.valueOf(by), 0, rounding)
            .longValueExact()
        return Money(minor, money.currencyCode)
    }
}
