package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.CategoryPresets
import com.lucento.app.domain.CategorySelection
import com.lucento.app.domain.DefaultCategories
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Onboarding category selection, the existing-user setup, category management and allocation edits. */
class CategorySetupRepositoryTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val budgetDao = FakeBudgetDao()
    private val recurringDao = FakeRecurringDao()

    init {
        categoryDao.transactions = transactionDao
        categoryDao.budgets = budgetDao
        categoryDao.recurring = recurringDao
    }

    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1L }, scheduleCount = { recurringDao.countForWallet(it) })
    private val categories = CategoryRepository(categoryDao, clock = { 1L })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2L })
    private val budgets = BudgetRepository(budgetDao, categoryDao, clock = { 3L })

    private fun preset(key: String) = CategorySelection.Choice.FromPreset(CategoryPresets.preset(key)!!)

    private suspend fun seed(list: List<DefaultCategories.Seed>) = list.forEach { categories.create(it.name, it.kind, it.iconKey) }

    private suspend fun expenseNames() = categories.observeCategories().first()
        .filter { it.kind == CategoryKind.EXPENSE && !it.archived }.map { it.name }

    @Test fun onboardingCreatesOnlyTheChosenExpenseCategories() = runTest {
        seed(DefaultCategories.freshInstall) // what a fresh 0.5 database contains
        assertTrue(expenseNames().isEmpty())
        val result = categories.applySelection(listOf(preset("rent"), preset("groceries"), CategorySelection.Choice.Custom("Boat")))
        assertEquals(3, result.created)
        assertEquals(listOf("Rent", "Groceries", "Boat"), expenseNames())
        // Income categories are untouched.
        assertEquals(DefaultCategories.freshInstall.size, categories.observeCategories().first().count { it.kind == CategoryKind.INCOME })
    }

    @Test fun applyingTheSameSelectionTwiceNeverDuplicates() = runTest {
        val choice = listOf(preset("rent"), preset("water"))
        categories.applySelection(choice)
        val ids = categories.observeCategories().first().map { it.id }
        val again = categories.applySelection(choice)
        assertEquals(0, again.created)
        assertEquals(ids, categories.observeCategories().first().map { it.id })
    }

    /** An existing (pre-0.5) user keeps their history: used categories are archived, never deleted. */
    @Test fun existingUserSetupPreservesIdsTransactionsAndBudgets() = runTest {
        seed(DefaultCategories.all) // what a pre-0.5 install has
        val all = categories.observeCategories().first()
        val groceries = all.single { it.name == "Groceries" }
        val dining = all.single { it.name == "Dining" }
        val bank = (wallets.create("Bank", WalletType.BANK, Money(100_000, "USD")) as WalletRepository.SaveResult.Saved).id
        transactions.save(TransactionRepository.Input(null, "t1", TransactionType.EXPENSE, Money(900, "USD"), bank, dining.id, LocalDate.parse("2026-10-02"), ""))
        budgets.save(null, groceries.id, Money(5_000, "USD"))

        val result = categories.applySelection(listOf(preset("groceries"), preset("internet")))
        assertEquals(1, result.created) // Internet
        assertEquals(1, result.archived) // Dining has a transaction
        assertEquals(DefaultCategories.all.count { it.kind == CategoryKind.EXPENSE } - 2, result.deleted)
        val after = categories.observeCategories().first()
        assertEquals(groceries.id, after.single { it.name == "Groceries" }.id)
        assertTrue(after.single { it.id == dining.id }.archived)
        assertEquals(dining.id, transactionDao.rows.value.single().categoryId) // history keeps its label
        assertEquals(groceries.id, budgetDao.rows.value.single().categoryId)

        // Choosing Dining again later restores the same category instead of creating a twin.
        categories.applySelection(listOf(preset("groceries"), preset("internet"), CategorySelection.Choice.Existing(dining.id, "Dining", "dining")))
        assertFalse(categories.observeCategories().first().single { it.id == dining.id }.archived)
        assertEquals(1, categories.observeCategories().first().count { it.name == "Dining" })
    }

    @Test fun allocationsDriveTheOverallBudgetAndCanBeChangedOrCleared() = runTest {
        categories.applySelection(listOf(preset("rent"), preset("groceries"), preset("public_transport")))
        val byName = categories.observeCategories().first().associateBy { it.name }
        budgets.setAllocation(byName.getValue("Rent").id, "PHP", Money(15_000_00, "PHP"))
        budgets.setAllocation(byName.getValue("Groceries").id, "PHP", Money(8_000_00, "PHP"))
        budgets.setAllocation(byName.getValue("Public transportation").id, "PHP", Money(3_000_00, "PHP"))

        suspend fun total() = BudgetPlanner.plan(categories.observeCategories().first(), budgets.observeBudgets().first(), emptyList(), "PHP", YearMonth.of(2026, 10)).total
        assertEquals(Money(26_000_00, "PHP"), total())

        val rentAllocation = budgetDao.rows.value.single { it.categoryId == byName.getValue("Rent").id }
        budgets.setAllocation(byName.getValue("Rent").id, "PHP", Money(16_000_00, "PHP"))
        assertEquals(Money(27_000_00, "PHP"), total())
        assertEquals(rentAllocation.id, budgetDao.rows.value.single { it.categoryId == byName.getValue("Rent").id }.id) // updated in place

        budgets.setAllocation(byName.getValue("Groceries").id, "PHP", null)
        assertEquals(Money(19_000_00, "PHP"), total())

        // Deactivating a category with an allocation archives it (the allocation is kept but no longer counts).
        categories.remove(byName.getValue("Public transportation").id)
        assertEquals(Money(16_000_00, "PHP"), total())
        assertEquals(2, budgetDao.rows.value.size)
    }

    @Test fun legacyOverallBudgetCanBeRemovedWithoutTouchingAllocations() = runTest {
        categories.applySelection(listOf(preset("rent")))
        val rent = categories.observeCategories().first().single { it.name == "Rent" }.id
        budgetDao.insert(com.lucento.app.data.local.BudgetEntity(categoryId = null, currencyCode = "PHP", limitMinor = 30_000_00, createdAt = 1, updatedAt = 1))
        budgets.setAllocation(rent, "PHP", Money(10_000_00, "PHP"))
        assertEquals(1, budgets.removeLegacyOverall())
        assertEquals(listOf(rent), budgetDao.rows.value.map { it.categoryId })
    }

    @Test fun categoriesCanBeRenamedAndReordered() = runTest {
        categories.applySelection(listOf(preset("rent"), preset("groceries"), preset("water")))
        val ids = categories.observeCategories().first().filter { it.kind == CategoryKind.EXPENSE }.map { it.id }
        assertTrue(categories.move(ids[2], -1))
        assertEquals(listOf("Rent", "Water", "Groceries"), expenseNames())
        assertFalse(categories.move(ids[0], -1)) // already first
        assertTrue(categories.rename(ids[0], "Apartment rent") is CategoryRepository.SaveResult.Saved)
        assertEquals("Apartment rent", expenseNames().first())
        assertEquals(CategoryRepository.SaveResult.DuplicateName, categories.rename(ids[0], "water"))
    }
}
