package com.lucento.app.feature.wallets

import com.lucento.app.core.money.Money
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the wallet editor saves for spending money and a card's counted credit (tester round; QA F4, 2026-11-17). */
class CardSpendingTest {

    private fun php(minor: Long) = Money(minor, "PHP")
    private fun card(spendable: Boolean?, text: String, limit: Long?) =
        CardSpending.of(WalletType.CREDIT_CARD, spendable, text, limit?.let(::php), "PHP", '.')

    @Test fun `a card with a limit counts the credit entered, no more than the limit`() {
        assertEquals(CardSpending.Save(true, php(10_000_00)), card(true, "10000", 60_000_00))
        assertEquals(CardSpending.AboveLimit, card(true, "70000", 60_000_00))
        assertEquals(CardSpending.Unreadable, card(true, "", 60_000_00))
        assertEquals(CardSpending.Unreadable, card(true, "0", 60_000_00))
    }

    @Test fun `a card whose limit was cleared saves as not counting instead of refusing to save`() {
        assertEquals(CardSpending.Save(false, php(0)), card(true, "10000", null))
        assertEquals(CardSpending.Save(false, php(0)), card(true, "", null))
    }

    @Test fun `credit turned off is kept as zero so the change syncs, and untouched stays empty`() {
        assertEquals(CardSpending.Save(false, php(0)), card(false, "10000", 60_000_00))
        assertEquals(CardSpending.Save(null, null), card(null, "", 60_000_00))
    }

    @Test fun `other types keep their choice and never count credit`() {
        assertEquals(CardSpending.Save(false, null), CardSpending.of(WalletType.BANK, false, "500", null, "PHP", '.'))
        assertEquals(CardSpending.Save(true, null), CardSpending.of(WalletType.SAVINGS, true, "", null, "PHP", '.'))
    }
}
