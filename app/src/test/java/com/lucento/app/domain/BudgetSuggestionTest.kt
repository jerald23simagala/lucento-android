package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetSuggestionTest {
    private val today = LocalDate.parse("2026-10-28")

    private fun expense(date: String, categoryId: Long, name: String, minor: Long) =
        SpentDay(LocalDate.parse(date), categoryId, name, minor, "USD")

    private fun days(count: Int, categoryId: Long = 1L, name: String = "Groceries", minor: Long = 1_000): List<SpentDay> =
        (0 until count).map { offset ->
            expense(today.minusDays(offset.toLong()).toString(), categoryId, name, minor)
        }

    @Test
    fun `nothing is suggested before seven days of spending`() {
        assertNull(BudgetSuggestions.suggest(days(6), today, "USD", emptySet(), null))
        assertNull(BudgetSuggestions.suggest(emptyList(), today, "USD", emptySet(), null))
        val oneOld = listOf(expense("2026-10-22", 1, "Groceries", 500))
        assertNull(BudgetSuggestions.suggest(oneOld, today, "USD", emptySet(), null))
    }

    @Test
    fun `seven distinct days of spending suggest a monthly amount`() {
        val suggestion = BudgetSuggestions.suggest(days(7), today, "USD", emptySet(), null)
        requireNotNull(suggestion)
        assertEquals(7, suggestion.days)
        assertEquals("Groceries", suggestion.lines.single().categoryName)
        // 7 days × 1000 minor, scaled to October's 31 days.
        val expected = Money(
            java.math.BigDecimal.valueOf(7_000L * 31).divide(java.math.BigDecimal.valueOf(7), 0, java.math.RoundingMode.HALF_UP).longValueExact(),
            "USD",
        )
        assertEquals(expected, suggestion.lines.single().suggested)
    }

    @Test
    fun `by day 14 a single expense is enough and the window stays at 14 days`() {
        val sparse = listOf(expense("2026-10-15", 2, "Restaurants", 2_000))
        val suggestion = BudgetSuggestions.suggest(sparse, today, "USD", emptySet(), null)
        requireNotNull(suggestion)
        assertEquals(14, suggestion.days)
        assertEquals(1, suggestion.lines.size)
        assertEquals("Restaurants", suggestion.lines.single().categoryName)
    }

    @Test
    fun `spending older than 14 days is outside the window`() {
        val old = expense("2026-09-01", 1, "Groceries", 9_000)
        val recent = days(7)
        val suggestion = BudgetSuggestions.suggest(recent + old, today, "USD", emptySet(), null)
        requireNotNull(suggestion)
        // History longer than two weeks uses the last 14 days only, so the September amount is not included.
        assertEquals(14, suggestion.days)
        // 7 × 1000 minor in the last fortnight, scaled to October's 31 days. The September 9000 is outside that window.
        assertEquals(15_500L, suggestion.lines.single().suggested.amountMinor)
    }

    @Test
    fun `categories that already have a budget are left out`() {
        val mixed = days(7, 1, "Groceries", 1_000) + days(7, 2, "Restaurants", 500)
        val suggestion = BudgetSuggestions.suggest(mixed, today, "USD", budgetedCategoryIds = setOf(1L), null)
        requireNotNull(suggestion)
        assertEquals(listOf("Restaurants"), suggestion.lines.map { it.categoryName })
        assertNull(BudgetSuggestions.suggest(days(7), today, "USD", budgetedCategoryIds = setOf(1L), null))
    }

    @Test
    fun `uncategorised spending never becomes a made-up budget`() {
        val bare = (0 until 14).map {
            SpentDay(today.minusDays(it.toLong()), categoryId = null, categoryName = null, amountMinor = 100, currencyCode = "USD")
        }
        assertNull(BudgetSuggestions.suggest(bare, today, "USD", emptySet(), null))
    }

    @Test
    fun `not now hides the card for seven days`() {
        val spent = days(7)
        val until = BudgetSuggestions.snoozeUntil(today)
        assertEquals(today.plusDays(7), until)
        assertNull(BudgetSuggestions.suggest(spent, today, "USD", emptySet(), until))
        assertNull(BudgetSuggestions.suggest(spent, today.plusDays(6), "USD", emptySet(), until))
        assertTrue(BudgetSuggestions.suggest(spent, today.plusDays(7), "USD", emptySet(), until) != null)
    }

    @Test
    fun `another currency is not folded in`() {
        val usd = days(7)
        val php = SpentDay(today, 3, "Groceries", 500, "PHP")
        val suggestion = BudgetSuggestions.suggest(usd + php, today, "USD", emptySet(), null)
        requireNotNull(suggestion)
        assertEquals("USD", suggestion.lines.single().suggested.currencyCode)
    }
}
