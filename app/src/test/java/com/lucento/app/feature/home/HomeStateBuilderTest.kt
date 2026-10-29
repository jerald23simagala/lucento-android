package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetHealth
import com.lucento.app.domain.BurnLevel
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.Goal
import com.lucento.app.domain.GoalContribution
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeStateBuilderTest {

    private val today = LocalDate.parse("2025-03-20")
    private var nextId = 1L

    private val housing = Category(10, "Housing", CategoryKind.EXPENSE, "housing")
    private val groceries = Category(11, "Groceries", CategoryKind.EXPENSE, "groceries")
    private val transport = Category(12, "Transport", CategoryKind.EXPENSE, "transport")
    private val categories = listOf(housing, groceries, transport)

    private fun wallet(currency: String, balance: Long = 0, type: WalletType = WalletType.BANK) =
        Wallet(nextId++, "W$nextId", type, Money(balance, currency), 0L)

    private fun tx(
        amount: Long,
        date: String,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "USD",
        category: Long? = null,
    ) = Transaction(nextId++, type, Money(amount, currency), 1L, category, LocalDate.parse(date), "", 0L)

    @Test fun emptyStateHasNoNetWorthOrBudget() {
        val state = HomeStateBuilder.build(emptyList(), emptyList(), emptyList(), "USD", today)
        assertFalse(state.loading)
        assertFalse(state.hasWallets)
        assertNull(state.netWorth)
        assertNull(state.monthTotals)
        assertEquals(0, state.expenseCategoryCount)
        assertEquals(BudgetHealth.NOT_SET, state.budget!!.health)
        assertEquals(BurnLevel.NO_BUDGET, state.burn!!.level)
        assertNull(state.burn!!.consumedPercent) // no misleading percentage without a budget
    }

    @Test fun focusesOnMainCurrencyWhenHeldAndFallsBackWithoutConverting() {
        assertEquals("USD", HomeStateBuilder.build(emptyList(), listOf(wallet("EUR"), wallet("USD")), emptyList(), "USD", today).focusCurrency)
        val jpy = HomeStateBuilder.build(emptyList(), listOf(wallet("JPY", 5_000)), emptyList(), "USD", today)
        assertEquals("JPY", jpy.focusCurrency)
        assertEquals(Money(5_000, "JPY"), jpy.netWorth!!.totals.single())
    }

    /** The hero card and the burn bar read the same plan: same total, same spent, same level. */
    @Test fun heroBudgetAndBurnRateAreConsistent() {
        val budgets = listOf(
            Budget(1, 10, Money(15_000, "USD")),
            Budget(2, 11, Money(8_000, "USD")),
            Budget(3, 12, Money(3_000, "USD")),
        )
        val all = listOf(
            tx(14_000, "2025-03-02", category = 10),
            tx(7_500, "2025-03-15", category = 11),
            tx(1_000, "2025-03-18", category = null), // uncategorised: outside the budget, still counted as spent
            tx(9_000, "2025-02-27", category = 11), // last month
        )
        val state = HomeStateBuilder.build(all, listOf(wallet("USD")), categories, "USD", today, budgets)
        val plan = state.budget!!
        val burn = state.burn!!
        assertEquals(Money(26_000, "USD"), plan.total)
        assertEquals(Money(22_500, "USD"), plan.spent) // actual money out, budgeted or not
        assertEquals(Money(21_500, "USD"), plan.budgetedSpending)
        assertEquals(Money(3_500, "USD"), plan.remaining)
        assertEquals(Money(1_000, "USD"), plan.unbudgetedSpending)
        assertEquals(plan.spent, burn.spent)
        assertEquals(plan.total, burn.total)
        assertEquals(plan.percentUsed, burn.consumedPercent)
        assertEquals(87, burn.consumedPercent) // 22,500 / 26,000 = 86.5%, half-up
        assertEquals(BudgetHealth.APPROACHING, plan.health)
        assertEquals(BurnLevel.APPROACHING, burn.level)
        assertEquals(64, burn.elapsedPercent) // day 20 of 31
    }

    @Test fun netFlowDirectionIncludingZeroAsNeutral() {
        val wallets = listOf(wallet("USD"))
        val up = HomeStateBuilder.build(listOf(tx(5_000, "2025-03-01", TransactionType.INCOME), tx(1_000, "2025-03-02")), wallets, emptyList(), "USD", today)
        assertEquals(FlowDirection.POSITIVE, up.flowDirection)
        val down = HomeStateBuilder.build(listOf(tx(500, "2025-03-01", TransactionType.INCOME), tx(1_000, "2025-03-02")), wallets, emptyList(), "USD", today)
        assertEquals(FlowDirection.NEGATIVE, down.flowDirection)
        val flat = HomeStateBuilder.build(listOf(tx(1_000, "2025-03-01", TransactionType.INCOME), tx(1_000, "2025-03-02")), wallets, emptyList(), "USD", today)
        assertEquals(FlowDirection.NEUTRAL, flat.flowDirection)
        val nothing = HomeStateBuilder.build(emptyList(), wallets, emptyList(), "USD", today)
        assertEquals(FlowDirection.NEUTRAL, nothing.flowDirection)
    }

    @Test fun transfersNeverCountAsIncomeOrSpending() {
        val out = Transaction(100, TransactionType.TRANSFER_OUT, Money(5_000, "USD"), 1L, null, LocalDate.parse("2025-03-19"), "", 0L, "g1")
        val inn = Transaction(101, TransactionType.TRANSFER_IN, Money(5_000, "USD"), 2L, null, LocalDate.parse("2025-03-19"), "", 0L, "g1")
        val spend = tx(1_000, "2025-03-18", category = 11)
        val state = HomeStateBuilder.build(
            listOf(inn, out, spend), listOf(wallet("USD", 10_000), wallet("USD")), categories, "USD", today,
            budgets = listOf(Budget(1, 11, Money(2_000, "USD"))),
        )
        assertEquals(Money(1_000, "USD"), state.netFlow!!.expense)
        assertTrue(state.netFlow!!.income.isZero)
        assertEquals(Money(1_000, "USD"), state.budget!!.spent)
    }

    @Test fun upcomingRecurringReplacesRecentTransactionsInDateOrder() {
        fun schedule(id: Long, name: String, start: String) = RecurringSchedule(
            id, name, TransactionType.EXPENSE, Money(500, "USD"), 1L, null, Frequency.MONTHLY, 1, LocalDate.parse(start), null, 0, false,
        )
        val state = HomeStateBuilder.build(
            emptyList(), listOf(wallet("USD")), emptyList(), "USD", today,
            schedules = listOf(schedule(1, "Rent", "2025-04-01"), schedule(2, "Phone", "2025-03-18"), schedule(3, "Gym", "2025-03-25")),
        )
        assertEquals(listOf("Phone", "Gym", "Rent"), state.upcoming.map { it.schedule.name })
        assertTrue(state.upcoming.first().overdue)
        assertFalse(state.upcoming[1].overdue)
        assertTrue(state.hasAnySchedules)
    }

    @Test fun savingsLevelUsesGoalsInFocusCurrency() {
        val goal = Goal(1, "Trip", Money(10_000, "USD"), null, null, 0L)
        val contribution = GoalContribution(1, 1, Money(2_500, "USD"), LocalDate.parse("2025-03-01"), "")
        val state = HomeStateBuilder.build(emptyList(), listOf(wallet("USD", 2_500)), emptyList(), "USD", today,
            goals = listOf(goal), contributions = listOf(contribution))
        assertEquals(25, state.goals!!.percent)
        // Earmarking money for a goal never changes net worth.
        assertEquals(Money(2_500, "USD"), state.netWorth!!.totals.single())
    }

    @Test fun disabledModulesKeepDataButLeaveTheDashboard() {
        val budget = Budget(1, 11, Money(2_000, "USD"))
        val rent = RecurringSchedule(
            1, "Rent", TransactionType.EXPENSE, Money(500, "USD"), 1L, null,
            Frequency.MONTHLY, 1, LocalDate.parse("2025-03-25"), null, 0, false,
        )
        val goal = Goal(1, "Trip", Money(10_000, "USD"), null, null, 0L)
        val all = listOf(tx(1_900, "2025-03-10", category = 11))
        val wallets = listOf(wallet("USD", 50_000))
        val on = HomeStateBuilder.build(all, wallets, categories, "USD", today, listOf(budget), listOf(rent), goals = listOf(goal))
        assertEquals(BudgetHealth.LOW, on.budget!!.health) // 100 of 2,000 left = 5%
        assertTrue(on.budget!!.health.isConcerning)
        assertEquals(1, on.upcoming.size)
        assertTrue(on.goals != null)

        val off = FeatureSet(setOf(Feature.INSIGHTS))
        val hidden = HomeStateBuilder.build(all, wallets, categories, "USD", today, listOf(budget), listOf(rent), off, goals = listOf(goal))
        assertNull(hidden.budget)
        assertNull(hidden.burn)
        assertTrue(hidden.upcoming.isEmpty())
        assertNull(hidden.goals)
    }
}
