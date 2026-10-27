package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.TransactionEntity
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Transfer atomicity, duplicate prevention and recurring idempotency (audit finding 1.6).
 * [RollbackRunner] behaves like Room's withTransaction: if the block throws, every write in it is undone.
 */
class TransferAndRecurringIntegrityTest {

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

    /** Fails the Nth insert, like a disk-full or constraint error halfway through a multi-row write. */
    private class FlakyTransactionDao(private val failOnInsert: Int) : TransactionDaoDelegate(FakeTransactionDao()) {
        var inserts = 0
        override suspend fun insert(transaction: TransactionEntity): Long {
            inserts++
            if (inserts == failOnInsert) throw IllegalStateException("simulated write failure")
            return super.insert(transaction)
        }
    }

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val day = LocalDate.parse("2026-10-15")

    private suspend fun twoWallets(txDao: com.lucento.app.data.local.TransactionDao): Pair<Long, Long> {
        val wallets = WalletRepository(walletDao, txDao, clock = { 1L })
        val a = (wallets.create("A", WalletType.BANK, Money(50_000, "EUR")) as WalletRepository.SaveResult.Saved).id
        val b = (wallets.create("B", WalletType.CASH, Money(0, "EUR")) as WalletRepository.SaveResult.Saved).id
        return a to b
    }

    @Test fun failedSecondLegRollsBackTheFirst() = runTest {
        val flaky = FlakyTransactionDao(failOnInsert = 2)
        val (a, b) = twoWallets(flaky)
        val repo = TransactionRepository(flaky, walletDao, categoryDao, clock = { 2L }, runner = RollbackRunner(flaky.inner))
        val outcome = runCatching { repo.saveTransfer(TransactionRepository.TransferInput(null, "t", a, b, Money(1_000, "EUR"), day, "")) }
        assertTrue(outcome.isFailure)
        assertTrue("no orphan leg may remain", flaky.inner.rows.value.isEmpty())
    }

    @Test fun retriedTransferAfterFailureSucceedsOnce() = runTest {
        val flaky = FlakyTransactionDao(failOnInsert = 2)
        val (a, b) = twoWallets(flaky)
        val repo = TransactionRepository(flaky, walletDao, categoryDao, clock = { 2L }, runner = RollbackRunner(flaky.inner))
        val input = TransactionRepository.TransferInput(null, "same", a, b, Money(1_000, "EUR"), day, "")
        assertNull(repo.saveTransferOrNull(input))
        assertTrue(repo.saveTransferOrNull(input) is TransactionRepository.TransferResult.Saved)
        assertTrue(repo.saveTransferOrNull(input) is TransactionRepository.TransferResult.Saved)
        assertEquals(2, flaky.inner.rows.value.size)
        assertEquals(setOf("TRANSFER_OUT", "TRANSFER_IN"), flaky.inner.rows.value.map { it.type }.toSet())
    }

    @Test fun insertRaceWithTheSameKeyReturnsTheExistingRow() = runTest {
        val dao = FakeTransactionDao()
        val (a, _) = twoWallets(dao)
        val repo = TransactionRepository(dao, walletDao, categoryDao, clock = { 2L })
        // Another submit with the same key lands between the duplicate check and the insert.
        val racing = object : TransactionDaoDelegate(dao) {
            var raced = false
            override suspend fun findByClientUuid(clientUuid: String): TransactionEntity? {
                val found = super.findByClientUuid(clientUuid)
                if (found == null && !raced) {
                    raced = true
                    super.insert(
                        TransactionEntity(0, "EXPENSE", 700, "EUR", a, null, day.toEpochDay(), "", clientUuid, 1, 1)
                    )
                    return null
                }
                return found
            }
        }
        val raceRepo = TransactionRepository(racing, walletDao, categoryDao, clock = { 2L })
        val result = raceRepo.save(TransactionRepository.Input(null, "dup", TransactionType.EXPENSE, Money(700, "EUR"), a, null, day, ""))
        assertEquals(TransactionRepository.SaveResult.Saved(dao.rows.value.single().id), result)
        assertEquals(1, dao.rows.value.size)
        assertTrue(repo.observeTransactions().first().size == 1)
    }

