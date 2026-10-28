package com.lucento.app.feature.capture

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.FakeCategoryDao
import com.lucento.app.data.repository.FakeTransactionDao
import com.lucento.app.data.repository.FakeWalletDao
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.capture.CaptureSource
import com.lucento.app.domain.capture.LocalTransactionInterpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * The on-device path: the composer ViewModel sends the typed line through [LocalTransactionInterpreter]
 * (BatchEntryParser) and saves that row. "got 500 from Jollibee" must not become income just because
 * the amount sits next to "got", including when the screen was opened with income as the default kind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposerQuickEntryTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1_000L })
    private val categories = CategoryRepository(categoryDao, clock = { 1_000L })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2_000L })

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun got500FromJollibeeSavesAsAnExpense() = runTest {
        val cash = (wallets.create("Cash", WalletType.CASH, Money(0, "PHP")) as WalletRepository.SaveResult.Saved).id
        val dining = (categories.create("Restaurants", CategoryKind.EXPENSE, "dining") as CategoryRepository.SaveResult.Saved).id
        val viewModel = ComposerViewModel(
            transactions,
            wallets,
            categories,
            LocalTransactionInterpreter(),
            today = { LocalDate.parse("2026-10-28") },
        )
        val collecting = launch(UnconfinedTestDispatcher()) { viewModel.state.collect {} }
        try {
            viewModel.state.first { it.loaded && it.wallets.any { wallet -> wallet.id == cash } }
            // Opened from a wallet's Income action: the hint must not turn a merchant purchase into income.
            viewModel.configure(walletId = cash, typeHint = TransactionType.INCOME, transfersEnabled = false)
            viewModel.send("got 500 from Jollibee", CaptureSource.TYPED)

            val row = viewModel.state.first { it.rows.isNotEmpty() }.rows.single()
            assertEquals(TransactionType.EXPENSE, row.type)
            assertEquals("500", row.amountText)
            assertEquals(dining, row.categoryId)
            assertTrue(viewModel.state.value.summary.canSave)

            var savedCount = 0
            viewModel.save { savedCount = it }
            viewModel.state.first { !it.saving && it.rows.isEmpty() }
            assertEquals(1, savedCount)

            val saved = transactions.observeTransactions().first().single()
            assertEquals(TransactionType.EXPENSE, saved.type)
            assertEquals(50_000L, saved.amount.amountMinor)
            assertEquals("PHP", saved.amount.currencyCode)
            assertEquals(dining, saved.categoryId)
        } finally {
            collecting.cancel()
        }
    }

    @Test fun gotMoneyFromAPersonStaysIncome() = runTest {
        wallets.create("Cash", WalletType.CASH, Money(0, "PHP"))
        categories.create("Restaurants", CategoryKind.EXPENSE, "dining")
        val viewModel = ComposerViewModel(
            transactions,
            wallets,
            categories,
            LocalTransactionInterpreter(),
            today = { LocalDate.parse("2026-10-28") },
        )
        val collecting = launch(UnconfinedTestDispatcher()) { viewModel.state.collect {} }
        try {
            viewModel.state.first { it.loaded && it.wallets.isNotEmpty() }
            viewModel.send("got 20 from mom", CaptureSource.TYPED)
            val row = viewModel.state.first { it.rows.isNotEmpty() }.rows.single()
            assertEquals(TransactionType.INCOME, row.type)
            assertEquals("20", row.amountText)
        } finally {
            collecting.cancel()
        }
    }
}
