package com.lucento.app.domain.capture

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BatchReviewTest {

    private val today = LocalDate.parse("2026-10-22")
    private val wallets = listOf(
        Wallet(1, "Cash", WalletType.CASH, Money(0, "PHP"), 0),
        Wallet(2, "Savings", WalletType.SAVINGS, Money(0, "PHP"), 0),
        Wallet(3, "Travel", WalletType.BANK, Money(0, "USD"), 0),
        Wallet(4, "Tokyo", WalletType.CASH, Money(0, "JPY"), 0),
    )
    private val categories = listOf(
        Category(10, "Dining", CategoryKind.EXPENSE, "dining"),
        Category(12, "Groceries", CategoryKind.EXPENSE, "groceries"),
        Category(20, "Salary", CategoryKind.INCOME, "work"),
        Category(21, "Refunds", CategoryKind.INCOME, "refund"),
    )
    private val setup = ReviewSetup(wallets, categories, '.', transfersAllowed = true)
    private val context = InterpretationContext(wallets, categories, today, defaultWalletId = 1)

    private fun rows(text: String) = BatchEntryParser.parse(text, context)
        .mapIndexed { i, c -> ReviewRow.from(c, "k$i", 1, CaptureSource.TYPED, '.') }

    private fun check(row: ReviewRow, s: ReviewSetup = setup) = BatchReview.check(row, s)

    @Test fun cleanRowsAreReadyAndSummarisedPerType() {
        val list = rows("Spent 250 on lunch, 180 for coffee, paid 1200 for groceries, and received 35000 salary")
        val statuses = list.associate { it.key to check(it) }
        assertTrue(statuses.values.all { it.ready })
        val summary = BatchReview.summarize(list, statuses)
        assertEquals(4, summary.ready)
        assertEquals(listOf(Money(1_630_00, "PHP")), summary.expenses)
        assertEquals(listOf(Money(35_000_00, "PHP")), summary.income)
    }

    @Test fun missingAmountBlocksOnlyThatRow() {
        val list = rows("Lunch 250, coffee 180, and paid the electricity bill")
        val statuses = list.associate { it.key to check(it) }
        assertEquals(listOf(true, true, false), list.map { statuses.getValue(it.key).ready })
        assertEquals(RowField.AMOUNT, statuses.getValue("k2").issues.first { it.blocking }.field)
        val summary = BatchReview.summarize(list, statuses)
        assertEquals(2, summary.ready)
        assertEquals(1, summary.needsAttention)
        assertFalse(summary.canSave)
    }

    @Test fun batchSavesOnlyWhenEveryRowIsComplete() {
        val list = rows("Lunch 250, coffee 180, and paid the electricity bill")
        val fixed = BatchReview.edit(list, "k2", RowEdit.Amount("1500"), categories, '.')
        val summary = BatchReview.summarize(fixed, fixed.associate { it.key to check(it) })
        assertEquals(0, summary.needsAttention)
        assertTrue(summary.canSave)
        assertFalse(BatchReview.summarize(emptyList(), emptyMap()).canSave)
    }

    @Test fun editingOneRowLeavesTheOthersAlone() {
        val list = rows("lunch 250, coffee 180")
        val edited = BatchReview.edit(list, "k1", RowEdit.Amount("200"), categories, '.')
        assertEquals(list[0], edited[0])
        assertEquals("200", edited[1].amountText)
        assertTrue(RowField.AMOUNT in edited[1].edited)
    }

    @Test fun removingARow() {
        val list = rows("lunch 250, coffee 180, groceries 900")
        val left = BatchReview.remove(list, "k1")
        assertEquals(listOf("k0", "k2"), left.map { it.key })
    }

    @Test fun choosingAnAmbiguousAmount() {
        val row = rows("12 eggs 95").single()
        assertEquals("Which amount?", check(row).issueFor(RowField.AMOUNT)?.message)
        val chosen = BatchReview.apply(row, RowEdit.ChooseAmount(BigDecimal("95")), categories, '.')
        assertTrue(check(chosen).ready)
        assertFalse(CandidateFlag.AMOUNT_AMBIGUOUS in chosen.flags)
    }

    @Test fun localeDecimalSeparator() {
        val row = ReviewRow.from(BatchEntryParser.parse("coffee 4.50", context).single(), "k", 1, CaptureSource.TYPED, ',')
        assertEquals("4,5", row.amountText)
        assertEquals(Money(4_50, "PHP"), check(row, setup.copy(separator = ',')).money)
    }

    @Test fun invalidAmounts() {
        val row = rows("lunch 250").single()
        fun amountIssue(text: String, wallet: Long = 1) =
            check(row.copy(amountText = text, walletId = wallet)).issueFor(RowField.AMOUNT)?.message
        assertEquals("Enter a valid amount", amountIssue("abc"))
        assertEquals("The amount must be more than zero", amountIssue("0"))
        assertEquals("JPY has no decimal places", amountIssue("4.50", wallet = 4))
    }

    @Test fun changingTypeFindsAFittingCategory() {
        val row = rows("refund 500").single()
        val asExpense = BatchReview.apply(row.copy(type = TransactionType.INCOME, categoryId = 21), RowEdit.Type(TransactionType.EXPENSE), categories, '.')
        assertNull(asExpense.categoryId)
        assertTrue(CandidateFlag.CATEGORY_UNMATCHED in asExpense.flags)
        val lunch = rows("lunch 250").single()
        val asIncome = BatchReview.apply(lunch, RowEdit.Type(TransactionType.INCOME), categories, '.')
        assertEquals(TransactionType.INCOME, asIncome.type)
        assertNull(asIncome.categoryId) // Dining doesn't fit income
        val transfer = BatchReview.apply(lunch, RowEdit.Type(TransactionType.TRANSFER_OUT), categories, '.')
        assertNull(transfer.categoryId)
        assertEquals(RowField.TO_WALLET, check(transfer).issues.first { it.blocking }.field)
    }

    @Test fun missingCategoryIsAHintNotABlock() {
        val row = rows("spent 500 on a birthday cake").single()
        val status = check(row)
        assertTrue(status.ready)
        assertFalse(status.issueFor(RowField.CATEGORY)!!.blocking)
    }

    @Test fun mentionedCurrencyThatTheAccountDoesntUseBlocksUntilResolved() {
        val row = rows("lunch 250 euros").single()
        val blocked = check(row)
        assertFalse(blocked.ready)
        assertEquals(RowField.WALLET, blocked.issues.first { it.blocking }.field)
        assertTrue(check(BatchReview.apply(row, RowEdit.Amount("250"), categories, '.')).ready)
    }

    @Test fun transferRules() {
        val row = rows("Transferred 5000 to savings").single()
        assertTrue(check(row).ready)
        assertFalse(check(row, setup.copy(transfersAllowed = false)).ready)
        assertEquals("Choose two different accounts", check(row.copy(toWalletId = 1)).issueFor(RowField.TO_WALLET)?.message)
        assertFalse(check(row.copy(toWalletId = 3)).ready) // PHP → USD
        val summary = BatchReview.summarize(listOf(row), mapOf(row.key to check(row)))
        assertEquals(1, summary.transfers)
        assertTrue(summary.expenses.isEmpty())
    }

    @Test fun alreadySavedHint() {
        val row = rows("lunch 250").single()
        val saved = Transaction(9, TransactionType.EXPENSE, Money(250_00, "PHP"), 1, 10, today, "Lunch", 0)
        val status = check(row, setup.copy(history = listOf(saved)))
        assertTrue(status.ready)
        assertNotNull(status.issues.firstOrNull { it.field == RowField.DESCRIPTION && !it.blocking })
        assertTrue(check(row.copy(date = today.minusDays(1)), setup.copy(history = listOf(saved))).issues.isEmpty())
    }

    @Test fun resendingKeepsTheUsersCorrections() {
        val old = rows("lunch 250, coffee 180")
        val corrected = BatchReview.edit(old, "k1", RowEdit.CategoryChoice(12), categories, '.')
            .let { BatchReview.edit(it, "k0", RowEdit.Amount("260"), categories, '.') }
        val fresh = BatchEntryParser.parse("lunch 250, coffee 180, dinner 600", context)
            .mapIndexed { i, c -> ReviewRow.from(c, "n$i", 2, CaptureSource.TYPED, '.') }
        val merged = BatchReview.carryOver(corrected, fresh, categories)
        assertEquals(listOf("k0", "k1", "n2"), merged.map { it.key })
        assertEquals("260", merged[0].amountText)
        assertEquals(12L, merged[1].categoryId)
        assertEquals("600", merged[2].amountText)
    }

    @Test fun mixedCurrencyTotalsStaySeparate() {
        val a = rows("lunch 250").single()
        val b = rows("coffee 5").single().copy(key = "x", walletId = 3)
        val statuses = mapOf(a.key to check(a), b.key to check(b))
        assertEquals(setOf(Money(250_00, "PHP"), Money(5_00, "USD")), BatchReview.summarize(listOf(a, b), statuses).expenses.toSet())
    }

    @Test fun switchingTheActiveWalletMovesOnlyRowsThatFollowedIt() {
        val list = rows("lunch 250, coffee 180 from Savings, dinner 400")
        val chosen = BatchReview.edit(list, "k2", RowEdit.Account(1), categories, '.')
        val moved = BatchReview.retarget(chosen, 4)
        assertEquals(listOf(4L, 2L, 1L), moved.map { it.walletId }) // named and user-chosen accounts stay
    }

    @Test fun switchingTheActiveWalletNeverMakesATransferPointAtItself() {
        val transfer = rows("transfer 500 to Savings").single()
        assertEquals(1L, transfer.walletId)
        assertEquals(1L, BatchReview.retarget(listOf(transfer), 2).single().walletId)
        assertEquals(3L, BatchReview.retarget(listOf(transfer), 3).single().walletId)
    }
}
