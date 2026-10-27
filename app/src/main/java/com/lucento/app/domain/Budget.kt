package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.YearMonth

/**
 * A monthly allocation for one expense category in one currency. The overall budget is never stored: it is the sum
 * of the allocations (see [BudgetPlanner]). [categoryId] null only appears for an "everything" limit saved by
 * Lucento 0.4 or earlier ([isLegacyOverall]); such a row is ignored by every calculation and can be removed by the user.
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
    /** A pre-0.5 "budget for everything". Ignored by all calculations; shown once so the user can remove it. */
    val isLegacyOverall: Boolean get() = categoryId == null
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
                .filter { it.categoryId != null && it.categoryId == budget.categoryId }
                .map { it.amount }
                .asIterable(),
        )
    }

    fun progress(budget: Budget, transactions: List<Transaction>, month: YearMonth): BudgetProgress =
        BudgetProgress(budget, month, spent(budget, transactions, month))

    /** Progress for every category allocation (legacy "everything" rows are skipped), most used first. */
    fun progressAll(budgets: List<Budget>, transactions: List<Transaction>, month: YearMonth): List<BudgetProgress> =
        budgets.filterNot { it.isLegacyOverall }
            .map { progress(it, transactions, month) }
            .sortedWith(compareByDescending<BudgetProgress> { it.percentUsed ?: 0 }.thenBy { it.budget.id })
}

/**
 * When to (calmly) acknowledge a month kept within budget. Pure and deterministic; the caller persists the
 * month it acknowledged so it is shown once, not on every visit or recomposition.
 */
object BudgetMilestones {

    /**
     * The most recent *finished* month (before [today]'s month) worth acknowledging, or null. Only allocations that
     * existed for that whole month (created on or before its first day) count, so a budget set up mid-month is never
     * judged on a partial month. The month qualifies when those allocations add up to a positive total in
     * [currencyCode], there was real budgeted spending, and it stayed at or under the total. Overspending is never
     * celebrated, nor is an empty month, nor a month already acknowledged ([lastAcknowledged] on or after it).
     */
    fun monthToAcknowledge(
        categories: List<Category>,
        budgets: List<Budget>,
        transactions: List<Transaction>,
        currencyCode: String,
        today: java.time.LocalDate,
        lastAcknowledged: YearMonth?,
    ): BudgetPlan? {
        val month = YearMonth.from(today).minusMonths(1)
        if (lastAcknowledged != null && !lastAcknowledged.isBefore(month)) return null
        val wholeMonth = budgets.filter { b -> b.createdOn != null && !b.createdOn.isAfter(month.atDay(1)) }
        val plan = BudgetPlanner.plan(categories, wholeMonth, transactions, currencyCode, month)
        if (!plan.hasBudget || plan.spent.isZero || plan.spent > plan.total) return null
        return plan
    }
}
