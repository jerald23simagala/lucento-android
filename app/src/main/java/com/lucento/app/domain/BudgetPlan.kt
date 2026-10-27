package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * How the month's budget is doing, shared by the Home hero card, the burn-rate bar and the Budget screen so all
 * three always agree. Thresholds are exact integer comparisons on minor units (see [BudgetPlanner.health]).
 */
enum class BudgetHealth {
    /** No category has an amount allocated in this currency (missing, zero or unfinished setup). */
    NOT_SET,

    /** Less than [BudgetPlanner.APPROACHING_PERCENT]% used. */
    ON_TRACK,

    /** At least [BudgetPlanner.APPROACHING_PERCENT]% used, more than [BudgetPlanner.LOW_REMAINING_PERCENT]% left. */
    APPROACHING,

    /** [BudgetPlanner.LOW_REMAINING_PERCENT]% or less of the budget left, but something is left. */
    LOW,

    /** Nothing left: spending reached or passed the total. */
    EXCEEDED;

    /** The mascot switches from happy to (supportively) concerned. */
    val isConcerning: Boolean get() = this == LOW || this == EXCEEDED
}

/** The three visual levels of the burn-rate bar, derived from [BudgetHealth] so the bar and the hero card agree. */
enum class BurnLevel {
    NO_BUDGET, NORMAL, APPROACHING, EXHAUSTED;

    companion object {
        fun of(health: BudgetHealth): BurnLevel = when (health) {
            BudgetHealth.NOT_SET -> NO_BUDGET
            BudgetHealth.ON_TRACK -> NORMAL
            BudgetHealth.APPROACHING, BudgetHealth.LOW -> APPROACHING
            BudgetHealth.EXCEEDED -> EXHAUSTED
        }
    }
}

/**
 * One active expense category in a month's plan. [allocation] is the monthly amount the user gave the category in
 * this currency (null when none); [spent] is that month's expenses recorded under the category in this currency.
 */
data class CategoryBudgetLine(
    val category: Category,
    val allocation: Budget?,
    val spent: Money,
) {
    val isAllocated: Boolean get() = allocation != null
    val limit: Money? get() = allocation?.limit

    /** Allocation minus spending; negative when over. Null for an unallocated category. */
    val remaining: Money? get() = allocation?.let { it.limit - spent }

    val percentUsed: Int? get() = allocation?.let { Insights.percentOf(spent, it.limit) }

    /** Display-only fraction 0..1. */
    val fraction: Float get() = allocation?.let { Insights.fraction(spent, it.limit) } ?: 0f

    /** Same thresholds as the overall plan, applied to this category. */
    val health: BudgetHealth get() = allocation?.let { BudgetPlanner.health(it.limit, spent) } ?: BudgetHealth.NOT_SET
}

/**
 * A month's budget in one currency. The overall budget is **derived**: [total] is always the sum of the category
 * allocations of active expense categories in [currencyCode] — there is no separately editable overall amount
 * that could disagree with them.
 *
 * - [spent] is the month's actual money out: **every** expense in this currency and month, budgeted or not, so
 *   `remaining = total − spent`, the percentage used, health and pace all measure real spending against the plan.
 * - [budgetedSpending] is the part in allocated categories; [unbudgetedSpending] is the rest (uncategorised,
 *   categories without an allocation, archived categories). Both are reported; nothing is forced into a category.
 * - Transfers, income and goal contributions are never spending. Other currencies are never converted.
 * - [legacyOverall] is an "everything" limit saved by Lucento 0.4 or earlier. It no longer counts anywhere; the
 *   Budget screen explains it once and lets the user remove it.
 */
data class BudgetPlan(
    val month: YearMonth,
    val currencyCode: String,
    /** Allocated categories first (most used first), then unallocated ones by spending, then the user's order. */
    val lines: List<CategoryBudgetLine>,
    val total: Money,
    /** Every expense this month in this currency: [budgetedSpending] plus [unbudgetedSpending]. */
    val spent: Money,
    val budgetedSpending: Money,
    val unbudgetedSpending: Money,
    val uncategorizedSpending: Money,
    val legacyOverall: Budget? = null,
) {
    val remaining: Money get() = total - spent
    val hasBudget: Boolean get() = total.amountMinor > 0L
    val health: BudgetHealth get() = BudgetPlanner.health(total, spent)

    /** Whole percent of the total used (may exceed 100); null without a budget so no misleading figure is shown. */
    val percentUsed: Int? get() = if (hasBudget) Insights.percentOf(spent, total) else null

    /** Display-only fraction 0..1. */
    val fraction: Float get() = if (hasBudget) Insights.fraction(spent, total) else 0f

    val allocatedLines: List<CategoryBudgetLine> get() = lines.filter { it.isAllocated }
    val unallocatedLines: List<CategoryBudgetLine> get() = lines.filterNot { it.isAllocated }
}

