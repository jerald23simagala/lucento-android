package com.lucento.app.data.ai

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.capture.CandidateFlag
import com.lucento.app.domain.capture.InterpretationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RemoteCandidatesTest {
    private val today = LocalDate.of(2026, 10, 27)
    private val context = InterpretationContext(
        wallets = listOf(
            Wallet(1, "Cash", WalletType.CASH, Money(0, "PHP"), 0),
            Wallet(2, "BPI", WalletType.BANK, Money(0, "PHP"), 0),
        ),
        categories = listOf(
            Category(10, "Food", CategoryKind.EXPENSE, "food"),
            Category(11, "Salary", CategoryKind.INCOME, "salary"),
        ),
        today = today,
        defaultWalletId = 1,
    )

    @Test
    fun `good rows become candidates with the user's own ids`() {
        val rows = RemoteCandidates.toCandidates(
            listOf(
                RemoteItem("Lunch", "EXPENSE", "250.50", walletId = 2, categoryId = 10, date = "2026-10-26"),
                RemoteItem("Pay", "INCOME", "30000", categoryId = 11),
                RemoteItem("To savings", "TRANSFER", "1000", walletId = 1, toWalletId = 2),
            ),
            context,
            "lunch 250.50 bpi yesterday, pay 30000, move 1000 to bpi",
        )
        assertEquals(3, rows.size)
        assertEquals(BigDecimal("250.50"), rows[0].amount)
        assertEquals(2L, rows[0].walletId)
        assertTrue(rows[0].walletFromText)
        assertEquals(LocalDate.of(2026, 10, 26), rows[0].date)
        assertEquals(TransactionType.INCOME, rows[1].type)
        assertEquals(1L, rows[1].walletId) // the composer's default when the words named none
        assertEquals(TransactionType.TRANSFER_OUT, rows[2].type)
        assertEquals(2L, rows[2].toWalletId)
    }

    @Test
    fun `nothing from the model is trusted blindly`() {
        val rows = RemoteCandidates.toCandidates(
            listOf(
                RemoteItem("Ghost wallet", "EXPENSE", "10", walletId = 99, categoryId = 999),
                RemoteItem("Salary as spending", "EXPENSE", "-5", categoryId = 11),
                RemoteItem("Transfer nowhere", "TRANSFER", "5", walletId = 1),
                RemoteItem("Refund?", "REFUND", "5"),
                RemoteItem("Odd date", "EXPENSE", "abc", date = "next tuesday"),
            ),
            context,
            "…",
        )
        assertEquals(3, rows.size) // the transfer without a destination and the unknown type are dropped
        assertEquals(1L, rows[0].walletId)
        assertNull(rows[0].categoryId)
        assertTrue(CandidateFlag.CATEGORY_UNMATCHED in rows[0].flags)
        assertNull(rows[1].amount)
        assertNull(rows[1].categoryId) // an income category never lands on spending
        assertTrue(CandidateFlag.AMOUNT_MISSING in rows[1].flags)
        assertEquals(today, rows[2].date)
        assertTrue(CandidateFlag.DATE_UNCERTAIN in rows[2].flags)
    }
}
