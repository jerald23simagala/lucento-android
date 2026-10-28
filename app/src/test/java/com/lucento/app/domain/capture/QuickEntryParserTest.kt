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

class QuickEntryParserTest {

    private val today = LocalDate.parse("2026-10-22") // a Thursday
    private val wallets = listOf(
        Wallet(1, "Cash Wallet", WalletType.CASH, Money(0, "PHP"), 0),
        Wallet(2, "BPI Savings", WalletType.SAVINGS, Money(0, "PHP"), 0),
        Wallet(3, "GCash", WalletType.E_WALLET, Money(0, "PHP"), 0),
        Wallet(4, "Main Account", WalletType.BANK, Money(0, "PHP"), 0),
        Wallet(5, "Visa", WalletType.CREDIT_CARD, Money(0, "PHP"), 0),
    )
    private val categories = listOf(
        Category(10, "Dining", CategoryKind.EXPENSE, "dining"),
        Category(11, "Transport", CategoryKind.EXPENSE, "transport"),
        Category(12, "Groceries", CategoryKind.EXPENSE, "groceries"),
        Category(13, "Pet food", CategoryKind.EXPENSE, "other"),
        Category(20, "Salary", CategoryKind.INCOME, "work"),
        Category(21, "Refunds", CategoryKind.INCOME, "refund"),
    )

    private fun parse(text: String) = QuickEntryParser.parse(text, wallets, categories, today)

    @Test fun theCanonicalExample() {
        val p = parse("Spent 250 pesos on lunch from my cash wallet")
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals(BigDecimal("250"), p.amount)
        assertEquals(1L, p.walletId)
        assertEquals(10L, p.categoryId)
        assertEquals(today, p.date)
        assertEquals("Lunch", p.note)
        assertEquals("PHP", p.mentionedCurrency)
        assertTrue(p.warnings.isEmpty())
    }

    @Test fun giftsAndMoneyFromAPersonAreIncome() {
        assertEquals(TransactionType.INCOME, parse("got 20 from mom").type)
        assertEquals(BigDecimal("20"), parse("got 20 from mom").amount)
        assertEquals(TransactionType.INCOME, parse("mom gave me 20").type)
        assertEquals(BigDecimal("20"), parse("mom gave me 20").amount)
        assertEquals(TransactionType.INCOME, parse("received 500 salary").type)
        assertEquals(BigDecimal("500"), parse("received 500 salary").amount)
        assertEquals(TransactionType.INCOME, parse("refund 15 from shop").type)
        assertEquals(BigDecimal("15"), parse("refund 15 from shop").amount)
    }

    @Test fun plainSpendingStaysAnExpense() {
        assertEquals(TransactionType.EXPENSE, parse("coffee 5").type)
        assertEquals(BigDecimal("5"), parse("coffee 5").amount)
        assertEquals(TransactionType.EXPENSE, parse("paid 20 to mom").type)
        assertEquals(BigDecimal("20"), parse("paid 20 to mom").amount)
    }

    @Test fun purchasesThatMerelyUseGotOrReceivedStayExpenses() {
        val jollibee = parse("got 500 from Jollibee")
        assertEquals(TransactionType.EXPENSE, jollibee.type)
        assertEquals(BigDecimal("500"), jollibee.amount)
        assertEquals(10L, jollibee.categoryId)
        assertEquals(TransactionType.EXPENSE, parse("got lunch from jollibee 150").type)
        assertEquals(BigDecimal("150"), parse("got lunch from jollibee 150").amount)
        assertEquals(TransactionType.EXPENSE, parse("got coffee from starbucks 180").type)
        assertEquals(BigDecimal("180"), parse("got coffee from starbucks 180").amount)
        assertEquals(TransactionType.EXPENSE, parse("got groceries 500 from sm").type)
        assertEquals(BigDecimal("500"), parse("got groceries 500 from sm").amount)
        assertEquals(TransactionType.EXPENSE, parse("received a parcel from lazada 300 shipping fee").type)
        assertEquals(BigDecimal("300"), parse("received a parcel from lazada 300 shipping fee").amount)
    }

    @Test fun incomeWithGroupedThousandsAndYesterday() {
        val p = parse("got paid 52,000 salary yesterday into Main Account")
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals(BigDecimal("52000"), p.amount)
        assertEquals(4L, p.walletId)
        assertEquals(20L, p.categoryId)
        assertEquals(today.minusDays(1), p.date)
    }

    @Test fun decimalsAndCurrencySigns() {
        assertEquals(BigDecimal("4.5"), parse("coffee ₱4.50 gcash").amount)
        assertEquals(BigDecimal("1250.75"), parse("groceries 1,250.75").amount)
        assertEquals(BigDecimal("1250.75"), parse("groceries 1.250,75").amount)
        assertEquals(BigDecimal("12.5"), parse("taxi 12,5").amount)
        assertEquals(BigDecimal("1500"), parse("rent 1.5k").amount)
    }

    @Test fun neverInventsAnAmount() {
        val p = parse("lunch with friends")
        assertNull(p.amount)
        assertTrue(p.warnings.any { it.contains("amount") })
    }

    @Test fun transfersFindSourceAndDestination() {
        val p = parse("transfer 5000 from Main Account to BPI Savings")
        assertEquals(TransactionType.TRANSFER_OUT, p.type)
        assertEquals(4L, p.walletId)
        assertEquals(2L, p.toWalletId)
        assertNull(p.categoryId)

        val pay = parse("paid my credit card 3000 from Main Account")
        assertEquals(TransactionType.TRANSFER_OUT, pay.type)
        assertEquals(4L, pay.walletId)
        assertEquals(5L, pay.toWalletId)

        // With only the card named, the card is where the money goes; the source is left to choose.
        val cardOnly = parse("paid my credit card 3000")
        assertEquals(TransactionType.TRANSFER_OUT, cardOnly.type)
        assertEquals(5L, cardOnly.toWalletId)
        assertNull(cardOnly.walletId)
    }

    @Test fun relativeDates() {
        assertEquals(today.minusDays(3), parse("grab 120 3 days ago").date)
        assertEquals(today.minusDays(2), parse("grab 120 day before yesterday").date)
        assertEquals(LocalDate.parse("2026-10-19"), parse("grab 120 on monday").date)
        assertEquals(today, parse("grab 120 thursday").date)
        assertEquals(today.minusDays(7), parse("grab 120 last thursday").date)
    }

    @Test fun customCategoryNamesWinOverKeywords() {
        assertEquals(13L, parse("pet food 300 from cash").categoryId)
        assertEquals(11L, parse("grab ride 180").categoryId)
    }

    @Test fun warnsAboutCurrencyMismatchWithoutConverting() {
        val p = parse("spent 20 dollars on lunch from cash")
        assertEquals(1L, p.walletId)
        assertTrue(p.warnings.any { it.contains("won’t convert") })
    }

    @Test fun asksForAccountWhenAmbiguous() {
        val p = parse("spent 90 on snacks")
        assertNull(p.walletId)
        assertTrue(p.warnings.any { it.contains("account") })
    }
}
