package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetWorthCalculatorTest {

    private var nextId = 1L
    private fun wallet(type: WalletType, minor: Long, currency: String = "USD") =
        Wallet(nextId++, "w$nextId", type, Money(minor, currency), 0L)

    @Test
    fun `credit cards reduce net worth`() {
        val summary = NetWorthCalculator.summarize(
            listOf(
                wallet(WalletType.BANK, 250_000),
                wallet(WalletType.CASH, 4_550),
                wallet(WalletType.CREDIT_CARD, 30_025),
            ),
            primaryCurrency = "USD",
        )
        assertEquals(Money(224_525, "USD"), summary.totalFor("USD"))
        assertEquals(Money(254_550, "USD"), summary.assets["USD"])
        assertEquals(Money(30_025, "USD"), summary.liabilities["USD"])
    }

    @Test
    fun `net worth can be negative`() {
        val summary = NetWorthCalculator.summarize(
            listOf(wallet(WalletType.CASH, 1_000), wallet(WalletType.CREDIT_CARD, 5_000)),
            primaryCurrency = "USD",
        )
        assertTrue(summary.totalFor("USD")!!.isNegative)
        assertEquals(Money(-4_000, "USD"), summary.totalFor("USD"))
    }

    @Test
    fun `currencies are never mixed and primary comes first`() {
        val summary = NetWorthCalculator.summarize(
            listOf(
                wallet(WalletType.BANK, 10_000, "USD"),
                wallet(WalletType.E_WALLET, 500_000, "PHP"),
                wallet(WalletType.SAVINGS, 1_000, "EUR"),
                wallet(WalletType.CREDIT_CARD, 100_000, "PHP"),
            ),
            primaryCurrency = "PHP",
        )
        assertEquals(listOf("PHP", "EUR", "USD"), summary.totals.map { it.currencyCode })
        assertEquals(Money(400_000, "PHP"), summary.totalFor("PHP"))
        assertEquals(Money(1_000, "EUR"), summary.totalFor("EUR"))
        assertEquals(Money(10_000, "USD"), summary.totalFor("USD"))
    }

    @Test
    fun `empty input yields no totals`() {
        val summary = NetWorthCalculator.summarize(emptyList(), "USD")
        assertTrue(summary.totals.isEmpty())
        assertNull(summary.totalFor("USD"))
    }

    @Test
    fun `contribution sign follows liability flag`() {
        assertEquals(Money(-100, "USD"), wallet(WalletType.CREDIT_CARD, 100).netWorthContribution)
        assertEquals(Money(100, "USD"), wallet(WalletType.SAVINGS, 100).netWorthContribution)
    }
}
