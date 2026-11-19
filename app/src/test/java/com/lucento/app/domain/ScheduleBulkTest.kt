package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Scheduled › All schedules, Select mode (tester round, 2026-11-18): the rules behind Change day, Change amount and
 * Pause, and the check behind the editor's warning about a second payment plan for the same card or loan.
 */
class ScheduleBulkTest {

    private fun d(text: String) = LocalDate.parse(text)

    private fun plan(
        id: Long = 1,
        start: String = "2026-01-15",
        frequency: Frequency = Frequency.MONTHLY,
        interval: Int = 1,
        next: Int = 0,
        end: String? = null,
        paused: Boolean = false,
        amount: Long = 150_000,
        interest: Long = 0,
        toWalletId: Long? = null,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "PHP",
    ) = RecurringSchedule(
        id = id, name = "Plan $id", type = type, amount = Money(amount, currency), walletId = 10, categoryId = 20,
        frequency = frequency, interval = interval, startDate = d(start), endDate = end?.let(::d), nextIndex = next,
        paused = paused, toWalletId = toWalletId, interest = Money(interest, currency), clientUuid = "uuid-$id",
    )

    /** The next [count] dates still waiting. */
    private fun RecurringSchedule.dates(count: Int): List<LocalDate> =
        (nextIndex until nextIndex + count).map { RecurringMath.occurrence(this, it)!! }

    /** How many dates are still waiting before the end date. */
    private fun RecurringSchedule.left(): Int = generateSequence(nextIndex) { it + 1 }
        .map { RecurringMath.occurrence(this, it)!! }
        .takeWhile { !it.isAfter(endDate!!) }
        .count()

    // ---------------------------------------------------------------- Change day

    @Test fun `a monthly plan moves its next date within the same month and keeps how often it repeats`() {
        val rent = plan(next = 10) // started Jan 15; Nov 15 is waiting
        assertEquals(d("2026-11-15"), rent.nextDue)

        val later = ScheduleBulk.onMonthDay(rent, 25)!!
        assertEquals(listOf(d("2026-11-25"), d("2026-12-25"), d("2027-01-25")), later.dates(3))
        assertEquals(0, later.nextIndex)
        assertEquals(Frequency.MONTHLY, later.frequency)
        assertEquals(1, later.interval)

        // An earlier day stays in November, so the bill still waiting is never skipped (it may now be overdue).
        val earlier = ScheduleBulk.onMonthDay(rent, 5)!!
        assertEquals(listOf(d("2026-11-05"), d("2026-12-05")), earlier.dates(2))
    }

    @Test fun `the 31st survives short months`() {
        val moved = ScheduleBulk.onMonthDay(plan(next = 10), 31)!!
        assertEquals(
            listOf(d("2026-11-30"), d("2026-12-31"), d("2027-01-31"), d("2027-02-28"), d("2027-03-31")),
            moved.dates(5),
        )
        assertEquals(d("2026-11-30"), moved.nextDue)
    }

    @Test fun `every other month keeps the 31st too`() {
        // Every 2 months from Jan 15: Nov 15 waiting. September has 30 days, so the anchor is July 31.
        val moved = ScheduleBulk.onMonthDay(plan(interval = 2, next = 5), 31)!!
        assertEquals(listOf(d("2026-11-30"), d("2027-01-31"), d("2027-03-31")), moved.dates(3))
    }

    @Test fun `a yearly February plan keeps the leap day`() {
        val dues = plan(start = "2026-02-10", frequency = Frequency.YEARLY, next = 1) // Feb 10, 2027 waiting
        val leap = ScheduleBulk.onMonthDay(dues, 29)!!
        assertEquals(listOf(d("2027-02-28"), d("2028-02-29"), d("2029-02-28")), leap.dates(3))
        // No February has a 30th: each year gets its last day.
        val last = ScheduleBulk.onMonthDay(dues, 30)!!
        assertEquals(listOf(d("2027-02-28"), d("2028-02-29")), last.dates(2))
    }

    @Test fun `a weekly plan moves within its week, and only takes a weekday`() {
        val gym = plan(start = "2026-11-04", frequency = Frequency.WEEKLY, next = 2) // Wednesday Nov 18 waiting
        assertTrue(ScheduleBulk.takesWeekday(gym))

        val monday = ScheduleBulk.onWeekday(gym, DayOfWeek.MONDAY)!!
        assertEquals(listOf(d("2026-11-16"), d("2026-11-23")), monday.dates(2))
        assertEquals(listOf(d("2026-11-20"), d("2026-11-27")), ScheduleBulk.onWeekday(gym, DayOfWeek.FRIDAY)!!.dates(2))

        assertNull(ScheduleBulk.onMonthDay(gym, 5))
        assertNull(ScheduleBulk.onWeekday(plan(next = 10), DayOfWeek.MONDAY))
    }

