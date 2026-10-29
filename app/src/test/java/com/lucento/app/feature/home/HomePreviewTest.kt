package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.FakeCategoryDao
import com.lucento.app.data.repository.FakeTransactionDao
import com.lucento.app.data.repository.FakeWalletDao
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.PreviewTour
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomePreviewTest {

    @Test fun openingTheTourRendersExampleDataAndDoesNotChangeRoomCounts() = runTest {
        val walletDao = FakeWalletDao()
        val categoryDao = FakeCategoryDao()
        val transactionDao = FakeTransactionDao()
        walletDao.transactions = transactionDao
        val wallets = WalletRepository(walletDao, transactionDao, clock = { 1L })
        val categories = CategoryRepository(categoryDao, clock = { 1L })
        val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2L })
        val today = LocalDate.parse("2026-10-28")

        val cash = (wallets.create("Everyday", WalletType.BANK, Money(50_00, "USD")) as WalletRepository.SaveResult.Saved).id
        val food = (categories.create("Groceries", CategoryKind.EXPENSE, "groceries") as CategoryRepository.SaveResult.Saved).id
        val saved = transactions.save(
            TransactionRepository.Input(null, "rent-1", TransactionType.EXPENSE, Money(20_00, "USD"), cash, food, today, "Rent"),
        )
        assertTrue(saved is TransactionRepository.SaveResult.Saved)

        val countsBefore = ledgerCounts(walletDao, categoryDao, transactionDao)
        val real = HomeStateBuilder.build(
            all = transactions.observeTransactions().first(),
            wallets = wallets.observeWallets().first(),
            categories = categories.observeCategories().first(),
            primaryCurrency = "USD",
            today = today,
        )
        assertEquals("Rent", real.recentEntries.single().note)

        val shown = openExampleTour(real)
        assertEquals(countsBefore, ledgerCounts(walletDao, categoryDao, transactionDao))

        val example = PreviewTour.example("USD")
        assertEquals(example.balance, shown.wallets.single().balance)
        assertEquals(example.entryNote, shown.recentEntries.single().note)
        assertEquals(example.entry, shown.recentEntries.single().amount)
        assertTrue(shown.recentEntries.none { it.note == "Rent" })
        assertTrue(shown.wallets.none { it.name == "Everyday" })
        assertEquals("Rent", real.recentEntries.single().note)
    }

    private fun ledgerCounts(wallets: FakeWalletDao, categories: FakeCategoryDao, transactions: FakeTransactionDao) =
        Triple(wallets.rows.value.size, categories.rows.value.size, transactions.rows.value.size)
}
