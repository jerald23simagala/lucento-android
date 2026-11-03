package com.lucento.app.domain

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.FakeBudgetDao
import com.lucento.app.data.repository.FakeCategoryDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Budget lifecycle (owner requirements C3–C5, C8, C9): a normal change applies to the selected month only; "update
 * future months" starts a new recurring amount the month after; earlier months keep what applied to them.
 */
class BudgetLifecycleTest {

    private val jan = YearMonth.of(2027, 1)
    private val feb = jan.plusMonths(1)
    private val dec = jan.minusMonths(1)
    private val groceries = Category(2, "Groceries", CategoryKind.EXPENSE, "groceries")
    private val categories = listOf(groceries)
    private var nextId = 1L

    private fun row(minor: Long, month: YearMonth? = null, from: YearMonth? = null) =
        Budget(nextId++, groceries.id, Money(minor, "USD"), month = month, effectiveFrom = from)

    private fun total(budgets: List<Budget>, month: YearMonth) =
        BudgetPlanner.plan(categories, budgets, emptyList(), "USD", month).total.amountMinor

    @Test fun `a budget saved before the lifecycle applies to every month, as it always did`() {
        val legacy = listOf(row(2_000_00))
        listOf(dec, jan, feb, feb.plusMonths(12)).forEach { assertEquals(2_000_00L, total(legacy, it)) }
    }

    @Test fun `changing January only - January uses the new amount, December and February keep theirs`() {
        val budgets = listOf(row(2_000_00), row(2_500_00, month = jan))
        assertEquals(2_500_00L, total(budgets, jan))
        assertEquals(2_000_00L, total(budgets, feb))
        assertEquals(2_000_00L, total(budgets, dec))
    }

    @Test fun `updating future months starts the month after - this month and earlier months are unchanged`() {
        val budgets = listOf(row(2_000_00), row(3_000_00, from = feb))
        assertEquals(2_000_00L, total(budgets, dec))
        assertEquals(2_000_00L, total(budgets, jan))
        assertEquals(3_000_00L, total(budgets, feb))
        assertEquals(3_000_00L, total(budgets, feb.plusMonths(5)))
    }

    @Test fun `a month override wins over a later recurring change for that month, and 0 means no amount that month`() {
        val budgets = listOf(row(2_000_00), row(3_000_00, from = jan), row(2_200_00, month = feb), row(0, month = jan.plusMonths(3)))
        assertEquals(3_000_00L, total(budgets, jan))
        assertEquals(2_200_00L, total(budgets, feb))
        assertEquals(0L, total(budgets, jan.plusMonths(3)))
        assertEquals(3_000_00L, total(budgets, jan.plusMonths(4)))
    }

    @Test fun `a recurring 0 stops the allocation from that month, earlier months keep it`() {
        val budgets = listOf(row(2_000_00), row(0, from = feb))
        assertEquals(2_000_00L, total(budgets, jan))
        assertEquals(0L, total(budgets, feb))
    }

    @Test fun `duplicates within one tier resolve as before - the first stored row with an amount`() {
        val budgets = listOf(row(0), row(500_00))
        assertEquals(500_00L, total(budgets, jan))
    }

    @Test fun `spending is counted by the transaction's calendar date, so a new month starts on the 1st whatever the timezone`() {
        val budgets = listOf(row(1_000_00))
        val lateDec31 = Transaction(1, TransactionType.EXPENSE, Money(10_00, "USD"), 1, groceries.id, LocalDate.of(2026, 12, 31), "", 0)
        val earlyJan1 = Transaction(2, TransactionType.EXPENSE, Money(20_00, "USD"), 1, groceries.id, LocalDate.of(2027, 1, 1), "", 0)
        assertEquals(10_00L, BudgetPlanner.plan(categories, budgets, listOf(lateDec31, earlyJan1), "USD", dec).spent.amountMinor)
        assertEquals(20_00L, BudgetPlanner.plan(categories, budgets, listOf(lateDec31, earlyJan1), "USD", jan).spent.amountMinor)
    }

    // ---------------------------------------------------------------- repository: what each action writes

    private suspend fun setup(): Triple<BudgetRepository, FakeBudgetDao, Long> {
        val categoryDao = FakeCategoryDao()
        val id = (CategoryRepository(categoryDao).create("Groceries", CategoryKind.EXPENSE, "groceries") as CategoryRepository.SaveResult.Saved).id
        val dao = FakeBudgetDao()
        return Triple(BudgetRepository(dao, categoryDao, clock = { 1_000L }), dao, id)
    }

    private suspend fun BudgetRepository.totalIn(categoryId: Long, month: YearMonth): Long {
        val cats = listOf(Category(categoryId, "Groceries", CategoryKind.EXPENSE, "groceries"))
        return BudgetPlanner.plan(cats, observeBudgets().first(), emptyList(), "USD", month).total.amountMinor
    }

