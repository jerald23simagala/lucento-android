package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.TransactionEntity
import com.lucento.app.data.repository.TransactionRepository.BatchEntry
import com.lucento.app.data.repository.TransactionRepository.BatchResult
import com.lucento.app.data.repository.TransactionRepository.Input
import com.lucento.app.data.repository.TransactionRepository.TransferInput
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Saving a reviewed batch from the composer: all or nothing, never twice. */
class TransactionBatchSaveTest {

    private class RollbackRunner(private val dao: FakeTransactionDao) : TransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R {
            val snapshot = dao.rows.value
            return try {
                block()
            } catch (e: Throwable) {
                dao.rows.value = snapshot
                throw e
            }
        }
    }

    private class FlakyDao(private val failOnInsert: Int, inner: FakeTransactionDao) : TransactionDaoDelegate(inner) {
        var inserts = 0
        override suspend fun insert(transaction: TransactionEntity): Long {
            inserts++
            if (inserts == failOnInsert) throw IllegalStateException("simulated write failure")
            return super.insert(transaction)
        }
    }

    private val day = LocalDate.parse("2026-10-22")
    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val txDao = FakeTransactionDao()

    private suspend fun setUp(dao: com.lucento.app.data.local.TransactionDao = txDao): Setup {
        val wallets = WalletRepository(walletDao, dao, clock = { 1L })
        val categories = CategoryRepository(categoryDao, clock = { 1L })
        fun id(r: WalletRepository.SaveResult) = (r as WalletRepository.SaveResult.Saved).id
        return Setup(
            cash = id(wallets.create("Cash", WalletType.CASH, Money(0, "PHP"))),
            savings = id(wallets.create("Savings", WalletType.SAVINGS, Money(0, "PHP"))),
            usd = id(wallets.create("Travel", WalletType.BANK, Money(0, "USD"))),
            dining = (categories.create("Dining", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id,
            salary = (categories.create("Salary", CategoryKind.INCOME) as CategoryRepository.SaveResult.Saved).id,
        )
    }

    private data class Setup(val cash: Long, val savings: Long, val usd: Long, val dining: Long, val salary: Long)

    private fun single(key: String, type: TransactionType, minor: Long, wallet: Long, category: Long?, currency: String = "PHP") =
        BatchEntry.Single(Input(null, key, type, Money(minor, currency), wallet, category, day, "note $key"))

    @Test fun savesEveryRowOfAValidBatch() = runTest {
        val s = setUp()
        val repo = TransactionRepository(txDao, walletDao, categoryDao, clock = { 5L })
        val result = repo.saveBatch(
            listOf(
                single("a", TransactionType.EXPENSE, 250_00, s.cash, s.dining),
                single("b", TransactionType.EXPENSE, 180_00, s.cash, null),
                single("c", TransactionType.INCOME, 35_000_00, s.cash, s.salary),
                BatchEntry.Move(TransferInput(null, "d", s.cash, s.savings, Money(5_000_00, "PHP"), day, "")),
            )
        )
        assertEquals(BatchResult.Saved(4), result)
        assertEquals(5, txDao.rows.value.size) // three rows plus two transfer legs
        val legs = txDao.rows.value.filter { it.transferGroup == "d" }
        assertEquals(setOf("TRANSFER_OUT", "TRANSFER_IN"), legs.map { it.type }.toSet())
        assertTrue(legs.all { it.categoryId == null })
    }

    @Test fun retryingTheSameBatchCreatesNoDuplicates() = runTest {
        val s = setUp()
        val repo = TransactionRepository(txDao, walletDao, categoryDao)
        val batch = listOf(
            single("a", TransactionType.EXPENSE, 250_00, s.cash, s.dining),
            BatchEntry.Move(TransferInput(null, "t", s.cash, s.savings, Money(100_00, "PHP"), day, "")),
        )
        repo.saveBatch(batch)
        repo.saveBatch(batch)
        assertEquals(3, txDao.rows.value.size)
    }

    @Test fun oneInvalidRowRejectsTheBatchAndWritesNothing() = runTest {
        val s = setUp()
        val repo = TransactionRepository(txDao, walletDao, categoryDao)
        val result = repo.saveBatch(
            listOf(
                single("a", TransactionType.EXPENSE, 250_00, s.cash, s.dining),
                single("b", TransactionType.EXPENSE, 10_00, s.usd, s.dining), // PHP amount into a USD account
            )
        )
        assertEquals(BatchResult.Rejected(1, TransactionRepository.SaveResult.CurrencyMismatch), result)
        assertTrue(txDao.rows.value.isEmpty())
    }

    @Test fun rejectsWrongCategoryKindZeroAndSameAccountTransfer() = runTest {
        val s = setUp()
        val repo = TransactionRepository(txDao, walletDao, categoryDao)
        assertTrue(repo.saveBatch(listOf(single("a", TransactionType.INCOME, 100, s.cash, s.dining))) is BatchResult.Rejected)
        assertTrue(repo.saveBatch(listOf(single("b", TransactionType.EXPENSE, 0, s.cash, null))) is BatchResult.Rejected)
        val same = BatchEntry.Move(TransferInput(null, "t", s.cash, s.cash, Money(100, "PHP"), day, ""))
        assertEquals(BatchResult.Rejected(0, TransactionRepository.TransferResult.SameWallet), repo.saveBatch(listOf(same)))
        assertTrue(txDao.rows.value.isEmpty())
    }

    @Test fun aWriteFailurePartWayRollsBackTheWholeBatch() = runTest {
        val flaky = FlakyDao(failOnInsert = 3, inner = txDao)
        val s = setUp(flaky)
        val repo = TransactionRepository(flaky, walletDao, categoryDao, runner = RollbackRunner(txDao))
        val batch = listOf(
            single("a", TransactionType.EXPENSE, 250_00, s.cash, s.dining),
            single("b", TransactionType.EXPENSE, 180_00, s.cash, s.dining),
            single("c", TransactionType.INCOME, 1_000_00, s.cash, s.salary),
        )
        assertNull(repo.saveBatchOrNull(batch))
        assertTrue(txDao.rows.value.isEmpty())
        // The retry succeeds and still writes each row once.
        assertEquals(BatchResult.Saved(3), repo.saveBatchOrNull(batch))
        assertEquals(3, txDao.rows.value.size)
    }

    @Test fun emptyBatchIsANoOp() = runTest {
        assertEquals(BatchResult.Saved(0), TransactionRepository(txDao, walletDao, categoryDao).saveBatch(emptyList()))
    }
}
