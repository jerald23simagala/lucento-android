package com.lucento.app.domain

import com.lucento.app.core.money.Money
import com.lucento.app.feature.home.HomeStateBuilder
import com.lucento.app.feature.home.spendingBreakdown
import com.lucento.app.feature.transactions.TransactionFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Financial integrity across screens (owner, 2026-11-04): a month's spending is one figure. Home's budget card
 * ("Spent"), the Budget page's Spent, the Transactions page's Expenses as it opens (this month), Insights' month, the
 * net flow and "Where it went" must all agree, and their parts must add up — on a ledger with everything that could
 * pull them apart: transfers, a balance adjustment, a debt's interest, a card purchase, another currency, last month,
 * an archived category, an uncategorised expense and a month-only amount overriding a recurring one.
 */
class MonthSpendingReconciliationTest {

    private val today = LocalDate.of(2026, 11, 4)
    private val month = YearMonth.from(today)
    private fun php(major: Long) = Money(major * 100, "PHP")
    private fun usd(major: Long) = Money(major * 100, "USD")

    private val bank = Wallet(1, "BPI - Personal", WalletType.BANK, php(0), 0)
    private val card = Wallet(2, "Card", WalletType.CREDIT_CARD, php(0), 0)
    private val loan = Wallet(3, "Loan", WalletType.LOAN, php(0), 0)
    private val dollars = Wallet(4, "Dollars", WalletType.BANK, usd(0), 0)
    private val wallets = listOf(bank, card, loan, dollars)

    private val restaurants = Category(10, "Restaurants", CategoryKind.EXPENSE, "restaurants")
    private val water = Category(11, "Water", CategoryKind.EXPENSE, "water")
    private val rent = Category(12, "Rent", CategoryKind.EXPENSE, "rent")
    private val electricity = Category(13, "Electricity", CategoryKind.EXPENSE, "electricity")
    private val old = Category(14, "Old hobby", CategoryKind.EXPENSE, "category", archived = true)
    private val salary = Category(20, "Salary", CategoryKind.INCOME, "salary")
    private val categories = listOf(restaurants, water, rent, electricity, old, salary)

    private val budgets = listOf(
        Budget(1, rent.id, php(6_000), effectiveFrom = month),
        Budget(2, rent.id, php(5_000), month = month), // this month only: wins
        Budget(3, electricity.id, php(235), effectiveFrom = month),
        Budget(4, null, php(99_999)), // a pre-0.5 "everything" limit: counts nowhere
    )

    private var nextId = 1L
    private fun tx(type: TransactionType, amount: Money, wallet: Wallet, category: Category?, date: LocalDate, group: String? = null) =
        Transaction(nextId++, type, amount, wallet.id, category?.id, date, "", 0, transferGroup = group)

    private val ledger = listOf(
        // The owner's November.
        tx(TransactionType.EXPENSE, php(250), bank, restaurants, today.withDayOfMonth(3)),
        tx(TransactionType.EXPENSE, php(800), bank, water, today.withDayOfMonth(3)),
        tx(TransactionType.INCOME, php(35_000), bank, salary, today.withDayOfMonth(3)),
        tx(TransactionType.EXPENSE, php(450), bank, restaurants, today.withDayOfMonth(3)),
        tx(TransactionType.EXPENSE, php(4_600), bank, rent, today),
        tx(TransactionType.EXPENSE, php(467), bank, electricity, today),
        // What else can happen in a month.
        tx(TransactionType.EXPENSE, php(1_200), card, restaurants, today.withDayOfMonth(2)), // card purchase: spending
        tx(TransactionType.EXPENSE, php(150), loan, null, today.withDayOfMonth(1)), // a debt's interest, no category
        tx(TransactionType.EXPENSE, php(90), bank, old, today.withDayOfMonth(2)), // an archived category
        tx(TransactionType.EXPENSE, php(300), bank, water, today.withDayOfMonth(28)), // later this month (scheduled ahead)
        tx(TransactionType.TRANSFER_OUT, php(2_000), bank, null, today, group = "t1"),
        tx(TransactionType.TRANSFER_IN, php(2_000), card, null, today, group = "t1"),
        tx(TransactionType.ADJUSTMENT_OUT, php(75), bank, null, today),
        tx(TransactionType.EXPENSE, usd(40), dollars, restaurants, today), // another currency: never added in
        tx(TransactionType.EXPENSE, php(1_000), bank, restaurants, today.minusMonths(1)), // last month
    )

