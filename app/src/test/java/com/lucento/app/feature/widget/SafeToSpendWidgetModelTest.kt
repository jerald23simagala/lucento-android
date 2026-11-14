package com.lucento.app.feature.widget

import com.lucento.app.core.designsystem.component.HIDDEN_AMOUNT_DESCRIPTION
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Budget
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.feature.budget.paceVerdict
import com.lucento.app.feature.home.HomeStateBuilder
import com.lucento.app.feature.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * The Safe to spend widget shows Home's own figures and words (docs/WIDGETS.md). Same fixtures as HomeBudgetCardTest:
 * a $680 budget, $202 spent, a $20 internet bill due on the 25th, on 20 March (12 days left).
 */
class SafeToSpendWidgetModelTest {

    private val us = Locale.US
    private val today = LocalDate.parse("2025-03-20")
    private var nextId = 100L

    private val categories = listOf(
        Category(10, "Housing", CategoryKind.EXPENSE, "housing"),
        Category(11, "Groceries", CategoryKind.EXPENSE, "groceries"),
        Category(12, "Transport", CategoryKind.EXPENSE, "transport"),
        Category(13, "Fun", CategoryKind.EXPENSE, "fun"),
        Category(14, "Travel", CategoryKind.EXPENSE, "travel"),
    )
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
        tx(10_000, "2025-03-02", 10),
        tx(6_000, "2025-03-05", 11),
        tx(2_700, "2025-03-09", 12),
        tx(1_500, "2025-03-12", 14),
        tx(9_000, "2025-02-10", 11),
    )

    private fun bill(amount: Long) = RecurringSchedule(
        1, "Internet", TransactionType.EXPENSE, Money(amount, "USD"), 1L, 11L, Frequency.MONTHLY, 1, LocalDate.parse("2025-03-25"), null, 0, false,
    )

    private fun home(
        features: FeatureSet = FeatureSet.ALL,
        categories: List<Category> = this.categories,
        budgets: List<Budget> = this.budgets,
        spending: List<Transaction> = this.spending,
        billAmount: Long = 2_000,
    ): HomeUiState = HomeStateBuilder.build(spending, wallets, categories, "USD", today, budgets, listOf(bill(billAmount)), features)

    private fun model(state: HomeUiState = home(), hide: Boolean = false, lock: Boolean = false, onboarded: Boolean = true) =
        SafeToSpendWidgetModel.from(state, onboarded = onboarded, appLock = lock, hideAmounts = hide, locale = us)

    private fun usd(minor: Long) = MoneyFormatter.format(Money(minor, "USD"), us)

    @Test fun showsHomesSafeToSpendWithItsDayFigureAndBills() {
        val state = home()
        val widget = model(state) as SafeToSpendWidgetModel.Budget
        assertEquals("Safe to spend", widget.label)
        assertEquals("March", widget.monthLabel)
        // 68,000 budget − 20,200 spent − 2,000 internet: the figure Home's card shows.
        assertEquals(usd(45_800), widget.figure)
        assertEquals(MoneyFormatter.format(state.safeToSpend!!.amount, us), widget.figure)
        // 45,800 over 12 days, rounded down as Home's calculator does.
        assertEquals("${usd(3_816)} a day · 12 days left", widget.perDay)
        assertEquals("After ${usd(2_000)} bills due", widget.bills)
        assertNull(widget.reason)
        assertEquals(WidgetTone.POSITIVE, widget.figureTone)
        assertEquals(state.burn!!.consumedPercent, widget.usedPercent)
        assertEquals(paceVerdict(state.budget!!, state.burn!!.pace, state.isCurrentMonth).label, widget.verdict)
    }

    @Test fun hiddenAmountsMaskEveryAmountButKeepThePercentAndTheWords() {
        val widget = model(hide = true) as SafeToSpendWidgetModel.Budget
        val masked = MoneyFormatter.masked("USD", us)
        assertEquals(masked, widget.figure)
        assertEquals("$masked a day · 12 days left", widget.perDay)
        assertEquals("After $masked bills due", widget.bills)
        assertEquals(WidgetTone.NEUTRAL, widget.figureTone)
        assertEquals((model() as SafeToSpendWidgetModel.Budget).usedPercent, widget.usedPercent)
        assertFalse(widget.description.contains("458"))
        assertTrue(widget.description.contains(HIDDEN_AMOUNT_DESCRIPTION))
    }

    @Test fun appLockShowsNothingAboutTheMonth() {
        assertEquals(SafeToSpendWidgetModel.Locked(MoneyFormatter.masked("USD", us)), model(lock = true))
    }

    @Test fun beforeOnboardingThereIsNothingToShow() {
        assertEquals(SafeToSpendWidgetModel.NotSetUp, model(onboarded = false))
    }

    @Test fun billsTakingTheRestShowZeroWithHomesReason() {
        val widget = model(home(billAmount = 60_000)) as SafeToSpendWidgetModel.Budget
        assertEquals(usd(0), widget.figure)
        assertEquals(SafeToSpendWidgetModel.BILLS_REASON, widget.reason)
        assertEquals(WidgetTone.NEGATIVE, widget.figureTone)
        assertNull(widget.perDay)
        assertNull(widget.bills)
    }

    @Test fun overspendingShowsZeroWithTheOverBudgetReasonAndVerdict() {
        val widget = model(home(spending = spending + tx(60_000, "2025-03-15", 13))) as SafeToSpendWidgetModel.Budget
        assertEquals(usd(0), widget.figure)
        assertEquals(SafeToSpendWidgetModel.OVER_BUDGET_REASON, widget.reason)
        assertEquals("Over budget", widget.verdict)
        assertEquals(WidgetTone.NEGATIVE, widget.verdictTone)
    }

    @Test fun noAmountsYetIsHomesOwnPrompt() {
        assertEquals(SafeToSpendWidgetModel.NoBudget("March"), model(home(budgets = emptyList())))
    }

    @Test fun noExpenseCategoriesAsksForThem() {
        assertEquals(SafeToSpendWidgetModel.ChooseCategories("March"), model(home(categories = emptyList(), budgets = emptyList())))
    }

    @Test fun budgetsSwitchedOffShowsHomesNetFlow() {
        val state = home(features = FeatureSet(FeatureSet.ALL.enabled - Feature.BUDGETS))
        val widget = model(state) as SafeToSpendWidgetModel.NetFlow
        val flow = state.netFlow!!
        assertEquals(MoneyFormatter.formatSigned(flow.income - flow.expense, showPlus = true, locale = us), widget.figure)
        assertEquals("In ${MoneyFormatter.format(flow.income, us)} · Out ${MoneyFormatter.format(flow.expense, us)}", widget.detail)
        assertEquals(SafeToSpendWidgetModel.BUDGETS_OFF_NOTE, widget.note)
    }
}
