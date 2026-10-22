package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class InsightsTest {

    private var nextId = 1L
    private fun tx(
        amount: Long,
        date: String,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "USD",
        category: Long? = null,
    ) = Transaction(nextId++, type, Money(amount, currency), 1L, category, LocalDate.parse(date), "", 0L)

    private fun usd(minor: Long) = Money(minor, "USD")

    // ---- monthly totals ---------------------------------------------------------------------

    @Test fun monthlyTotalsAreOldestFirstAndIncludeEmptyMonths() {
        val list = listOf(
            tx(1_000, "2025-01-31"),
            tx(250, "2025-03-01"),
            tx(750, "2025-03-15"),
            tx(9_999, "2024-12-31"), // before the window
            tx(5_000, "2025-03-10", TransactionType.INCOME), // other type
            tx(400, "2025-03-10", currency = "EUR"), // other currency
        )
        val result = Insights.monthlyTotals(list, "USD", YearMonth.of(2025, 3), months = 3)
        assertEquals(listOf(YearMonth.of(2025, 1), YearMonth.of(2025, 2), YearMonth.of(2025, 3)), result.map { it.month })
        assertEquals(listOf(usd(1_000), usd(0), usd(1_000)), result.map { it.amount })
    }

    @Test fun monthlyTotalsCrossYearBoundaryAndCanCountIncome() {
        val list = listOf(tx(300, "2024-12-05", TransactionType.INCOME), tx(700, "2025-01-05", TransactionType.INCOME))
        val result = Insights.monthlyTotals(list, "USD", YearMonth.of(2025, 1), months = 2, type = TransactionType.INCOME)
        assertEquals(listOf(usd(300), usd(700)), result.map { it.amount })
    }

    @Test fun monthlyTotalsRespectZeroDecimalCurrencies() {
        val result = Insights.monthlyTotals(listOf(tx(1_500, "2025-05-02", currency = "JPY")), "JPY", YearMonth.of(2025, 5), 1)
        assertEquals(Money(1_500, "JPY"), result.single().amount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun monthlyTotalsRejectNonPositiveWindow() {
        Insights.monthlyTotals(emptyList(), "USD", YearMonth.of(2025, 1), months = 0)
    }

    // ---- category breakdown -----------------------------------------------------------------

    @Test fun categoryBreakdownIsLargestFirstWithStableTies() {
        val march = DateRange(LocalDate.parse("2025-03-01"), LocalDate.parse("2025-03-31"))
        val list = listOf(
            tx(500, "2025-03-02", category = 2),
            tx(500, "2025-03-03", category = 1),
            tx(500, "2025-03-04", category = null),
            tx(900, "2025-03-05", category = 3),
            tx(100, "2025-03-06", category = 3),
            tx(10_000, "2025-04-01", category = 1), // outside range
            tx(10_000, "2025-03-07", TransactionType.INCOME, category = 9), // income
        )
        val result = Insights.categoryBreakdown(list, "USD", march)
        assertEquals(listOf(3L, 1L, 2L, null), result.map { it.categoryId })
        assertEquals(listOf(usd(1_000), usd(500), usd(500), usd(500)), result.map { it.amount })
    }

    @Test fun categoryBreakdownIsEmptyWithoutSpending() {
        val range = DateRange.monthOf(LocalDate.parse("2025-03-10"))
        assertEquals(emptyList<CategoryShare>(), Insights.categoryBreakdown(emptyList(), "USD", range))
    }

    // ---- net by currency --------------------------------------------------------------------

    @Test fun netByCurrencyKeepsCurrenciesSeparateAndPrimaryFirst() {
        val list = listOf(
            tx(1_000, "2025-03-01", TransactionType.INCOME),
            tx(250, "2025-03-01"),
            tx(300, "2025-03-01", currency = "EUR"),
            tx(100, "2025-03-01", currency = "AUD", type = TransactionType.INCOME),
        )
        val result = Insights.netByCurrency(list, primaryCurrency = "USD")
        assertEquals(listOf(usd(750), Money(100, "AUD"), Money(-300, "EUR")), result)
    }

    // ---- percentages ------------------------------------------------------------------------

    @Test fun percentOfRoundsHalfUpAndCanExceedHundred() {
        assertEquals(50, Insights.percentOf(usd(500), usd(1_000)))
        assertEquals(33, Insights.percentOf(usd(1), usd(3)))
        assertEquals(67, Insights.percentOf(usd(2), usd(3)))
        assertEquals(1, Insights.percentOf(usd(5), usd(1_000))) // 0.5 rounds up
        assertEquals(150, Insights.percentOf(usd(1_500), usd(1_000)))
        assertEquals(0, Insights.percentOf(usd(0), usd(1_000)))
    }

    @Test fun percentOfIsNullWithoutABase() {
        assertNull(Insights.percentOf(usd(500), usd(0)))
    }

    @Test fun percentOfHandlesExtremeAmountsWithoutOverflow() {
        val big = Money(Long.MAX_VALUE / 2, "USD")
        assertEquals(100, Insights.percentOf(big, big))
    }

    @Test(expected = IllegalArgumentException::class)
    fun percentOfRejectsMixedCurrencies() {
        Insights.percentOf(usd(1), Money(1, "EUR"))
    }

    @Test fun fractionIsClampedForDrawing() {
        assertEquals(0.25f, Insights.fraction(usd(250), usd(1_000)), 0.0001f)
        assertEquals(1f, Insights.fraction(usd(2_000), usd(1_000)), 0f)
        assertEquals(0f, Insights.fraction(usd(-5), usd(1_000)), 0f)
        assertEquals(0f, Insights.fraction(usd(5), usd(0)), 0f)
    }

    // ---- unbounded totals -------------------------------------------------------------------

    @Test fun totalsWithoutRangeCoverEverything() {
        val list = listOf(
            tx(100, "2020-01-01", TransactionType.INCOME),
            tx(40, "2026-01-01"),
            tx(7, "2026-01-01", currency = "EUR"),
        )
        val totals = TransactionQueries.totalsByCurrency(list, primaryCurrency = "USD")
        assertEquals(listOf("USD", "EUR"), totals.map { it.currencyCode })
        assertEquals(usd(60), totals.first().net)
    }
}
