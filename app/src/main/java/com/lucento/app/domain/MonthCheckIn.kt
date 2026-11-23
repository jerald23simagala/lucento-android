package com.lucento.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * The turn-of-month check-in (owner, 2026-11-23; docs/CALCULATIONS.md "Monthly check-in"): a card on Home from the last
 * [DAYS_BEFORE] days of a month to the [LAST_DAY]th of the next. It asks to plan the coming month (birthdays, events and
 * other costs that aren't the same every month) and, from the 1st, offers the month that ended as a recap. It shows only
 * while Lucento is open: it is never a notification. Pure, so it is tested on the JVM.
 */
object MonthCheckIn {
    /** The card appears this many days before a month ends (the last 3 days)… */
    const val DAYS_BEFORE = 3

    /** …and stays until this day of the new month. */
    const val LAST_DAY = 7

    /** Someone who set their budget up less than this many days ago isn't asked to plan again yet. */
    const val SETTLE_DAYS = 14L

    /**
     * What the card offers. [plan] is the month to plan (null when there is nothing to plan, or it is done); [recap] is
     * the finished month to look back on (null outside the 1st–7th, or when it was seen). At least one is set.
     */
    data class Card(val plan: YearMonth?, val recap: YearMonth?) {
        init {
            require(plan != null || recap != null) { "A check-in card offers something" }
        }
    }

    /** The month a check-in on [today] plans: next month in the last days of this one, this month in its first days. */
    fun planMonth(today: LocalDate): YearMonth? = when {
        today.dayOfMonth > today.lengthOfMonth() - DAYS_BEFORE -> YearMonth.from(today).plusMonths(1)
        today.dayOfMonth <= LAST_DAY -> YearMonth.from(today)
        else -> null
    }

    /** The finished month a check-in on [today] looks back on: last month, from the 1st to the [LAST_DAY]th. */
    fun recapMonth(today: LocalDate): YearMonth? =
        if (today.dayOfMonth <= LAST_DAY) YearMonth.from(today).minusMonths(1) else null

    /** Days left in [today]'s month after today: 0 on its last day. */
    fun daysLeft(today: LocalDate): Int = today.lengthOfMonth() - today.dayOfMonth

    /**
     * The card for [today], or null.
     * - [plannedThrough]: the latest month planned (or dismissed); a month up to it is never asked about again.
     * - [recapSeenThrough]: the latest month whose recap was seen (or dismissed).
     * - [canPlan]: Budgets is on, this phone may change the budget, and [budgetSettled] says it isn't brand new.
     * - [hasRecords]: whether a month has income or expenses recorded; an empty month has nothing to recap.
     */
    fun card(
        today: LocalDate,
        plannedThrough: YearMonth?,
        recapSeenThrough: YearMonth?,
        canPlan: Boolean,
        hasRecords: (YearMonth) -> Boolean,
    ): Card? {
        val plan = planMonth(today)?.takeIf { canPlan && (plannedThrough == null || it > plannedThrough) }
        val recap = recapMonth(today)?.takeIf { (recapSeenThrough == null || it > recapSeenThrough) && hasRecords(it) }
        return if (plan == null && recap == null) null else Card(plan, recap)
    }

    /**
     * Whether the budget is old enough to plan again: it has an amount in [today]'s month, and the first amount was set at
     * least [SETTLE_DAYS] days ago. Rows without a creation date predate that record and count as settled.
     */
    fun budgetSettled(budgets: List<Budget>, categories: List<Category>, today: LocalDate): Boolean {
        if (BudgetPlanner.activeAllocations(budgets, categories, YearMonth.from(today)).isEmpty()) return false
        val rows = budgets.filter { !it.isLegacyOverall }
        if (rows.any { it.createdOn == null }) return true
        val first = rows.mapNotNull { it.createdOn }.minOrNull() ?: return true
        return !first.isAfter(today.minusDays(SETTLE_DAYS))
    }

    /** Whether [month] has any income or expense recorded (transfers and adjustments aren't a month's story). */
    fun hasRecords(transactions: List<Transaction>, month: YearMonth): Boolean =
        transactions.any { it.type.isCashFlow && YearMonth.from(it.date) == month }
}
