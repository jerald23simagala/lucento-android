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
    /**
     * Set for a debt payment: the card or loan being paid from [walletId]. [amount] is then the part that goes against
     * the debt and [interest] the part that is interest (both entered by the user). Marking it paid records the
     * interest as an expense on the debt (category [categoryId]) and a transfer of [total] from [walletId] to it.
     */
    val toWalletId: Long? = null,
    val interest: Money = Money.zero(amount.currencyCode),
    /** The schedule's stable id across devices (v9); null only for a row that predates it in a test. */
    val clientUuid: String? = null,
) {
    init {
        require(type == TransactionType.EXPENSE || type == TransactionType.INCOME) { "Schedules are income or expense" }
        require(toWalletId == null || type == TransactionType.EXPENSE) { "Debt payments are expenses" }
        require(!interest.isNegative && interest.currencyCode == amount.currencyCode) { "Interest must be a non-negative amount in the same currency" }
        require(interval >= 1) { "Interval must be at least 1" }
        require(nextIndex >= 0) { "Occurrence index cannot be negative" }
    }

    val currencyCode: String get() = amount.currencyCode

    val isDebtPayment: Boolean get() = toWalletId != null

    /** Everything that leaves [walletId] for one occurrence: the amount, plus interest for a debt payment. */
    val total: Money get() = amount + interest

    /** Date of the occurrence still waiting, or null when the schedule has ended. */
    val nextDue: LocalDate? get() = RecurringMath.occurrence(this, nextIndex)?.takeIf { endDate == null || !it.isAfter(endDate) }

    val isFinished: Boolean get() = nextDue == null

    /**
     * Stable idempotency key for the transaction recorded when occurrence [index] is paid: the schedule id plus
     * the occurrence's *due date*. Keying by date (not by index) matters because editing a schedule's timing
     * restarts the index at 0 — an index key would then match an occurrence paid under the old timing, and the
     * new payment would be silently swallowed as a "duplicate". Keys written by v0.3 (`recurring-<id>-<index>`)
     * can never collide with this format.
     */
    fun occurrenceKey(index: Int): String {
        // With cloud sync the stable id is what both devices share; a local row id would let each record it once.
        val key = clientUuid ?: id.toString()
        val due = RecurringMath.occurrence(this, index) ?: return "recurring-$key-$index"
        return "recurring-$key-on-${due.toEpochDay()}"
    }
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
     * [through] (overdue ones included, because they are still owed). A debt payment counts in full (it all leaves
     * the wallet) unless [spendingOnly], when only its interest — the part that is spending — counts.
     */
    fun committedExpenses(
        schedules: List<RecurringSchedule>,
        currencyCode: String,
        through: LocalDate,
        spendingOnly: Boolean = false,
    ): Money =
        Money.sum(
            currencyCode,
            schedules
                .filter { it.type == TransactionType.EXPENSE && it.currencyCode == currencyCode }
                .flatMap { s ->
                    val each = if (spendingOnly && s.isDebtPayment) s.interest else s.total
                    dueThrough(s, through).map { each }
                },
        )

    /** Next unpaid occurrence of each active schedule due on or before [through] (overdue included), soonest first. */
    fun upcoming(schedules: List<RecurringSchedule>, through: LocalDate): List<Pair<RecurringSchedule, LocalDate>> =
        schedules
            .filter { !it.paused }
            .mapNotNull { s -> s.nextDue?.let { s to it } }
            .filter { (_, date) -> !date.isAfter(through) }
            .sortedWith(compareBy<Pair<RecurringSchedule, LocalDate>> { it.second }.thenBy { it.first.id })

    /**
     * The dashboard list: the next unpaid occurrence of each active schedule due on or before
     * [today] + [horizonDays] (overdue ones included and flagged), in date order, at most [limit]. Pure: listing a
     * payment never records it — only the user's "Mark paid" does.
     */
    fun upcomingPayments(
        schedules: List<RecurringSchedule>,
        today: LocalDate,
        horizonDays: Long = DASHBOARD_HORIZON_DAYS,
        limit: Int = DASHBOARD_LIMIT,
    ): List<UpcomingPayment> =
        upcoming(schedules, today.plusDays(horizonDays))
            .take(limit)
            .map { (s, date) -> UpcomingPayment(s, date, overdue = date.isBefore(today)) }

    fun state(schedule: RecurringSchedule, today: LocalDate): RecurringState = when {
        schedule.isFinished -> RecurringState.FINISHED
        schedule.paused -> RecurringState.PAUSED
        schedule.nextDue?.let { !it.isAfter(today.plusDays(UPCOMING_WINDOW_DAYS)) } == true -> RecurringState.UPCOMING
        else -> RecurringState.ACTIVE
    }

    /** Schedules due within this many days (or overdue) are shown as upcoming. */
    const val UPCOMING_WINDOW_DAYS = 14L

    const val DASHBOARD_HORIZON_DAYS = 30L
    const val DASHBOARD_LIMIT = 4

    /** What the Home bell flags: every overdue item plus everything due within [DUE_SOON_DAYS], soonest first. */
    fun dueSoon(schedules: List<RecurringSchedule>, today: LocalDate): List<UpcomingPayment> =
        upcomingPayments(schedules, today, horizonDays = DUE_SOON_DAYS, limit = Int.MAX_VALUE)

    const val DUE_SOON_DAYS = 7L
}

/** A scheduled payment (or expected income) waiting for the user's confirmation. */
data class UpcomingPayment(val schedule: RecurringSchedule, val dueDate: LocalDate, val overdue: Boolean)
