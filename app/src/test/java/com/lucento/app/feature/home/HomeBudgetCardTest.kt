package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.CashflowCalculator
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.SafeToSpend
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Core screens refinement: the Monthly budget card's figures (safe to spend, "To watch") come from existing calculators. */
class HomeBudgetCardTest {

    private val today = LocalDate.parse("2025-03-20")
    private var nextId = 100L

    private val housing = Category(10, "Housing", CategoryKind.EXPENSE, "housing")
    private val groceries = Category(11, "Groceries", CategoryKind.EXPENSE, "groceries")
    private val transport = Category(12, "Transport", CategoryKind.EXPENSE, "transport")
    private val fun_ = Category(13, "Fun", CategoryKind.EXPENSE, "fun")
    private val travel = Category(14, "Travel", CategoryKind.EXPENSE, "travel")
    private val categories = listOf(housing, groceries, transport, fun_, travel)

    private val wallets = listOf(Wallet(1, "Bank", WalletType.BANK, Money(500_000, "USD"), 0L))

    private val budgets = listOf(
        Budget(1, 10, Money(50_000, "USD")),
        Budget(2, 11, Money(10_000, "USD")),
        Budget(3, 12, Money(3_000, "USD")),
        Budget(4, 13, Money(5_000, "USD")),
    )

    private fun tx(amount: Long, date: String, category: Long?) =
        Transaction(nextId++, TransactionType.EXPENSE, Money(amount, "USD"), 1L, category, LocalDate.parse(date), "", 0L)

    private val spending = listOf(
        tx(10_000, "2025-03-02", 10), // housing 20%
        tx(6_000, "2025-03-05", 11), // groceries 60%
        tx(2_700, "2025-03-09", 12), // transport 90%
        tx(1_500, "2025-03-12", 14), // travel: no allocation
        tx(9_000, "2025-02-10", 11), // last month
    )

    /** Internet, in a budgeted category, due before the month ends. */
    private val bill = RecurringSchedule(
        1, "Internet", TransactionType.EXPENSE, Money(2_000, "USD"), 1L, 11L, Frequency.MONTHLY, 1, LocalDate.parse("2025-03-25"), null, 0, false,
    )

    private fun build(month: YearMonth = YearMonth.from(today), features: FeatureSet = FeatureSet.ALL, budgets: List<Budget> = this.budgets) =
        HomeStateBuilder.build(spending, wallets, categories, "USD", today, budgets, listOf(bill), features, month = month)

    @Test fun safeToSpendIsTheDocumentedCalculationForThisMonth() {
        val state = build()
        val expected = CashflowCalculator.safeToSpend("USD", today, spending, wallets, budgets, listOf(bill), categories)
        assertEquals(expected, state.safeToSpend)
        val safe = state.safeToSpend!!
        assertEquals(SafeToSpend.Basis.BUDGET, safe.basis)
        // 68,000 budget − 20,200 spent (all expenses, travel included) − 2,000 internet due by month end.
        assertEquals(Money(68_000 - 20_200 - 2_000, "USD"), safe.amount)
        assertEquals(Money(2_000, "USD"), safe.committed)
        assertEquals(state.budget!!.remaining - safe.committed, safe.amount)
    }

    @Test fun pastMonthsShowNoSafeToSpend() {
        val state = build(month = YearMonth.of(2025, 2))
        assertEquals(YearMonth.of(2025, 2), state.month)
        assertNull(state.safeToSpend)
    }

    @Test fun noBudgetMeansNoSafeToSpendEvenWithBalances() {
        // Without allocations the calculator would estimate from balances; the budget card never shows that figure.
        assertNull(build(budgets = emptyList()).safeToSpend)
        assertNull(build(features = FeatureSet.ALL.with(Feature.BUDGETS, false)).safeToSpend)
    }

    @Test fun billsAreNotDeductedWhenRecurringIsOff() {
        val safe = build(features = FeatureSet.ALL.with(Feature.RECURRING, false)).safeToSpend!!
        assertTrue(safe.committed.isZero)
        assertEquals(Money(68_000 - 20_200, "USD"), safe.amount)
    }

    @Test fun watchListShowsBudgetedCategoriesWithSpendingClosestToTheLimit() {
        val watch = build().watchLines
        // Transport 90 %, groceries 60 %, housing 20 %; fun (nothing spent) and travel (no amount) are left out.
        assertEquals(listOf("Transport", "Groceries", "Housing"), watch.map { it.category.name })
        assertEquals(listOf(90, 60, 20), watch.map { it.percentUsed })
        assertTrue(watch.size <= HomeStateBuilder.WATCH_LIMIT)
    }

    @Test fun formulaAddsUpFromBudgetToSafeToSpend() {
        val state = build()
        val f = HomeStateBuilder.budgetFormula(state.budget!!, state.safeToSpend)
        assertEquals(Money(68_000, "USD"), f.budget)
        // Used is every expense: 18,700 in budgeted categories + 1,500 travel (no amount set) = 20,200.
        assertEquals(Money(18_700, "USD"), f.budgeted)
        assertEquals(Money(1_500, "USD"), f.unbudgeted)
        assertEquals(f.budgeted + f.unbudgeted, f.used)
        assertEquals(f.budget - f.used, f.remaining)
        assertEquals(Money(2_000, "USD"), f.billsDue)
        assertEquals(f.remaining - f.billsDue!!, f.safeToSpend)
        assertEquals(state.safeToSpend!!.amount, f.safeToSpend)
    }

    @Test fun pastMonthFormulaEndsAtRemaining() {
        val state = build(month = YearMonth.of(2025, 2))
        val f = HomeStateBuilder.budgetFormula(state.budget!!, state.safeToSpend)
        assertNull(f.billsDue)
        assertNull(f.safeToSpend)
        assertEquals(Money(9_000, "USD"), f.used)
        assertEquals(f.budget - f.used, f.remaining)
    }

    @Test fun formulaShowsOverspendingSigned() {
        val over = spending + tx(60_000, "2025-03-19", 10)
        val state = HomeStateBuilder.build(over, wallets, categories, "USD", today, budgets, listOf(bill), FeatureSet.ALL, month = YearMonth.from(today))
        val f = HomeStateBuilder.budgetFormula(state.budget!!, state.safeToSpend)
        // 68,000 − 80,200 = −12,200 remaining; the 2,000 bill takes safe to spend to −14,200 (shown, never clamped).
        assertEquals(Money(-12_200, "USD"), f.remaining)
        assertEquals(Money(-14_200, "USD"), f.safeToSpend)
    }

    @Test fun watchListIsEmptyWithoutBudgetedSpending() {
        assertTrue(HomeStateBuilder.watchList(null).isEmpty())
        assertTrue(build(budgets = emptyList()).watchLines.isEmpty())
    }
}
