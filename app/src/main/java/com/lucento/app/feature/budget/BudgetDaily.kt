package com.lucento.app.feature.budget

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetBurn
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.CashflowCalculator
import com.lucento.app.domain.Category
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.SafeToSpend
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Wallet
import java.time.LocalDate
import java.time.YearMonth

/**
 * The Budget hero's per-day figure. For the current month it is "Safe per day" (docs/CALCULATIONS.md): safe to spend —
 * the budget left after the bills due by month end — over the days left including today, the same calculation as
 * Home's "Safe to spend". A finished month shows what was spent per day instead.
 */
sealed interface DailyFigure {
    /** This month, money left to spread: [perDay] for each of [daysLeft] days, with [committed] bills set aside. */
    data class SafePerDay(val perDay: Money, val daysLeft: Int, val committed: Money) : DailyFigure

    /** This month, nothing left once bills due are set aside: safe to spend itself ([safe] is zero or negative). */
    data class NothingLeft(val safe: Money, val committed: Money) : DailyFigure

    /** A finished month: its average spending per day over [days] days. */
    data class DailyAverage(val perDay: Money, val days: Int) : DailyFigure
}

/**
 * Picks [plan]'s per-day figure. [schedules] should already exclude switched-off modules (Recurring off → none), as on
 * Home. Null when the current month has no budget in this currency (the hero then shows its no-budget state).
 */
fun budgetDailyFigure(
    plan: BudgetPlan,
    burn: BudgetBurn,
    today: LocalDate,
    transactions: List<Transaction>,
    wallets: List<Wallet>,
    budgets: List<Budget>,
    schedules: List<RecurringSchedule>,
    categories: List<Category>,
): DailyFigure? {
    if (plan.month != YearMonth.from(today)) return DailyFigure.DailyAverage(burn.dailyAverage, burn.daysInMonth)
    val safe = CashflowCalculator.safeToSpend(plan.currencyCode, today, transactions, wallets, budgets, schedules, categories)
        ?.takeIf { it.basis == SafeToSpend.Basis.BUDGET }
        ?: return null
    val perDay = safe.perDay
    return if (perDay != null) DailyFigure.SafePerDay(perDay, safe.daysLeft, safe.committed)
    else DailyFigure.NothingLeft(safe.amount, safe.committed)
}
