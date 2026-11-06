package com.lucento.app.domain.fx

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** The "≈ ₱…" values under foreign wallets and movements (owner, 2026-11-06): the same book as the totals. */
class FxDisplayTest {

    private val day = LocalDate.of(2026, 11, 1)

    private fun wallet(id: Long, currency: String, minor: Long, type: WalletType = WalletType.BANK, basis: String? = "55") =
        Money(minor, currency).let {
            Wallet(id = id, name = "W$id", type = type, balance = it, createdAtEpochMillis = 0, openingBalance = it,
                fxBasisRate = basis?.let(::BigDecimal))
        }

    private fun expense(id: Long, walletId: Long, amount: Money, base: Money?) = Transaction(
        id = id, type = TransactionType.EXPENSE, amount = amount, walletId = walletId, categoryId = null, date = day,
        note = "", createdAtEpochMillis = id, baseAmount = base, fxSource = base?.let { FxSource.MARKET },
    )

    @Test
    fun `only main-currency wallets give nothing to show`() {
        val display = FxDisplay.of("PHP", listOf(wallet(1, "PHP", 10_000)), emptyList())
        assertTrue(display.wallets.isEmpty())
        assertNull(display.netWorthValue(wallet(1, "PHP", 10_000)))
    }

    @Test
    fun `a foreign wallet shows its carrying value, a foreign debt negative`() {
        val savings = wallet(1, "USD", 10_000)
        val card = wallet(2, "USD", 2_000, type = WalletType.CREDIT_CARD)
        val display = FxDisplay.of("PHP", listOf(savings, card, wallet(3, "PHP", 5_000)), emptyList())
        assertEquals(Money(550_000, "PHP"), display.netWorthValue(savings))
        assertEquals(Money(-110_000, "PHP"), display.netWorthValue(card))
        assertNull(display.netWorthValue(wallet(3, "PHP", 5_000)))
    }

    @Test
    fun `a wallet without a rate isn't given a value`() {
        val unvalued = wallet(1, "USD", 10_000, basis = null)
        assertNull(FxDisplay.of("PHP", listOf(unvalued), emptyList()).netWorthValue(unvalued))
    }

    @Test
    fun `a foreign movement shows its own value, signed as shown`() {
        val savings = wallet(1, "USD", 10_000)
        val spend = expense(10, 1, Money(1_000, "USD"), base = Money(58_000, "PHP"))
        val display = FxDisplay.of("PHP", listOf(savings), listOf(spend))
        assertEquals(Money(-58_000, "PHP"), display.value(spend, sign = -1))
        assertEquals(Money(58_000, "PHP"), display.value(spend, sign = 0))
    }
}
