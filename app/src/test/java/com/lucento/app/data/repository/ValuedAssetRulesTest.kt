package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.NetWorthCalculator
import com.lucento.app.domain.NetWorthComposer
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.transactable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Manually valued assets (a home, a car, investments) hold a valuation, never money movements: they count once
 * towards net worth and every write path refuses to post to them.
 */
class ValuedAssetRulesTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val recurringDao = FakeRecurringDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1L }, scheduleCount = { recurringDao.countForWallet(it) })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2L })
    private val recurring = RecurringRepository(recurringDao, walletDao, categoryDao, transactions, clock = { 3L })
    private val day = LocalDate.parse("2026-10-01")

    private var bank = 0L
    private var home = 0L
    private var card = 0L
    private var food = 0L

    @Before fun setUp() = runTest {
        bank = (wallets.create("Bank", WalletType.BANK, Money(250_000, "USD")) as WalletRepository.SaveResult.Saved).id
        home = (wallets.create("Apartment", WalletType.OTHER_ASSET, Money(18_000_000, "USD")) as WalletRepository.SaveResult.Saved).id
        card = (wallets.create("Card", WalletType.CREDIT_CARD, Money(40_000, "USD")) as WalletRepository.SaveResult.Saved).id
        food = (CategoryRepository(categoryDao).create("Food", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id
    }

    @Test fun valuationCountsOnceTowardsNetWorth() = runTest {
        val all = wallets.observeWallets().first()
        val composition = NetWorthComposer.compose(all, "USD")
        assertEquals(Money(18_250_000, "USD"), composition.totalAssets)
        assertEquals(Money(40_000, "USD"), composition.totalLiabilities)
        assertEquals(Money(18_210_000, "USD"), composition.netWorth)
        // The summary and the composition read the same balances, so they always agree.
        assertEquals(composition.netWorth, NetWorthCalculator.summarize(all, "USD").totalFor("USD"))
    }

    @Test fun valuationIsEditedDirectlyWithoutTransactions() = runTest {
        wallets.update(home, "Apartment", WalletType.OTHER_ASSET, Money(19_500_000, "USD"))
        val updated = wallets.observeWallets().first().first { it.id == home }
        assertEquals(Money(19_500_000, "USD"), updated.balance)
        assertTrue(transactions.observeTransactions().first().isEmpty())
    }

    @Test fun pickersNeverOfferAValuedAsset() = runTest {
        val ids = wallets.observeWallets().first().transactable().map { it.id }
        assertEquals(listOf(bank, card).sorted(), ids.sorted())
    }

    @Test fun transactionsCannotBePostedToAValuedAsset() = runTest {
        val result = transactions.save(
            TransactionRepository.Input(null, "k1", TransactionType.EXPENSE, Money(1_000, "USD"), home, food, day, ""),
        )
        assertEquals(TransactionRepository.SaveResult.WalletNotFound, result)
    }

    @Test fun transfersCannotTouchAValuedAsset() = runTest {
        fun move(from: Long, to: Long, key: String) =
            TransactionRepository.TransferInput(null, key, from, to, Money(5_000, "USD"), day, "")
        assertEquals(TransactionRepository.TransferResult.WalletNotFound, transactions.saveTransfer(move(bank, home, "t1")))
        assertEquals(TransactionRepository.TransferResult.WalletNotFound, transactions.saveTransfer(move(home, bank, "t2")))
    }

    @Test fun recurringPaymentsCannotUseAValuedAsset() = runTest {
        val result = recurring.save(
            RecurringRepository.Input(null, "Upkeep", TransactionType.EXPENSE, Money(9_900, "USD"), home, food, Frequency.MONTHLY, 1, day, null),
        )
        assertEquals(RecurringRepository.SaveResult.WalletNotFound, result)
    }

    @Test fun anAccountWithHistoryCannotBecomeAValuedAsset() = runTest {
        transactions.save(TransactionRepository.Input(null, "k2", TransactionType.EXPENSE, Money(1_000, "USD"), bank, food, day, ""))
        val result = wallets.update(bank, "Bank", WalletType.OTHER_ASSET, Money(250_000, "USD"))
        assertTrue(result is WalletRepository.SaveResult.TypeLocked)
        assertEquals("BANK", walletDao.getById(bank)!!.type)
    }

    @Test fun anUnusedAccountMayBecomeAValuedAsset() = runTest {
        val spare = (wallets.create("Spare", WalletType.CASH, Money(0, "USD")) as WalletRepository.SaveResult.Saved).id
        assertEquals(WalletRepository.SaveResult.Saved(spare), wallets.update(spare, "Spare", WalletType.OTHER_ASSET, Money(300_000, "USD")))
        assertEquals("OTHER_ASSET", walletDao.getById(spare)!!.type)
    }
}
