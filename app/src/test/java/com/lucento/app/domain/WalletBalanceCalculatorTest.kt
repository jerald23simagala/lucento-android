package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WalletBalanceCalculatorTest {
    private val usd = "USD"

    @Test fun assetWalletAddsIncomeAndSubtractsExpense() {
        val b = WalletBalanceCalculator.currentBalance(Money(10_000, usd), WalletType.BANK, 2_500, 1_050)
        assertEquals(Money(11_450, usd), b)
    }

    @Test fun assetWalletCanGoNegative() {
        val b = WalletBalanceCalculator.currentBalance(Money(500, usd), WalletType.CASH, 0, 1_200)
        assertEquals(Money(-700, usd), b)
    }

    @Test fun creditCardTracksAmountOwed() {
        // Owed 100.00, spent 40.00 more, paid 25.00 -> owed 115.00
        val b = WalletBalanceCalculator.currentBalance(Money(10_000, usd), WalletType.CREDIT_CARD, 2_500, 4_000)
        assertEquals(Money(11_500, usd), b)
    }

    @Test fun creditCardOverpaymentBecomesNegativeOwed() {
        val b = WalletBalanceCalculator.currentBalance(Money(0, usd), WalletType.CREDIT_CARD, 1_000, 0)
        assertEquals(Money(-1_000, usd), b)
    }

    @Test fun zeroFlowsReturnOpeningBalance() {
        assertEquals(Money(777, "JPY"), WalletBalanceCalculator.currentBalance(Money(777, "JPY"), WalletType.E_WALLET, 0, 0))
    }

    @Test fun overflowThrowsInsteadOfWrapping() {
        assertThrows(ArithmeticException::class.java) {
            WalletBalanceCalculator.currentBalance(Money(Long.MAX_VALUE, usd), WalletType.BANK, 1, 0)
        }
    }
}
