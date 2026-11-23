package com.lucento.app.feature.home

import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.money.Money
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.MonthCheckIn
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** The monthly check-in card's words, from its figures (owner, 2026-11-23). */
class MonthCheckInCopyTest {
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private fun usd(minor: Long) = Money(minor, "USD")
    private fun plan(month: YearMonth, total: Long, spent: Long) =
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

    private fun copy(card: MonthCheckIn.Card, today: String, budget: BudgetPlan?) =
        CheckInCopy.of(card, LocalDate.parse(today), budget, AmountFormat.Shown)

    @Test fun `in a month's last days it asks to plan the next one`() = english {
        // This month's figures are on the budget card right below it, so the card doesn't repeat them.
        val c = copy(MonthCheckIn.Card(dec, null), "2026-11-28", plan(nov, 20_000, 18_200))
        // The title doesn't repeat the "Plan December" button under it.
        assertEquals("Ready for December?", c.title)
        assertEquals("November ends in 2 days. Set December’s amounts now, so a birthday or another one-off cost has room.", c.body)
        assertEquals("November ends tomorrow. Set December’s amounts now, so a birthday or another one-off cost has room.", copy(MonthCheckIn.Card(dec, null), "2026-11-29", null).body)
        assertEquals("November ends today.", copy(MonthCheckIn.Card(dec, null), "2026-11-30", null).body.substringBefore(" Set"))
    }

    @Test fun `in the first days it says how last month ended, the sign in words`() = english {
        val over = copy(MonthCheckIn.Card(dec, nov), "2026-12-03", plan(nov, 20_000, 21_200))
        assertEquals("December has started", over.title)
        assertEquals("November: \$12.00 over budget. Check December’s amounts for anything different this month.", over.body)
        assertEquals("See November’s recap", over.recapAction)
        val within = copy(MonthCheckIn.Card(dec, nov), "2026-12-03", plan(nov, 20_000, 18_200))
        assertEquals("November: \$182.00 of \$200.00 spent. Check December’s amounts for anything different this month.", within.body)
        val noBudget = copy(MonthCheckIn.Card(dec, nov), "2026-12-03", null)
        assertEquals("November is done. Check December’s amounts for anything different this month.", noBudget.body)
    }

    @Test fun `with only one thing left, the card is about that`() = english {
        val planOnly = copy(MonthCheckIn.Card(dec, null), "2026-12-02", null)
        assertEquals("December has started", planOnly.title)
        assertEquals("Check its amounts for anything different this month, like a birthday or an event.", planOnly.body)
        val recapOnly = copy(MonthCheckIn.Card(null, nov), "2026-12-02", plan(nov, 20_000, 18_200))
        assertEquals("November in review", recapOnly.title)
        assertEquals("\$182.00 of \$200.00 spent. See what came in, what went out and where it went.", recapOnly.body)
    }

    @Test fun `hidden amounts stay hidden in the card's sentences`() = english {
        val c = CheckInCopy.of(MonthCheckIn.Card(dec, nov), LocalDate.parse("2026-12-03"), plan(nov, 20_000, 21_200), AmountFormat.Hidden)
        assertEquals(false, c.body.contains("12.00"))
    }
}
