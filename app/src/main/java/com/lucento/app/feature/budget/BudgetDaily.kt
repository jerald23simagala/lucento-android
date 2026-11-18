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
 * The Budget tab's per-day figure. For the current month it is "Safe per day" (docs/CALCULATIONS.md): safe to spend over
 * the days left including today, the same [SafeToSpend] as Home's. Since the owner's redesign (2026-11-18) the tab shows
 * it under Safe to spend, read from that [SafeToSpend]; these cases keep decision D1 pinned in tests. A finished month
 * shows what was spent per day on the hero instead.
 */
sealed interface DailyFigure {
    /**
     * This month, money left to spread: [perDay] for each of [daysLeft] days, with [committed] bills set aside.
     * [limitedByMoney]: the spending money, not the budget, set it (tester round, 2026-11-17).
     */
    data class SafePerDay(
        val perDay: Money,
        val daysLeft: Int,
        val committed: Money,
        val limitedByMoney: Boolean = false,
        val moneyEmpty: Boolean = false,
    ) : DailyFigure

    /** This month, nothing left once bills due are set aside: safe to spend itself ([safe] is zero or negative). */
    data class NothingLeft(val safe: Money, val committed: Money, val limitedByMoney: Boolean = false, val moneyEmpty: Boolean = false) : DailyFigure

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
): DailyFigure? = dailyFigure(plan, burn, today, budgetSafeToSpend(plan, today, ledger, budgets, schedules, categories))

/**
 * Safe to spend for [plan]'s month on the Budget tab, the same calculation and inputs as Home's: the translated
 * [ledger], with schedules from a valued foreign wallet at that wallet's rate (tester round, 2026-11-17). Null outside
 * the current month, and when there is no budget in this currency to measure against. The per-day figure and the
 * spending limits panel both read it, so they can't disagree.
 */
fun budgetSafeToSpend(
    plan: BudgetPlan,
    today: LocalDate,
    ledger: TranslatedLedger,
    budgets: List<Budget>,
    schedules: List<RecurringSchedule>,
    categories: List<Category>,
): SafeToSpend? {
    if (plan.month != YearMonth.from(today)) return null
    val valued = com.lucento.app.domain.fx.FxTranslation.valueSchedules(ledger, schedules)
    return CashflowCalculator.safeToSpend(plan.currencyCode, today, ledger.transactions, ledger.wallets, budgets, valued, categories)
        ?.takeIf { it.basis == SafeToSpend.Basis.BUDGET }
}

/** [plan]'s per-day figure from its [safe] to spend ([budgetSafeToSpend]); a finished month's daily average. */
fun dailyFigure(plan: BudgetPlan, burn: BudgetBurn, today: LocalDate, safe: SafeToSpend?): DailyFigure? {
    if (plan.month != YearMonth.from(today)) return DailyFigure.DailyAverage(burn.dailyAverage, burn.daysInMonth)
    safe ?: return null
    val perDay = safe.perDay
    val empty = com.lucento.app.feature.home.BudgetFormula.moneyEmpty(safe)
    return if (perDay != null) DailyFigure.SafePerDay(perDay, safe.daysLeft, safe.committed, safe.limitedByMoney, empty)
    else DailyFigure.NothingLeft(safe.amount, safe.committed, safe.limitedByMoney, empty)
}
