package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletClassification
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Lucento Shared: a Partner's phone (structure access off) can't change wallets, categories, budgets, goals or schedules
 * — and keeps full use of transactions, which are stamped with whoever wrote them.
 */
class SharedSpaceGuardsTest {

    private var partnerPhone = false
    private val access = StructureAccess { !partnerPhone }

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val recurringDao = FakeRecurringDao()
    private val goalDao = FakeGoalDao()
    private var actor: String? = "owner-id"
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 10L }, scheduleCount = { recurringDao.countForWallet(it) }, access = access)
    private val categories = CategoryRepository(categoryDao, clock = { 10L }, access = access)
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 20L }, actor = { actor })
    private val budgets = BudgetRepository(FakeBudgetDao(), categoryDao, clock = { 10L }, access = access)
    private val goals = GoalRepository(goalDao, clock = { 10L }, access = access)
    private val recurring = RecurringRepository(recurringDao, walletDao, categoryDao, transactions, clock = { 10L }, access = access)

    private suspend fun ownerSetsUp(): Triple<Long, Long, Long> {
        val bank = (wallets.create("Household", WalletType.BANK, Money(100_000, "PHP")) as WalletRepository.SaveResult.Saved).id
        val groceries = (categories.create("Groceries", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id
        val goal = (goals.save(null, "Trip", Money(50_000, "PHP"), null, null, null) as GoalRepository.SaveResult.Saved).id
        return Triple(bank, groceries, goal)
    }

    @Test
    fun `a partner's phone can't change wallets, and nothing is written`() = runTest {
        val (bank, _, _) = ownerSetsUp()
        partnerPhone = true
        val before = walletDao.rows.value

        assertEquals(WalletRepository.SaveResult.NotPermitted, wallets.create("Mine", WalletType.CASH, Money(0, "PHP")))
        assertEquals(WalletRepository.SaveResult.NotPermitted, wallets.update(bank, "Renamed", WalletType.BANK, Money(0, "PHP")))
        assertEquals(WalletRepository.SaveResult.NotPermitted, wallets.setClassification(bank, WalletClassification.PARTNER))
        assertEquals(WalletRepository.DeleteResult.NotPermitted, wallets.delete(bank))
        assertEquals(before, walletDao.rows.value)
    }

    @Test
    fun `the owner labels a wallet, and the label shows on the wallet`() = runTest {
        val (bank, _, _) = ownerSetsUp()
        assertTrue(wallets.setClassification(bank, WalletClassification.OWNER) is WalletRepository.SaveResult.Saved)
        assertEquals(WalletClassification.OWNER, wallets.observeWallets().first().single().classification)
        assertEquals("OWNER", walletDao.getById(bank)!!.classification)
        assertEquals(10L, walletDao.getById(bank)!!.updatedAt) // an edit like any other, so it syncs
    }

    @Test
    fun `a partner's phone can't change categories, and only finds the ones lucento needs`() = runTest {
        val (_, groceries, _) = ownerSetsUp()
        partnerPhone = true

        assertEquals(CategoryRepository.SaveResult.NotPermitted, categories.create("Snacks", CategoryKind.EXPENSE))
        assertEquals(CategoryRepository.SaveResult.NotPermitted, categories.rename(groceries, "Food"))
        assertEquals(CategoryRepository.DeleteResult.NotPermitted, categories.remove(groceries))
        assertFalse(categories.move(groceries, 1))
        assertEquals(groceries, categories.ensure("Groceries", CategoryKind.EXPENSE, "cart"))
        assertNull("never created on a partner's phone", categories.ensure("Interest", CategoryKind.EXPENSE, "percent"))
        assertEquals(1, categoryDao.getAll().size)
    }

    @Test
    fun `a partner's phone can't change budgets, goals or schedules`() = runTest {
        val (bank, groceries, goal) = ownerSetsUp()
        val schedule = (recurring.save(
            RecurringRepository.Input(null, "Rent", TransactionType.EXPENSE, Money(20_000, "PHP"), bank, groceries, Frequency.MONTHLY, 1, LocalDate.parse("2026-11-01"), null)
        ) as RecurringRepository.SaveResult.Saved).id
        partnerPhone = true

        assertEquals(BudgetRepository.SaveResult.NotPermitted, budgets.save(null, groceries, Money(10_000, "PHP")))
        assertEquals(BudgetRepository.SaveResult.NotPermitted, budgets.setMonthAmount(groceries, "PHP", java.time.YearMonth.of(2026, 11), Money(1, "PHP")))
        assertEquals(GoalRepository.SaveResult.NotPermitted, goals.save(goal, "Trip!", Money(1, "PHP"), null, null, null))
        assertEquals(GoalRepository.ContributionResult.NotPermitted, goals.contribute(goal, Money(100, "PHP"), LocalDate.parse("2026-11-05"), "", "c-1"))
        assertEquals(GoalRepository.ArchiveResult.NotPermitted, goals.archive(goal))
        assertEquals(RecurringRepository.PayResult.NotPermitted, recurring.markPaid(schedule))
        recurring.skip(schedule)
        recurring.delete(schedule)
        assertEquals(0, recurringDao.getById(schedule)!!.nextIndex)
        assertTrue(transactionDao.rows.value.isEmpty())
    }

    @Test
    fun `transactions stay fully open on a partner's phone and record who wrote them`() = runTest {
        val (bank, groceries, _) = ownerSetsUp()
        partnerPhone = true
        actor = "partner-id"

        val saved = transactions.save(
            TransactionRepository.Input(null, "t-1", TransactionType.EXPENSE, Money(1_250_00, "PHP"), bank, groceries, LocalDate.parse("2026-11-04"), "groceries")
        ) as TransactionRepository.SaveResult.Saved
        assertEquals("partner-id", transactionDao.getById(saved.id)!!.createdBy)
        assertNull(transactionDao.getById(saved.id)!!.updatedBy)

        actor = "owner-id"
        transactions.save(
            TransactionRepository.Input(saved.id, "t-1", TransactionType.EXPENSE, Money(1_300_00, "PHP"), bank, groceries, LocalDate.parse("2026-11-04"), "groceries")
        )
        val edited = transactionDao.getById(saved.id)!!
        assertEquals("partner-id", edited.createdBy)
        assertEquals("owner-id", edited.updatedBy)

        transactions.delete(saved.id)
        assertTrue(transactionDao.rows.value.isEmpty())
    }

    @Test
    fun `both legs of a transfer are stamped, and a signed-out phone stamps nothing`() = runTest {
        val (bank, _, _) = ownerSetsUp()
        val cash = (wallets.create("Cash", WalletType.CASH, Money(0, "PHP")) as WalletRepository.SaveResult.Saved).id
        actor = "partner-id"
        transactions.saveTransfer(TransactionRepository.TransferInput(null, "x-1", bank, cash, Money(500, "PHP"), LocalDate.parse("2026-11-04"), ""))
        assertEquals(listOf("partner-id", "partner-id"), transactionDao.rows.value.map { it.createdBy })

        actor = null
        transactions.save(TransactionRepository.Input(null, "t-2", TransactionType.EXPENSE, Money(1, "PHP"), bank, null, LocalDate.parse("2026-11-04"), ""))
        assertNull(transactionDao.findByClientUuid("t-2")!!.createdBy)
    }
}
