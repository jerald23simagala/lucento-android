package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.domain.NetWorthComposer
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** P2-1: spendable money beside net worth, and recent activity on Home. */
class HomeOnTrackTest {

    @Test fun `spendable is cash, bank and e-wallets only, from the same balances as net worth`() {
        val wallets = listOf(
            Wallet(1, "Cash", WalletType.CASH, Money(20_000, "USD"), 0L),
            Wallet(2, "Bank", WalletType.BANK, Money(150_000, "USD"), 0L),
            Wallet(3, "GCash", WalletType.E_WALLET, Money(5_000, "USD"), 0L),
            Wallet(4, "Savings", WalletType.SAVINGS, Money(500_000, "USD"), 0L),
            Wallet(5, "Card", WalletType.CREDIT_CARD, Money(30_000, "USD"), 0L),
            Wallet(6, "Euro cash", WalletType.CASH, Money(9_999, "EUR"), 0L),
        )
        val c = NetWorthComposer.compose(wallets, "USD")
        assertEquals(Money(175_000, "USD"), c.available) // never mixes in EUR, savings or the card
        assertTrue(c.hasNonSpendable)
        assertTrue(c.showSpendable)
        assertEquals(Money(645_000, "USD"), c.netWorth)

        // A zero-balance savings account makes nothing different: Home doesn't repeat net worth (QA L9).
        val zeroSavings = NetWorthComposer.compose(listOf(wallets[0], Wallet(9, "Savings", WalletType.SAVINGS, Money(0, "USD"), 0L)), "USD")
        assertTrue(zeroSavings.hasNonSpendable)
        assertFalse(zeroSavings.showSpendable)

        val simple = NetWorthComposer.compose(wallets.take(2), "USD")
        assertFalse("only spendable accounts: Home doesn't repeat the same figure", simple.hasNonSpendable)
        assertEquals(simple.netWorth, simple.available)
    }

    @Test fun `a negative spendable balance stays negative`() {
        val c = NetWorthComposer.compose(listOf(Wallet(1, "Bank", WalletType.BANK, Money(-1_000, "USD"), 0L), Wallet(2, "Loan", WalletType.LOAN, Money(5_000, "USD"), 0L)), "USD")
        assertEquals(Money(-1_000, "USD"), c.available)
    }

    private fun tx(id: Long, type: TransactionType, date: String, group: String? = null) =
        Transaction(id, type, Money(100, "USD"), 1L, null, LocalDate.parse(date), "", 0L, transferGroup = group)

    @Test fun `recent activity is the newest three, newest first, with each transfer once`() {
        val all = listOf(
            tx(1, TransactionType.EXPENSE, "2026-10-01"),
            tx(2, TransactionType.INCOME, "2026-10-30"),
            tx(3, TransactionType.TRANSFER_OUT, "2026-10-31", "g"),
            tx(4, TransactionType.TRANSFER_IN, "2026-10-31", "g"),
            tx(5, TransactionType.EXPENSE, "2026-10-31"),
            tx(6, TransactionType.EXPENSE, "2026-10-29"),
        )
        assertEquals(listOf(5L, 3L, 2L), recentActivity(all).map { it.id })
        assertEquals(emptyList<Long>(), recentActivity(emptyList()).map { it.id })
    }
}