    @Test fun `a one-time plan stays one-time, whatever repeat it was saved with`() {
        val once = plan(start = "2026-11-20", end = "2026-11-20")
        val moved = ScheduleBulk.onMonthDay(once, 5)!!
        assertEquals(d("2026-11-05"), moved.startDate)
        assertEquals(d("2026-11-05"), moved.endDate)
        assertTrue(ScheduleBulk.isOnce(moved))

        val savedWeekly = plan(start = "2026-11-20", end = "2026-11-20", frequency = Frequency.WEEKLY)
        assertFalse(ScheduleBulk.takesWeekday(savedWeekly))
        assertEquals(d("2026-11-30"), ScheduleBulk.onMonthDay(savedWeekly, 31)!!.nextDue)
        assertNull(ScheduleBulk.onWeekday(savedWeekly, DayOfWeek.MONDAY))
    }

    @Test fun `a plan with an end date keeps the same number of dates left`() {
        val loan = plan(end = "2026-12-15", next = 10) // Nov 15 and Dec 15 left
        assertEquals(2, loan.left())

        // A later day would push the last payment past the end: the end date follows it.
        val later = ScheduleBulk.onMonthDay(loan, 20)!!
        assertEquals(d("2026-12-20"), later.endDate)
        assertEquals(2, later.left())

        // An earlier day still fits: the end date stays.
        val earlier = ScheduleBulk.onMonthDay(loan, 10)!!
        assertEquals(d("2026-12-15"), earlier.endDate)
        assertEquals(2, earlier.left())
    }

    @Test fun `an earlier weekday never adds a date before the end`() {
        val plan = plan(start = "2026-11-06", frequency = Frequency.WEEKLY, next = 3, end = "2026-12-31") // Fri Nov 27 waiting
        assertEquals(5, plan.left())
        val monday = ScheduleBulk.onWeekday(plan, DayOfWeek.MONDAY)!!
        assertEquals(5, monday.left())
        assertEquals(d("2026-12-21"), monday.endDate)
    }

    @Test fun `the last date moved past the end keeps a repeating plan repeating`() {
        // Weekly until Thursday Dec 31; Monday Dec 28 is the last date left.
        val plan = plan(start = "2026-11-02", frequency = Frequency.WEEKLY, next = 8, end = "2026-12-31")
        assertEquals(d("2026-12-28"), plan.nextDue)
        assertEquals(1, plan.left())

        val sunday = ScheduleBulk.onWeekday(plan, DayOfWeek.SUNDAY)!! // weeks from Monday: Sunday Jan 3
        assertEquals(d("2027-01-03"), sunday.nextDue)
        assertEquals(1, sunday.left())
        // Not stored as a one-time plan (first date = end date): still weekly, still offered weekdays.
        assertFalse(ScheduleBulk.isOnce(sunday))
        assertTrue(ScheduleBulk.takesWeekday(sunday))
    }

    @Test fun `a weekday move stays in the week the phone shows`() {
        val sundayPlan = plan(start = "2026-11-01", frequency = Frequency.WEEKLY) // Sunday Nov 1
        // Weeks from Sunday (as in the Philippines and the US): Monday is the next day.
        assertEquals(d("2026-11-02"), ScheduleBulk.onWeekday(sundayPlan, DayOfWeek.MONDAY, DayOfWeek.SUNDAY)!!.nextDue)
        // Weeks from Monday: that week's Monday, six days before.
        assertEquals(d("2026-10-26"), ScheduleBulk.onWeekday(sundayPlan, DayOfWeek.MONDAY, DayOfWeek.MONDAY)!!.nextDue)
    }

    @Test fun `the moved date never reuses a paid occurrence`() {
        // Oct 15 was paid; Nov 15 is waiting. The new waiting date gets its own key.
        val paidOnce = plan(start = "2026-10-15", next = 1)
        val paidKey = paidOnce.occurrenceKey(0)
        listOf(10, 31).forEach { day ->
            val moved = ScheduleBulk.onMonthDay(paidOnce, day)!!
            assertNotEquals(paidKey, moved.occurrenceKey(moved.nextIndex))
            assertEquals(day.coerceAtMost(30), moved.nextDue!!.dayOfMonth)
        }
    }

