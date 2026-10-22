package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

class CashflowCalculatorTest {

    private val today = LocalDate.parse("2026-10-22") // 10 days left including today (22..31)
    private var nextId = 1L
    private fun tx(type: TransactionType, minor: Long, date: String, code: String = "PHP", category: Long? = 6) =
        Transaction(nextId++, type, Money(minor, code), 1, category, LocalDate.parse(date), "", 0L,
            transferGroup = if (type.isTransfer) "g" else null)

    private fun wallet(id: Long, type: WalletType, minor: Long, code: String = "PHP") =
        Wallet(id, "W$id", type, Money(minor, code), 0L)

    private val housing = Category(5, "Housing", CategoryKind.EXPENSE, "housing")
    private val food = Category(6, "Food", CategoryKind.EXPENSE, "groceries")
    private val categories = listOf(housing, food)

    private val rent = RecurringSchedule(1, "Rent", TransactionType.EXPENSE, Money(10_000_00, "PHP"), 1, 5,
        Frequency.MONTHLY, 1, LocalDate.parse("2026-10-28"), null, 0, false)

    private val txs = listOf(
        tx(TransactionType.EXPENSE, 5_000_00, "2026-10-03"),
        tx(TransactionType.EXPENSE, 2_000_00, "2026-10-20"),
        tx(TransactionType.INCOME, 40_000_00, "2026-10-15"),
        tx(TransactionType.TRANSFER_OUT, 9_999_00, "2026-10-16"),
        tx(TransactionType.EXPENSE, 3_100_00, "2026-09-12"),
        tx(TransactionType.INCOME, 30_000_00, "2026-09-15"),
    )

    @Test fun safeToSpendFromBudgetSubtractsSpendingAndCommittedBills() {
        // The budget is the sum of the category allocations: 20,000 housing + 10,000 food = 30,000.
        val result = CashflowCalculator.safeToSpend(
            "PHP", today, txs,
            wallets = listOf(wallet(1, WalletType.BANK, 50_000_00)),
            budgets = listOf(Budget(1, 5, Money(20_000_00, "PHP")), Budget(2, 6, Money(10_000_00, "PHP"))),
            schedules = listOf(rent),
            categories = categories,
        )!!
        assertEquals(SafeToSpend.Basis.BUDGET, result.basis)
        assertEquals(Money(7_000_00, "PHP"), result.spentThisMonth)
        assertEquals(Money(10_000_00, "PHP"), result.committed)
        assertEquals(Money(13_000_00, "PHP"), result.amount)
        assertEquals(10, result.daysLeft)
        assertEquals(Money(1_300_00, "PHP"), result.perDay)
        assertEquals(57, result.budgetUsedPercent)
        assertEquals(Money(30_000_00, "PHP"), result.budgetLimit)
    }

    @Test fun legacyOverallBudgetIsIgnoredAndBillsOutsideBudgetedCategoriesDoNotCount() {
        // Only food is allocated; rent (housing) is not budgeted, so its bill does not reduce the budget figure.
        val result = CashflowCalculator.safeToSpend(
            "PHP", today, txs, emptyList(),
            budgets = listOf(Budget(9, null, Money(99_000_00, "PHP")), Budget(2, 6, Money(10_000_00, "PHP"))),
            schedules = listOf(rent),
            categories = categories,
        )!!
        assertEquals(SafeToSpend.Basis.BUDGET, result.basis)
        assertEquals(Money(10_000_00, "PHP"), result.budgetLimit)
        assertEquals(Money.zero("PHP"), result.committed)
        assertEquals(Money(3_000_00, "PHP"), result.amount)
    }

    @Test fun withoutBudgetUsesOnlySpendableBalancesAndSaysSo() {
        val result = CashflowCalculator.safeToSpend(
            "PHP", today, txs,
            wallets = listOf(
                wallet(1, WalletType.BANK, 20_000_00),
                wallet(2, WalletType.CASH, 1_000_00),
                wallet(3, WalletType.SAVINGS, 99_000_00), // set aside, excluded
                wallet(4, WalletType.CREDIT_CARD, 5_000_00), // owed, excluded
                wallet(5, WalletType.BANK, 7_000_00, code = "USD"), // other currency, excluded
            ),
            budgets = emptyList(),
            schedules = listOf(rent),
        )!!
        assertEquals(SafeToSpend.Basis.BALANCES, result.basis)
        assertEquals(Money(21_000_00, "PHP"), result.spendableBalance)
        assertEquals(Money(11_000_00, "PHP"), result.amount)
        assertNull(result.budgetLimit)
    }

    @Test fun overspendingIsShownNotClamped() {
        val result = CashflowCalculator.safeToSpend(
            "PHP", today, txs, emptyList(), listOf(Budget(1, 6, Money(5_000_00, "PHP"))), emptyList(), categories,
        )!!
        assertEquals(Money(-2_000_00, "PHP"), result.amount)
        assertNull(result.perDay)
    }

    @Test fun noBudgetAndNoSpendableAccountGivesNoFigure() {
        assertNull(CashflowCalculator.safeToSpend("PHP", today, txs, listOf(wallet(1, WalletType.SAVINGS, 1)), emptyList(), emptyList()))
    }

    @Test fun burnRateIsActualAverageWithProjectionAndComparison() {
        val burn = CashflowCalculator.burnRate("PHP", today, txs)
        assertEquals(Money(7_000_00, "PHP"), burn.spentThisMonth)
        assertEquals(22, burn.daysElapsed)
        assertEquals(Money(318_18, "PHP"), burn.dailyAverage) // 700000/22 = 31818.18 -> 31818
        assertEquals(Money(9_863_64, "PHP"), burn.projectedMonthSpend) // 700000*31/22 = 986363.6 -> 986364
        assertEquals(Money(103_33, "PHP"), burn.previousDailyAverage) // 310000/30 = 10333.3
        assertEquals(208, burn.changePercent)
    }

    @Test fun burnRateWithoutHistory() {
        val burn = CashflowCalculator.burnRate("PHP", LocalDate.parse("2026-10-01"), emptyList())
        assertEquals(Money.zero("PHP"), burn.dailyAverage)
        assertNull(burn.previousDailyAverage)
        assertNull(burn.changePercent)
    }

    @Test fun netFlowExcludesTransfersAndComparesWithLastMonth() {
        val flow = CashflowCalculator.netFlow("PHP", YearMonth.of(2026, 10), txs)
        assertEquals(Money(40_000_00, "PHP"), flow.income)
        assertEquals(Money(7_000_00, "PHP"), flow.expense)
        assertEquals(Money(33_000_00, "PHP"), flow.net)
        assertEquals(Money(26_900_00, "PHP"), flow.previousNet)
        assertEquals(Money(6_100_00, "PHP"), flow.changeVsPrevious)
        assertNull(CashflowCalculator.netFlow("PHP", YearMonth.of(2026, 9), txs).previousNet)
    }

    @Test fun divisionRoundsToMinorUnits() {
        assertEquals(Money(3, "JPY"), CashflowCalculator.divide(Money(10, "JPY"), 3))
        assertEquals(Money(4, "USD"), CashflowCalculator.divide(Money(7, "USD"), 2))
        assertEquals(Money(3, "USD"), CashflowCalculator.divide(Money(7, "USD"), 2, RoundingMode.DOWN))
    }
}
