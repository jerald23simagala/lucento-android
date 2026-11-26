package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Budget pace with bills on their due dates, paid or not (owner, 2026-11-26; docs/CALCULATIONS.md "Budget pace",
 * docs/BUDGET_PACE_PROPOSAL.md). The proposal's before/after table and the owner's late-payment question are these cases.
 */
class BudgetPaceTest {
    private val month = YearMonth.of(2026, 11) // 30 days
    private fun usd(cents: Long) = Money(cents, "USD")
    private val budget = usd(259_000)          // $2,590.00, the demo month's nine categories
    private val flexible = 109_000L            // everything but the $1,500.00 rent

    private fun rentNov(owed: Boolean) = BudgetPace.Bill(month.atDay(2), usd(150_000), owed)
    private fun rentOct(owed: Boolean) = BudgetPace.Bill(LocalDate.of(2026, 10, 2), usd(150_000), owed)

    /** Spent by the end of [day]: [rents] full rents paid this month, plus the rest of the budget at [speed]× the even pace. */
    private fun spentBy(day: Int, rents: Int = 1, speed: Int = 1): Money =
        usd(rents * 150_000L + flexible * speed * day / month.lengthOfMonth())

    private fun on(day: Int) = month.atDay(day)

    /** Before: the straight line, as BudgetBurn.pace did it. */
    private fun before(day: Int, spent: Money) =
        BudgetPace.position(Insights.percentOf(spent, budget), (day * 100) / month.lengthOfMonth())

    /** After: bills on their due dates, paid or not. */
    private fun after(day: Int, spent: Money, bills: List<BudgetPace.Bill>) = BudgetPace.position(
        BudgetPace.consumedPercent(spent, budget, bills, on(day)),
        BudgetPace.expectedPercent(budget, bills, month, day),
    )

