package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Changes made to several scheduled plans at once from Scheduled › All schedules (tester round, 2026-11-18), and the
 * check for a card or loan that already has a payment plan. Pure functions: the repository applies them in one step.
 *
 * A new day moves each plan's next occurrence to that day in the same month (a weekly plan: the same Monday-to-Sunday
 * week), so no occurrence is gained or lost, and the plan restarts from there, as changing its first date in the editor
 * does (RecurringRepository.save resets the count when the timing changes). How often it repeats stays, every
 * transaction it already recorded stays, and a plan with an end date keeps the same number of dates still to come.
 */
object ScheduleBulk {

    /** Weekly plans move to a weekday; every other plan, a one-time one included, to a day of the month. */
    fun takesWeekday(schedule: RecurringSchedule): Boolean =
        schedule.frequency == Frequency.WEEKLY && !isOnce(schedule)

    /** A plan with one date and no repeats ("Once" in the editor). */
    fun isOnce(schedule: RecurringSchedule): Boolean = schedule.endDate == schedule.startDate

    /**
     * A monthly, yearly or one-time plan moved to [day] of the month, from its next occurrence. A month shorter than
     * [day] uses its last day, and later months still use [day] (the 31st stays the 31st after November’s 30th). Null
     * for a weekly or finished plan, and when none of its dates would change (it is already on that day).
     */
    fun onMonthDay(schedule: RecurringSchedule, day: Int): RecurringSchedule? {
        require(day in 1..31) { "A day of the month is 1 to 31" }
        if (takesWeekday(schedule)) return null
        val next = schedule.nextDue ?: return null
        val moved = next.withDayOfMonth(minOf(day, next.lengthOfMonth()))
        val result = if (isOnce(schedule)) {
            schedule.copy(startDate = moved, endDate = moved, nextIndex = 0)
        } else {
            val (anchor, index) = anchorFor(schedule, next, day, moved)
            restart(schedule, anchor, index)
        }
        return result?.takeIf { firstDifference(schedule, it) != null }
    }

    /**
     * A weekly plan moved to [weekday], from its next occurrence: the same week, as the phone’s calendar draws it from
     * [firstDayOfWeek] (QA, 2026-11-19: where weeks start on Sunday, a Sunday plan moved to Monday goes to the next day,
     * not six days back). Null for any other plan, a finished one, and when none of its dates would change.
     */
    fun onWeekday(schedule: RecurringSchedule, weekday: DayOfWeek, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): RecurringSchedule? {
        if (!takesWeekday(schedule)) return null
        val next = schedule.nextDue ?: return null
        val weekStart = next.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val moved = weekStart.plusDays(((weekday.value - firstDayOfWeek.value + 7) % 7).toLong())
        return restart(schedule, moved, 0)?.takeIf { firstDifference(schedule, it) != null }
    }

    /**
     * The first of the coming dates that differs between [before] and [after], as (was, becomes); either side is null
     * when that plan has no such date. Null when the coming dates are the same: the change changes nothing. A plan
     * already on the 30th moved to the 31st keeps November’s 30th, so its first difference is in December.
     */
    fun firstDifference(before: RecurringSchedule, after: RecurringSchedule): Pair<LocalDate?, LocalDate?>? {
        val was = coming(before)
        val becomes = coming(after)
        for (k in 0 until maxOf(was.size, becomes.size)) {
            val a = was.getOrNull(k)
            val b = becomes.getOrNull(k)
            if (a != b) return a to b
        }
        return null
    }

    /** The next [DATES_COMPARED] dates of [schedule], up to its end date. */
    private fun coming(schedule: RecurringSchedule): List<LocalDate> =
        (schedule.nextIndex until schedule.nextIndex + DATES_COMPARED)
            .asSequence()
            .map { RecurringMath.occurrence(schedule, it) }
            .takeWhile { it != null && (schedule.endDate == null || !it.isAfter(schedule.endDate)) }
            .map { it!! }
            .toList()

