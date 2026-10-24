package com.lucento.app.domain.capture

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.TransactionType.EXPENSE
import com.lucento.app.domain.TransactionType.INCOME
import com.lucento.app.domain.TransactionType.TRANSFER_OUT
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BatchEntryParserTest {

    private val today = LocalDate.parse("2026-10-22") // a Thursday
    private val wallets = listOf(
        Wallet(1, "Cash", WalletType.CASH, Money(0, "PHP"), 0),
        Wallet(2, "BPI Savings", WalletType.SAVINGS, Money(0, "PHP"), 0),
        Wallet(3, "GCash", WalletType.E_WALLET, Money(0, "PHP"), 0),
        Wallet(4, "Travel USD", WalletType.BANK, Money(0, "USD"), 0),
    )
    private val categories = listOf(
        Category(10, "Restaurants", CategoryKind.EXPENSE, "dining"),
        Category(11, "Cafes and coffee", CategoryKind.EXPENSE, "cafe"),
        Category(12, "Groceries", CategoryKind.EXPENSE, "groceries"),
        Category(13, "Electricity", CategoryKind.EXPENSE, "utilities"),
        Category(14, "Parking and tolls", CategoryKind.EXPENSE, "parking"),
        Category(20, "Salary", CategoryKind.INCOME, "work"),
        Category(21, "Business", CategoryKind.INCOME, "business"),
    )
    private val context = InterpretationContext(wallets, categories, today, defaultWalletId = 1)

    private fun parse(text: String, ctx: InterpretationContext = context) = BatchEntryParser.parse(text, ctx)
    private fun amounts(list: List<TransactionCandidate>) = list.map { it.amount?.toPlainString() }

    @Test fun theBriefsFourTransactions() {
        val rows = parse("Spent 250 on lunch, 180 for coffee, paid 1200 for groceries, and received 35000 salary.")
        assertEquals(listOf("250", "180", "1200", "35000"), amounts(rows))
        assertEquals(listOf(EXPENSE, EXPENSE, EXPENSE, INCOME), rows.map { it.type })
        assertEquals(listOf(10L, 11L, 12L, 20L), rows.map { it.categoryId })
        assertEquals(listOf("Lunch", "Coffee", "Groceries", "Salary"), rows.map { it.description })
        assertTrue(rows.all { it.date == today && it.walletId == 1L && it.flags.isEmpty() })
    }

    @Test fun placeholderStyleList() {
        val rows = parse("Lunch 250, groceries 800, salary 35000")
        assertEquals(listOf("250", "800", "35000"), amounts(rows))
        assertEquals(listOf(EXPENSE, EXPENSE, INCOME), rows.map { it.type })
    }

    @Test fun oneDateAppliesToTheWholeMessage() {
        val rows = parse("Yesterday I spent 180 on coffee, 650 on dinner, paid 1200 for groceries, and received 5000 from a client.")
        assertEquals(4, rows.size)
        assertTrue(rows.all { it.date == today.minusDays(1) })
        assertEquals(listOf(EXPENSE, EXPENSE, EXPENSE, INCOME), rows.map { it.type })
        assertEquals(21L, rows[3].categoryId)
        assertEquals("Client", rows[3].description)
    }

    @Test fun spokenBatchWithPesosAndIncome() {
        val rows = parse("Add 450 pesos for dinner, 90 for parking, and I received 2,000 from freelance work.")
        assertEquals(listOf("450", "90", "2000"), amounts(rows))
        assertEquals(listOf(EXPENSE, EXPENSE, INCOME), rows.map { it.type })
        assertEquals(listOf(10L, 14L, 21L), rows.map { it.categoryId })
        assertEquals(listOf("Dinner", "Parking", "Freelance work"), rows.map { it.description })
    }

    @Test fun numberWords() {
        val rows = parse("spent two thousand five hundred on groceries and a hundred and fifty on coffee")
        assertEquals(listOf("2500", "150"), amounts(rows))
    }

    @Test fun singleIncomeAndExpense() {
        assertEquals(INCOME, parse("Received salary of 45000").single().type)
        val bill = parse("Paid electricity bill 2200").single()
        assertEquals(EXPENSE, bill.type)
        assertEquals(13L, bill.categoryId)
        assertEquals("Electricity bill", bill.description)
        assertEquals(INCOME, parse("Received 3000 from freelance work").single().type)
    }

    @Test fun transferStaysATransfer() {
        val row = parse("Transferred 5000 to savings").single()
        assertEquals(TRANSFER_OUT, row.type)
        assertEquals(2L, row.toWalletId)
        assertEquals(1L, row.walletId) // the default account, as the manual transfer form does
        assertNull(row.categoryId)
    }

    @Test fun explicitDates() {
        assertEquals(LocalDate.parse("2026-10-12"), parse("Lunch 250 on October 12").single().date)
        assertEquals(LocalDate.parse("2026-10-12"), parse("October 12, 2026: lunch 250").single().date)
        // No year and still ahead this year: last year's.
        assertEquals(LocalDate.parse("2025-12-24"), parse("Gifts 3000 Dec 24").single().date)
        assertEquals(LocalDate.parse("2026-10-16"), parse("dinner 600 last friday").single().date)
        assertEquals(LocalDate.parse("2026-10-12"), parse("12 October lunch 250").single().date)
    }

    @Test fun differentDatesCarryForward() {
        val rows = parse("Yesterday lunch 250, coffee 120. Today parking 60")
        assertEquals(listOf(today.minusDays(1), today.minusDays(1), today), rows.map { it.date })
    }

    @Test fun missingAmountIsFlaggedNotInvented() {
        val row = parse("paid the electricity bill").single()
        assertNull(row.amount)
        assertTrue(CandidateFlag.AMOUNT_MISSING in row.flags)
    }

    @Test fun oneUnclearRowDoesNotStopTheOthers() {
        val rows = parse("Lunch 250, coffee 180, and paid the electricity bill")
        assertEquals(listOf("250", "180", null), amounts(rows))
        assertTrue(CandidateFlag.AMOUNT_MISSING in rows[2].flags)
    }

    @Test fun competingAmountsAreChoices() {
        val row = parse("12 eggs 95").single()
        assertNull(row.amount)
        assertTrue(CandidateFlag.AMOUNT_AMBIGUOUS in row.flags)
        assertEquals(listOf(BigDecimal("95"), BigDecimal("12")), row.amountOptions)
    }

    @Test fun quantitiesAreNotPrices() {
        assertEquals("1500", parse("bought 2 shirts 1500").single().amount?.toPlainString())
    }

    @Test fun unpunctuatedLists() {
        assertEquals(listOf("250", "180"), amounts(parse("lunch 250 coffee 180")))
        assertEquals(listOf("250", "180"), amounts(parse("spent 250 on lunch 180 on coffee")))
        assertEquals(listOf("250", "180"), amounts(parse("lunch 250 pesos coffee 180 pesos")))
    }

    @Test fun amountlessWordsJoinTheirNeighbour() {
        val row = parse("Spent 300 on fish and chips").single()
        assertEquals("Fish and chips", row.description)
        assertEquals(1, parse("fish and chips 300").size)
    }

    @Test fun unknownCategoryIsFlagged() {
        val row = parse("spent 500 on a birthday cake").single()
        assertNull(row.categoryId)
        assertTrue(CandidateFlag.CATEGORY_UNMATCHED in row.flags)
    }

    @Test fun clausesFollowTheirNeighboursType() {
        val rows = parse("received 5000 from a client and 2000 from a friend")
        assertEquals(listOf(INCOME, INCOME), rows.map { it.type })
    }

    @Test fun typeHintFromAWallet() {
        val row = parse("2000 from a friend", context.copy(typeHint = INCOME)).single()
        assertEquals(INCOME, row.type)
    }

    @Test fun namedAccountAndCurrency() {
        val gcash = parse("Grab 180 from GCash").single()
        assertEquals(3L, gcash.walletId)
        val usd = parse("coffee 4.50 dollars").single()
        assertEquals(4L, usd.walletId)
        assertEquals("USD", usd.mentionedCurrency)
    }

    @Test fun thousandsAndYearsAreNotListCommas() {
        val rows = parse("Rent 12,500, internet 1,699")
        assertEquals(listOf("12500", "1699"), amounts(rows))
    }

    @Test fun nothingToRecord() {
        assertTrue(parse("hello there").isEmpty())
        assertTrue(parse("   ").isEmpty())
    }
}
