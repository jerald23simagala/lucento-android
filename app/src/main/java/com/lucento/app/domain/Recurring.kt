package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.LocalDate

enum class Frequency { WEEKLY, MONTHLY, YEARLY }

/**
 * A planned, repeating income or expense (rent, salary, a subscription).
 *
 * Schedules are plans, not records: nothing is ever posted automatically. The user marks an occurrence
 * as paid (which records a real transaction, idempotently keyed by schedule and occurrence) or skips it.
 * [nextIndex] is the occurrence index still waiting; occurrence dates are always computed from
 * [startDate] so month-end anchors survive short months (Jan 31 → Feb 28 → Mar 31).
 */
data class RecurringSchedule(
    val id: Long,
    val name: String,
    val type: TransactionType,
    val amount: Money,
    val walletId: Long,
    val categoryId: Long?,
    val frequency: Frequency,
    val interval: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val nextIndex: Int,
    val paused: Boolean,
) {
    init {
        require(type == TransactionType.EXPENSE || type == TransactionType.INCOME) { "Schedules are income or expense" }
        require(interval >= 1) { "Interval must be at least 1" }
        require(nextIndex >= 0) { "Occurrence index cannot be negative" }
    }

    val currencyCode: String get() = amount.currencyCode

    /** Date of the occurrence still waiting, or null when the schedule has ended. */
    val nextDue: LocalDate? get() = RecurringMath.occurrence(this, nextIndex)?.takeIf { endDate == null || !it.isAfter(endDate) }

    val isFinished: Boolean get() = nextDue == null

    /** Stable idempotency key for the transaction recorded when occurrence [index] is paid. */
    fun occurrenceKey(index: Int): String = "recurring-$id-$index"
}

enum class RecurringState { UPCOMING, ACTIVE, PAUSED, FINISHED }

object RecurringMath {

    fun occurrence(schedule: RecurringSchedule, index: Int): LocalDate? =
        occurrence(schedule.startDate, schedule.frequency, schedule.interval, index)

    fun occurrence(start: LocalDate, frequency: Frequency, interval: Int, index: Int): LocalDate? {
        if (index < 0) return null
        val steps = index.toLong() * interval
        return when (frequency) {
            Frequency.WEEKLY -> start.plusWeeks(steps)
            Frequency.MONTHLY -> start.plusMonths(steps)
            Frequency.YEARLY -> start.plusYears(steps)
        }
    }

    /**
     * Unpaid occurrences of [schedule] due on or before [through] (including overdue ones), oldest first.
     * Paused or finished schedules have none. Bounded by [limit] as a safety net.
     */
    fun dueThrough(schedule: RecurringSchedule, through: LocalDate, limit: Int = 400): List<Pair<Int, LocalDate>> {
        if (schedule.paused) return emptyList()
        val result = ArrayList<Pair<Int, LocalDate>>()
        var index = schedule.nextIndex
        while (result.size < limit) {
            val date = occurrence(schedule, index) ?: break
            if (date.isAfter(through)) break
            if (schedule.endDate != null && date.isAfter(schedule.endDate)) break
            result.add(index to date)
            index++
        }
        return result
    }

    /**
     * Total still to pay for active expense schedules in [currencyCode], for occurrences due on or before
     * [through] (overdue ones included, because they are still owed).
     */
    fun committedExpenses(schedules: List<RecurringSchedule>, currencyCode: String, through: LocalDate): Money =
        Money.sum(
            currencyCode,
            schedules
                .filter { it.type == TransactionType.EXPENSE && it.currencyCode == currencyCode }
                .flatMap { s -> dueThrough(s, through).map { s.amount } },
        )

    /** Next unpaid occurrence of each active schedule due on or before [through] (overdue included), soonest first. */
    fun upcoming(schedules: List<RecurringSchedule>, through: LocalDate): List<Pair<RecurringSchedule, LocalDate>> =
        schedules
            .filter { !it.paused }
            .mapNotNull { s -> s.nextDue?.let { s to it } }
            .filter { (_, date) -> !date.isAfter(through) }
            .sortedWith(compareBy<Pair<RecurringSchedule, LocalDate>> { it.second }.thenBy { it.first.id })

    fun state(schedule: RecurringSchedule, today: LocalDate): RecurringState = when {
        schedule.isFinished -> RecurringState.FINISHED
        schedule.paused -> RecurringState.PAUSED
        schedule.nextDue?.let { !it.isAfter(today.plusDays(UPCOMING_WINDOW_DAYS)) } == true -> RecurringState.UPCOMING
        else -> RecurringState.ACTIVE
    }

    /** Schedules due within this many days (or overdue) are shown as upcoming. */
    const val UPCOMING_WINDOW_DAYS = 14L
}
