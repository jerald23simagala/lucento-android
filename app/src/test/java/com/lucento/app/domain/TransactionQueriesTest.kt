package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransactionQueriesTest {

    private var nextId = 1L
    private fun tx(
        amount: Long,
        date: String,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "USD",
        wallet: Long = 1,
        category: Long? = null,
        note: String = "",
    ) = Transaction(nextId++, type, Money(amount, currency), wallet, category, LocalDate.parse(date), note, 0L)

    // ---- ranges -----------------------------------------------------------------------------

    @Test fun thisMonthCoversFirstToLastDay() {
        val r = RangePreset.THIS_MONTH.resolve(LocalDate.parse("2024-02-15"))!!
        assertEquals(LocalDate.parse("2024-02-01"), r.start)
        assertEquals(LocalDate.parse("2024-02-29"), r.endInclusive) // leap year
    }

    @Test fun lastMonthCrossesYearBoundary() {
        val r = RangePreset.LAST_MONTH.resolve(LocalDate.parse("2025-01-10"))!!
        assertEquals(LocalDate.parse("2024-12-01"), r.start)
        assertEquals(LocalDate.parse("2024-12-31"), r.endInclusive)
    }

    @Test fun last30DaysIsInclusiveOfToday() {
        val r = RangePreset.LAST_30_DAYS.resolve(LocalDate.parse("2025-03-31"))!!
        assertEquals(LocalDate.parse("2025-03-02"), r.start)
        assertTrue(LocalDate.parse("2025-03-31") in r)
        assertFalse(LocalDate.parse("2025-03-01") in r)
    }

    @Test fun allPresetIsUnbounded() {
        assertEquals(null, RangePreset.ALL.resolve(LocalDate.parse("2025-03-31")))
    }

    // ---- filtering --------------------------------------------------------------------------

    @Test fun filtersByTypeWalletCategoryAndRange() {
        val list = listOf(
            tx(100, "2025-01-05", TransactionType.INCOME, wallet = 1, category = 10),
            tx(200, "2025-01-06", wallet = 2, category = 11),
            tx(300, "2025-02-01", wallet = 1, category = 11),
        )
        assertEquals(1, TransactionQueries.filter(list, TransactionFilter(type = TransactionType.INCOME)).size)
        assertEquals(2, TransactionQueries.filter(list, TransactionFilter(walletId = 1)).size)
        assertEquals(2, TransactionQueries.filter(list, TransactionFilter(categoryId = 11)).size)
        val jan = DateRange(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-31"))
        assertEquals(2, TransactionQueries.filter(list, TransactionFilter(range = jan)).size)
    }

    @Test fun searchMatchesNoteCategoryWalletAndAmount() {
        val list = listOf(
            tx(450, "2025-01-05", note = "Morning Coffee", category = 1, wallet = 5),
            tx(12_000, "2025-01-06", note = "", category = 2, wallet = 6),
        )
        val cats = mapOf(1L to "Dining", 2L to "Groceries")
        val wallets = mapOf(5L to "Everyday", 6L to "Savings jar")
        fun count(q: String) = TransactionQueries.filter(list, TransactionFilter(query = q), cats, wallets).size
        assertEquals(1, count("coffee"))
        assertEquals(1, count("GROCER"))
        assertEquals(1, count("jar"))
        assertEquals(1, count("4.50"))
        assertEquals(1, count("4,5")) // comma decimal input matches "4.50"
        assertEquals(0, count("nothing"))
        assertEquals(2, count("  ")) // blank query matches all
    }

    // ---- grouping ---------------------------------------------------------------------------

    @Test fun groupsConsecutiveDaysPreservingOrder() {
        val list = listOf(tx(1, "2025-01-03"), tx(2, "2025-01-03"), tx(3, "2025-01-02"), tx(4, "2025-01-01"))
        val groups = TransactionQueries.groupByDay(list)
        assertEquals(listOf("2025-01-03", "2025-01-02", "2025-01-01"), groups.map { it.date.toString() })
        assertEquals(listOf(2, 1, 1), groups.map { it.items.size })
    }

    @Test fun groupingEmptyListIsEmpty() {
        assertTrue(TransactionQueries.groupByDay(emptyList()).isEmpty())
    }

    // ---- totals -----------------------------------------------------------------------------

    @Test fun totalsRespectInclusiveBoundariesAndNeverMixCurrencies() {
        val feb = DateRange.monthOf(LocalDate.parse("2025-02-10"))
        val list = listOf(
            tx(1_000, "2025-01-31", TransactionType.INCOME),               // outside
            tx(5_000, "2025-02-01", TransactionType.INCOME),               // first day
            tx(1_250, "2025-02-28", TransactionType.EXPENSE),              // last day
            tx(300, "2025-02-14", TransactionType.EXPENSE),
            tx(9_999, "2025-03-01", TransactionType.EXPENSE),              // outside
            tx(700, "2025-02-10", TransactionType.EXPENSE, currency = "EUR"),
        )
        val totals = TransactionQueries.totalsByCurrency(list, feb, primaryCurrency = "USD")
        assertEquals(listOf("USD", "EUR"), totals.map { it.currencyCode })
        val usd = totals[0]
        assertEquals(Money(5_000, "USD"), usd.income)
        assertEquals(Money(1_550, "USD"), usd.expense)
        assertEquals(Money(3_450, "USD"), usd.net)
        assertEquals(Money(700, "EUR"), totals[1].expense)
        assertEquals(Money(0, "EUR"), totals[1].income)
    }

    @Test fun totalsOrderPutsPrimaryFirstThenAlphabetical() {
        val r = DateRange.monthOf(LocalDate.parse("2025-02-10"))
        val list = listOf(
            tx(1, "2025-02-01", currency = "GBP"),
            tx(1, "2025-02-01", currency = "EUR"),
            tx(1, "2025-02-01", currency = "JPY"),
        )
        assertEquals(listOf("JPY", "EUR", "GBP"), TransactionQueries.totalsByCurrency(list, r, "JPY").map { it.currencyCode })
    }

    @Test fun signedAmountFollowsType() {
        assertEquals(Money(500, "USD"), tx(500, "2025-01-01", TransactionType.INCOME).signedAmount)
        assertEquals(Money(-500, "USD"), tx(500, "2025-01-01", TransactionType.EXPENSE).signedAmount)
    }
}
