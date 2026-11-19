package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.RecurringDao
import com.lucento.app.data.local.RecurringEntity
import com.lucento.app.data.repository.RecurringRepository.BulkResult
import com.lucento.app.data.repository.RecurringRepository.Input
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Scheduled › All schedules, Select mode (tester round, 2026-11-18): each bulk change is written in one step, only to the
 * plans that can take it, never on a Partner’s phone, and not at all when a write fails part-way.
 */
class ScheduleBulkRepositoryTest {

    /** Like Room’s withTransaction: if the block throws, every schedule write in it is undone. */
    private class RollbackRunner(private val dao: FakeRecurringDao) : TransactionRunner {
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

    /** Fails the Nth schedule update, like a disk-full error halfway through a bulk change. */
    private class FlakyRecurringDao(private val inner: FakeRecurringDao, private val failOnUpdate: Int) : RecurringDao by inner {
        private var updates = 0
        override suspend fun update(schedule: RecurringEntity) {
            updates++
            if (updates == failOnUpdate) throw IllegalStateException("simulated write failure")
            inner.update(schedule)
        }
    }

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val recurringDao = FakeRecurringDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1L }, scheduleCount = { recurringDao.countForWallet(it) })
    private val categories = CategoryRepository(categoryDao, clock = { 1L })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2L })
    private var now = 10L
    private var partnerPhone = false
    private val recurring = RecurringRepository(
        recurringDao, walletDao, categoryDao, transactions, clock = { now }, access = StructureAccess { !partnerPhone },
    )

    private val start = LocalDate.parse("2026-11-15")
    private var bank = 0L
    private var dollars = 0L
    private var bills = 0L

    @Before fun setUp() = runTest {
        bank = (wallets.create("Bank", WalletType.BANK, Money(1_000_000, "PHP")) as WalletRepository.SaveResult.Saved).id
        dollars = (wallets.create("Dollars", WalletType.BANK, Money(100_000, "USD")) as WalletRepository.SaveResult.Saved).id
        bills = (categories.create("Bills", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id
    }

    private suspend fun schedule(
        name: String,
        amount: Money = Money(150_000, "PHP"),
        wallet: Long = bank,
        frequency: Frequency = Frequency.MONTHLY,
        end: LocalDate? = null,
    ): Long = (recurring.save(Input(null, name, TransactionType.EXPENSE, amount, wallet, bills, frequency, 1, start, end))
        as RecurringRepository.SaveResult.Saved).id

    private suspend fun plans() = recurring.observeSchedules().first().associateBy { it.id }

    @Test fun `Change day moves every selected plan in one step and leaves the rest`() = runTest {
        val rent = schedule("Rent")
        val internet = schedule("Internet")
        val water = schedule("Water")
        now = 20L

        assertEquals(BulkResult.Done(changed = 2, skipped = 0), recurring.moveDay(listOf(rent, internet), monthDay = 5, weekday = null))

        val after = plans()
        assertEquals(LocalDate.parse("2026-11-05"), after.getValue(rent).nextDue)
        assertEquals(LocalDate.parse("2026-11-05"), after.getValue(internet).nextDue)
        assertEquals(start, after.getValue(water).nextDue)
        // Changed rows carry the new time, so sync sends them; the plan left alone doesn’t.
        assertEquals(20L, recurringDao.getById(rent)!!.updatedAt)
        assertEquals(10L, recurringDao.getById(water)!!.updatedAt)
    }

    @Test fun `marking paid after a move to the 31st records November 30 and then waits for December 31`() = runTest {
        val rent = schedule("Rent") // the 15th
        recurring.moveDay(listOf(rent), monthDay = 31, weekday = null)
        assertEquals(LocalDate.parse("2026-11-30"), plans().getValue(rent).nextDue)

        assertTrue(recurring.markPaid(rent) is RecurringRepository.PayResult.Recorded)
        val paid = transactionDao.rows.value.single()
        assertEquals(LocalDate.parse("2026-11-30").toEpochDay(), paid.occurredOn)
        assertTrue(paid.clientUuid.endsWith("-on-${LocalDate.parse("2026-11-30").toEpochDay()}"))
        assertEquals(LocalDate.parse("2026-12-31"), plans().getValue(rent).nextDue)
    }

    @Test fun `a new amount on a debt payment is stored as the whole payment`() = runTest {
        val loan = (wallets.create("Car loan", WalletType.LOAN, Money(500_000, "PHP")) as WalletRepository.SaveResult.Saved).id
        val interest = categories.ensure(DebtMath.INTEREST_CATEGORY, CategoryKind.EXPENSE, DebtMath.INTEREST_ICON)!!
        val payment = (recurring.save(
            Input(
                null, "Monthly Payment - Car loan", TransactionType.EXPENSE, Money(18_446, "PHP"), bank, interest,
                Frequency.MONTHLY, 1, start, null, toWalletId = loan, interest = Money(5_208, "PHP"),
            ),
        ) as RecurringRepository.SaveResult.Saved).id

        assertEquals(BulkResult.Done(1, 0), recurring.setAmount(listOf(payment), Money(25_000, "PHP")))
        val stored = recurringDao.getById(payment)!!
        assertEquals(25_000L, stored.amountMinor)
        assertEquals(0L, stored.interestMinor)
    }

    @Test fun `a plan that no longer exists is skipped and counted, for a change as for a delete`() = runTest {
        val rent = schedule("Rent")
        assertEquals(BulkResult.Done(changed = 1, skipped = 1), recurring.setPaused(listOf(rent, 999L), paused = true))
        assertEquals(BulkResult.Done(changed = 1, skipped = 1), recurring.delete(listOf(rent, 999L)))
    }

    @Test fun `a plan already on that day is skipped, not rewritten`() = runTest {
        val rent = schedule("Rent")
        now = 30L
        assertEquals(BulkResult.Done(changed = 0, skipped = 1), recurring.moveDay(listOf(rent), monthDay = 15, weekday = null))
        assertEquals(10L, recurringDao.getById(rent)!!.updatedAt)
        assertEquals(BulkResult.Done(changed = 0, skipped = 1), recurring.setAmount(listOf(rent), Money(150_000, "PHP")))
    }

    @Test fun `a weekly plan waits for a weekday and a monthly one for a day of the month`() = runTest {
        val rent = schedule("Rent")
        val gym = schedule("Gym", frequency = Frequency.WEEKLY)

        assertEquals(BulkResult.Done(changed = 1, skipped = 1), recurring.moveDay(listOf(rent, gym), monthDay = 20, weekday = null))
        assertEquals(start, plans().getValue(gym).nextDue)

        // Sunday Nov 15 moves to the Monday of its week.
        assertEquals(BulkResult.Done(changed = 1, skipped = 0), recurring.moveDay(listOf(gym), monthDay = null, weekday = DayOfWeek.MONDAY))
        assertEquals(LocalDate.parse("2026-11-09"), plans().getValue(gym).nextDue)
    }

    @Test fun `a later day moves the end date with the last payment`() = runTest {
        val instalment = schedule("Instalment", end = start.plusMonths(1)) // Nov 15 and Dec 15
        recurring.moveDay(listOf(instalment), monthDay = 20, weekday = null)
        assertEquals(LocalDate.parse("2026-12-20").toEpochDay(), recurringDao.getById(instalment)!!.endOn)
    }

    @Test fun `Change amount skips plans in another currency`() = runTest {
        val rent = schedule("Rent")
        val streaming = schedule("Streaming", amount = Money(1_599, "USD"), wallet = dollars)

        assertEquals(BulkResult.Done(changed = 1, skipped = 1), recurring.setAmount(listOf(rent, streaming), Money(200_000, "PHP")))
        assertEquals(Money(200_000, "PHP"), plans().getValue(rent).amount)
        assertEquals(Money(1_599, "USD"), plans().getValue(streaming).amount)
    }

    @Test fun `Pause and Resume change only the plans that aren’t that way yet`() = runTest {
        val rent = schedule("Rent")
        val internet = schedule("Internet")
        val water = schedule("Water")

        assertEquals(BulkResult.Done(2, 0), recurring.setPaused(listOf(rent, internet), paused = true))
        assertEquals(BulkResult.Done(1, 2), recurring.setPaused(listOf(rent, internet, water), paused = true))
        assertTrue(plans().values.all { it.paused })
        assertEquals(BulkResult.Done(3, 0), recurring.setPaused(listOf(rent, internet, water), paused = false))
        assertTrue(plans().values.none { it.paused })
    }

    @Test fun `Delete removes the plans and keeps what they recorded`() = runTest {
        val rent = schedule("Rent")
        val internet = schedule("Internet")
        val water = schedule("Water")
        assertTrue(recurring.markPaid(rent) is RecurringRepository.PayResult.Recorded)

        assertEquals(BulkResult.Done(2, 0), recurring.delete(listOf(rent, internet)))
        assertEquals(setOf(water), plans().keys)
        assertEquals(1, transactionDao.rows.value.size)
    }

    @Test fun `a Partner’s phone changes nothing`() = runTest {
        val rent = schedule("Rent")
        val before = recurringDao.rows.value
        partnerPhone = true

        assertEquals(BulkResult.NotPermitted, recurring.moveDay(listOf(rent), monthDay = 5, weekday = null))
        assertEquals(BulkResult.NotPermitted, recurring.setAmount(listOf(rent), Money(1, "PHP")))
        assertEquals(BulkResult.NotPermitted, recurring.setPaused(listOf(rent), paused = true))
        assertEquals(BulkResult.NotPermitted, recurring.delete(listOf(rent)))
        assertEquals(before, recurringDao.rows.value)
    }

    @Test fun `a failure part-way leaves every plan as it was`() = runTest {
        val rent = schedule("Rent")
        val internet = schedule("Internet")
        val before = recurringDao.rows.value
        val atomic = RecurringRepository(
            FlakyRecurringDao(recurringDao, failOnUpdate = 2), walletDao, categoryDao, transactions,
            clock = { now }, runner = RollbackRunner(recurringDao),
        )

        assertTrue(runCatching { atomic.moveDay(listOf(rent, internet), monthDay = 5, weekday = null) }.isFailure)
        assertEquals(before, recurringDao.rows.value)
    }
}