    /** Every PHP expense dated this month, by hand. */
    private val expected = php(250 + 800 + 450 + 4_600 + 467 + 1_200 + 150 + 90 + 300)

    @Test fun `the month's spending is one figure on every screen`() {
        val focus = FocusCurrency.of("PHP", wallets, ledger)
        assertEquals("PHP", focus)

        val home = HomeStateBuilder.build(ledger, wallets, categories, "PHP", today, budgets = budgets)
        val homePlan = home.budget!!
        val budgetPage = BudgetPlanner.plan(categories, budgets, ledger, focus, month)
        val opening = TransactionFilterState()
        val transactionsPage = TransactionQueries.totalsByCurrency(
            TransactionQueries.filter(ledger.filter { !it.isTransfer || it.type == TransactionType.TRANSFER_OUT }, TransactionFilter(range = opening.preset.resolve(today))),
            focus,
        ).first()
        val insights = Insights.incomeExpenseTrend(ledger, focus, month, 6).last()
        val netFlow = CashflowCalculator.netFlow(focus, month, ledger)
        val whereItWent = Money.sum(focus, spendingBreakdown(ledger, categories.associateBy { it.id }, focus, month).map { it.amount })

        assertEquals("Home Spent", expected, homePlan.spent)
        assertEquals("Budget Spent", expected, budgetPage.spent)
        assertEquals("Transactions Expenses (this month)", expected, transactionsPage.expense)
        assertEquals("Home month totals", expected, home.monthTotals!!.expense)
        assertEquals("Insights", expected, insights.expense)
        assertEquals("Net flow", expected, netFlow.expense)
        assertEquals("Where it went", expected, whereItWent)
        assertEquals("Transactions Income", php(35_000), transactionsPage.income)
    }

    @Test fun `the parts add up and the words are true`() {
        val plan = BudgetPlanner.plan(categories, budgets, ledger, "PHP", month)
        // Budget = the category amounts; the month-only Rent amount wins and the legacy limit counts nowhere.
        assertEquals(php(5_000 + 235), plan.total)
        // Spent = budgeted + unbudgeted; the budgeted rows sum to the budgeted part.
        assertEquals(plan.spent, plan.budgetedSpending + plan.unbudgetedSpending)
        assertEquals(plan.budgetedSpending, Money.sum("PHP", plan.allocatedLines.map { it.spent }))
        assertEquals(php(4_600 + 467), plan.budgetedSpending)
        // Unbudgeted: restaurants (incl. the card), water (incl. later this month), the loan's interest, the archived category.
        assertEquals(php(250 + 450 + 1_200 + 800 + 300 + 150 + 90), plan.unbudgetedSpending)
        assertEquals(php(150), plan.uncategorizedSpending)
        // Remaining is negative: "Overspent" on Home, "Over your budget" on the Budget page, never "Remaining".
        assertEquals(plan.total - plan.spent, plan.remaining)
        assert(plan.remaining.isNegative)
        assertEquals(BudgetHealth.EXCEEDED, plan.health)
        val formula = HomeStateBuilder.budgetFormula(plan, null)
        assert(formula.overspent)
        assertEquals(plan.spent, formula.used)
    }

    @Test fun `Home and Budget pick the same currency, even when the main one holds nothing`() {
        // Main currency USD but every wallet and entry is in PHP: both show PHP (the Budget page used to show USD).
        val phpOnly = listOf(bank)
        val entries = ledger.filter { it.currencyCode == "PHP" }
        assertEquals("PHP", FocusCurrency.of("USD", phpOnly, entries))
        val home = HomeStateBuilder.build(entries, phpOnly, categories, "USD", today, budgets = budgets)
        assertEquals("PHP", home.focusCurrency)
        assertEquals(expected, home.budget!!.spent)
    }

    @Test fun `the Transactions page opens on this month, so last month never leaks into its totals`() {
        val opening = TransactionFilterState()
        assertEquals(RangePreset.THIS_MONTH, opening.preset)
        assertFalse(opening.isActive)
        val anyDate = TransactionQueries.totalsByCurrency(ledger, "PHP").first()
        assertEquals("all-time includes last month", expected + php(1_000), anyDate.expense)
    }
}