/** Spending compared with the month's progress: faster than the calendar, broadly in step, or slower. */
enum class PacePosition { AHEAD, ALIGNED, BEHIND }

/**
 * How fast the month's budget is being used. [elapsedPercent] is how far through the month [today] is (100 for a
 * finished month), so the bar can show consumption against time. Uses the same [BudgetPlan] as the hero card.
 */
data class BudgetBurn(
    val month: YearMonth,
    val level: BurnLevel,
    val health: BudgetHealth,
    val spent: Money,
    val total: Money?,
    /** Whole percent of the total consumed; null without a budget. */
    val consumedPercent: Int?,
    /** Display-only fraction 0..1 of the total consumed. */
    val consumedFraction: Float,
    val daysElapsed: Int,
    val daysInMonth: Int,
    /**
     * Days still to come, today included (0 for a finished month, the whole month for a future one). The single
     * source for every "days left" label and the [dailyAllowance] divisor, so the two can never disagree.
     */
    val daysLeft: Int,
    val elapsedPercent: Int,
    /** Average budgeted spending per elapsed day (rounded half-up to the minor unit). */
    val dailyAverage: Money,
    /** Even amount per remaining day (including today) that keeps the month within budget; null when nothing is left. */
    val dailyAllowance: Money?,
    /** Month-end budgeted spending if the current pace continues (labelled as a projection). */
    val projected: Money,
) {
    /** Spending is running ahead of the calendar by more than [BudgetPlanner.PACE_MARGIN_PERCENT] points. */
    val aheadOfPace: Boolean get() = consumedPercent != null && consumedPercent > elapsedPercent + BudgetPlanner.PACE_MARGIN_PERCENT

    /**
     * Where spending stands against the calendar: budget used vs month elapsed, with the same
     * [BudgetPlanner.PACE_MARGIN_PERCENT] tolerance either side. Null without a budget.
     */
    val pace: PacePosition?
        get() {
            val consumed = consumedPercent ?: return null
            return when {
                consumed > elapsedPercent + BudgetPlanner.PACE_MARGIN_PERCENT -> PacePosition.AHEAD
                consumed < elapsedPercent - BudgetPlanner.PACE_MARGIN_PERCENT -> PacePosition.BEHIND
                else -> PacePosition.ALIGNED
            }
        }
}

object BudgetPlanner {
    const val APPROACHING_PERCENT = 80
    const val LOW_REMAINING_PERCENT = 10
    const val PACE_MARGIN_PERCENT = 10

    /**
     * Exact thresholds on minor units:
     * - total ≤ 0 → [BudgetHealth.NOT_SET] (no allocation, or nothing to measure against)
     * - spent ≥ total → [BudgetHealth.EXCEEDED] (exhausted or over)
     * - remaining × 100 ≤ total × 10 → [BudgetHealth.LOW]
     * - spent × 100 ≥ total × 80 → [BudgetHealth.APPROACHING]
     * - otherwise [BudgetHealth.ON_TRACK]
     */
    fun health(total: Money, spent: Money): BudgetHealth {
        require(total.currencyCode == spent.currencyCode) { "Currency mismatch" }
        if (total.amountMinor <= 0L) return BudgetHealth.NOT_SET
        if (spent >= total) return BudgetHealth.EXCEEDED
        val t = BigInteger.valueOf(total.amountMinor)
        val s = BigInteger.valueOf(spent.amountMinor)
        val remaining = t.subtract(s)
        val hundred = BigInteger.valueOf(100)
        if (remaining.multiply(hundred) <= t.multiply(BigInteger.valueOf(LOW_REMAINING_PERCENT.toLong()))) return BudgetHealth.LOW
        if (s.multiply(hundred) >= t.multiply(BigInteger.valueOf(APPROACHING_PERCENT.toLong()))) return BudgetHealth.APPROACHING
        return BudgetHealth.ON_TRACK
    }

