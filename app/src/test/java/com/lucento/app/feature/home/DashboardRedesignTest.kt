package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Achievement
import com.lucento.app.domain.Achievements
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.PacePosition
import com.lucento.app.domain.ProgressFacts
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.feature.budget.verdictLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Redesign Parts D and E: milestones, the + spotlight, getting started, the spending breakdown and pace wording. */
class DashboardRedesignTest {

    private val nov = YearMonth.of(2026, 11)
    private var id = 1L
    private fun tx(type: TransactionType, minor: Long, category: Long?, day: Int, code: String = "USD", month: YearMonth = nov) =
        Transaction(id++, type, Money(minor, code), 1, category, month.atDay(day), "", 0)

    private val categories = mapOf(
        1L to Category(1, "Groceries", CategoryKind.EXPENSE, "groceries"),
        2L to Category(2, "Rent", CategoryKind.EXPENSE, "rent"),
        3L to Category(3, "Coffee", CategoryKind.EXPENSE, "coffee"),
        4L to Category(4, "Transport", CategoryKind.EXPENSE, "transport"),
        5L to Category(5, "Books", CategoryKind.EXPENSE, "books"),
    )

    @Test fun `milestones come from records, and none depend on streaks`() {
        val none = ProgressFacts(0, 0, 0, 0, 0, false)
        assertTrue(Achievements.earned(none).isEmpty())
        val some = ProgressFacts(assetWallets = 1, budgetedCategories = 2, recordedTransactions = 3, activeDays = 6, goals = 0, visitedInsights = true)
        assertEquals(
            setOf(Achievement.MONEY_KEEPER, Achievement.BUDGET_BUILDER, Achievement.FIRST_STEP, Achievement.FINANCIAL_EXPLORER),
            Achievements.earned(some),
        )
        // Seven different days, in any order and with gaps, is consistency; nothing is lost by missing a day.
        assertTrue(Achievement.GETTING_CONSISTENT in Achievements.earned(some.copy(activeDays = 7)))
        assertTrue(Achievement.GOAL_SETTER in Achievements.earned(some.copy(goals = 1)))
    }

    @Test fun `award progress agrees with what is earned, and only multi-step awards are counted`() {
        val facts = ProgressFacts(assetWallets = 3, budgetedCategories = 0, recordedTransactions = 12, activeDays = 4, goals = 0, visitedInsights = false)
        Achievement.entries.forEach { a ->
            assertEquals(a.name, a in Achievements.earned(facts), Achievements.progress(a, facts).earned)
        }
        val consistent = Achievements.progress(Achievement.GETTING_CONSISTENT, facts)
        assertEquals(com.lucento.app.domain.AwardProgress(4, Achievements.CONSISTENT_DAYS), consistent)
        assertTrue(consistent.isCounted)
        // Three wallets is still one step done of one: single-step awards never show a count or overflow.
        assertEquals(com.lucento.app.domain.AwardProgress(1, 1), Achievements.progress(Achievement.MONEY_KEEPER, facts))
        assertFalse(Achievements.progress(Achievement.MONEY_KEEPER, facts).isCounted)
        // Past the target the count stops at the target.
        assertEquals(1f, Achievements.progress(Achievement.GETTING_CONSISTENT, facts.copy(activeDays = 30)).fraction)
    }

    @Test fun `every award says how to earn it`() {
        Achievement.entries.forEach { assertTrue(it.name, it.howTo.isNotBlank() && it.howTo != it.description) }
    }

    @Test fun `an archived goal still counts as a goal set, but not in Home's savings`() {
        val reached = com.lucento.app.domain.Goal(7, "Trip", Money(50_000, "USD"), null, null, 0)
        val home = HomeStateBuilder.build(emptyList(), emptyList(), categories.values.toList(), "USD", nov.atDay(10), archivedGoals = listOf(reached))
        assertTrue(Achievement.GOAL_SETTER in home.earned)
        assertNull(home.goals)
        assertFalse(home.hasAnyGoals)
    }

    @Test fun `only expenses and income count as recorded, on distinct days`() {
        val all = listOf(
            tx(TransactionType.EXPENSE, 100, 1, 1), tx(TransactionType.EXPENSE, 100, 1, 1), tx(TransactionType.INCOME, 100, null, 2),
            tx(TransactionType.TRANSFER_OUT, 100, null, 3), tx(TransactionType.ADJUSTMENT_IN, 100, null, 4),
        )
        val facts = Achievements.facts(all, emptyList(), categories.values.toList(), emptyList(), emptyList(), nov.atDay(10), false)
        assertEquals(3, facts.recordedTransactions)
        assertEquals(2, facts.activeDays)
    }