    @Test fun `repository - first amount, this-month change, future change, and nothing else moves`() = runTest {
        val (repo, dao, cat) = setup()
        assertTrue(repo.setAllocation(cat, "USD", Money(2_000_00, "USD"), from = jan) is BudgetRepository.SaveResult.Saved)
        assertEquals(0L, repo.totalIn(cat, dec)) // a brand-new budget doesn't reach back into earlier months

        repo.setMonthAmount(cat, "USD", jan, Money(2_500_00, "USD"))
        assertEquals(2_500_00L, repo.totalIn(cat, jan))
        assertEquals(2_000_00L, repo.totalIn(cat, feb))

        repo.setRecurring(cat, "USD", feb, Money(3_000_00, "USD"))
        assertEquals(2_500_00L, repo.totalIn(cat, jan)) // January's own change kept
        assertEquals(3_000_00L, repo.totalIn(cat, feb))
        assertEquals(3, dao.rows.value.size)

        repo.setMonthAmount(cat, "USD", jan, Money(2_600_00, "USD")) // edits the same override, no new row
        assertEquals(3, dao.rows.value.size)
        repo.setMonthAmount(cat, "USD", jan, null) // removing the override: January follows the recurring amount again
        assertEquals(2_000_00L, repo.totalIn(cat, jan))
    }

    @Test fun `repository - removing an amount from a month on keeps earlier months, removing a brand-new one deletes it`() = runTest {
        val (repo, dao, cat) = setup()
        repo.setAllocation(cat, "USD", Money(2_000_00, "USD"), from = dec)
        repo.setRecurring(cat, "USD", feb, null)
        assertEquals(2_000_00L, repo.totalIn(cat, jan))
        assertEquals(0L, repo.totalIn(cat, feb))

        val (repo2, dao2, cat2) = setup()
        repo2.setAllocation(cat2, "USD", Money(1_000_00, "USD"), from = jan)
        repo2.setAllocation(cat2, "USD", null, from = jan)
        assertTrue(dao2.rows.value.isEmpty())
        assertNull(BudgetPlanner.effectiveAllocation(emptyList(), jan))
        assertTrue(dao.rows.value.isNotEmpty())
    }

    @Test fun `repository - onward from a month replaces that month's own amount and later plans, not earlier months`() = runTest {
        val (repo, _, cat) = setup()
        repo.setAllocation(cat, "USD", Money(2_000_00, "USD"), from = dec)
        repo.setMonthAmount(cat, "USD", jan, Money(2_500_00, "USD"))
        repo.setMonthAmount(cat, "USD", feb.plusMonths(1), Money(1_000_00, "USD")) // March changed on its own
        repo.setRecurring(cat, "USD", feb.plusMonths(2), Money(9_000_00, "USD")) // a plan starting in April

        repo.setFrom(cat, "USD", jan, Money(3_000_00, "USD"))
        assertEquals(2_000_00L, repo.totalIn(cat, dec))
        assertEquals(3_000_00L, repo.totalIn(cat, jan))
        assertEquals(3_000_00L, repo.totalIn(cat, feb))
        assertEquals(1_000_00L, repo.totalIn(cat, feb.plusMonths(1))) // its own amount is kept
        assertEquals(3_000_00L, repo.totalIn(cat, feb.plusMonths(2))) // the later plan is replaced
    }

    @Test fun `repository - removing for one month leaves the usual amount for every other month`() = runTest {
        val (repo, dao, cat) = setup()
        repo.setAllocation(cat, "USD", Money(2_000_00, "USD"), from = dec)
        repo.clearMonth(cat, "USD", jan)
        assertEquals(0L, repo.totalIn(cat, jan))
        assertEquals(2_000_00L, repo.totalIn(cat, dec))
        assertEquals(2_000_00L, repo.totalIn(cat, feb))

        repo.setMonthAmount(cat, "USD", jan, null) // "use the usual amount" again
        assertEquals(2_000_00L, repo.totalIn(cat, jan))
        assertEquals(1, dao.rows.value.size)

        val (repo2, dao2, cat2) = setup()
        repo2.setMonthAmount(cat2, "USD", jan, Money(500_00, "USD")) // a month-only amount with no usual one
        repo2.clearMonth(cat2, "USD", jan)
        assertTrue("nothing to override, so the row is simply removed", dao2.rows.value.isEmpty())
    }

    @Test fun `repository - zero or negative amounts are refused, the user removes instead`() = runTest {
        val (repo, _, cat) = setup()
        assertEquals(BudgetRepository.SaveResult.LimitNotPositive, repo.setMonthAmount(cat, "USD", jan, Money(0, "USD")))
        assertEquals(BudgetRepository.SaveResult.LimitNotPositive, repo.setRecurring(cat, "USD", jan, Money(-1, "USD")))
    }
}
