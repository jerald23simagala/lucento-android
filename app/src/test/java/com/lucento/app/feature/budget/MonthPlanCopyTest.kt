package com.lucento.app.feature.budget

import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryBudgetLine
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.PlanHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** The plan page's words and the planning default (owner, 2026-11-23). */
class MonthPlanCopyTest {
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private fun usd(minor: Long) = Money(minor, "USD")
    private fun plan(month: YearMonth, total: Long, spent: Long = 0) =
        BudgetPlan(month, "USD", emptyList(), usd(total), usd(spent), usd(spent), usd(0), usd(0))

    private fun <T> english(block: () -> T): T {
        val locale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            return block()
        } finally {
            Locale.setDefault(locale)
        }
    }

    @Test fun `the planned total is compared with the month before`() = english {
        val fmt = AmountFormat.Shown
        assertEquals("\$30.00 more than November’s \$200.00.", plannedComparison(plan(dec, 23_000), plan(nov, 20_000), fmt))
        assertEquals("\$10.00 less than November’s \$200.00.", plannedComparison(plan(dec, 19_000), plan(nov, 20_000), fmt))
        assertEquals("The same as November.", plannedComparison(plan(dec, 20_000), plan(nov, 20_000), fmt))
        assertEquals("The sum of your category amounts for December.", plannedComparison(plan(dec, 20_000), plan(nov, 0), fmt))
        assertTrue(plannedComparison(plan(dec, 0), plan(nov, 20_000), fmt).startsWith("No amounts yet"))
    }

    @Test fun `the plan page opens on how the month before went`() = english {
        val fmt = AmountFormat.Shown
        val lateNovember = LocalDate.parse("2026-11-29")
        assertEquals(
            "November so far: \$182.00 of \$200.00 spent. What’s different about December? A birthday, a trip or a yearly bill " +
                "can have its own amount, for December only.",
            planIntro(dec, plan(nov, 20_000, 18_200), lateNovember, fmt),
        )
        assertTrue(planIntro(dec, plan(nov, 20_000, 21_000), LocalDate.parse("2026-12-02"), fmt).startsWith("November: \$10.00 over budget. "))
        assertTrue(planIntro(dec, plan(nov, 0), lateNovember, fmt).startsWith("What’s different about December?"))
    }

    @Test fun `each hint reads as one fact and where it comes from`() = english {
        val fmt = AmountFormat.Shown
        val names = mapOf(6L to "Gifts", 7L to "Insurance")
        val name: (Long?) -> String? = { id -> id?.let(names::get) }
        assertEquals(
            "Car insurance: \$500.00 due Dec\u00A012" to "Yearly · Insurance",
            hintText(PlanHint.DueInMonth(7, "Car insurance", usd(50_000), LocalDate.parse("2026-12-12"), Frequency.YEARLY, 1), name, fmt, Locale.US),
        )
        assertEquals(
            "Last December: \$50.00 on Gifts" to "\$30.00 planned this time",
            hintText(PlanHint.LastYear(6, usd(5_000), YearMonth.of(2025, 12), usd(3_000)), name, fmt, Locale.US),
        )
        assertEquals(
            "Last December: \$20.00 on Insurance" to "Nothing planned for it this time",
            hintText(PlanHint.LastYear(7, usd(2_000), YearMonth.of(2025, 12), null), name, fmt, Locale.US),
        )
        assertEquals(
            "Gifts: \$5.00 over so far in November" to "Worth a different amount this time?",
            hintText(PlanHint.WentOver(6, usd(500), nov, finished = false), name, fmt, Locale.US),
        )
        assertEquals("Gifts: \$5.00 over in November", hintText(PlanHint.WentOver(6, usd(500), nov, finished = true), name, fmt, Locale.US).first)
        assertEquals("Every 3 months", frequencyLabel(Frequency.MONTHLY, 3))
        assertEquals("Every 2 years", frequencyLabel(Frequency.YEARLY, 2))
    }

    private val gifts = Category(6, "Gifts", CategoryKind.EXPENSE, "gift")

    @Test fun `a planned row says where its amount comes from`() = english {
        val fmt = AmountFormat.Shown
        val own = Budget(3, 6, usd(5_000), month = dec)
        val usual = Budget(2, 6, usd(2_000))
        val line = CategoryBudgetLine(gifts, own, usd(0))
        val source = plannedSource(listOf(usual, own), line, dec)
        assertEquals(PlannedSource(ownAmount = true, usual = usd(2_000)), source)
        assertEquals("Only December · usually \$20.00", plannedRowDetail(line, source, fmt))
        val monthly = CategoryBudgetLine(gifts, usual, usd(1_500))
        assertEquals("Every month · \$15.00 spent so far", plannedRowDetail(monthly, plannedSource(listOf(usual), monthly, dec), fmt))
    }

    @Test fun `a hint's amount comes with it into the sheet it opens`() = english {
        val fmt = AmountFormat.Shown
        val usual = Budget(2, 6, usd(2_000))
        val budgeted = CategoryBudgetLine(gifts, usual, usd(0))
        val unbudgeted = CategoryBudgetLine(gifts, null, usd(0))
        val due = PlanHint.DueInMonth(6, "Car insurance", usd(64_000), LocalDate.parse("2026-12-12"), Frequency.YEARLY, 1)
        // A bill due adds to the usual amount, and says so.
        assertEquals(HintSuggestion(usd(66_000), "Your usual \$20.00 plus Car insurance’s \$640.00."), hintSuggestion(due, budgeted, fmt))
        assertEquals(HintSuggestion(usd(64_000), "Car insurance’s \$640.00, due December 12."), hintSuggestion(due, unbudgeted, fmt))
        // Last year's month proposes what it cost then; last month's overspending, what was spent.
        assertEquals(
            HintSuggestion(usd(5_000), "What Gifts cost last December."),
            hintSuggestion(PlanHint.LastYear(6, usd(5_000), YearMonth.of(2025, 12), usd(3_000)), budgeted, fmt),
        )
        assertEquals(
            HintSuggestion(usd(2_500), "What Gifts cost in November so far."),
            hintSuggestion(PlanHint.WentOver(6, usd(500), nov, finished = false), budgeted, fmt),
        )
    }

    @Test fun `planning starts every change as that month's only`() {
        val none = BudgetChangeContext.of(emptyList(), 6, "Gifts", "USD", dec, nov)
        // On the Budget tab a category's first amount is a monthly one…
        assertEquals(BudgetScope.ONWARD, none.defaultScope)
        // …but planning a month is about what is different in it.
        val planning = BudgetChangeContext.of(emptyList(), 6, "Gifts", "USD", dec, nov, planning = true)
        assertEquals(BudgetScope.THIS_MONTH, planning.defaultScope)
        assertEquals(BudgetScope.entries, planning.scopes)
        assertFalse(planning.isPastMonth)
    }
}
