package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * Something worth a look while planning a month (docs/CALCULATIONS.md "Monthly check-in"). Facts from the person's own
 * records, never guesses: a scheduled cost that doesn't come every month, what the same month cost last year, and what
 * went over last month. Each names the category it is about (when it has one), so a tap can open that amount.
 */
sealed interface PlanHint {
    val categoryId: Long?

    /** A scheduled expense that isn't monthly (yearly, every few months) falls due in the month being planned. */
    data class DueInMonth(
        override val categoryId: Long?,
        val name: String,
        val amount: Money,
        val date: LocalDate,
        val frequency: Frequency,
        val interval: Int,
    ) : PlanHint

    /** The same month a year ago: spending in a category was above what this month plans for it ([planned] null: nothing). */
    data class LastYear(override val categoryId: Long, val spent: Money, val month: YearMonth, val planned: Money?) : PlanHint

    /**
     * A category went over its budget in [month] by [over], having spent [spent] there; [finished] false when that month
     * is still running.
     */
    data class WentOver(
        override val categoryId: Long,
        val over: Money,
        val spent: Money,
        val month: YearMonth,
        val finished: Boolean,
    ) : PlanHint
}

object MonthPlanHints {
    /** At most this many hints in all, and [EACH] of each kind, so one kind never crowds out the others. */
    const val MAX = 4
    const val EACH = 2

    /**
     * Last year's spending is worth naming when it was at least this many percent above the plan (or there is no plan
     * for the category), so ordinary month-to-month wobbles don't fill the list.
     */
    const val LAST_YEAR_MARGIN_PERCENT = 10L

    /**
     * Hints for planning [planMonth] in [currencyCode], most concrete first: scheduled costs due that month, then the
     * same month last year, then last month's overspending. [transactions] are the translated ledger's, as the Budget tab
     * reads them; [schedules] already exclude a switched-off Recurring module (then there are none).
     */
    fun hints(
        planMonth: YearMonth,
        today: LocalDate,
        categories: List<Category>,
        budgets: List<Budget>,
        transactions: List<Transaction>,
        schedules: List<RecurringSchedule>,
        currencyCode: String,
        max: Int = MAX,
    ): List<PlanHint> = (due(planMonth, schedules, currencyCode) +
        lastYear(planMonth, categories, budgets, transactions, currencyCode) +
        wentOver(planMonth, today, categories, budgets, transactions, currencyCode)).take(max)

    /** Expense schedules that don't come every month, with an occurrence in [month], soonest first. */
    fun due(month: YearMonth, schedules: List<RecurringSchedule>, currencyCode: String): List<PlanHint.DueInMonth> =
        schedules
            .filter { s ->
                s.type == TransactionType.EXPENSE && !s.isLinked && !s.paused && s.currencyCode == currencyCode &&
                    s.frequency != Frequency.WEEKLY && !(s.frequency == Frequency.MONTHLY && s.interval == 1)
            }
            .flatMap { s ->
                RecurringMath.dueThrough(s, month.atEndOfMonth())
                    .map { it.second }
                    .filter { YearMonth.from(it) == month }
                    .map { date -> PlanHint.DueInMonth(s.categoryId, s.name, s.amount, date, s.frequency, s.interval) }
            }
            .sortedWith(compareBy<PlanHint.DueInMonth> { it.date }.thenBy { it.name })
            .take(EACH)

    /**
     * Categories whose spending in the same month last year was clearly above their usual amount in [month] (an amount
     * set for that month alone doesn't count, so the hint stays, marked as acted on, once the person has set one).
     */
    fun lastYear(
        month: YearMonth,
        categories: List<Category>,
        budgets: List<Budget>,
        transactions: List<Transaction>,
        currencyCode: String,
    ): List<PlanHint.LastYear> {
        val then = month.minusYears(1)
        val spentThen = transactions
            .filter { it.type == TransactionType.EXPENSE && !it.fxResult && it.currencyCode == currencyCode && YearMonth.from(it.date) == then }
            .groupBy { it.categoryId }
        if (spentThen.isEmpty()) return emptyList()
        val active = categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }
        val usual: (Long) -> Money? = { id ->
            BudgetPlanner.recurringAllocation(budgets.filter { it.categoryId == id && it.currencyCode == currencyCode }, month)
                ?.limit?.takeIf { it.amountMinor > 0L }
        }
        return active.mapNotNull { category ->
            val spent = Money.sum(currencyCode, spentThen[category.id].orEmpty().map { it.amount })
            val plan = usual(category.id)
            val notable = when {
                spent.isZero -> false
                plan == null -> true
                // At least LAST_YEAR_MARGIN_PERCENT above the plan, in exact minor units.
                else -> Math.multiplyExact(Math.subtractExact(spent.amountMinor, plan.amountMinor), 100L) >=
                    Math.multiplyExact(plan.amountMinor, LAST_YEAR_MARGIN_PERCENT)
            }
            if (notable) PlanHint.LastYear(category.id, spent, then, plan) else null
        }
            .sortedWith(
                compareByDescending<PlanHint.LastYear> { Math.subtractExact(it.spent.amountMinor, it.planned?.amountMinor ?: 0L) }
                    .thenBy { it.categoryId },
            )
            .take(EACH)
    }

    /** The month before [month]: its budgeted categories that went over (so far, if it is still running), most first. */
    fun wentOver(
        month: YearMonth,
        today: LocalDate,
        categories: List<Category>,
        budgets: List<Budget>,
        transactions: List<Transaction>,
        currencyCode: String,
    ): List<PlanHint.WentOver> {
        val before = month.minusMonths(1)
        if (before > YearMonth.from(today)) return emptyList()
        val plan = BudgetPlanner.plan(categories, budgets, transactions, currencyCode, before)
        return plan.allocatedLines
            .mapNotNull { line ->
                line.remaining?.takeIf { it.isNegative }?.let {
                    PlanHint.WentOver(line.category.id, over = -it, spent = line.spent, month = before, finished = before < YearMonth.from(today))
                }
            }
            .sortedWith(compareByDescending<PlanHint.WentOver> { it.over.amountMinor }.thenBy { it.categoryId })
            .take(EACH)
    }
}