    @Test fun withoutBillsItIsTheStraightLineToThePercent() {
        for (m in listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 4), YearMonth.of(2026, 11), YearMonth.of(2026, 12))) {
            for (day in 0..m.lengthOfMonth()) {
                assertEquals("$m day $day", (day * 100) / m.lengthOfMonth(), BudgetPace.expectedPercent(budget, emptyList(), m, day))
            }
        }
        val spent = spentBy(8)
        assertEquals(Insights.percentOf(spent, budget), BudgetPace.consumedPercent(spent, budget, emptyList(), on(8)))
    }

    @Test fun aRenterOnPlanIsNoLongerAheadOfPace() {
        for (day in listOf(2, 8, 15, 22)) {
            assertEquals("before, day $day", PacePosition.AHEAD, before(day, spentBy(day)))
            assertEquals("after, day $day", PacePosition.ALIGNED, after(day, spentBy(day), listOf(rentNov(owed = false))))
        }
        assertEquals(PacePosition.ALIGNED, before(29, spentBy(29)))
        assertEquals(PacePosition.ALIGNED, after(29, spentBy(29), listOf(rentNov(owed = false))))
    }

    @Test fun realOverspendingIsStillCaught() {
        assertEquals(PacePosition.AHEAD, after(8, spentBy(8, speed = 2), listOf(rentNov(owed = false))))
        assertEquals(PacePosition.AHEAD, after(12, spentBy(12, speed = 2), listOf(rentNov(owed = false))))
    }

    @Test fun thisMonthsRentDueButNotPaidCountsAsSpentForPace() {
        // Day 8, rent due on the 2nd still unpaid, everything else on plan: on track, not "Under budget".
        val bills = listOf(rentNov(owed = true))
        assertEquals(PacePosition.ALIGNED, after(8, spentBy(8, rents = 0), bills))
        assertEquals(69, BudgetPace.consumedPercent(spentBy(8, rents = 0), budget, bills, on(8)))
    }

    @Test fun lastMonthsRentPaidLateWhileThisMonthsIsStillOwedIsAheadOfPace() {
        // The owner's question: October's rent paid late on the 5th (spending this month), November's not paid yet.
        val bills = listOf(rentNov(owed = true))
        assertEquals(PacePosition.AHEAD, after(8, spentBy(8, rents = 1), bills))
        assertEquals(127, BudgetPace.consumedPercent(spentBy(8, rents = 1), budget, bills, on(8)))
    }

    @Test fun lastMonthsRentPaidLateAndThisMonthsPaidIsAheadOfPace() {
        // Both rents left the account this month: well past the plan (and the budget card says Over budget).
        assertEquals(PacePosition.AHEAD, after(8, spentBy(8, rents = 2), listOf(rentNov(owed = false))))
    }

    @Test fun lastMonthsRentStillUnpaidCountsToo() {
        // October's rent never paid, November's neither: both are owed and already due.
        val bills = listOf(rentOct(owed = true), rentNov(owed = true))
        assertEquals(PacePosition.AHEAD, after(8, spentBy(8, rents = 0), bills))
        // October's bill isn't part of November's plan: only November's rent moves the expectation.
        assertEquals(69, BudgetPace.expectedPercent(budget, bills, month, 8))
    }

    @Test fun aRentThatIsNotScheduledReadsAsBefore() {
        assertEquals(before(8, spentBy(8)), after(8, spentBy(8), emptyList()))
    }

    @Test fun aBillLaterInTheMonthIsNotSpentYet() {
        val later = BudgetPace.Bill(month.atDay(20), usd(150_000), owed = true)
        assertEquals(Insights.percentOf(spentBy(8, rents = 0), budget), BudgetPace.consumedPercent(spentBy(8, rents = 0), budget, listOf(later), on(8)))
    }

    @Test fun expectationsAreExactAndCapped() {
        val rent = listOf(rentNov(owed = false))
        assertEquals(69, BudgetPace.expectedPercent(budget, rent, month, 8))   // $1,500 + $1,090 × 8 ÷ 30 of $2,590
        assertEquals(1, BudgetPace.expectedPercent(budget, rent, month, 1))
        assertEquals(60, BudgetPace.expectedPercent(budget, rent, month, 2))
        assertEquals(100, BudgetPace.expectedPercent(budget, rent, month, 30))
        assertEquals(0, BudgetPace.expectedPercent(budget, rent, month, 0))
        val huge = BudgetPace.Bill(month.atDay(1), usd(500_000), owed = false)
        assertEquals(100, BudgetPace.expectedPercent(budget, listOf(huge), month, 1))
        assertEquals(100, BudgetPace.expectedPercent(budget, listOf(huge) + rent, month, 15))
    }

    @Test fun billsOutsideTheMonthOrCurrencyAreIgnored() {
        val nextMonth = BudgetPace.Bill(LocalDate.of(2026, 12, 2), usd(150_000), owed = true)
        val pesos = BudgetPace.Bill(month.atDay(2), Money(15_000_000, "PHP"), owed = true)
        val nothing = BudgetPace.Bill(month.atDay(2), usd(0), owed = true)
        val bills = listOf(nextMonth, pesos, nothing)
        for (day in listOf(2, 15)) {
            assertEquals(BudgetPace.expectedPercent(budget, emptyList(), month, day), BudgetPace.expectedPercent(budget, bills, month, day))
            assertEquals(Insights.percentOf(spentBy(day), budget), BudgetPace.consumedPercent(spentBy(day), budget, bills, on(day)))
        }
    }

    @Test fun noBudgetMeansNoPosition() {
        assertNull(BudgetPace.position(null, 50))
        assertNull(BudgetPace.consumedPercent(usd(100), usd(0), emptyList(), on(8)))
        assertEquals(26, BudgetPace.expectedPercent(usd(0), listOf(rentNov(owed = false)), month, 8))
    }

    // ------------------------------------------------------------------------------------------- bills from schedules

    private fun schedule(
        nextIndex: Int,
        id: Long = 1,
        type: TransactionType = TransactionType.EXPENSE,
        toWallet: Long? = null,
        paused: Boolean = false,
        frequency: Frequency = Frequency.MONTHLY,
        start: LocalDate = LocalDate.of(2026, 9, 2), // index 0 = 2 Sep, 1 = 2 Oct, 2 = 2 Nov, 3 = 2 Dec
        amount: Long = 150_000,
    ) = RecurringSchedule(id, "Rent", type, usd(amount), 1, null, frequency, 1, start, null, nextIndex, paused, toWalletId = toWallet)

    @Test fun billsFromSchedules() {
        val today = on(8)
        // November's rent paid: settled.
        assertEquals(listOf(rentNov(owed = false)), BudgetPace.bills(listOf(schedule(nextIndex = 3)), month, today))
        // Not paid yet: owed.
        assertEquals(listOf(rentNov(owed = true)), BudgetPace.bills(listOf(schedule(nextIndex = 2)), month, today))
        // October's never paid: October's (owed, due) and November's.
        assertEquals(listOf(rentOct(owed = true), rentNov(owed = true)), BudgetPace.bills(listOf(schedule(nextIndex = 1)), month, today))
        // Paid ahead into December: November's settled, December's not this month's.
        assertEquals(listOf(rentNov(owed = false)), BudgetPace.bills(listOf(schedule(nextIndex = 4)), month, today))
    }

    @Test fun onlyActiveBillsCount() {
        val today = on(8)
        assertTrue(BudgetPace.bills(listOf(schedule(nextIndex = 2, paused = true)), month, today).isEmpty())
        assertTrue(BudgetPace.bills(listOf(schedule(nextIndex = 2, type = TransactionType.INCOME)), month, today).isEmpty())
        assertTrue(BudgetPace.bills(listOf(schedule(nextIndex = 2, toWallet = 2)), month, today).isEmpty()) // a debt payment
        // A weekly bill: its November dates (Mondays from 2 Nov), the ones before the 8th paid.
        val weekly = schedule(nextIndex = 2, frequency = Frequency.WEEKLY, start = LocalDate.of(2026, 10, 26), amount = 2_000)
        val dates = BudgetPace.bills(listOf(weekly), month, today).map { it.due.dayOfMonth to it.owed }
        assertEquals(listOf(2 to false, 9 to true, 16 to true, 23 to true, 30 to true), dates)
    }

    // ------------------------------------------------------------------------------------------- the whole burn

    @Test fun burnUsesBillsOnlyForTheCurrentMonth() {
        val housing = Category(1, "Housing", CategoryKind.EXPENSE, "housing")
        val other = Category(2, "Everything else", CategoryKind.EXPENSE, "other")
        val budgets = listOf(Budget(1, 1, usd(150_000)), Budget(2, 2, usd(flexible)))
        val txs = listOf(
            Transaction(10, TransactionType.EXPENSE, usd(150_000), 1, 1, on(2), "Rent", 0L),
            Transaction(11, TransactionType.EXPENSE, usd(flexible * 8 / 30), 1, 2, on(8), "Groceries and the rest", 0L),
        )
        val plan = BudgetPlanner.plan(listOf(housing, other), budgets, txs, "USD", month)
        val bills = BudgetPace.bills(listOf(schedule(nextIndex = 3)), month, on(8))

        val withBills = BudgetPlanner.burn(plan, on(8), bills)
        assertEquals(PacePosition.ALIGNED, withBills.pace)
        assertFalse(withBills.aheadOfPace)
        assertEquals(26, withBills.elapsedPercent) // the bar's tick is unchanged

        val withoutBills = BudgetPlanner.burn(plan, on(8))
        assertEquals(PacePosition.AHEAD, withoutBills.pace)
        assertTrue(withoutBills.aheadOfPace)

        // Looking back at November from December: a finished month ignores the bills.
        assertEquals(BudgetPlanner.burn(plan, LocalDate.of(2026, 12, 3)).pace, BudgetPlanner.burn(plan, LocalDate.of(2026, 12, 3), bills).pace)
    }
}
