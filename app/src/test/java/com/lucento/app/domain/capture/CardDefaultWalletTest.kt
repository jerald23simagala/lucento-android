package com.lucento.app.domain.capture

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType.EXPENSE
import com.lucento.app.domain.TransactionType.INCOME
import com.lucento.app.domain.TransactionType.TRANSFER_OUT
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tester round (2026-11-18): a credit card can be the default wallet. Expenses then go to it; income and transfers go to
 * the first cash, bank or e-wallet instead (owner's choice), unless the person picked the card for this entry.
 */
class CardDefaultWalletTest {

    private val today = LocalDate.parse("2026-11-18")
    private val visa = Wallet(1, "Visa", WalletType.CREDIT_CARD, Money(0, "PHP"), 0)
    private val bank = Wallet(2, "Everyday bank", WalletType.BANK, Money(0, "PHP"), 0)
    private val savings = Wallet(3, "Savings", WalletType.SAVINGS, Money(0, "PHP"), 0)
    private val wallets = listOf(visa, bank, savings)
    private val categories = listOf(
        Category(10, "Restaurants", CategoryKind.EXPENSE, "dining"),
        Category(20, "Salary", CategoryKind.INCOME, "work"),
    )

    /** As the composer builds it while the card is only the saved default: income and transfers have a money account. */
    private val cardDefault = InterpretationContext(wallets, categories, today, defaultWalletId = visa.id, moneyWalletId = bank.id)

    @Test fun `cash, bank, e-wallets and credit cards can be the default, nothing else`() {
        val allowed = WalletType.entries.filter { it.canBeDefault }.toSet()
        assertEquals(setOf(WalletType.CASH, WalletType.BANK, WalletType.E_WALLET, WalletType.CREDIT_CARD), allowed)
        assertFalse(WalletType.LOAN.canBeDefault)
        assertFalse(WalletType.SAVINGS.canBeDefault)
    }

    @Test fun `a card default takes expenses, a money account takes income and transfers`() {
        assertEquals(visa.id, cardDefault.walletFor(EXPENSE))
        assertEquals(bank.id, cardDefault.walletFor(INCOME))
        assertEquals(bank.id, cardDefault.walletFor(TRANSFER_OUT))
    }

    @Test fun `without a money account to use, everything stays on the default`() {
        // The person picked the card (moneyWalletId null), or there is no cash, bank or e-wallet at all.
        val picked = cardDefault.copy(moneyWalletId = null)
        assertEquals(visa.id, picked.walletFor(INCOME))
        assertEquals(visa.id, picked.walletFor(TRANSFER_OUT))
        // A money account that no longer exists is ignored.
        val gone = cardDefault.copy(moneyWalletId = 99)
        assertEquals(visa.id, gone.walletFor(INCOME))
    }

    @Test fun `typed entries follow the rule`() {
        val rows = BatchEntryParser.parse("Lunch 250, salary 35000", cardDefault)
        assertEquals(listOf(EXPENSE, INCOME), rows.map { it.type })
        assertEquals(listOf(visa.id, bank.id), rows.map { it.walletId })
    }

    @Test fun `a transfer from nowhere named starts at the money account, not the card`() {
        val row = BatchEntryParser.parse("Transferred 5000 to savings", cardDefault).single()
        assertEquals(TRANSFER_OUT, row.type)
        assertEquals(bank.id, row.walletId)
        assertEquals(savings.id, row.toWalletId)
    }

    @Test fun `a default that isn't a card serves every type, as before`() {
        val bankDefault = InterpretationContext(wallets, categories, today, defaultWalletId = bank.id)
        assertTrue(listOf(EXPENSE, INCOME, TRANSFER_OUT).all { bankDefault.walletFor(it) == bank.id })
    }
}
