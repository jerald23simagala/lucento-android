package com.lucento.app.domain.capture

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType.EXPENSE
import com.lucento.app.domain.TransactionType.INCOME
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The Google Play listings' descriptions promise what the composer does with their example sentences
 * (docs/play/PLAY_STORE_SUBMISSION.md §4). These read them on the phone, with the preset categories a new person picks, so
 * a listing never promises something the app doesn't do.
 */
class ListingExampleTest {
    private val today = LocalDate.parse("2026-11-26")
    private val wallets = listOf(Wallet(1, "Cash", WalletType.CASH, Money(0, "PHP"), 0))
    private val categories = listOf(
        Category(10, "Public transportation", CategoryKind.EXPENSE, "transport"),
        Category(11, "Mobile phone", CategoryKind.EXPENSE, "phone"),
        Category(12, "Electricity", CategoryKind.EXPENSE, "utilities"),
        Category(13, "Groceries", CategoryKind.EXPENSE, "groceries"),
        Category(14, "Restaurants", CategoryKind.EXPENSE, "dining"),
        Category(20, "Salary", CategoryKind.INCOME, "work"),
    )
    private val context = InterpretationContext(wallets, categories, today, defaultWalletId = 1)

    @Test fun thePhilippineListingsSentence() {
        val rows = BatchEntryParser.parse("jeep 13, load 100, electric bill 2450, sweldo 25000", context)
        assertEquals(listOf("13", "100", "2450", "25000"), rows.map { it.amount?.toPlainString() })
        assertEquals(listOf(EXPENSE, EXPENSE, EXPENSE, INCOME), rows.map { it.type })
        assertEquals(listOf(10L, 11L, 12L, 20L), rows.map { it.categoryId })
    }

    @Test fun theGlobalListingsSentence() {
        val rows = BatchEntryParser.parse("coffee 4.50, groceries 85, salary 3000", context)
        assertEquals(listOf("4.5", "85", "3000"), rows.map { it.amount?.stripTrailingZeros()?.toPlainString() })
        assertEquals(listOf(EXPENSE, EXPENSE, INCOME), rows.map { it.type })
    }
}
