package com.lucento.app.feature.budget

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

/** What the budget sheet offers and says for each situation (budget lifecycle C4–C6). */
class BudgetChangeTest {

    private val nov = YearMonth.of(2026, 11)
    private var nextId = 1L
    private fun row(minor: Long, month: YearMonth? = null, from: YearMonth? = null, category: Long = 7) =
        Budget(nextId++, category, Money(minor, "USD"), month = month, effectiveFrom = from)

    private fun context(budgets: List<Budget>, month: YearMonth = nov) =
        BudgetChangeContext.of(budgets, 7, "Groceries", "USD", month, currentMonth = nov)

    @Test fun `a category without any amount starts as a monthly amount, with no confirmation`() {
        val c = context(emptyList())
        assertEquals(BudgetScope.ONWARD, c.defaultScope)
        assertFalse(c.needsConfirmation(BudgetScope.ONWARD, removing = false))
        assertNull(c.status)
    }

    @Test fun `adjusting an existing amount is month-only by default, and onward asks first`() {
        val c = context(listOf(row(2_000_00)))
        assertEquals(BudgetScope.THIS_MONTH, c.defaultScope)
        assertEquals(listOf(BudgetScope.THIS_MONTH, BudgetScope.ONWARD), c.scopes)
        assertFalse(c.needsConfirmation(BudgetScope.THIS_MONTH, removing = false))
        assertFalse(c.needsConfirmation(BudgetScope.THIS_MONTH, removing = true))
        assertTrue(c.needsConfirmation(BudgetScope.ONWARD, removing = false))
        assertTrue(c.needsConfirmation(BudgetScope.ONWARD, removing = true))
    }

    @Test fun `a past month can only be corrected for itself`() {
        val c = context(listOf(row(2_000_00)), month = nov.minusMonths(2))
        assertTrue(c.isPastMonth)
        assertEquals(listOf(BudgetScope.THIS_MONTH), c.scopes)
        assertEquals(BudgetScope.THIS_MONTH, c.defaultScope)
    }

    @Test fun `a month with its own amount says so and offers the usual amount back`() {
        val c = context(listOf(row(2_000_00), row(2_500_00, month = nov)))
        assertTrue(c.hasOwnAmount)
        assertEquals(Money(2_000_00, "USD"), c.usual)
        assertTrue(c.status!!.contains("has its own amount"))
    }

    @Test fun `a month removed on its own says it was removed, not that it has its own amount`() {
        val c = context(listOf(row(2_000_00), row(0, month = nov)))
        assertTrue(c.removedThisMonth)
        assertTrue(c.status!!.startsWith("Removed for ") && c.status!!.contains("only. Other months use"))
        assertFalse(context(listOf(row(2_000_00), row(2_500_00, month = nov))).removedThisMonth)
    }

    @Test fun `later month-only amounts are counted and mentioned, other categories and currencies ignored`() {
        val c = context(listOf(row(2_000_00), row(1_000_00, month = nov.plusMonths(1)), row(1_000_00, month = nov.plusMonths(3)), row(9, month = nov.plusMonths(2), category = 8)))
        assertEquals(2, c.laterOwnAmounts)
        assertTrue(c.scopeExplanation(BudgetScope.ONWARD).contains("2 later months"))
    }

    @Test fun `a stopped recurring amount is not a usual amount`() {
        val c = context(listOf(row(2_000_00), row(0, from = nov)))
        assertNull(c.usual)
    }

    @Test fun `confirmation copy names the months that keep their amounts`() {
        val c = context(listOf(row(2_000_00)))
        val locale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals(
                "Groceries becomes \$2,500.00 a month from November on. October and earlier keep their amounts.",
                c.confirmBody(Money(2_500_00, "USD"), removing = false),
            )
            assertTrue(c.confirmBody(null, removing = true).endsWith("Your transactions are not affected."))
            assertEquals("Nov onward", c.scopeLabel(BudgetScope.ONWARD))
            assertEquals("Remove for November", c.removeLabel(BudgetScope.THIS_MONTH))
        } finally {
            Locale.setDefault(locale)
        }
    }
}
