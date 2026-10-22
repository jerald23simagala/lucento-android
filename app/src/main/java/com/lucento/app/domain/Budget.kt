package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.YearMonth

/**
 * A monthly spending limit in one currency. [categoryId] null means an overall budget covering every
 * expense in that currency; otherwise it covers one expense category.
 *
 * Period rules: budgets apply to calendar months (1st to last day, by the transaction's calendar date).
 * There is no rollover — every month starts again from the full limit. Only EXPENSE transactions count:
 * transfers never do, and refunds recorded as income are not netted against spending (they appear as
 * income), so nothing is double-counted.
 */
data class Budget(
    val id: Long,
    val categoryId: Long?,
    val limit: Money,
    /** Local date the budget was created; null when unknown. Used to tell whether it covered a whole month. */
    val createdOn: java.time.LocalDate? = null,
) {
    val currencyCode: String get() = limit.currencyCode
    val isOverall: Boolean get() = categoryId == null
}

enum class BudgetStatus { ON_TRACK, NEAR_LIMIT, OVER_LIMIT }

data class BudgetProgress(
    val budget: Budget,
    val month: YearMonth,
    val spent: Money,
) {
    /** Limit minus spent; negative when over budget. */
    val remaining: Money get() = budget.limit - spent

    /** Whole-number percentage of the limit used (may exceed 100), or null for a zero limit. */
    val percentUsed: Int? get() = Insights.percentOf(spent, budget.limit)

    /** Display-only fraction 0..1 for progress bars. */
    val fraction: Float get() = Insights.fraction(spent, budget.limit)

    val status: BudgetStatus
        get() {
            if (spent > budget.limit) return BudgetStatus.OVER_LIMIT
            if (budget.limit.amountMinor <= 0L) return BudgetStatus.ON_TRACK
            // Exact comparison (spent / limit >= 80%) so rounding never moves a budget across the threshold.
            val near = java.math.BigInteger.valueOf(spent.amountMinor).multiply(java.math.BigInteger.valueOf(100)) >=
                java.math.BigInteger.valueOf(budget.limit.amountMinor).multiply(java.math.BigInteger.valueOf(NEAR_LIMIT_PERCENT.toLong()))
            return if (near) BudgetStatus.NEAR_LIMIT else BudgetStatus.ON_TRACK
        }

    companion object {
        const val NEAR_LIMIT_PERCENT = 80
    }
}

object BudgetCalculator {

    /** Spending that counts against [budget] in [month]. */
    fun spent(budget: Budget, transactions: List<Transaction>, month: YearMonth): Money {
        val range = DateRange(month.atDay(1), month.atEndOfMonth())
        return Money.sum(
            budget.currencyCode,
            transactions
                .asSequence()
                .filter { it.type == TransactionType.EXPENSE && it.currencyCode == budget.currencyCode && it.date in range }
                .filter { budget.isOverall || it.categoryId == budget.categoryId }
                .map { it.amount }
                .asIterable(),
        )
    }

    fun progress(budget: Budget, transactions: List<Transaction>, month: YearMonth): BudgetProgress =
        BudgetProgress(budget, month, spent(budget, transactions, month))

    /**
     * Progress for every budget, overall budgets first, then category budgets by most used.
     */
    fun progressAll(budgets: List<Budget>, transactions: List<Transaction>, month: YearMonth): List<BudgetProgress> =
        budgets.map { progress(it, transactions, month) }
            .sortedWith(
                compareBy<BudgetProgress> { !it.budget.isOverall }
                    .thenByDescending { it.percentUsed ?: 0 }
                    .thenBy { it.budget.id },
            )

    /** The overall budget for [currencyCode], if the user set one. */
    fun overallFor(budgets: List<Budget>, currencyCode: String): Budget? =
        budgets.firstOrNull { it.isOverall && it.currencyCode == currencyCode }
}

/**
 * When to (calmly) acknowledge a month kept within budget. Pure and deterministic; the caller persists the
 * month it acknowledged so it is shown once, not on every visit or recomposition.
 */
object BudgetMilestones {

    /**
     * The most recent *finished* month (before [today]'s month) worth acknowledging, or null. It qualifies when an
     * overall budget in [currencyCode] existed for that whole month, there was real spending, and spending stayed
     * at or under the limit. Overspending is never celebrated, nor is an empty month, nor a month already
     * acknowledged ([lastAcknowledged] on or after it).
     */
    fun monthToAcknowledge(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        currencyCode: String,
        today: java.time.LocalDate,
        lastAcknowledged: YearMonth?,
    ): BudgetProgress? {
        val month = YearMonth.from(today).minusMonths(1)
        if (lastAcknowledged != null && !lastAcknowledged.isBefore(month)) return null
        val budget = BudgetCalculator.overallFor(budgets, currencyCode) ?: return null
        val created = budget.createdOn ?: return null
        if (created.isAfter(month.atDay(1))) return null
        val progress = BudgetCalculator.progress(budget, transactions, month)
        if (progress.spent.isZero || progress.status == BudgetStatus.OVER_LIMIT) return null
        return progress
    }
}
