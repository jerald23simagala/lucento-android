package com.lucento.app.feature.progress

import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.money.Money
import com.lucento.app.domain.CategoryAmount
import com.lucento.app.domain.CategoryChange
import com.lucento.app.domain.MonthBudgetResult
import com.lucento.app.domain.MonthRecap
import com.lucento.app.domain.RecapBudget
import com.lucento.app.domain.RecapMonthResult
import com.lucento.app.domain.YearRecap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

/** The Milestones page's words, from the recap's figures only. */
class RecapCopyTest {
    private val oct = YearMonth.of(2026, 10)
    private fun usd(minor: Long) = Money(minor, "USD")
    private val names = mapOf(4L to "Food", 5L to "Fun")
    private val name: (Long?) -> String? = { id -> id?.let(names::get) }

    private fun <T> english(block: () -> T): T {
        val locale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            return block()
        } finally {
            Locale.setDefault(locale)
        }
    }

    private fun recap(
        income: Long = 50_000,
        spent: Long = 15_000,
        finished: Boolean = true,
        budget: RecapBudget? = null,
        setAside: Money? = null,
        reached: Int = 0,
        paid: Money? = null,
    ) = MonthRecap(
        month = oct, currencyCode = "USD", finished = finished, income = usd(income), spent = usd(spent), budget = budget,
        top = listOf(CategoryAmount(4, usd(12_000), 80), CategoryAmount(5, usd(3_000), 20)),
        change = CategoryChange(4, usd(12_000), usd(8_000)),
        netWorth = usd(114_000), netWorthChange = usd(35_000),
        setAside = setAside, goalsReached = reached, paidToDebts = paid, daysRecorded = 4, days = 31,
    )

    @Test fun `the headline says what the signed figure means`() = english {
        assertEquals("Kept: more came in than went out.", RecapCopy.headline(recap()))
        assertEquals("Kept so far: more came in than went out.", RecapCopy.headline(recap(finished = false)))
        assertEquals("More went out than came in.", RecapCopy.headline(recap(income = 10_000)))
        assertEquals("What came in and what went out matched.", RecapCopy.headline(recap(income = 15_000)))
        assertEquals("No income recorded in October.", RecapCopy.headline(recap(income = 0)))
    }

    @Test fun `each line says one thing, with the facts under it`() = english {
        val budget = RecapBudget(usd(14_000), usd(15_000), budgeted = 2, within = 1, over = listOf(CategoryAmount(4, usd(2_000))))
        val lines = RecapCopy.lines(recap(budget = budget, setAside = usd(6_000), reached = 1, paid = usd(5_000)), name, AmountFormat.Shown)
        assertEquals(
            listOf(
                RecapLine(RecapLine.Kind.BUDGET, "\$10.00 over budget", listOf("\$150.00 of \$140.00 spent", "1 of 2 categories within budget", "Over: Food \$20.00")),
                RecapLine(RecapLine.Kind.TOP, "Where it went", listOf("Food \$120.00 · 80%", "Fun \$30.00 · 20%")),
                RecapLine(RecapLine.Kind.MORE, "Food: \$40.00 more than September", listOf("\$120.00 this time, \$80.00 in September")),
                RecapLine(RecapLine.Kind.NET_WORTH, "Net worth \$1,140.00 at the month’s end", listOf("+\$350.00 since the end of September")),
                RecapLine(RecapLine.Kind.GOALS, "\$60.00 set aside for goals", listOf("1 goal reached its target")),
                RecapLine(RecapLine.Kind.DEBTS, "\$50.00 paid to cards and loans", listOf("Payments move your own money, so they lower what you owe.")),
                RecapLine(RecapLine.Kind.DAYS, "Recorded on 4 of 31 days"),
            ),
            lines,
        )
    }

    @Test fun `parts with nothing to say are left out`() = english {
        val kinds = RecapCopy.lines(recap(setAside = usd(0), paid = usd(0)), name, AmountFormat.Shown).map { it.kind }
        assertEquals(listOf(RecapLine.Kind.TOP, RecapLine.Kind.MORE, RecapLine.Kind.NET_WORTH, RecapLine.Kind.DAYS), kinds)
        val taken = RecapCopy.lines(recap(setAside = usd(-2_500)), name, AmountFormat.Shown).first { it.kind == RecapLine.Kind.GOALS }
        assertEquals("\$25.00 taken back from goals", taken.title)
        val within = RecapBudget(usd(20_000), usd(15_000), budgeted = 2, within = 2, over = emptyList())
        assertEquals("Within budget so far", RecapCopy.lines(recap(budget = within, finished = false), name, AmountFormat.Shown).first().title)
    }

    @Test fun `a budget set up during the month says when it began`() = english {
        // Why this month can't count for Steady Month, under its verdict (critic r2, P1-3).
        val late = RecapBudget(usd(20_000), usd(15_000), budgeted = 2, within = 2, over = emptyList(), since = java.time.LocalDate.parse("2026-10-10"))
        val line = RecapCopy.lines(recap(budget = late), name, AmountFormat.Shown).first()
        assertEquals("Within budget", line.title)
        assertEquals("Your budget began on October\u00A010", line.details.last())
    }

    @Test fun `a month square is one sentence for screen readers`() = english {
        val over = RecapMonthResult(oct, usd(35_000), true, true, budgetKept = false, budget = MonthBudgetResult(usd(15_000), usd(16_000)))
        assertEquals("October 2026: 107% of the budget used, over budget", RecapCopy.squareDescription(over))
        val running = RecapMonthResult(YearMonth.of(2026, 11), usd(-1_000), true, false, null, budget = MonthBudgetResult(usd(15_000), usd(1_000)))
        assertEquals("November 2026, so far: 7% of the budget used, on track", RecapCopy.squareDescription(running))
        val unbudgeted = RecapMonthResult(YearMonth.of(2026, 8), usd(500), hasRecords = true, finished = true, budgetKept = null)
        assertEquals("August 2026: no budget", RecapCopy.squareDescription(unbudgeted))
        val empty = RecapMonthResult(YearMonth.of(2026, 7), usd(0), hasRecords = false, finished = true, budgetKept = null)
        assertEquals("July 2026: nothing recorded", RecapCopy.squareDescription(empty))
        val toCome = RecapMonthResult(YearMonth.of(2026, 12), usd(0), hasRecords = false, finished = false, budgetKept = null, upcoming = true)
        assertEquals("December 2026: still to come", RecapCopy.squareDescription(toCome))
        // The plan preview names the month only, never its result.
        assertEquals("October 2026", RecapCopy.squareDescription(over, hidden = true))
    }

    @Test fun `a square's level uses the Budget tab's words`() {
        assertEquals("on track", RecapCopy.levelWords(MonthBudgetResult(usd(10_000), usd(7_999))))
        assertEquals("getting close", RecapCopy.levelWords(MonthBudgetResult(usd(10_000), usd(8_000))))
        assertEquals("getting close", RecapCopy.levelWords(MonthBudgetResult(usd(10_000), usd(9_950))))
        // Spent to the cent is used up, not over.
        assertEquals("fully used", RecapCopy.levelWords(MonthBudgetResult(usd(10_000), usd(10_000))))
        assertEquals("over budget", RecapCopy.levelWords(MonthBudgetResult(usd(10_000), usd(10_001))))
    }

    @Test fun `the year's totals lead with the budget, and a year still running says so far`() = english {
        // 2026 to November: nothing recorded before September, December still to come.
        val before = (1..8).map { RecapMonthResult(YearMonth.of(2026, it), usd(0), hasRecords = false, finished = true, budgetKept = null) }
        val months = before + listOf(
            RecapMonthResult(YearMonth.of(2026, 9), usd(-11_000), true, true, budgetKept = true),
            RecapMonthResult(oct, usd(35_000), true, true, budgetKept = false),
            RecapMonthResult(YearMonth.of(2026, 11), usd(-1_000), true, false, budgetKept = null),
            RecapMonthResult(YearMonth.of(2026, 12), usd(0), false, false, null, upcoming = true),
        )
        val year = YearRecap(YearMonth.of(2026, 11), "USD", months, income = usd(50_000), spent = usd(27_000), netWorthChange = usd(23_000))
        val lines = RecapCopy.yearLines(year, AmountFormat.Shown)
        assertEquals(
            listOf(
                "Within budget in 1 of 2 budgeted months so far",
                // The first month with records is September: the year is told from there, never called a whole year.
                "\$230.00 kept since September",
                "\$500.00 came in and \$270.00 went out",
                "More came in than went out in 1 of 3 months with records",
                "Net worth +\$230.00 in 2026 so far",
            ),
            lines.map { it.text },
        )
        // Each bullet has its mark, and its figures are the parts shown in bold.
        assertEquals(
            listOf(YearLine.Kind.BUDGET, YearLine.Kind.KEPT, YearLine.Kind.FLOW, YearLine.Kind.MONTHS, YearLine.Kind.NET_WORTH),
            lines.map { it.kind },
        )
        assertEquals(
            listOf(listOf("1 of 2"), listOf("\$230.00"), listOf("\$500.00", "\$270.00"), listOf("1 of 3"), listOf("+\$230.00")),
            lines.map { it.figures },
        )
        assertTrue(lines.all { line -> line.figures.all { it in line.text } })
        // More out than in says so, in words.
        val spentMore = year.copy(income = usd(10_000))
        assertEquals("\$170.00 more went out than came in since September", RecapCopy.yearLines(spentMore, AmountFormat.Shown)[1].text)
    }

    @Test fun `a finished year is told as that year`() = english {
        // Records all year, no budget: the kept line leads.
        val full = (1..12).map { RecapMonthResult(YearMonth.of(2025, it), usd(1_000), true, true, null) }
        assertEquals(
            listOf(
                "\$120.00 kept in 2025",
                "\$120.00 came in and \$0.00 went out",
                "More came in than went out in 12 of 12 months with records",
            ),
            RecapCopy.yearLines(YearRecap(YearMonth.of(2025, 12), "USD", full, usd(12_000), usd(0), null), AmountFormat.Shown).map { it.text },
        )
        // Records from September on: told from September of that year.
        val late = (1..12).map { m ->
            RecapMonthResult(YearMonth.of(2025, m), usd(if (m >= 9) 1_000 else 0), hasRecords = m >= 9, finished = true, budgetKept = if (m >= 9) true else null)
        }
        val lines = RecapCopy.yearLines(YearRecap(YearMonth.of(2025, 12), "USD", late, usd(4_000), usd(0), usd(4_000)), AmountFormat.Shown).map { it.text }
        assertEquals("Within budget in 4 of 4 budgeted months", lines[0])
        assertEquals("\$40.00 kept since September 2025", lines[1])
        assertEquals("Net worth +\$40.00 in 2025", lines.last())
    }

    @Test fun `a year without records says so`() = english {
        val running = (1..12).map { m ->
            RecapMonthResult(YearMonth.of(2026, m), usd(0), hasRecords = false, finished = m < 11, budgetKept = null, upcoming = m > 11)
        }
        val empty = RecapCopy.yearLines(YearRecap(YearMonth.of(2026, 11), "USD", running, usd(0), usd(0), null), AmountFormat.Shown)
        assertEquals(listOf(YearLine(YearLine.Kind.NOTHING, "Nothing recorded in 2026 yet")), empty)
        val past = (1..12).map { RecapMonthResult(YearMonth.of(2025, it), usd(0), false, true, null) }
        assertEquals(listOf("Nothing recorded in 2025"), RecapCopy.yearLines(YearRecap(YearMonth.of(2025, 12), "USD", past, usd(0), usd(0), null), AmountFormat.Shown).map { it.text })
    }

    @Test fun `a month still running is compared with the same days of the month before`() = english {
        val running = recap(finished = false).copy(month = YearMonth.of(2026, 11), changeThrough = java.time.LocalDate.parse("2026-10-15"))
        val line = RecapCopy.lines(running, name, AmountFormat.Shown).first { it.kind == RecapLine.Kind.MORE }
        assertEquals("Food: \$40.00 more than at this point in October", line.title)
        assertEquals(listOf("\$120.00 so far, \$80.00 by October 15"), line.details)
    }
}