    /**
     * Where a plan moved to [day] restarts, as its first date and the index of the occurrence still waiting. Usually
     * [moved] itself at index 0. When [moved] had to use a short month’s last day, the plan is anchored on an earlier
     * occurrence month that has [day] (Oct 31 for a plan moving to the 31st in November), with that earlier occurrence
     * counted as handled: occurrence dates come from the first date, so later months keep [day]. When no month of the
     * cycle has [day], the latest day one has (Feb 29 for a yearly February plan moved to the 30th), so each month
     * still gets its last day.
     */
    private fun anchorFor(schedule: RecurringSchedule, next: LocalDate, day: Int, moved: LocalDate): Pair<LocalDate, Int> {
        if (moved.dayOfMonth == day || schedule.frequency == Frequency.WEEKLY) return moved to 0
        var best: Pair<LocalDate, Int>? = null
        for (back in 1..MAX_ANCHOR_STEPS) {
            val steps = back.toLong() * schedule.interval
            val month = if (schedule.frequency == Frequency.MONTHLY) next.minusMonths(steps) else next.minusYears(steps)
            val anchorDay = minOf(day, month.lengthOfMonth())
            if (anchorDay <= (best?.first?.dayOfMonth ?: moved.dayOfMonth)) continue
            val anchor = month.withDayOfMonth(anchorDay)
            // The anchor must give exactly the moved date as the occurrence still waiting.
            if (RecurringMath.occurrence(anchor, schedule.frequency, schedule.interval, back) != moved) continue
            best = anchor to back
            if (anchorDay == day) break
        }
        return best ?: (moved to 0)
    }

    /**
     * [schedule] restarting from [start] with occurrence [index] still waiting. A plan with an end date keeps the same
     * number of dates still to come: its end date stays when that number fits, and otherwise becomes the new last date
     * (so a loan's final payment is never dropped by a later day, nor an extra one added by an earlier day).
     */
    private fun restart(schedule: RecurringSchedule, start: LocalDate, index: Int): RecurringSchedule? {
        val moved = schedule.copy(startDate = start, nextIndex = index)
        val end = schedule.endDate ?: return moved
        val before = remaining(schedule, end) ?: return moved
        if (before == 0) return null
        if (remaining(moved, end) == before) return moved
        val last = RecurringMath.occurrence(moved, index + before - 1) ?: return null
        // One date left, and it is the first: an end date on it would equal the first date, which is how a one-time plan
        // is stored ("Once"). End the day before the date after it instead: the same single date (QA, 2026-11-19).
        if (before == 1 && index == 0 && !isOnce(schedule)) {
            val after = RecurringMath.occurrence(moved, 1) ?: return moved.copy(endDate = last)
            return moved.copy(endDate = after.minusDays(1))
        }
        return moved.copy(endDate = last)
    }

    /** How many occurrences of [schedule], from the one still waiting, fall on or before [end]; null past a sane count. */
    private fun remaining(schedule: RecurringSchedule, end: LocalDate): Int? {
        var count = 0
        var index = schedule.nextIndex
        while (count <= MAX_COUNTED) {
            val date = RecurringMath.occurrence(schedule, index) ?: return count
            if (date.isAfter(end)) return count
            count++
            index++
        }
        return null
    }

    /**
     * A plan with [amount] as its amount. A debt payment's is its whole payment, as the editor plans it (the principal
     * and interest split is entered at Mark paid), so any interest part set on an older plan is cleared. Null when
     * [amount] is in another currency, isn’t above zero or is already the plan’s payment, or when the plan has ended.
     */
    fun withAmount(schedule: RecurringSchedule, amount: Money): RecurringSchedule? {
        if (schedule.isFinished || amount.currencyCode != schedule.currencyCode || amount.amountMinor <= 0L) return null
        // Already that payment: nothing to change (an older plan’s split of the same total is left as it is).
        if (amount == schedule.total) return null
        return schedule.copy(amount = amount, interest = Money.zero(amount.currencyCode))
    }

    /** [schedule] paused or resumed; null when it has ended or is already that way, so nothing changes. */
    fun withPaused(schedule: RecurringSchedule, paused: Boolean): RecurringSchedule? =
        if (schedule.isFinished || schedule.paused == paused) null else schedule.copy(paused = paused)

    /** The one currency [schedules] share, so one new amount can apply to them all; null when they use more than one. */
    fun sharedCurrency(schedules: Collection<RecurringSchedule>): String? = schedules.map { it.currencyCode }.distinct().singleOrNull()

    /**
     * The plans already paying [debtWalletId] (a card or loan), or collecting from it (a receivable): any unfinished plan
     * whose money goes to or comes from it, paused ones included, since resuming one would remind twice. [except] is
     * the plan being edited.
     */
    fun paymentPlansFor(schedules: List<RecurringSchedule>, debtWalletId: Long, except: Long? = null): List<RecurringSchedule> =
        schedules.filter { it.id != except && it.toWalletId == debtWalletId && !it.isTransfer && !it.isFinished }

    /** Enough steps back to meet every month of a monthly cycle, and a leap year in a yearly one. */
    private const val MAX_ANCHOR_STEPS = 12

    /** Weekly for a century is about 5,200 dates; past this the end date is simply left as it is. */
    private const val MAX_COUNTED = 10_000

    /** Enough coming dates to see any change of day: two years of a monthly plan, every month of the year at least once. */
    private const val DATES_COMPARED = 24
}
