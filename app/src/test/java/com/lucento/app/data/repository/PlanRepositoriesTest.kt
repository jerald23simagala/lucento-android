package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class PlanRepositoriesTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val recurringDao = FakeRecurringDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1L }, scheduleCount = { recurringDao.countForWallet(it) })
    private val categories = CategoryRepository(categoryDao, clock = { 1L })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2L })
    private val budgets = BudgetRepository(FakeBudgetDao(), categoryDao, clock = { 3L })
    private val goalDao = FakeGoalDao()
    private val goals = GoalRepository(goalDao, clock = { 4L })
    private val recurring = RecurringRepository(recurringDao, walletDao, categoryDao, transactions, clock = { 5L })

    private var bank = 0L
    private var rent = 0L
    private var salary = 0L
    private val start = LocalDate.parse("2026-01-31")

    @Before fun setUp() = runTest {
        bank = (wallets.create("Bank", WalletType.BANK, Money(100_000, "USD")) as WalletRepository.SaveResult.Saved).id
        rent = (categories.create("Rent", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id
        salary = (categories.create("Salary", CategoryKind.INCOME) as CategoryRepository.SaveResult.Saved).id
    }

    // ---- budgets

    @Test fun oneOverallBudgetPerCurrency() = runTest {
        assertTrue(budgets.save(null, null, Money(50_000, "USD")) is BudgetRepository.SaveResult.Saved)
        assertEquals(BudgetRepository.SaveResult.Duplicate, budgets.save(null, null, Money(60_000, "USD")))
        assertTrue(budgets.save(null, null, Money(60_000, "EUR")) is BudgetRepository.SaveResult.Saved)
        assertTrue(budgets.save(null, rent, Money(20_000, "USD")) is BudgetRepository.SaveResult.Saved)
        assertEquals(BudgetRepository.SaveResult.Duplicate, budgets.save(null, rent, Money(1, "USD")))
    }

    @Test fun budgetsRejectIncomeCategoriesAndNonPositiveLimits() = runTest {
        assertEquals(BudgetRepository.SaveResult.InvalidCategory, budgets.save(null, salary, Money(1_000, "USD")))
        assertEquals(BudgetRepository.SaveResult.LimitNotPositive, budgets.save(null, null, Money(0, "USD")))
    }

    @Test fun editingABudgetKeepsItsSlot() = runTest {
        val id = (budgets.save(null, null, Money(50_000, "USD")) as BudgetRepository.SaveResult.Saved).id
        assertTrue(budgets.save(id, null, Money(70_000, "USD")) is BudgetRepository.SaveResult.Saved)
        assertEquals(Money(70_000, "USD"), budgets.observeBudgets().first().single().limit)
    }

    // ---- goals

    @Test fun contributionsEarmarkWithoutTouchingWallets() = runTest {
        val id = (goals.save(null, "Laptop", Money(100_000, "USD"), null, bank) as GoalRepository.SaveResult.Saved).id
        assertEquals(GoalRepository.ContributionResult.Saved, goals.contribute(id, Money(30_000, "USD"), start, "", "c1"))
        assertEquals(GoalRepository.ContributionResult.Saved, goals.contribute(id, Money(30_000, "USD"), start, "", "c1"))
        assertEquals(1, goalDao.contributions.value.size)
        assertEquals(Money(100_000, "USD"), wallets.observeWallets().first().single().balance)
        val c = goals.observeContributions().first().single()
        assertEquals(Money(30_000, "USD"), c.amount)
    }

    @Test fun cannotWithdrawMoreThanSaved() = runTest {
        val id = (goals.save(null, "Trip", Money(100_000, "USD"), null, null) as GoalRepository.SaveResult.Saved).id
        goals.contribute(id, Money(10_000, "USD"), start, "", "a")
        assertEquals(GoalRepository.ContributionResult.ExceedsSaved, goals.contribute(id, Money(-10_001, "USD"), start, "", "b"))
        assertEquals(GoalRepository.ContributionResult.Saved, goals.contribute(id, Money(-10_000, "USD"), start, "", "c"))
        assertEquals(GoalRepository.ContributionResult.CurrencyMismatch, goals.contribute(id, Money(1, "EUR"), start, "", "d"))
        assertEquals(GoalRepository.ContributionResult.AmountZero, goals.contribute(id, Money(0, "USD"), start, "", "e"))
    }

    @Test fun goalCurrencyIsLockedAfterCreation() = runTest {
        val id = (goals.save(null, "Trip", Money(100_000, "USD"), null, null) as GoalRepository.SaveResult.Saved).id
        assertEquals(GoalRepository.SaveResult.CurrencyLocked, goals.save(id, "Trip", Money(100_000, "EUR"), null, null))
        assertEquals(GoalRepository.SaveResult.BlankName, goals.save(null, "  ", Money(1, "USD"), null, null))
    }

    // ---- recurring

    private suspend fun rentSchedule(): Long = (
        recurring.save(
            RecurringRepository.Input(null, "Rent", TransactionType.EXPENSE, Money(12_000, "USD"), bank, rent, Frequency.MONTHLY, 1, start, null)
        ) as RecurringRepository.SaveResult.Saved
        ).id

    @Test fun markingPaidRecordsOneTransactionAndAdvances() = runTest {
        val id = rentSchedule()
        assertTrue(recurring.markPaid(id) is RecurringRepository.PayResult.Recorded)
        val schedule = recurring.observeSchedules().first().single()
        assertEquals(1, schedule.nextIndex)
        assertEquals(LocalDate.parse("2026-02-28"), schedule.nextDue)
        val recorded = transactions.observeTransactions().first().single()
        assertEquals(LocalDate.parse("2026-01-31"), recorded.date)
        assertEquals(Money(12_000, "USD"), recorded.amount)
        assertEquals(Money(88_000, "USD"), wallets.observeWallets().first().single().balance)
    }

    @Test fun payingTheSameOccurrenceTwiceNeverDuplicates() = runTest {
        val id = rentSchedule()
        val key = recurring.observeSchedules().first().single().occurrenceKey(0)
        recurring.markPaid(id)
        // Simulate a stale retry of occurrence 0 after the index advanced.
        transactions.save(
            TransactionRepository.Input(null, key, TransactionType.EXPENSE, Money(12_000, "USD"), bank, rent, start, "Rent")
        )
        assertEquals(1, transactionDao.rows.value.size)
    }

    @Test fun skipAdvancesWithoutRecording() = runTest {
        val id = rentSchedule()
        recurring.skip(id)
        assertEquals(1, recurring.observeSchedules().first().single().nextIndex)
        assertTrue(transactionDao.rows.value.isEmpty())
    }

    @Test fun variableBillCanBePaidWithADifferentAmount() = runTest {
        val id = rentSchedule()
        recurring.markPaid(id, amount = Money(12_345, "USD"), date = LocalDate.parse("2026-02-02"))
        val recorded = transactions.observeTransactions().first().single()
        assertEquals(Money(12_345, "USD"), recorded.amount)
        assertEquals(LocalDate.parse("2026-02-02"), recorded.date)
    }

    @Test fun walletsWithSchedulesCannotBeDeleted() = runTest {
        rentSchedule()
        assertEquals(WalletRepository.DeleteResult.HasSchedules(1), wallets.delete(bank))
    }

    @Test fun scheduleValidation() = runTest {
        fun input(type: TransactionType = TransactionType.EXPENSE, amount: Long = 100, interval: Int = 1, end: LocalDate? = null, category: Long? = rent) =
            RecurringRepository.Input(null, "X", type, Money(amount, "USD"), bank, category, Frequency.WEEKLY, interval, start, end)
        assertEquals(RecurringRepository.SaveResult.InvalidType, recurring.save(input(type = TransactionType.TRANSFER_OUT)))
        assertEquals(RecurringRepository.SaveResult.AmountNotPositive, recurring.save(input(amount = 0)))
        assertEquals(RecurringRepository.SaveResult.InvalidInterval, recurring.save(input(interval = 0)))
        assertEquals(RecurringRepository.SaveResult.EndBeforeStart, recurring.save(input(end = start.minusDays(1))))
        assertEquals(RecurringRepository.SaveResult.CategoryMismatch, recurring.save(input(type = TransactionType.INCOME)))
    }
}
