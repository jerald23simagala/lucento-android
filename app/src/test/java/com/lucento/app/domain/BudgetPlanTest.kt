package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The overall budget is always the sum of category allocations; every surface reads the same plan. */
class BudgetPlanTest {

    private val month = YearMonth.of(2026, 10)
    private val housing = Category(1, "Housing", CategoryKind.EXPENSE, "housing")
    private val groceries = Category(2, "Groceries", CategoryKind.EXPENSE, "groceries")
    private val transport = Category(3, "Transportation", CategoryKind.EXPENSE, "transport")
    private val dining = Category(4, "Dining", CategoryKind.EXPENSE, "dining")
    private val salary = Category(9, "Salary", CategoryKind.INCOME, "work")
    private val categories = listOf(housing, groceries, transport, dining, salary)
    private var nextId = 1L

    private fun alloc(category: Long?, minor: Long, code: String = "PHP", id: Long = nextId++) = Budget(id, category, Money(minor, code))
    private fun spend(minor: Long, category: Long?, date: String = "2026-10-10", code: String = "PHP", type: TransactionType = TransactionType.EXPENSE) =
        Transaction(nextId++, type, Money(minor, code), 1, category, LocalDate.parse(date), "", 0L,
            transferGroup = if (type.isTransfer) "g${nextId}" else null)

    private val example = listOf(alloc(1, 15_000_00), alloc(2, 8_000_00), alloc(3, 3_000_00))

    private fun plan(budgets: List<Budget>, txs: List<Transaction> = emptyList(), cats: List<Category> = categories, code: String = "PHP") =
        BudgetPlanner.plan(cats, budgets, txs, code, month)

    @Test fun overallEqualsSumOfCategoryAllocations() {
        val p = plan(example)
        assertEquals(Money(26_000_00, "PHP"), p.total)
        assertEquals(3, p.allocatedLines.size)
        assertEquals(listOf(dining.id), p.unallocatedLines.map { it.category.id })
    }

    @Test fun changingAnAllocationRecalculatesTheTotal() {
        val updated = example.map { if (it.categoryId == 2L) it.copy(limit = Money(10_000_00, "PHP")) else it }
        assertEquals(Money(28_000_00, "PHP"), plan(updated).total)
        val added = example + alloc(4, 2_000_00)
        assertEquals(Money(28_000_00, "PHP"), plan(added).total)
        val removed = example.filterNot { it.categoryId == 3L }
        assertEquals(Money(23_000_00, "PHP"), plan(removed).total)
    }

    @Test fun deactivatedCategoriesLeaveTheTotalButKeepTheirSpendingVisible() {
        val archivedTransport = categories.map { if (it.id == 3L) it.copy(archived = true) else it }
        val p = plan(example, listOf(spend(500_00, 3)), cats = archivedTransport)
        assertEquals(Money(23_000_00, "PHP"), p.total)
        assertEquals(Money.zero("PHP"), p.spent)
        assertEquals(Money(500_00, "PHP"), p.unbudgetedSpending)
        assertTrue(p.lines.none { it.category.id == 3L })
    }

    @Test fun legacyOverallRowAndIncomeCategoryAllocationsNeverCount() {
        val legacy = alloc(null, 99_000_00)
        val p = plan(example + legacy + alloc(9, 1_000_00))
        assertEquals(Money(26_000_00, "PHP"), p.total)
        assertEquals(legacy, p.legacyOverall)
    }

    @Test fun currenciesAreNeverSummedTogether() {
        val budgets = example + alloc(2, 100_00, code = "USD")
        assertEquals(Money(26_000_00, "PHP"), plan(budgets).total)
        val usd = plan(budgets, listOf(spend(40_00, 2, code = "USD"), spend(999_00, 2)), code = "USD")
        assertEquals(Money(100_00, "USD"), usd.total)
        assertEquals(Money(40_00, "USD"), usd.spent)
        assertEquals(listOf("PHP", "USD"), BudgetPlanner.currencies(budgets, categories, "PHP"))
        assertEquals(listOf("EUR", "PHP", "USD"), BudgetPlanner.currencies(budgets, categories, "EUR"))
    }

    @Test fun duplicateAllocationsForOneCategoryCountOnce() {
        val p = plan(listOf(alloc(1, 1_000, id = 5), alloc(1, 7_000, id = 6)))
        assertEquals(Money(1_000, "PHP"), p.total)
    }

    @Test fun spendingIsSplitIntoBudgetedAndTransparentlyUnbudgeted() {
        val txs = listOf(
            spend(14_000_00, 1),
            spend(2_000_00, 2),
            spend(700_00, 4), // dining: active but no allocation
            spend(300_00, null), // uncategorised
            spend(5_000_00, null, type = TransactionType.INCOME),
            spend(9_000_00, null, type = TransactionType.TRANSFER_OUT),
            spend(1_000_00, 1, date = "2026-09-30"), // previous month
        )
        val p = plan(example, txs)
        assertEquals(Money(16_000_00, "PHP"), p.spent)
        assertEquals(Money(1_000_00, "PHP"), p.unbudgetedSpending)
        assertEquals(Money(300_00, "PHP"), p.uncategorizedSpending)
        assertEquals(Money(17_000_00, "PHP"), p.allSpending)
        assertEquals(Money(10_000_00, "PHP"), p.remaining)
        assertEquals(Money(700_00, "PHP"), p.lines.single { it.category.id == 4L }.spent)
        // Allocated first (most used first), then unallocated.
        assertEquals(listOf(1L, 2L, 3L, 4L), p.lines.map { it.category.id })
    }

