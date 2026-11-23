package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The monthly check-in's days and what its card offers (docs/CALCULATIONS.md "Monthly check-in"). */
class MonthCheckInTest {
    private fun day(text: String) = LocalDate.parse(text)
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private val any: (YearMonth) -> Boolean = { true }

    @Test fun `it plans next month in a month's last three days and this month until the seventh`() {
        // November has 30 days: the 28th, 29th and 30th are its last three.
        assertNull(MonthCheckIn.planMonth(day("2026-11-27")))
        assertEquals(dec, MonthCheckIn.planMonth(day("2026-11-28")))
        assertEquals(dec, MonthCheckIn.planMonth(day("2026-11-30")))
        assertEquals(dec, MonthCheckIn.planMonth(day("2026-12-01")))
        assertEquals(dec, MonthCheckIn.planMonth(day("2026-12-07")))
        assertNull(MonthCheckIn.planMonth(day("2026-12-08")))
        assertNull(MonthCheckIn.planMonth(day("2026-12-15")))
        // A 28-day February, and the turn of a year.
        assertNull(MonthCheckIn.planMonth(day("2027-02-25")))
        assertEquals(YearMonth.of(2027, 3), MonthCheckIn.planMonth(day("2027-02-26")))
        assertEquals(YearMonth.of(2027, 1), MonthCheckIn.planMonth(day("2026-12-31")))
    }

    @Test fun `planning counts for the month the card asks about next, never for one further ahead`() {
        // From the 8th, the next card is about next month; until the 7th, about this one.
        assertTrue(MonthCheckIn.countsAsPlanned(dec, day("2026-11-15")))
        assertTrue(MonthCheckIn.countsAsPlanned(dec, day("2026-11-29")))
        assertTrue(MonthCheckIn.countsAsPlanned(dec, day("2026-12-03")))
        assertTrue(MonthCheckIn.countsAsPlanned(nov, day("2026-11-15")))
        // March planned from November keeps its amounts but leaves December's, January's and February's check-ins.
        assertFalse(MonthCheckIn.countsAsPlanned(YearMonth.of(2027, 3), day("2026-11-15")))
        assertFalse(MonthCheckIn.countsAsPlanned(YearMonth.of(2027, 1), day("2026-12-03")))
        assertTrue(MonthCheckIn.countsAsPlanned(YearMonth.of(2027, 1), day("2026-12-08")))
    }

    @Test fun `the recap is last month's, from the 1st to the 7th only`() {
        assertNull(MonthCheckIn.recapMonth(day("2026-11-30")))
        assertEquals(nov, MonthCheckIn.recapMonth(day("2026-12-01")))
        assertEquals(nov, MonthCheckIn.recapMonth(day("2026-12-07")))
        assertNull(MonthCheckIn.recapMonth(day("2026-12-08")))
        assertEquals(dec, MonthCheckIn.recapMonth(day("2027-01-03")))
    }

    @Test fun `days left counts the days after today`() {
        assertEquals(2, MonthCheckIn.daysLeft(day("2026-11-28")))
        assertEquals(1, MonthCheckIn.daysLeft(day("2026-11-29")))
        assertEquals(0, MonthCheckIn.daysLeft(day("2026-11-30")))
    }

    @Test fun `the last days of a month offer only the plan`() {
        val card = MonthCheckIn.card(day("2026-11-29"), plannedThrough = null, recapSeenThrough = null, canPlan = true, hasRecords = any)
        assertEquals(MonthCheckIn.Card(plan = dec, recap = null), card)
    }

    @Test fun `the first days offer the plan and last month's recap, each until it is done`() {
        val today = day("2026-12-03")
        assertEquals(MonthCheckIn.Card(dec, nov), MonthCheckIn.card(today, null, null, canPlan = true, hasRecords = any))
        // Planned in November's last days: only the recap is left.
        assertEquals(MonthCheckIn.Card(null, nov), MonthCheckIn.card(today, plannedThrough = dec, recapSeenThrough = null, canPlan = true, hasRecords = any))
        // Recap seen: only the plan.
        assertEquals(MonthCheckIn.Card(dec, null), MonthCheckIn.card(today, plannedThrough = nov, recapSeenThrough = nov, canPlan = true, hasRecords = any))
        // Both done: no card.
        assertNull(MonthCheckIn.card(today, plannedThrough = dec, recapSeenThrough = nov, canPlan = true, hasRecords = any))
        // A later month already planned (planned ahead in the Budget tab's plan page) covers this one too.
        assertNull(MonthCheckIn.card(today, plannedThrough = YearMonth.of(2027, 1), recapSeenThrough = nov, canPlan = true, hasRecords = any))
    }

    @Test fun `nothing to plan or an empty month leaves only what applies`() {
        val today = day("2026-12-02")
        assertEquals(MonthCheckIn.Card(null, nov), MonthCheckIn.card(today, null, null, canPlan = false, hasRecords = any))
        assertEquals(MonthCheckIn.Card(dec, null), MonthCheckIn.card(today, null, null, canPlan = true, hasRecords = { false }))
        assertNull(MonthCheckIn.card(today, null, null, canPlan = false, hasRecords = { false }))
        // Outside the window there is never a card.
        assertNull(MonthCheckIn.card(day("2026-12-15"), null, null, canPlan = true, hasRecords = any))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a card always offers something`() {
        MonthCheckIn.Card(plan = null, recap = null)
    }

    private val food = Category(4, "Food", CategoryKind.EXPENSE, "groceries")
    private fun budget(created: String?) = Budget(1, 4, Money(6_000, "PHP"), createdOn = created?.let(LocalDate::parse))

    @Test fun `a budget set up in the last two weeks isn't asked about again yet`() {
        val today = day("2026-11-29")
        assertFalse("no amounts at all", MonthCheckIn.budgetSettled(emptyList(), listOf(food), today))
        assertFalse(MonthCheckIn.budgetSettled(listOf(budget("2026-11-20")), listOf(food), today))
        assertTrue("14 days", MonthCheckIn.budgetSettled(listOf(budget("2026-11-15")), listOf(food), today))
        assertTrue("from before creation dates were kept", MonthCheckIn.budgetSettled(listOf(budget(null)), listOf(food), today))
        // An amount that no longer applies this month doesn't count as a budget.
        val ended = Budget(2, 4, Money(0, "PHP"), createdOn = day("2026-01-01"), effectiveFrom = YearMonth.of(2026, 10))
        assertFalse(MonthCheckIn.budgetSettled(listOf(ended), listOf(food), today))
    }

    @Test fun `only income and expenses make a month worth recapping`() {
        fun tx(type: TransactionType, date: String) = Transaction(1, type, Money(100, "PHP"), 1, null, day(date), "", 0)
        assertFalse(MonthCheckIn.hasRecords(listOf(tx(TransactionType.TRANSFER_OUT, "2026-11-10"), tx(TransactionType.ADJUSTMENT_IN, "2026-11-11")), nov))
        assertTrue(MonthCheckIn.hasRecords(listOf(tx(TransactionType.EXPENSE, "2026-11-10")), nov))
        assertFalse(MonthCheckIn.hasRecords(listOf(tx(TransactionType.EXPENSE, "2026-10-31")), nov))
    }
}
