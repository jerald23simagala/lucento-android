package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.TransactionRepository.AdjustResult
import com.lucento.app.data.repository.TransactionRepository.Input
import com.lucento.app.data.repository.TransactionRepository.SaveResult
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class BalanceAdjustmentRepositoryTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1_000L })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2_000L })

    private val today = LocalDate.parse("2026-10-26")
    private var cash = 0L
    private var card = 0L
    private var home = 0L

    private fun usd(minor: Long) = Money(minor, "USD")

    @Before fun setUp() = runTest {
        cash = (wallets.create("Cash", WalletType.CASH, usd(10_000)) as WalletRepository.SaveResult.Saved).id
        card = (wallets.create("Visa", WalletType.CREDIT_CARD, usd(20_000)) as WalletRepository.SaveResult.Saved).id
        home = (wallets.create("Home", WalletType.OTHER_ASSET, usd(1_000_000)) as WalletRepository.SaveResult.Saved).id
    }

    private suspend fun balance(id: Long) = wallets.observeWallets().first().first { it.id == id }.balance

    private suspend fun spend(minor: Long, date: LocalDate, uuid: String) {
        transactions.save(Input(null, uuid, TransactionType.EXPENSE, usd(minor), cash, null, date, ""))
    }

    @Test fun overridingTheBalanceRecordsTheGapAsOneAdjustment() = runTest {
        spend(14_000, today, "e1") // cash now reads -40.00
        val result = transactions.adjustBalance(cash, usd(2_500), today, "adj", today)
        assertTrue(result is AdjustResult.Adjusted)
        assertEquals(usd(2_500), balance(cash))
        val adjustment = transactions.observeTransactions().first().single { it.isAdjustment }
        assertEquals(TransactionType.ADJUSTMENT_IN, adjustment.type)
        assertEquals(usd(6_500), adjustment.amount)
        assertEquals(null, adjustment.categoryId)
    }

    @Test fun aBackdatedOverrideKeepsLaterTransactionsOnTop() = runTest {
        spend(1_000, today.minusDays(5), "e1")
        spend(500, today, "e2")
        // Five days ago the cash really held 50.00 (tracked: 90.00); today's 5.00 expense still applies.
        transactions.adjustBalance(cash, usd(5_000), today.minusDays(5), "adj", today)
        assertEquals(usd(4_500), balance(cash))
    }

    @Test fun owingMoreOnACardRaisesTheAmountOwed() = runTest {
        transactions.adjustBalance(card, usd(26_000), today, "adj", today)
        assertEquals(usd(26_000), balance(card))
        assertEquals(TransactionType.ADJUSTMENT_OUT, transactions.observeTransactions().first().single().type)
    }

    @Test fun nothingIsWrittenWhenBalancesAlreadyAgree() = runTest {
        assertEquals(AdjustResult.Unchanged, transactions.adjustBalance(cash, usd(10_000), today, "adj", today))
        assertTrue(transactions.observeTransactions().first().isEmpty())
    }

    @Test fun refusesFutureDatesOtherCurrenciesAndValuedAssets() = runTest {
        assertEquals(AdjustResult.FutureDate, transactions.adjustBalance(cash, usd(1), today.plusDays(1), "a", today))
        assertEquals(AdjustResult.CurrencyMismatch, transactions.adjustBalance(cash, Money(1, "EUR"), today, "b", today))
        assertEquals(AdjustResult.WalletNotFound, transactions.adjustBalance(home, usd(1), today, "c", today))
        assertTrue(transactions.observeTransactions().first().isEmpty())
    }

    @Test fun aRepeatedSubmitRecordsTheAdjustmentOnce() = runTest {
        val first = transactions.adjustBalance(cash, usd(2_000), today, "same", today) as AdjustResult.Adjusted
        val second = transactions.adjustBalance(cash, usd(2_000), today, "same", today) as AdjustResult.Adjusted
        assertEquals(first.id, second.id)
        assertEquals(1, transactions.observeTransactions().first().size)
        assertEquals(usd(2_000), balance(cash))
    }

    @Test fun anAdjustmentCantBeEditedOrCreatedAsAnOrdinaryTransaction() = runTest {
        val id = (transactions.adjustBalance(cash, usd(2_000), today, "adj", today) as AdjustResult.Adjusted).id
        assertEquals(
            SaveResult.InvalidType,
            transactions.save(Input(id, "x", TransactionType.EXPENSE, usd(100), cash, null, today, "")),
        )
        assertEquals(
            SaveResult.InvalidType,
            transactions.save(Input(null, "y", TransactionType.ADJUSTMENT_IN, usd(100), cash, null, today, "")),
        )
        assertEquals(usd(2_000), balance(cash))
    }
}