    @Test fun missingAndZeroBudgetsHaveNoPercentage() {
        val none = plan(emptyList(), listOf(spend(100, 1)))
        assertFalse(none.hasBudget)
        assertEquals(BudgetHealth.NOT_SET, none.health)
        assertNull(none.percentUsed)
        assertEquals(0f, none.fraction)
        val zero = plan(listOf(alloc(1, 0)), listOf(spend(100, 1)))
        assertFalse(zero.hasBudget)
        assertNull(zero.percentUsed)
        assertEquals(Money(100, "PHP"), zero.unbudgetedSpending)
    }

    @Test fun healthThresholdsAreExact() {
        fun h(total: Long, spent: Long) = BudgetPlanner.health(Money(total, "PHP"), Money(spent, "PHP"))
        assertEquals(BudgetHealth.NOT_SET, h(0, 0))
        assertEquals(BudgetHealth.ON_TRACK, h(10_000, 0))
        assertEquals(BudgetHealth.ON_TRACK, h(10_000, 7_999))
        assertEquals(BudgetHealth.APPROACHING, h(10_000, 8_000))
        assertEquals(BudgetHealth.APPROACHING, h(10_000, 8_999))
        assertEquals(BudgetHealth.LOW, h(10_000, 9_000)) // exactly 10% left
        assertEquals(BudgetHealth.LOW, h(10_000, 9_999))
        assertEquals(BudgetHealth.EXCEEDED, h(10_000, 10_000)) // exhausted
        assertEquals(BudgetHealth.EXCEEDED, h(10_000, 10_001))
        assertFalse(BudgetHealth.APPROACHING.isConcerning)
        assertTrue(BudgetHealth.LOW.isConcerning)
        assertTrue(BudgetHealth.EXCEEDED.isConcerning)
        // Huge values compare exactly (no overflow, no rounding).
        assertEquals(BudgetHealth.LOW, h(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2 - 1))
    }

    @Test fun exceededBudgetShowsNegativeRemaining() {
        val p = plan(listOf(alloc(2, 1_000)), listOf(spend(1_250, 2)))
        assertEquals(Money(-250, "PHP"), p.remaining)
        assertEquals(125, p.percentUsed)
        assertEquals(1f, p.fraction)
        assertEquals(BudgetHealth.EXCEEDED, p.health)
        assertEquals(BudgetHealth.EXCEEDED, p.lines.first().health)
    }

    @Test fun burnUsesTheSamePlanAndLevels() {
        val p = plan(example, listOf(spend(20_800_00, 1)))
        val burn = BudgetPlanner.burn(p, LocalDate.parse("2026-10-16"))
        assertEquals(p.spent, burn.spent)
        assertEquals(p.percentUsed, burn.consumedPercent)
        assertEquals(80, burn.consumedPercent)
        assertEquals(BudgetHealth.APPROACHING, burn.health)
        assertEquals(BurnLevel.APPROACHING, burn.level)
        assertEquals(16, burn.daysElapsed)
        assertEquals(51, burn.elapsedPercent) // 16 of 31
        assertTrue(burn.aheadOfPace)
        assertEquals(Money(1_300_00, "PHP"), burn.dailyAverage)
        assertEquals(Money(40_300_00, "PHP"), burn.projected)
        assertEquals(Money(325_00, "PHP"), burn.dailyAllowance) // 5,200 over 16 days (16th..31st)
    }

    @Test fun burnLevelsMapFromHealth() {
        assertEquals(BurnLevel.NO_BUDGET, BurnLevel.of(BudgetHealth.NOT_SET))
        assertEquals(BurnLevel.NORMAL, BurnLevel.of(BudgetHealth.ON_TRACK))
        assertEquals(BurnLevel.APPROACHING, BurnLevel.of(BudgetHealth.LOW))
        assertEquals(BurnLevel.EXHAUSTED, BurnLevel.of(BudgetHealth.EXCEEDED))
    }

    @Test fun burnWithoutBudgetHasNoPercentagesOrAllowance() {
        val burn = BudgetPlanner.burn(plan(emptyList(), listOf(spend(100, 1))), LocalDate.parse("2026-10-05"))
        assertEquals(BurnLevel.NO_BUDGET, burn.level)
        assertNull(burn.total)
        assertNull(burn.consumedPercent)
        assertNull(burn.dailyAllowance)
        assertFalse(burn.aheadOfPace)
    }

    @Test fun finishedMonthHasNoAllowanceAndFullElapsed() {
        val burn = BudgetPlanner.burn(plan(example, listOf(spend(100_00, 1))), LocalDate.parse("2026-11-03"))
        assertEquals(31, burn.daysElapsed)
        assertEquals(100, burn.elapsedPercent)
        assertNull(burn.dailyAllowance)
    }
}