    /** Allocations that count: a category budget for an active expense category, with a positive amount. */
    fun activeAllocations(budgets: List<Budget>, categories: List<Category>): List<Budget> {
        val active = categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }.map { it.id }.toSet()
        return budgets
            .filter { b -> b.categoryId != null && b.categoryId in active && b.limit.amountMinor > 0L }
            .groupBy { it.categoryId to it.currencyCode }
            .map { (_, list) -> list.minBy { it.id } }
    }

    /**
     * Currencies a plan can be shown in, [preferred] first: every currency with an allocation, plus [preferred]
     * itself (so a user without allocations still sees an empty plan in their main currency).
     */
    fun currencies(budgets: List<Budget>, categories: List<Category>, preferred: String): List<String> =
        (listOf(preferred) + activeAllocations(budgets, categories).map { it.currencyCode }.sorted()).distinct()

    fun plan(
        categories: List<Category>,
        budgets: List<Budget>,
        transactions: List<Transaction>,
        currencyCode: String,
        month: YearMonth,
    ): BudgetPlan {
        val expenses = transactions.filter {
            it.type == TransactionType.EXPENSE && it.currencyCode == currencyCode && YearMonth.from(it.date) == month
        }
        val spentByCategory: Map<Long?, Money> = expenses.groupBy { it.categoryId }
            .mapValues { (_, list) -> Money.sum(currencyCode, list.map { it.amount }) }
        val zero = Money.zero(currencyCode)
        val allocations = activeAllocations(budgets, categories)
            .filter { it.currencyCode == currencyCode }
            .associateBy { it.categoryId!! }
        val active = categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }
        val order = active.withIndex().associate { (index, c) -> c.id to index }

        val lines = active.map { c -> CategoryBudgetLine(c, allocations[c.id], spentByCategory[c.id] ?: zero) }
            .sortedWith(
                compareBy<CategoryBudgetLine> { !it.isAllocated }
                    .thenByDescending { if (it.isAllocated) it.percentUsed ?: 0 else 0 }
                    .thenByDescending { if (it.isAllocated) 0L else it.spent.amountMinor }
                    .thenBy { order[it.category.id] ?: Int.MAX_VALUE },
            )
        val total = Money.sum(currencyCode, allocations.values.map { it.limit })
        val budgeted = Money.sum(currencyCode, lines.filter { it.isAllocated }.map { it.spent })
        val all = Money.sum(currencyCode, expenses.map { it.amount })
        return BudgetPlan(
            month = month,
            currencyCode = currencyCode,
            lines = lines,
            total = total,
            spent = all,
            budgetedSpending = budgeted,
            unbudgetedSpending = all - budgeted,
            uncategorizedSpending = spentByCategory[null] ?: zero,
            legacyOverall = budgets.firstOrNull { it.categoryId == null && it.currencyCode == currencyCode },
        )
    }

    fun burn(plan: BudgetPlan, today: LocalDate): BudgetBurn {
        val month = plan.month
        val current = YearMonth.from(today)
        val length = month.lengthOfMonth()
        val elapsed = when {
            month.isBefore(current) -> length
            month.isAfter(current) -> 0
            else -> today.dayOfMonth
        }
        val currency = plan.currencyCode
        val daily = if (elapsed > 0) CashflowCalculator.divide(plan.spent, elapsed.toLong()) else Money.zero(currency)
        val projected = if (elapsed > 0) {
            Money(
                BigDecimal.valueOf(plan.spent.amountMinor)
                    .multiply(BigDecimal.valueOf(length.toLong()))
                    .divide(BigDecimal.valueOf(elapsed.toLong()), 0, RoundingMode.HALF_UP)
                    .longValueExact(),
                currency,
            )
        } else {
            plan.spent
        }
        val daysLeft = length - elapsed + if (month == current) 1 else 0
        val allowance = if (plan.hasBudget && plan.remaining.amountMinor > 0L && daysLeft > 0) {
            CashflowCalculator.divide(plan.remaining, daysLeft.toLong(), RoundingMode.DOWN)
        } else {
            null
        }
        return BudgetBurn(
            month = month,
            level = BurnLevel.of(plan.health),
            health = plan.health,
            spent = plan.spent,
            total = plan.total.takeIf { plan.hasBudget },
            consumedPercent = plan.percentUsed,
            consumedFraction = plan.fraction,
            daysElapsed = elapsed,
            daysInMonth = length,
            daysLeft = daysLeft,
            elapsedPercent = (elapsed * 100) / length,
            dailyAverage = daily,
            dailyAllowance = allowance,
            projected = projected,
        )
    }
}