    @Test fun overflowingAmountsAreRejectedNotWrapped() = runTest {
        val dao = FakeTransactionDao()
        val (a, _) = twoWallets(dao)
        val repo = TransactionRepository(dao, walletDao, categoryDao, clock = { 2L })
        repo.save(TransactionRepository.Input(null, "big1", TransactionType.INCOME, Money(Long.MAX_VALUE - 10, "EUR"), a, null, day, ""))
        val wallets = WalletRepository(walletDao, dao)
        val failure = runCatching { wallets.observeWallets().first() }.exceptionOrNull()
        assertTrue("balance overflow must throw, never wrap", failure is ArithmeticException)
    }

    // ---- recurring

    private val transactionDao = FakeTransactionDao()
    private val recurringDao = FakeRecurringDao()
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2L })
    private val recurring = RecurringRepository(recurringDao, walletDao, categoryDao, transactions, clock = { 3L })
    private var bank = 0L
    private var bills = 0L

    @Before fun setUp() = runTest {
        bank = (WalletRepository(walletDao, transactionDao).create("Bank", WalletType.BANK, Money(100_000, "USD")) as WalletRepository.SaveResult.Saved).id
        bills = (CategoryRepository(categoryDao).create("Bills", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id
    }

    private suspend fun schedule(start: LocalDate, id: Long? = null): Long = (
        recurring.save(RecurringRepository.Input(id, "Internet", TransactionType.EXPENSE, Money(5_000, "USD"), bank, bills, Frequency.MONTHLY, 1, start, null))
            as RecurringRepository.SaveResult.Saved
        ).id

    @Test fun markPaidTwiceRecordsOnePerOccurrence() = runTest {
        val id = schedule(LocalDate.parse("2026-01-10"))
        recurring.markPaid(id)
        recurring.markPaid(id)
        assertEquals(2, transactionDao.rows.value.size)
        assertEquals(setOf(LocalDate.parse("2026-01-10"), LocalDate.parse("2026-02-10")), transactionDao.rows.value.map { LocalDate.ofEpochDay(it.occurredOn) }.toSet())
        assertEquals(2, recurringDao.getById(id)!!.nextIndex)
    }

    /** Regression: changing the timing restarts the index at 0; the next payment must not be swallowed. */
    @Test fun paymentAfterTimingChangeIsRecorded() = runTest {
        val id = schedule(LocalDate.parse("2026-01-10"))
        recurring.markPaid(id) // occurrence 0 on Jan 10
        schedule(LocalDate.parse("2026-03-05"), id = id) // new start date: index back to 0
        val result = recurring.markPaid(id)
        assertTrue(result is RecurringRepository.PayResult.Recorded)
        assertEquals(2, transactionDao.rows.value.size)
        val ids = transactionDao.rows.value.map { it.id }
        assertNotEquals(ids[0], ids[1])
        assertTrue(transactionDao.rows.value.any { LocalDate.ofEpochDay(it.occurredOn) == LocalDate.parse("2026-03-05") })
    }

    @Test fun occurrenceKeysAreDistinctPerDueDate() = runTest {
        val id = schedule(LocalDate.parse("2026-01-31"))
        val s = recurring.observeSchedules().first().single { it.id == id }
        // Keyed by the schedule's stable cross-device id (v9), so two synced devices can't each record the same payment.
        val key = s.clientUuid!!
        assertEquals("recurring-$key-on-${LocalDate.parse("2026-01-31").toEpochDay()}", s.occurrenceKey(0))
        assertEquals("recurring-$key-on-${LocalDate.parse("2026-02-28").toEpochDay()}", s.occurrenceKey(1))
    }
}

/** Forwards every call to [inner]; tests override single methods to inject failures or races. */
open class TransactionDaoDelegate(val inner: FakeTransactionDao) : com.lucento.app.data.local.TransactionDao by inner
