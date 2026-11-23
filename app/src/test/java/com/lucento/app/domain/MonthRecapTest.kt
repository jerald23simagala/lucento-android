package com.lucento.app.domain

import com.lucento.app.core.money.Money
import com.lucento.app.domain.fx.FxTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** The Progress page's month and year recaps (docs/CALCULATIONS.md "Progress recap"). */
class MonthRecapTest {
    private val today = LocalDate.parse("2026-11-15")
    private fun php(minor: Long) = Money(minor, "PHP")

    private val salary = Category(1, "Salary", CategoryKind.INCOME, "salary")
    private val food = Category(4, "Food", CategoryKind.EXPENSE, "groceries")
    private val fun_ = Category(5, "Fun", CategoryKind.EXPENSE, "entertainment")
    private val categories = listOf(salary, food, fun_)

    // Food ₱100 + Fun ₱40 a month, set up in August.
    private val budgets = listOf(
        Budget(1, 4, php(10_000), createdOn = LocalDate.parse("2026-08-15")),
        Budget(2, 5, php(4_000), createdOn = LocalDate.parse("2026-08-15")),
    )

    // A bank (₱1,000 to start) and a card owing ₱100; today's balances follow the records below.
    private val bank = Wallet(1, "Bank", WalletType.BANK, php(118_000), 0, openingBalance = php(100_000))
    private val card = Wallet(2, "Card", WalletType.CREDIT_CARD, php(5_000), 0, openingBalance = php(10_000))

    private var id = 1L
    private fun tx(type: TransactionType, minor: Long, wallet: Long, category: Long?, date: String, group: String? = null) =
        Transaction(id++, type, php(minor), wallet, category, LocalDate.parse(date), "", 0L, transferGroup = group)

    private val transactions = listOf(
        // September: ₱80 food, ₱30 fun.
        tx(TransactionType.EXPENSE, 8_000, 1, 4, "2026-09-05"),
        tx(TransactionType.EXPENSE, 3_000, 1, 5, "2026-09-12"),
        // October: ₱500 in; ₱120 food (₱20 over), ₱30 fun; ₱50 paid to the card.
        tx(TransactionType.INCOME, 50_000, 1, 1, "2026-10-01"),
        tx(TransactionType.EXPENSE, 6_000, 1, 4, "2026-10-05"),
        tx(TransactionType.EXPENSE, 3_000, 1, 5, "2026-10-12"),
        tx(TransactionType.EXPENSE, 6_000, 1, 4, "2026-10-20"),
        tx(TransactionType.TRANSFER_OUT, 5_000, 1, null, "2026-10-25", group = "pay"),
        tx(TransactionType.TRANSFER_IN, 5_000, 2, null, "2026-10-25", group = "pay"),
        // November so far: ₱10 food.
        tx(TransactionType.EXPENSE, 1_000, 1, 4, "2026-11-03"),
    )

    private val trip = Goal(1, "Trip", php(20_000), targetDate = null, linkedWalletId = null, createdAtEpochMillis = 0)
    private val contributions = listOf(
        GoalContribution(1, 1, php(15_000), LocalDate.parse("2026-09-20"), ""),
        GoalContribution(2, 1, php(6_000), LocalDate.parse("2026-10-10"), ""),
    )

    private fun inputs(features: FeatureSet = FeatureSet.ALL, txs: List<Transaction> = transactions): RecapInputs {
        val ledger = FxTranslation.translate("PHP", listOf(bank, card), txs)
        return RecapInputs(
            wallets = ledger.wallets,
            transactions = ledger.transactions,
            withFxResults = ledger.withFxResults,
            categories = categories,
            budgets = budgets,
            goals = listOf(trip),
            contributions = contributions,
            features = features,
            today = today,
            zone = ZoneOffset.UTC,
        )
    }

    private val october = YearMonth.of(2026, 10)

    @Test fun `a finished month's money in, money out and what was kept`() {
        val recap = MonthRecaps.month(inputs(), "PHP", october)
        assertTrue(recap.finished)
        assertEquals(php(50_000), recap.income)
        assertEquals(php(15_000), recap.spent)
        assertEquals(php(35_000), recap.net)
        // Four days with income or expenses; the card payment is a transfer, not a record of the month's story.
        assertEquals(4, recap.daysRecorded)
        assertEquals(31, recap.days)
    }

    @Test fun `the budget part is the Budget tab's own plan`() {
        val budget = MonthRecaps.month(inputs(), "PHP", october).budget!!
        assertEquals(php(14_000), budget.total)
        assertEquals(php(15_000), budget.spent)
        assertEquals(php(-1_000), budget.remaining)
        assertFalse(budget.kept)
        assertEquals(2, budget.budgeted)
        assertEquals(1, budget.within)
        assertEquals(listOf(CategoryAmount(4, php(2_000))), budget.over)
        // The same figures as BudgetPlanner for that month.
        val plan = BudgetPlanner.plan(categories, budgets, inputs().transactions, "PHP", october)
        assertEquals(plan.total, budget.total)
        assertEquals(plan.spent, budget.spent)
    }

