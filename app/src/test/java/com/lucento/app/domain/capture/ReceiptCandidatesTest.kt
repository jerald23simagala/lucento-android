package com.lucento.app.domain.capture

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReceiptCandidatesTest {

    private val today = LocalDate.parse("2026-10-22")
    private val wallets = listOf(
        Wallet(1, "Cash", WalletType.CASH, Money(0, "PHP"), 0),
        Wallet(2, "Euro card", WalletType.BANK, Money(0, "EUR"), 0),
    )
    private val categories = listOf(Category(12, "Groceries", CategoryKind.EXPENSE, "groceries"))
    private val context = InterpretationContext(wallets, categories, today, defaultWalletId = 1)

    @Test fun aReceiptIsOneExpenseForItsTotal() {
        val lines = listOf(
            "SM SUPERMARKET", "Mall of Asia", "10/20/2026",
            "Rice 5kg  320.00", "Eggs  95.50", "Milk  110.00",
            "TOTAL  525.50", "CASH  1000.00", "CHANGE  474.50",
        )
        val c = ReceiptCandidates.from(lines, context)!!
        assertEquals(TransactionType.EXPENSE, c.type)
        assertEquals(BigDecimal("525.50"), c.amount)
        assertTrue(c.description.equals("SM SUPERMARKET", ignoreCase = true))
        assertEquals(12L, c.categoryId)
        assertEquals(1L, c.walletId)
        assertTrue(c.flags.isEmpty())
    }

    @Test fun itemsThatDontAddUpAreFlagged() {
        val lines = listOf("CORNER STORE", "Bread  60.00", "Juice  45.00", "TOTAL  150.00")
        assertTrue(CandidateFlag.ITEMS_DONT_ADD_UP in ReceiptCandidates.from(lines, context)!!.flags)
    }

    @Test fun addedTaxAndSubtotalCount() {
        val withTax = listOf("CAFE", "Latte  4.50", "Bagel  3.00", "Tax  0.60", "TOTAL  8.10")
        assertEquals(ReceiptParser.ItemsCheck.ADDS_UP, ReceiptParser.checkItems(withTax, BigDecimal("8.10")))
        val withSubtotal = listOf("SHOP", "Item  10.00", "SUBTOTAL  10.00", "VAT 12%  1.20", "TOTAL  11.20")
        assertEquals(ReceiptParser.ItemsCheck.ADDS_UP, ReceiptParser.checkItems(withSubtotal, BigDecimal("11.20")))
        assertEquals(ReceiptParser.ItemsCheck.UNKNOWN, ReceiptParser.checkItems(listOf("SHOP", "TOTAL 99.00"), BigDecimal("99.00")))
    }

    @Test fun noLabelledTotalIsNeverPrefilled() {
        val c = ReceiptCandidates.from(listOf("MARKET STALL", "Mangoes  120.00", "Bananas  45.00"), context)!!
        assertNull(c.amount)
        assertTrue(CandidateFlag.TOTAL_UNCONFIRMED in c.flags)
        assertEquals(BigDecimal("120.00"), c.amountOptions.first())
    }

    @Test fun missingDateDefaultsToTodayAndSaysSo() {
        val c = ReceiptCandidates.from(listOf("KIOSK", "TOTAL 50.00"), context)!!
        assertEquals(today, c.date)
        assertTrue(CandidateFlag.DATE_UNCERTAIN in c.flags)
    }

    @Test fun foreignReceiptGoesToAnAccountInThatCurrency() {
        val c = ReceiptCandidates.from(listOf("BOULANGERIE", "TOTAL €12,40"), context)!!
        assertEquals("EUR", c.mentionedCurrency)
        assertEquals(2L, c.walletId)
    }

    @Test fun unreadableReceipt() {
        assertNull(ReceiptCandidates.from(listOf("", "  "), context))
        val c = ReceiptCandidates.from(listOf("blurry text only"), context)!!
        assertNull(c.amount)
        assertTrue(CandidateFlag.AMOUNT_MISSING in c.flags)
    }
}
