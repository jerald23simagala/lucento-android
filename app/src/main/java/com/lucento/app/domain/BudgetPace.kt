package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

/**
 * Budget pace with bills on their due dates (owner, 2026-11-26; proposal in docs/BUDGET_PACE_PROPOSAL.md, not wired
 * into Home or Budget yet).
 *
 * Today's pace compares spending with a straight line across the month, so a rent paid on the 2nd reads as "Ahead of
 * pace" for weeks while the person is exactly on plan. Here the line follows the month's scheduled bills: by the end of
 * a day, the plan expects every bill due so far, plus the rest of the budget spread evenly over the days. Without bills
 * it is the straight line, to the percent.
 */
object BudgetPace {
    /** One scheduled expense in the month, in the budget's currency (a schedule's occurrence, paid or not). */
    data class Bill(val due: LocalDate, val amount: Money)

    /**
     * Whole percent of [total] the plan expects spent once [elapsedDays] of [month] have passed (rounded down, 0–100).
     * Bills in another month or currency, and amounts of zero or less, are ignored. Bills that add up to more than the
     * budget are capped at it, so the expectation never passes 100 %.
     */
    fun expectedPercent(total: Money, bills: List<Bill>, month: YearMonth, elapsedDays: Int): Int {
        val length = month.lengthOfMonth()
        val days = elapsedDays.coerceIn(0, length)
        val t = BigInteger.valueOf(total.amountMinor)
        if (t.signum() <= 0) return (days * 100) / length
        val inMonth = bills.filter {
            YearMonth.from(it.due) == month && it.amount.currencyCode == total.currencyCode && it.amount.amountMinor > 0L
        }
        val allBills = inMonth.fold(BigInteger.ZERO) { sum, b -> sum.add(BigInteger.valueOf(b.amount.amountMinor)) }.min(t)
        val dueSoFar = inMonth.filter { it.due.dayOfMonth <= days }
            .fold(BigInteger.ZERO) { sum, b -> sum.add(BigInteger.valueOf(b.amount.amountMinor)) }.min(allBills)
        val flexible = t.subtract(allBills)
        // expected = dueSoFar + flexible × days ÷ length, then × 100 ÷ total, all in whole minor units and rounded down once.
        val expectedTimesLength = dueSoFar.multiply(BigInteger.valueOf(length.toLong())).add(flexible.multiply(BigInteger.valueOf(days.toLong())))
        val percent = expectedTimesLength.multiply(BigInteger.valueOf(100)).divide(t.multiply(BigInteger.valueOf(length.toLong())))
        return percent.toInt().coerceIn(0, 100)
    }

    /**
     * Where spending stands against [expectedPercent]: the same [BudgetPlanner.PACE_MARGIN_PERCENT] tolerance either side
     * as today's pace. Null without a budget ([consumedPercent] null).
     */
    fun position(consumedPercent: Int?, expectedPercent: Int): PacePosition? {
        val consumed = consumedPercent ?: return null
        return when {
            consumed > expectedPercent + BudgetPlanner.PACE_MARGIN_PERCENT -> PacePosition.AHEAD
            consumed < expectedPercent - BudgetPlanner.PACE_MARGIN_PERCENT -> PacePosition.BEHIND
            else -> PacePosition.ALIGNED
        }
    }
}
