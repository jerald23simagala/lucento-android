package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Budget pace with bills on their due dates (owner, 2026-11-26; docs/CALCULATIONS.md "Budget pace",
 * docs/BUDGET_PACE_PROPOSAL.md).
 *
 * The old pace compared spending with a straight line across the month, so a rent paid on the 2nd read as "Ahead of
 * pace" for weeks while the person was exactly on plan. Here every scheduled bill counts on its due date, paid or not:
 * - the plan expects, by today, the month's bills due so far plus the rest of the budget spread evenly;
 * - spending for pace is what was spent plus every bill already due and still owed (this month's or an earlier one's),
 *   so a late payment, or one not made yet, can never make the month look fine.
 * Without scheduled bills both are exactly the old straight line. Only the pace chip reads this; the bar, its "days
 * elapsed" tick, health, Safe to spend and the daily allowance are unchanged.
 */
object BudgetPace {
    /**
     * One occurrence of a scheduled bill, in the bill's currency. [owed] is true while it is unpaid (on or after the
     * schedule's next index); a paid or skipped one is settled. A skip isn't recorded apart from a payment, so a skipped
     * bill still counts as planned for its day: that errs toward "On track", never toward a false warning.
     */
    data class Bill(val due: LocalDate, val amount: Money, val owed: Boolean)

    /** How far back from the next index to look for this month's settled bills (a weekly bill has five a month). */
    private const val SETTLED_LOOKBACK = 62
    private const val MAX_STEPS = 2_000

    /**
     * The bills that matter to [month]'s pace on [today]: every occurrence of an active bill schedule dated in [month]
     * (settled or owed), plus every earlier occurrence still owed. Only bills count (`ScheduledFlow.EXPENSE`): income,
     * debt payments, collections and transfers aren't spending. Paused or finished plans count nothing.
     */
    fun bills(schedules: List<RecurringSchedule>, month: YearMonth, today: LocalDate): List<Bill> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        return schedules.filter { it.flow == ScheduledFlow.EXPENSE && !it.paused }.flatMap { schedule ->
            val out = ArrayList<Bill>()
            var index = maxOf(0, schedule.nextIndex - SETTLED_LOOKBACK)
            var steps = 0
            while (steps++ < MAX_STEPS) {
                val date = RecurringMath.occurrence(schedule, index) ?: break
                if (date.isAfter(last) || (schedule.endDate != null && date.isAfter(schedule.endDate))) break
                val owed = index >= schedule.nextIndex
                val inMonth = !date.isBefore(first)
                // This month's bills, settled or not; an earlier one only while it's still owed (and already due).
                if (inMonth || (owed && !date.isAfter(today))) out += Bill(date, schedule.total, owed)
                index++
                if (index < 0) break
            }
            out
        }
    }

    /**
     * Whole percent of [total] the plan expects spent once [elapsedDays] of [month] have passed (rounded down, 0–100):
     * the month's bills due by then, paid or not, plus the rest of the budget spread evenly. Bills in another month or
     * currency, and amounts of zero or less, are ignored; bills above the budget are capped at it.
     */
    fun expectedPercent(total: Money, bills: List<Bill>, month: YearMonth, elapsedDays: Int): Int {
        val length = month.lengthOfMonth()
        val days = elapsedDays.coerceIn(0, length)
        val t = BigInteger.valueOf(total.amountMinor)
        if (t.signum() <= 0) return (days * 100) / length
        val inMonth = bills.filter { YearMonth.from(it.due) == month && it.counts(total.currencyCode) }
        val allBills = inMonth.sum().min(t)
        val dueSoFar = inMonth.filter { it.due.dayOfMonth <= days }.sum().min(allBills)
        val flexible = t.subtract(allBills)
        // expected = dueSoFar + flexible × days ÷ length, then × 100 ÷ total, in whole minor units, rounded down once.
        val expectedTimesLength = dueSoFar.multiply(BigInteger.valueOf(length.toLong())).add(flexible.multiply(BigInteger.valueOf(days.toLong())))
        val percent = expectedTimesLength.multiply(BigInteger.valueOf(100)).divide(t.multiply(BigInteger.valueOf(length.toLong())))
        return percent.toInt().coerceIn(0, 100)
    }

    /**
     * Whole percent of [total] spent for pace (rounded half up, as the budget's own percentage): [spent] plus every bill
     * due by [today] and still owed, whichever month it belongs to. Null without a budget.
     */
    fun consumedPercent(spent: Money, total: Money, bills: List<Bill>, today: LocalDate): Int? {
        if (total.amountMinor <= 0L) return null
        val owed = bills.filter { it.owed && !it.due.isAfter(today) && it.counts(total.currencyCode) }.sum()
        val forPace = BigInteger.valueOf(spent.amountMinor).add(owed)
        return BigDecimal(forPace).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(total.amountMinor), 0, RoundingMode.HALF_UP)
            .toInt()
    }

    /**
     * Where spending stands against [expectedPercent]: the same [BudgetPlanner.PACE_MARGIN_PERCENT] tolerance either side
     * as before. Null without a budget ([consumedPercent] null).
     */
    fun position(consumedPercent: Int?, expectedPercent: Int): PacePosition? {
        val consumed = consumedPercent ?: return null
        return when {
            consumed > expectedPercent + BudgetPlanner.PACE_MARGIN_PERCENT -> PacePosition.AHEAD
            consumed < expectedPercent - BudgetPlanner.PACE_MARGIN_PERCENT -> PacePosition.BEHIND
            else -> PacePosition.ALIGNED
        }
    }

    private fun Bill.counts(currency: String) = amount.currencyCode == currency && amount.amountMinor > 0L

    private fun List<Bill>.sum(): BigInteger = fold(BigInteger.ZERO) { s, b -> s.add(BigInteger.valueOf(b.amount.amountMinor)) }
}