    @Test fun `a first run is baselined quietly, then each new milestone is acknowledged once, in order`() {
        val earned = setOf(Achievement.MONEY_KEEPER, Achievement.FIRST_STEP)
        assertNull("never had milestones: no burst of congratulations", Achievements.toAcknowledge(earned, acknowledged = null))
        assertEquals(Achievement.MONEY_KEEPER, Achievements.toAcknowledge(earned, emptySet()))
        assertEquals(Achievement.FIRST_STEP, Achievements.toAcknowledge(earned, setOf(Achievement.MONEY_KEEPER)))
        assertNull(Achievements.toAcknowledge(earned, earned))
        // Awards that land together (a restored backup, a first sync) are presented together, in display order.
        assertEquals(listOf(Achievement.MONEY_KEEPER, Achievement.FIRST_STEP), Achievements.toAcknowledgeAll(earned, emptySet()))
        assertEquals(emptyList<Achievement>(), Achievements.toAcknowledgeAll(earned, acknowledged = null))
        assertEquals(emptyList<Achievement>(), Achievements.toAcknowledgeAll(earned, earned))
    }

    @Test fun `the plus spotlight shows once after setup and never to someone who already records`() {
        assertTrue(showAddSpotlight(seen = false, guidePending = false, recordedCount = 0))
        assertFalse(showAddSpotlight(seen = true, guidePending = false, recordedCount = 0))
        assertFalse("not while the guided setup is still running", showAddSpotlight(seen = false, guidePending = true, recordedCount = 0))
        assertFalse(showAddSpotlight(seen = false, guidePending = false, recordedCount = 1))
    }

    @Test fun `getting started lists only what is missing and skips the budget when Budgets is off`() {
        val base = HomeUiState(features = FeatureSet.ALL)
        assertEquals(listOf(Achievement.MONEY_KEEPER, Achievement.BUDGET_BUILDER, Achievement.FIRST_STEP), base.gettingStartedLeft)
        assertEquals(listOf(Achievement.FIRST_STEP), base.copy(earned = setOf(Achievement.MONEY_KEEPER, Achievement.BUDGET_BUILDER)).gettingStartedLeft)
        val noBudgets = HomeUiState(features = FeatureSet(Feature.entries.toSet() - Feature.BUDGETS), earned = setOf(Achievement.MONEY_KEEPER, Achievement.FIRST_STEP))
        assertTrue(noBudgets.gettingStartedLeft.isEmpty())
    }

    @Test fun `spending breakdown - largest four, the rest as Other, expenses in one currency and month only`() {
        val all = listOf(
            tx(TransactionType.EXPENSE, 500_00, 2, 1), tx(TransactionType.EXPENSE, 200_00, 1, 2), tx(TransactionType.EXPENSE, 100_00, 1, 3),
            tx(TransactionType.EXPENSE, 50_00, 3, 4), tx(TransactionType.EXPENSE, 40_00, 4, 5), tx(TransactionType.EXPENSE, 10_00, 5, 6),
            tx(TransactionType.EXPENSE, 5_00, null, 7),
            tx(TransactionType.INCOME, 9_000_00, null, 8), tx(TransactionType.TRANSFER_OUT, 999_00, null, 9),
            tx(TransactionType.EXPENSE, 77_00, 1, 9, code = "EUR"), tx(TransactionType.EXPENSE, 66_00, 1, 9, month = nov.minusMonths(1)),
        )
        val slices = spendingBreakdown(all, categories, "USD", nov)
        assertEquals(listOf("Rent", "Groceries", "Coffee", "Transport", "Other"), slices.map { it.label })
        assertEquals(listOf(500_00L, 300_00L, 50_00L, 40_00L, 15_00L), slices.map { it.amount.amountMinor })
        assertTrue(slices.last().isOther)
        assertEquals(1f, slices.sumOf { it.fraction.toDouble() }.toFloat(), 0.001f)
        assertTrue(spendingBreakdown(all, categories, "USD", nov.plusMonths(1)).isEmpty())
        assertEquals("No category", spendingBreakdown(listOf(tx(TransactionType.EXPENSE, 1_00, null, 1)), categories, "USD", nov).single().label)
    }

    @Test fun `pace verdicts are words, and going over always says so`() {
        val cats = listOf(categories.getValue(1L))
        val budget = listOf(com.lucento.app.domain.Budget(1, 1, Money(100_00, "USD")))
        val within = BudgetPlanner.plan(cats, budget, listOf(tx(TransactionType.EXPENSE, 10_00, 1, 1)), "USD", nov)
        val over = BudgetPlanner.plan(cats, budget, listOf(tx(TransactionType.EXPENSE, 150_00, 1, 1)), "USD", nov)
        assertEquals("Over budget", verdictLabel(over, PacePosition.BEHIND, isCurrentMonth = true))
        assertEquals("On track", verdictLabel(within, PacePosition.ALIGNED, isCurrentMonth = true))
        assertEquals("Ahead of pace", verdictLabel(within, PacePosition.AHEAD, isCurrentMonth = true))
        assertEquals("Under budget", verdictLabel(within, PacePosition.BEHIND, isCurrentMonth = true))
        assertEquals("Under budget", verdictLabel(within, PacePosition.AHEAD, isCurrentMonth = false))
    }

    @Test fun `home lists up to five recent entries`() {
        val all = (1..8).map { tx(TransactionType.EXPENSE, 1_00, 1, it) }
        assertEquals(5, recentActivity(all, HomeStateBuilder.RECENT_LIMIT).size)
        assertEquals(LocalDate.of(2026, 11, 8), recentActivity(all, HomeStateBuilder.RECENT_LIMIT).first().date)
    }
}
