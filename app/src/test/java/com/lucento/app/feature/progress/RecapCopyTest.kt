package com.lucento.app.feature.progress

import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.money.Money
import com.lucento.app.domain.CategoryAmount
import com.lucento.app.domain.CategoryChange
import com.lucento.app.domain.MonthRecap
import com.lucento.app.domain.RecapBudget
import com.lucento.app.domain.RecapMonthResult
import com.lucento.app.domain.YearRecap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

/** The Progress page's words, from the recap's figures only. */
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

    @Test fun `a month tile is one sentence for screen readers`() = english {
        val kept = RecapMonthResult(oct, usd(35_000), hasRecords = true, finished = true, budgetKept = false)
        assertEquals("October 2026: kept \$350.00, over budget", RecapCopy.tileDescription(kept, AmountFormat.Shown))
        val short = RecapMonthResult(YearMonth.of(2026, 11), usd(-1_000), hasRecords = true, finished = false, budgetKept = null)
        assertEquals("November 2026, so far: \$10.00 more went out than came in", RecapCopy.tileDescription(short, AmountFormat.Shown))
        val empty = RecapMonthResult(YearMonth.of(2026, 7), usd(0), hasRecords = false, finished = true, budgetKept = null)
        assertEquals("July 2026: nothing recorded", RecapCopy.tileDescription(empty, AmountFormat.Shown))
    }

    @Test fun `the year's totals lead with what was kept`() = english {
        // Twelve months to November; nothing recorded before September.
        val empty = (0L until 9L).map { RecapMonthResult(YearMonth.of(2025, 12).plusMonths(it), usd(0), false, true, null) }
        val months = empty + listOf(
            RecapMonthResult(YearMonth.of(2026, 9), usd(-11_000), true, true, budgetKept = true),
            RecapMonthResult(oct, usd(35_000), true, true, budgetKept = false),
            RecapMonthResult(YearMonth.of(2026, 11), usd(-1_000), true, false, budgetKept = null),
        )
        val year = YearRecap(YearMonth.of(2026, 11), "USD", months, income = usd(50_000), spent = usd(27_000), netWorthChange = usd(23_000))
        assertEquals(
            listOf(
                // The first month with records is September: the year is told from there.
                "\$230.00 kept since September",
                "\$500.00 came in and \$270.00 went out.",
                "More came in than went out in 1 of 3 months with records.",
                "Within budget in 1 of 2 budgeted months.",
                "Net worth +\$230.00 over the year.",
            ),
            RecapCopy.yearLines(year, AmountFormat.Shown),
        )
        assertTrue(RecapCopy.yearLines(YearRecap(oct, "USD", emptyList(), usd(0), usd(0), null), AmountFormat.Shown).single().startsWith("Nothing recorded"))
        // Records from the window's first month on: then it is the year.
        val full = (0L until 12L).map { RecapMonthResult(YearMonth.of(2025, 12).plusMonths(it), usd(1_000), true, true, null) }
        assertEquals("\$120.00 kept over 12 months", RecapCopy.yearLines(YearRecap(YearMonth.of(2026, 11), "USD", full, usd(12_000), usd(0), null), AmountFormat.Shown).first())
    }

    @Test fun `a month still running is compared with the same days of the month before`() = english {
        val running = recap(finished = false).copy(month = YearMonth.of(2026, 11), changeThrough = java.time.LocalDate.parse("2026-10-15"))
        val line = RecapCopy.lines(running, name, AmountFormat.Shown).first { it.kind == RecapLine.Kind.MORE }
        assertEquals("Food: \$40.00 more than at this point in October", line.title)
        assertEquals(listOf("\$120.00 so far, \$80.00 by October 15"), line.details)
    }
}
