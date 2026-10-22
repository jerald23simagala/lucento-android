package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HomeStateBuilderTest {

    private val today = LocalDate.parse("2025-03-20")
    private var nextId = 1L

    private fun wallet(currency: String, balance: Long = 0, type: WalletType = WalletType.BANK) =
        Wallet(nextId++, "W$nextId", type, Money(balance, currency), 0L)

    private fun tx(
        amount: Long,
        date: String,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "USD",
        category: Long? = null,
    ) = Transaction(nextId++, type, Money(amount, currency), 1L, category, LocalDate.parse(date), "", 0L)

    @Test fun emptyStateHasNoNetWorthOrInsights() {
        val state = HomeStateBuilder.build(emptyList(), emptyList(), emptyList(), "USD", today)
        assertFalse(state.loading)
        assertFalse(state.hasWallets)
        assertNull(state.netWorth)
        assertNull(state.monthTotals)
        assertFalse(state.hasTrend)
        assertEquals(HomeStateBuilder.TREND_MONTHS, state.trend.size)
        assertEquals(YearMonth.of(2025, 3), state.trend.last().month)
    }

    @Test fun focusesOnMainCurrencyWhenHeld() {
        val state = HomeStateBuilder.build(emptyList(), listOf(wallet("EUR"), wallet("USD")), emptyList(), "USD", today)
        assertEquals("USD", state.focusCurrency)
    }

    @Test fun fallsBackToFirstWalletCurrencyWithoutConverting() {
        val state = HomeStateBuilder.build(emptyList(), listOf(wallet("JPY", 5_000)), emptyList(), "USD", today)
        assertEquals("JPY", state.focusCurrency)
        assertEquals(Money(5_000, "JPY"), state.netWorth!!.totals.single())
    }

    @Test fun monthTotalsAndCategoriesCoverOnlyThisMonthInFocusCurrency() {
        val groceries = Category(10, "Groceries", CategoryKind.EXPENSE, "groceries")
        // Newest first, as the DAO returns them.
        val all = listOf(
            tx(4_000, "2025-03-19", category = 10),
            tx(1_000, "2025-03-18", category = null),
            tx(250_000, "2025-03-01", TransactionType.INCOME),
            tx(9_000, "2025-03-05", currency = "EUR"),
            tx(7_000, "2025-02-27", category = 10),
        )
        val state = HomeStateBuilder.build(all, listOf(wallet("USD")), listOf(groceries), "USD", today)

        assertEquals(Money(250_000, "USD"), state.monthTotals!!.income)
        assertEquals(Money(5_000, "USD"), state.monthTotals!!.expense)
        assertEquals(listOf("EUR"), state.otherMonthTotals.map { it.currencyCode })
        assertEquals(listOf(10L, null), state.topCategories.map { it.categoryId })
        assertEquals(Money(7_000, "USD"), state.trend[state.trend.size - 2].amount) // February
        assertEquals(Money(5_000, "USD"), state.trend.last().amount) // March
        assertTrue(state.hasTrend)
        assertEquals(5, state.transactionCount)
        assertEquals(groceries, state.categoriesById[10])
    }

    @Test fun recentIsCappedAndKeepsOrder() {
        val all = (1..8).map { tx(it * 100L, "2025-03-%02d".format(20 - it)) }
        val state = HomeStateBuilder.build(all, listOf(wallet("USD")), emptyList(), "USD", today)
        assertEquals(all.take(HomeStateBuilder.RECENT_COUNT), state.recent)
    }

    @Test fun transfersAppearOnceAndNeverAsIncomeOrSpending() {
        val out = Transaction(100, TransactionType.TRANSFER_OUT, Money(5_000, "USD"), 1L, null, LocalDate.parse("2025-03-19"), "", 0L, "g1")
        val inn = Transaction(101, TransactionType.TRANSFER_IN, Money(5_000, "USD"), 2L, null, LocalDate.parse("2025-03-19"), "", 0L, "g1")
        val spend = tx(1_000, "2025-03-18")
        val state = HomeStateBuilder.build(listOf(inn, out, spend), listOf(wallet("USD", 10_000), wallet("USD")), emptyList(), "USD", today)
        assertEquals(listOf(100L, spend.id), state.recent.map { it.id })
        assertEquals(2, state.transactionCount)
        assertEquals(Money(1_000, "USD"), state.netFlow!!.expense)
        assertTrue(state.netFlow!!.income.isZero)
        assertEquals(1L, state.transfers.getValue("g1").fromWalletId)
    }

    @Test fun disabledModulesKeepDataButLeaveTheDashboard() {
        val budget = com.lucento.app.domain.Budget(1, null, Money(2_000, "USD"))
        val rent = com.lucento.app.domain.RecurringSchedule(
            1, "Rent", TransactionType.EXPENSE, Money(500, "USD"), 1L, null,
            com.lucento.app.domain.Frequency.MONTHLY, 1, LocalDate.parse("2025-03-25"), null, 0, false,
        )
        val all = listOf(tx(1_900, "2025-03-10"))
        val wallets = listOf(wallet("USD", 50_000))
        val on = HomeStateBuilder.build(all, wallets, emptyList(), "USD", today, listOf(budget), listOf(rent))
        assertEquals(com.lucento.app.domain.SafeToSpend.Basis.BUDGET, on.safeToSpend!!.basis)
        assertEquals(Money(-400, "USD"), on.safeToSpend!!.amount) // 2,000 − 1,900 − 500
        assertEquals(1, on.budgetAlerts.size)
        assertEquals(1, on.upcoming.size)

        val off = com.lucento.app.domain.FeatureSet(setOf(com.lucento.app.domain.Feature.INSIGHTS))
        val hidden = HomeStateBuilder.build(all, wallets, emptyList(), "USD", today, listOf(budget), listOf(rent), off)
        assertEquals(com.lucento.app.domain.SafeToSpend.Basis.BALANCES, hidden.safeToSpend!!.basis)
        assertEquals(Money(50_000, "USD"), hidden.safeToSpend!!.amount)
        assertTrue(hidden.budgetAlerts.isEmpty())
        assertTrue(hidden.upcoming.isEmpty())
    }
}
