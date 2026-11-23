package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** What the plan page names as worth a look (docs/CALCULATIONS.md "Monthly check-in"). */
class MonthPlanHintsTest {
    private fun php(minor: Long) = Money(minor, "PHP")
    private val dec = YearMonth.of(2026, 12)

    private val food = Category(4, "Food", CategoryKind.EXPENSE, "groceries")
    private val gifts = Category(6, "Gifts", CategoryKind.EXPENSE, "gift")
    private val insurance = Category(7, "Insurance", CategoryKind.EXPENSE, "shield")
    private val categories = listOf(food, gifts, insurance)

    private fun schedule(
        id: Long,
        name: String,
        frequency: Frequency,
        interval: Int,
        start: String,
        nextIndex: Int = 0,
        type: TransactionType = TransactionType.EXPENSE,
        paused: Boolean = false,
        currency: String = "PHP",
    ) = RecurringSchedule(
        id, name, type, Money(50_000, currency), walletId = 1, categoryId = 7, frequency = frequency, interval = interval,
        startDate = LocalDate.parse(start), endDate = null, nextIndex = nextIndex, paused = paused,
    )

    @Test fun `costs that don't come every month are named in the month they fall in`() {
        val schedules = listOf(
            schedule(1, "Car insurance", Frequency.YEARLY, 1, "2025-12-12", nextIndex = 1),
            schedule(2, "Water filter", Frequency.MONTHLY, 3, "2026-06-20", nextIndex = 2),
            schedule(3, "Rent", Frequency.MONTHLY, 1, "2026-01-01", nextIndex = 11),
            schedule(4, "Gym", Frequency.WEEKLY, 1, "2026-11-02", nextIndex = 4),
            schedule(5, "Bonus", Frequency.YEARLY, 1, "2025-12-15", nextIndex = 1, type = TransactionType.INCOME),
            schedule(6, "Paused", Frequency.YEARLY, 1, "2025-12-01", nextIndex = 1, paused = true),
            schedule(7, "In euros", Frequency.YEARLY, 1, "2025-12-01", nextIndex = 1, currency = "EUR"),
        )
        val due = MonthPlanHints.due(dec, schedules, "PHP")
        // The yearly insurance on 12 December and the three-monthly filter on 20 December; rent, the gym, income,
        // a paused plan and another currency are left out.
        assertEquals(listOf("Car insurance", "Water filter"), due.map { it.name })
        assertEquals(LocalDate.parse("2026-12-12"), due.first().date)
        assertEquals(LocalDate.parse("2026-12-20"), due.last().date)
        assertEquals(7L, due.first().categoryId)
    }

    private fun spend(minor: Long, category: Long, date: String) =
        Transaction(minor + category, TransactionType.EXPENSE, php(minor), 1, category, LocalDate.parse(date), "", 0)

    private val budgets = listOf(
        Budget(1, 4, php(10_000), createdOn = LocalDate.parse("2025-06-01")),
        Budget(2, 6, php(3_000), createdOn = LocalDate.parse("2025-06-01")),
    )

    @Test fun `last year's month is named only where it was clearly above the plan`() {
        val lastDecember = listOf(
            spend(5_000, 6, "2025-12-20"), // Gifts: ₱50 against ₱30 planned
            spend(10_500, 4, "2025-12-10"), // Food: 5 % above ₱100, an ordinary wobble
            spend(2_000, 7, "2025-12-05"), // Insurance: nothing planned at all
        )
        val hints = MonthPlanHints.lastYear(dec, categories, budgets, lastDecember, "PHP")
        assertEquals(listOf(6L, 7L), hints.map { it.categoryId })
        assertEquals(php(5_000), hints.first().spent)
        assertEquals(php(3_000), hints.first().planned)
        assertEquals(null, hints.last().planned)
        assertEquals(YearMonth.of(2025, 12), hints.first().month)
        // Without a record of that month a year ago, nothing to say.
        assertTrue(MonthPlanHints.lastYear(dec, categories, budgets, emptyList(), "PHP").isEmpty())
    }

    @Test fun `last year's hint stays once December has its own amount, measured against the usual one`() {
        val lastDecember = listOf(spend(5_000, 6, "2025-12-20"))
        // ₱50 set for Gifts in December alone: the hint is still about the usual ₱30, so the plan page can show it as
        // acted on instead of dropping it (critic r2, P0-2).
        val own = budgets + Budget(3, 6, php(5_000), createdOn = LocalDate.parse("2026-11-29"), month = dec)
        val hint = MonthPlanHints.lastYear(dec, categories, own, lastDecember, "PHP").single()
        assertEquals(php(3_000), hint.planned)
        // Raising the usual amount itself is what makes it ordinary.
        val raised = budgets + Budget(4, 6, php(5_000), createdOn = LocalDate.parse("2026-11-29"), effectiveFrom = dec)
        assertTrue(MonthPlanHints.lastYear(dec, categories, raised, lastDecember, "PHP").isEmpty())
    }

    @Test fun `last month's overspending, so far while it is still running`() {
        val november = listOf(spend(12_500, 4, "2026-11-10"), spend(2_000, 6, "2026-11-11"))
        val lateNovember = MonthPlanHints.wentOver(dec, LocalDate.parse("2026-11-29"), categories, budgets, november, "PHP")
        // Food: ₱125 spent against ₱100, so ₱25 over; the hint carries what was spent, for the sheet it opens.
        assertEquals(listOf(PlanHint.WentOver(4, over = php(2_500), spent = php(12_500), month = YearMonth.of(2026, 11), finished = false)), lateNovember)
        val earlyDecember = MonthPlanHints.wentOver(dec, LocalDate.parse("2026-12-02"), categories, budgets, november, "PHP")
        assertEquals(true, earlyDecember.single().finished)
    }

    @Test fun `scheduled costs first, at most two of each kind and four in all`() {
        val schedules = (1..3).map { schedule(it.toLong(), "Yearly $it", Frequency.YEARLY, 1, "2025-12-0$it", nextIndex = 1) }
        val history = listOf(spend(5_000, 6, "2025-12-20"), spend(2_000, 7, "2025-12-05"), spend(12_500, 4, "2026-11-10"))
        val hints = MonthPlanHints.hints(dec, LocalDate.parse("2026-12-02"), categories, budgets, history, schedules, "PHP")
        assertEquals(4, hints.size)
        assertTrue(hints[0] is PlanHint.DueInMonth && hints[1] is PlanHint.DueInMonth)
        assertTrue(hints[2] is PlanHint.LastYear && hints[3] is PlanHint.LastYear)
    }
}
