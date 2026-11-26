package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Budget pace with bills on their due dates (docs/BUDGET_PACE_PROPOSAL.md). The before/after table in the proposal is
 * these cases: today's rule (spending against the straight line) next to the proposed one (against the bills' dates).
 */
class BudgetPaceTest {
    private val month = YearMonth.of(2026, 11) // 30 days
    private fun usd(cents: Long) = Money(cents, "USD")
    private val budget = usd(259_000)          // $2,590.00, the demo month's nine categories
    private val rent = BudgetPace.Bill(month.atDay(2), usd(150_000)) // $1,500.00 rent, scheduled on the 2nd
    private val flexible = 109_000L            // the other $1,090.00 of the budget

    /** What a person has spent by the end of [day]: the rent once it's due (when [paysRent]), plus flexible spending at [speed]× the even pace. */
    private fun spentBy(day: Int, speed: Int = 1, paysRent: Boolean = true): Money =
        usd((if (paysRent && day >= 2) 150_000L else 0L) + flexible * speed * day / month.lengthOfMonth())

    private fun consumed(spent: Money) = Insights.percentOf(spent, budget)

    /** Today's rule: the straight line, as BudgetBurn.pace does it. */
    private fun today(day: Int, spent: Money) = BudgetPace.position(consumed(spent), (day * 100) / month.lengthOfMonth())

    /** The proposed rule. */
    private fun proposed(day: Int, spent: Money, bills: List<BudgetPace.Bill>) =
        BudgetPace.position(consumed(spent), BudgetPace.expectedPercent(budget, bills, month, day))

    @Test fun withoutBillsItIsTodaysStraightLineToThePercent() {
        for (m in listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 4), YearMonth.of(2026, 11), YearMonth.of(2026, 12))) {
            for (day in 0..m.lengthOfMonth()) {
                assertEquals("$m day $day", (day * 100) / m.lengthOfMonth(), BudgetPace.expectedPercent(budget, emptyList(), m, day))
            }
        }
    }

    @Test fun aRenterOnPlanIsNoLongerAheadOfPace() {
        // Rent on the 2nd, everything else spent evenly: exactly the plan.
        for (day in listOf(2, 8, 15, 22)) {
            assertEquals("today's rule, day $day", PacePosition.AHEAD, today(day, spentBy(day)))
            assertEquals("proposed rule, day $day", PacePosition.ALIGNED, proposed(day, spentBy(day), listOf(rent)))
        }
        // Near the month's end the straight line catches up, and both agree.
        assertEquals(PacePosition.ALIGNED, today(29, spentBy(29)))
        assertEquals(PacePosition.ALIGNED, proposed(29, spentBy(29), listOf(rent)))
    }

    @Test fun realOverspendingIsStillCaught() {
        // The same renter spending everything else twice as fast as planned.
        assertEquals(PacePosition.AHEAD, proposed(8, spentBy(8, speed = 2), listOf(rent)))
        assertEquals(PacePosition.AHEAD, proposed(12, spentBy(12, speed = 2), listOf(rent)))
    }

    @Test fun aRentThatIsNotScheduledReadsAsToday() {
        // Typed by hand with no schedule, the rent is invisible to the plan: the proposed rule can only follow bills it knows.
        assertEquals(PacePosition.AHEAD, proposed(8, spentBy(8), emptyList()))
        assertEquals(today(8, spentBy(8)), proposed(8, spentBy(8), emptyList()))
    }

    @Test fun aBillNotPaidYetIsStillExpectedOnItsDay() {
        // Rent due on the 2nd but not paid by the 8th: spending looks slower than the plan, never faster.
        assertEquals(PacePosition.BEHIND, proposed(8, spentBy(8, paysRent = false), listOf(rent)))
    }

    @Test fun expectationsAreExactAndCapped() {
        // Day 8: $1,500 + $1,090 × 8 ÷ 30 = $1,790.66… of $2,590 → 69 %.
        assertEquals(69, BudgetPace.expectedPercent(budget, listOf(rent), month, 8))
        // The bill's own day counts it in full; the day before doesn't.
        assertEquals(1, BudgetPace.expectedPercent(budget, listOf(rent), month, 1))
        assertEquals(60, BudgetPace.expectedPercent(budget, listOf(rent), month, 2))
        // A finished month expects the whole budget; a month not started expects nothing.
        assertEquals(100, BudgetPace.expectedPercent(budget, listOf(rent), month, 30))
        assertEquals(0, BudgetPace.expectedPercent(budget, listOf(rent), month, 0))
        // Bills above the budget are capped: never more than 100 %.
        val huge = BudgetPace.Bill(month.atDay(1), usd(500_000))
        assertEquals(100, BudgetPace.expectedPercent(budget, listOf(huge), month, 1))
        assertEquals(100, BudgetPace.expectedPercent(budget, listOf(huge, rent), month, 15))
    }

    @Test fun billsOutsideTheMonthOrCurrencyAreIgnored() {
        val nextMonth = BudgetPace.Bill(LocalDate.of(2026, 12, 2), usd(150_000))
        val pesos = BudgetPace.Bill(month.atDay(2), Money(15_000_000, "PHP"))
        val nothing = BudgetPace.Bill(month.atDay(2), usd(0))
        for (day in listOf(2, 15)) {
            assertEquals(BudgetPace.expectedPercent(budget, emptyList(), month, day), BudgetPace.expectedPercent(budget, listOf(nextMonth, pesos, nothing), month, day))
        }
    }

    @Test fun noBudgetMeansNoPosition() {
        assertNull(BudgetPace.position(null, 50))
        assertEquals(26, BudgetPace.expectedPercent(usd(0), listOf(rent), month, 8))
    }
}