    @Test fun `where it went, and what moved most since the month before`() {
        val recap = MonthRecaps.month(inputs(), "PHP", october)
        assertEquals(listOf(CategoryAmount(4, php(12_000), 80), CategoryAmount(5, php(3_000), 20)), recap.top)
        // Food: ₱120 against ₱80 in September. Fun didn't move.
        assertEquals(CategoryChange(4, php(12_000), php(8_000)), recap.change)
        assertEquals(php(4_000), recap.change!!.change)
    }

    @Test fun `net worth at the month's end and since the month before`() {
        val recap = MonthRecaps.month(inputs(), "PHP", october)
        // End of September: ₱1,000 − ₱110 in the bank, ₱100 owed: ₱790. End of October: ₱1,190 − ₱50 owed: ₱1,140.
        assertEquals(php(114_000), recap.netWorth)
        assertEquals(php(35_000), recap.netWorthChange)
    }

    @Test fun `goals and debts, each only with something to say`() {
        val recap = MonthRecaps.month(inputs(), "PHP", october)
        assertEquals(php(6_000), recap.setAside)
        // ₱150 before October, ₱210 after it: the ₱200 target was reached in October, not in September.
        assertEquals(1, recap.goalsReached)
        assertEquals(0, MonthRecaps.month(inputs(), "PHP", YearMonth.of(2026, 9)).goalsReached)
        assertEquals(php(5_000), recap.paidToDebts)
    }

    @Test fun `switched-off modules leave their parts out`() {
        val off = FeatureSet(Feature.entries.toSet() - setOf(Feature.BUDGETS, Feature.SAVINGS_GOALS, Feature.DEBTS_LOANS))
        val recap = MonthRecaps.month(inputs(features = off), "PHP", october)
        assertNull(recap.budget)
        assertNull(recap.setAside)
        assertEquals(0, recap.goalsReached)
        assertNull(recap.paidToDebts)
        // The money itself is still the month's.
        assertEquals(php(35_000), recap.net)
    }

    @Test fun `this month is so far, and a month with nothing before it has nothing to compare`() {
        val november = MonthRecaps.month(inputs(), "PHP", YearMonth.of(2026, 11))
        assertFalse(november.finished)
        assertEquals(15, november.days)
        assertEquals(php(-1_000), november.net)
        // Compared with October's first 15 days only (₱60 food then), never with all of October (₱120).
        assertEquals(CategoryChange(4, php(1_000), php(6_000)), november.change)
        assertEquals(LocalDate.parse("2026-10-15"), november.changeThrough)
        // Two finished months are compared whole.
        assertNull(MonthRecaps.month(inputs(), "PHP", october).changeThrough)
        val september = MonthRecaps.month(inputs(), "PHP", YearMonth.of(2026, 9))
        assertNull("August had nothing recorded", september.change)
        val empty = MonthRecaps.month(inputs(), "PHP", YearMonth.of(2026, 7))
        assertFalse(empty.hasRecords)
    }

    @Test fun `the biggest change is the largest either way, stable on ties`() {
        val now = listOf(CategoryShare(4, php(5_000)), CategoryShare(5, php(9_000)))
        val before = listOf(CategoryShare(4, php(8_000)), CategoryShare(5, php(6_000)), CategoryShare(6, php(1_000)))
        // Food −₱30 and Fun +₱30 tie on size: the larger month's category wins.
        assertEquals(CategoryChange(5, php(9_000), php(6_000)), MonthRecaps.biggestChange(now, before, "PHP"))
        assertNull(MonthRecaps.biggestChange(now, now, "PHP"))
    }

    @Test fun `the year is twelve months to this one, with its totals`() {
        val year = MonthRecaps.year(inputs(), "PHP", YearMonth.of(2026, 11))
        assertEquals(12, year.months.size)
        assertEquals(YearMonth.of(2025, 12), year.months.first().month)
        assertEquals(YearMonth.of(2026, 11), year.months.last().month)
        assertEquals(php(50_000), year.income)
        assertEquals(php(27_000), year.spent)
        assertEquals(php(23_000), year.net)
        // The end of November 2025 (₱900) to today (₱1,180 − ₱50 owed): +₱230, the year's net flow; transfers move nothing.
        assertEquals(php(23_000), year.netWorthChange)
        assertEquals(3, year.recordedMonths)
        assertEquals(1, year.keptMonths)
        // September within its budget, October over it; November is still running.
        val byMonth = year.months.associateBy { it.month }
        assertEquals(true, byMonth.getValue(YearMonth.of(2026, 9)).budgetKept)
        assertEquals(false, byMonth.getValue(YearMonth.of(2026, 10)).budgetKept)
        assertNull(byMonth.getValue(YearMonth.of(2026, 11)).budgetKept)
        assertNull("no records, no verdict", byMonth.getValue(YearMonth.of(2026, 7)).budgetKept)
        assertEquals(2, year.budgetedMonths)
        assertEquals(1, year.withinBudgetMonths)
    }

    @Test fun `the year never runs past this month`() {
        val year = MonthRecaps.year(inputs(), "PHP", YearMonth.of(2027, 3))
        assertEquals(YearMonth.of(2026, 11), year.endMonth)
        assertEquals(YearMonth.of(2026, 11), year.months.last().month)
    }
}
