package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.CategoryRepository.DeleteResult
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.CategoryUsage
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Deleting a category must never cascade away a budget or silently uncategorise schedules (audit finding 1.3). */
class CategoryDeletionTest {

    private val walletDao = FakeWalletDao()
    private val transactionDao = FakeTransactionDao()
    private val budgetDao = FakeBudgetDao()
    private val recurringDao = FakeRecurringDao()
    private val categoryDao = FakeCategoryDao().also {
        it.transactions = transactionDao
        it.budgets = budgetDao
        it.recurring = recurringDao
    }
    private val categories = CategoryRepository(categoryDao, clock = { 1L })
    private val budgets = BudgetRepository(budgetDao, categoryDao, clock = { 2L })
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 3L })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 4L })
    private val recurring = RecurringRepository(recurringDao, walletDao, categoryDao, transactions, clock = { 5L })

    private var dining = 0L
    private var bank = 0L

    @Before fun setUp() = runTest {
        dining = (categories.create("Dining", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id
        bank = (wallets.create("Bank", WalletType.BANK, Money(10_000, "USD")) as WalletRepository.SaveResult.Saved).id
    }

    @Test fun unusedCategoryIsDeleted() = runTest {
        assertEquals(DeleteResult.Deleted, categories.remove(dining))
        assertTrue(categoryDao.rows.value.none { it.id == dining })
    }

    /** The bug: no transactions, but a budget — deleting used to cascade-delete the budget. */
    @Test fun categoryWithOnlyABudgetIsArchivedAndTheBudgetSurvives() = runTest {
        budgets.save(null, dining, Money(20_000, "USD"))
        val result = categories.remove(dining)
        assertEquals(DeleteResult.Archived(CategoryUsage(transactionCount = 0, budgetCount = 1, scheduleCount = 0)), result)
        assertTrue(categoryDao.getById(dining)!!.archived)
        assertEquals(1, budgetDao.rows.value.size)
        assertEquals(dining, budgetDao.rows.value.single().categoryId)
        assertEquals(20_000L, budgetDao.rows.value.single().limitMinor)
    }

    @Test fun categoryUsedBySchedulesIsArchivedAndKeepsTheirLink() = runTest {
        recurring.save(
            RecurringRepository.Input(null, "Lunch club", TransactionType.EXPENSE, Money(1_500, "USD"), bank, dining, Frequency.WEEKLY, 1, LocalDate.parse("2026-10-01"), null)
        )
        assertEquals(DeleteResult.Archived(CategoryUsage(0, 0, 1)), categories.remove(dining))
        assertEquals(dining, recurringDao.rows.value.single().categoryId)
    }

    @Test fun categoryWithHistoryIsArchivedAndTransactionsKeepTheirLabel() = runTest {
        transactions.save(TransactionRepository.Input(null, "k", TransactionType.EXPENSE, Money(900, "USD"), bank, dining, LocalDate.parse("2026-10-02"), ""))
        assertEquals(DeleteResult.Archived(CategoryUsage(1, 0, 0)), categories.remove(dining))
        assertEquals(dining, transactionDao.rows.value.single().categoryId)
    }

    @Test fun removingTwiceIsHarmlessAndRestoreBringsItBack() = runTest {
        budgets.save(null, dining, Money(20_000, "USD"))
        categories.remove(dining)
        assertTrue(categories.remove(dining) is DeleteResult.Archived)
        assertEquals(CategoryRepository.SaveResult.Saved(dining), categories.restore(dining))
        assertFalse(categoryDao.getById(dining)!!.archived)
        assertEquals(1, budgetDao.rows.value.size)
    }

    @Test fun restoreRefusesANameNowTakenByAnActiveCategory() = runTest {
        budgets.save(null, dining, Money(1, "USD"))
        categories.remove(dining)
        categories.create("dining", CategoryKind.EXPENSE)
        assertEquals(CategoryRepository.SaveResult.DuplicateName, categories.restore(dining))
    }

    @Test fun missingCategory() = runTest {
        assertEquals(DeleteResult.NotFound, categories.remove(404))
    }
}