    @Test fun `the day a plan is already on changes nothing`() {
        assertNull(ScheduleBulk.onMonthDay(plan(next = 10), 15))
        val gym = plan(start = "2026-11-04", frequency = Frequency.WEEKLY, next = 2)
        assertNull(ScheduleBulk.onWeekday(gym, DayOfWeek.WEDNESDAY))
        assertNull(ScheduleBulk.onMonthDay(plan(start = "2026-11-20", end = "2026-11-20"), 20))
    }

    @Test fun `a plan on the 30th moved to the 31st changes from December`() {
        val rent = plan(start = "2026-11-30")
        val moved = ScheduleBulk.onMonthDay(rent, 31)!!
        assertEquals(d("2026-11-30"), moved.nextDue)
        assertEquals(d("2026-12-30") to d("2026-12-31"), ScheduleBulk.firstDifference(rent, moved))
        assertEquals(listOf(d("2026-11-30"), d("2026-12-31"), d("2027-01-31")), moved.dates(3))
    }

    @Test fun `the first difference is the next date when that moves`() {
        val rent = plan(next = 10)
        val moved = ScheduleBulk.onMonthDay(rent, 5)!!
        assertEquals(d("2026-11-15") to d("2026-11-05"), ScheduleBulk.firstDifference(rent, moved))
    }

    @Test fun `an ended plan takes no change`() {
        val ended = plan(end = "2026-03-15", next = 3)
        assertTrue(ended.isFinished)
        assertNull(ScheduleBulk.onMonthDay(ended, 5))
        assertNull(ScheduleBulk.withAmount(ended, Money(1_000, "PHP")))
        assertNull(ScheduleBulk.withPaused(ended, true))
    }

    // ---------------------------------------------------------------- Change amount, Pause

    @Test fun `a new amount is the whole payment, so an old interest part is cleared`() {
        val loan = plan(amount = 18_446, interest = 5_208, toWalletId = 99)
        val changed = ScheduleBulk.withAmount(loan, Money(25_000, "PHP"))!!
        assertEquals(Money(25_000, "PHP"), changed.amount)
        assertTrue(changed.interest.isZero)
        assertEquals(Money(25_000, "PHP"), changed.total)

        assertNull(ScheduleBulk.withAmount(loan, Money(25_000, "USD")))
        assertNull(ScheduleBulk.withAmount(loan, Money(0, "PHP")))
        // Already the whole payment: nothing to change.
        assertNull(ScheduleBulk.withAmount(loan, Money(23_654, "PHP")))
    }

    @Test fun `pausing skips plans already paused`() {
        assertTrue(ScheduleBulk.withPaused(plan(), true)!!.paused)
        assertNull(ScheduleBulk.withPaused(plan(paused = true), true))
        assertFalse(ScheduleBulk.withPaused(plan(paused = true), false)!!.paused)
    }

    @Test fun `one amount needs one currency`() {
        assertEquals("PHP", ScheduleBulk.sharedCurrency(listOf(plan(id = 1), plan(id = 2))))
        assertNull(ScheduleBulk.sharedCurrency(listOf(plan(id = 1), plan(id = 2, currency = "USD"))))
    }

    // ---------------------------------------------------------------- the duplicate payment plan warning

    @Test fun `payment plans for a debt include paused ones and leave out ended ones and the plan being edited`() {
        val loan = 99L
        val active = plan(id = 1, toWalletId = loan)
        val paused = plan(id = 2, toWalletId = loan, paused = true)
        val ended = plan(id = 3, toWalletId = loan, end = "2026-03-15", next = 3)
        val otherDebt = plan(id = 4, toWalletId = 98)
        val bill = plan(id = 5)
        val all = listOf(active, paused, ended, otherDebt, bill)

        assertEquals(listOf(1L, 2L), ScheduleBulk.paymentPlansFor(all, loan).map { it.id })
        assertEquals(listOf(2L), ScheduleBulk.paymentPlansFor(all, loan, except = 1).map { it.id })
        assertTrue(ScheduleBulk.paymentPlansFor(all, 97).isEmpty())
    }

    @Test fun `collection plans count for a receivable`() {
        val loanToAna = 77L
        val collect = plan(id = 1, type = TransactionType.INCOME, toWalletId = loanToAna)
        assertEquals(listOf(1L), ScheduleBulk.paymentPlansFor(listOf(collect), loanToAna).map { it.id })
    }
}
