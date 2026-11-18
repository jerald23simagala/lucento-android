package com.lucento.app.feature.budget

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetBurn
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.CashflowCalculator
import com.lucento.app.domain.Category
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.SafeToSpend
import com.lucento.app.domain.fx.TranslatedLedger
import java.time.LocalDate
import java.time.YearMonth

/**
 * The Budget hero's per-day figure. For the current month it is "Safe per day" (docs/CALCULATIONS.md): safe to spend —
 * the budget left after the bills due by month end — over the days left including today, the same calculation as
 * Home's "Safe to spend". A finished month shows what was spent per day instead.
 */
sealed interface DailyFigure {
    /**
     * This month, money left to spread: [perDay] for each of [daysLeft] days, with [committed] bills set aside.
     * [limitedByMoney]: the spending money, not the budget, set it (tester round, 2026-11-17).
     */
    data class SafePerDay(val perDay: Money, val daysLeft: Int, val committed: Money, val limitedByMoney: Boolean = false) : DailyFigure

    /** This month, nothing left once bills due are set aside: safe to spend itself ([safe] is zero or negative). */
    data class NothingLeft(val safe: Money, val committed: Money, val limitedByMoney: Boolean = false) : DailyFigure

    /** A finished month: its average spending per day over [days] days. */
    data class DailyAverage(val perDay: Money, val days: Int) : DailyFigure
}

/**
 * Picks [plan]'s per-day figure. [ledger] is the translated ledger [plan] was built from (`FxTranslation.translate`), so
 * spending in other currencies counts at its own value here exactly as in the hero and on Home. It is a ledger rather
 * than two lists so the untranslated ones can't be passed by mistake: until 2026-11-16 they were, and "Safe per day"
 * left foreign-wallet spending out. [schedules] should already exclude switched-off modules (Recurring off → none), as
 * on Home. Null when the current month has no budget in this currency (the hero then shows its no-budget state).
 */
fun budgetDailyFigure(
    plan: BudgetPlan,
    burn: BudgetBurn,
    today: LocalDate,
    ledger: TranslatedLedger,
    budgets: List<Budget>,
    schedules: List<RecurringSchedule>,
    categories: List<Category>,
): DailyFigure? {
    if (plan.month != YearMonth.from(today)) return DailyFigure.DailyAverage(burn.dailyAverage, burn.daysInMonth)
    val safe = CashflowCalculator.safeToSpend(plan.currencyCode, today, ledger.transactions, ledger.wallets, budgets, schedules, categories)
        ?.takeIf { it.basis == SafeToSpend.Basis.BUDGET }
        ?: return null
    val perDay = safe.perDay
    return if (perDay != null) DailyFigure.SafePerDay(perDay, safe.daysLeft, safe.committed, safe.limitedByMoney)
    else DailyFigure.NothingLeft(safe.amount, safe.committed, safe.limitedByMoney)
}
