package com.lucento.app.feature.plan

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringMath
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** "Once" in the schedule editor (owner test 2026-11-09): one date, saved as a schedule that ends on its first date. */
class OneTimeScheduleTest {
    private val day = LocalDate.of(2026, 11, 20)

    private fun schedule(end: LocalDate?, nextIndex: Int = 0) = RecurringSchedule(
        id = 1, name = "Doctor", type = TransactionType.EXPENSE, amount = Money(1_500_00, "PHP"), walletId = 1,
        categoryId = 2, frequency = Frequency.MONTHLY, interval = 1, startDate = day, endDate = end,
        nextIndex = nextIndex, paused = false,
    )

    @Test
    fun `a one-time schedule is due once, then finished`() {
        val once = schedule(end = day)
        assertEquals(day, once.nextDue)
        assertEquals(listOf(0 to day), RecurringMath.dueThrough(once, day.plusYears(1)))
        assertTrue(schedule(end = day, nextIndex = 1).isFinished)
    }

    @Test
    fun `editing reads a single-date schedule back as Once, and a repeating one as repeating`() {
        assertTrue(schedule(end = day).toDraft().once)
        assertFalse(schedule(end = null).toDraft().once)
        assertFalse(schedule(end = day.plusMonths(3)).toDraft().once)
    }

    @Test
    fun `a new schedule starts repeating`() {
        val draft = ScheduleDraft()
        assertFalse(draft.once)
        assertNull(draft.endDate)
    }
}
