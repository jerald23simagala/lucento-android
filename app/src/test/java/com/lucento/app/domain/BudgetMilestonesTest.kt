package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetMilestonesTest {
    private val today = LocalDate.parse("2026-10-03")
    private val budget = Budget(1, null, Money(10_000, "PHP"), createdOn = LocalDate.parse("2026-08-15"))
    private fun spend(minor: Long, date: String) =
        Transaction(minor, TransactionType.EXPENSE, Money(minor, "PHP"), 1, null, LocalDate.parse(date), "", 0)

    @Test fun keptLastMonthIsAcknowledged() {
        val p = BudgetMilestones.monthToAcknowledge(listOf(budget), listOf(spend(9_000, "2026-09-10")), "PHP", today, null)
        assertEquals(YearMonth.of(2026, 9), p?.month)
    }

    @Test fun overspendingIsNeverCelebrated() =
        assertNull(BudgetMilestones.monthToAcknowledge(listOf(budget), listOf(spend(10_001, "2026-09-10")), "PHP", today, null))

    @Test fun exactlyAtTheLimitCounts() =
        assertEquals(YearMonth.of(2026, 9), BudgetMilestones.monthToAcknowledge(listOf(budget), listOf(spend(10_000, "2026-09-10")), "PHP", today, null)?.month)

    @Test fun shownOnlyOnce() =
        assertNull(BudgetMilestones.monthToAcknowledge(listOf(budget), listOf(spend(1, "2026-09-10")), "PHP", today, YearMonth.of(2026, 9)))

    @Test fun emptyMonthsAndPartialMonthsDoNotCount() {
        assertNull(BudgetMilestones.monthToAcknowledge(listOf(budget), emptyList(), "PHP", today, null))
        val late = budget.copy(createdOn = LocalDate.parse("2026-09-02"))
        assertNull(BudgetMilestones.monthToAcknowledge(listOf(late), listOf(spend(1, "2026-09-10")), "PHP", today, null))
        assertNull(BudgetMilestones.monthToAcknowledge(listOf(budget.copy(createdOn = null)), listOf(spend(1, "2026-09-10")), "PHP", today, null))
    }

    @Test fun onlyTheMatchingCurrencyAndOverallBudget() {
        assertNull(BudgetMilestones.monthToAcknowledge(listOf(budget), listOf(spend(1, "2026-09-10")), "USD", today, null))
        assertNull(BudgetMilestones.monthToAcknowledge(listOf(budget.copy(categoryId = 4)), listOf(spend(1, "2026-09-10")), "PHP", today, null))
    }
}
