package com.lucento.app.domain

import com.lucento.app.core.money.Money
import com.lucento.app.domain.fx.FxSource
import com.lucento.app.domain.fx.FxTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class BudgetMilestonesTest {
    private val today = LocalDate.parse("2026-10-03")
    private val food = Category(4, "Food", CategoryKind.EXPENSE, "groceries")
    private val fun_ = Category(5, "Fun", CategoryKind.EXPENSE, "entertainment")
    private val categories = listOf(food, fun_)
    private val created = LocalDate.parse("2026-08-15")
    // Total = 6,000 + 4,000 = 10,000.
    private val budgets = listOf(
        Budget(1, 4, Money(6_000, "PHP"), createdOn = created),
        Budget(2, 5, Money(4_000, "PHP"), createdOn = created),
    )
    private fun spend(minor: Long, date: String, category: Long? = 4) =
        Transaction(minor, TransactionType.EXPENSE, Money(minor, "PHP"), 1, category, LocalDate.parse(date), "", 0)

    // Main currency PHP: a peso wallet and a yen wallet valued at 0.38 (¥1,000 opening = ₱380).
    private val pesoCash = Wallet(1, "Cash", WalletType.CASH, Money(0, "PHP"), 0)
    private val yenCash = Wallet(2, "Yen", WalletType.CASH, Money(900, "JPY"), 0, openingBalance = Money(1_000, "JPY"), fxBasisRate = BigDecimal("0.38"))

    /** Yen spending with its value in pesos at the wallet's own rate, so it makes no FX result. */
    private fun yen(amount: Long, pesoMinor: Long, date: String) = Transaction(
        id = 900_000 + amount, type = TransactionType.EXPENSE, amount = Money(amount, "JPY"), walletId = 2, categoryId = 4,
        date = LocalDate.parse(date), note = "", createdAtEpochMillis = 0,
        baseAmount = Money(pesoMinor, "PHP"), fxSource = FxSource.MARKET,
    )

    // As BudgetScreen does: judged on the translated ledger its plan reads.
    private fun ack(budgets: List<Budget>, txs: List<Transaction>, currency: String = "PHP", last: YearMonth? = null) =
        BudgetMilestones.monthToAcknowledge(categories, budgets, FxTranslation.translate("PHP", listOf(pesoCash, yenCash), txs), currency, today, last)

    @Test fun keptLastMonthIsAcknowledged() =
        assertEquals(YearMonth.of(2026, 9), ack(budgets, listOf(spend(9_000, "2026-09-10")))?.month)

    @Test fun overspendingTheCategoryTotalIsNeverCelebrated() =
        assertNull(ack(budgets, listOf(spend(6_001, "2026-09-10"), spend(4_000, "2026-09-11", category = 5))))

    @Test fun exactlyAtTheTotalCounts() =
        assertEquals(YearMonth.of(2026, 9), ack(budgets, listOf(spend(10_000, "2026-09-10")))?.month)

    @Test fun shownOnlyOnce() = assertNull(ack(budgets, listOf(spend(1, "2026-09-10")), last = YearMonth.of(2026, 9)))

    @Test fun emptyMonthsAndAllocationsCreatedMidMonthDoNotCount() {
        assertNull(ack(budgets, emptyList()))
        val late = budgets.map { it.copy(createdOn = LocalDate.parse("2026-09-02")) }
        assertNull(ack(late, listOf(spend(1, "2026-09-10"))))
        assertNull(ack(budgets.map { it.copy(createdOn = null) }, listOf(spend(1, "2026-09-10"))))
    }

    @Test fun anAmountSetWhilePlanningTheMonthCounts() {
        // The budget began in August; Food raised from ₱60 to ₱80 for September only, on the 3rd (the check-in runs to the
        // 7th). ₱110 against ₱120 is within, as the Budget tab and the recap say (critic r2, P1-3).
        val planned = budgets + Budget(3, 4, Money(8_000, "PHP"), createdOn = LocalDate.parse("2026-09-03"), month = YearMonth.of(2026, 9))
        val txs = listOf(spend(7_000, "2026-09-10"), spend(4_000, "2026-09-11", category = 5))
        assertEquals(YearMonth.of(2026, 9), ack(planned, txs)?.month)
        assertNull(ack(budgets, txs))
    }

    @Test fun aBudgetThatBeganAfterTheFirstNeverCountsForThatMonth() {
        val late = budgets.map { it.copy(createdOn = LocalDate.parse("2026-09-10")) }
        assertNull(ack(late, listOf(spend(1, "2026-09-12"))))
        // A legacy "everything" row from August doesn't start the budget either.
        val legacy = Budget(9, null, Money(10_000, "PHP"), createdOn = created)
        assertNull(ack(late + legacy, listOf(spend(1, "2026-09-12"))))
    }

    @Test fun theFirstWholeMonthFollowsTheFirstAmount() {
        // Set on 15 August: September is the first month with a budget from its 1st.
        assertEquals(YearMonth.of(2026, 9), BudgetMilestones.firstWholeMonth(budgets, "PHP"))
        // Set on a 1st, that month counts.
        assertEquals(YearMonth.of(2026, 8), BudgetMilestones.firstWholeMonth(budgets.map { it.copy(createdOn = LocalDate.parse("2026-08-01")) }, "PHP"))
        // Another currency, no amounts, or an amount without a date: nothing to say.
        assertNull(BudgetMilestones.firstWholeMonth(budgets, "USD"))
        assertNull(BudgetMilestones.firstWholeMonth(emptyList(), "PHP"))
        assertNull(BudgetMilestones.firstWholeMonth(budgets + Budget(3, 4, Money(1_000, "PHP")), "PHP"))
    }

    @Test fun foreignWalletSpendingCountsTowardTheMonthAtItsOwnValue() {
        // Audit D2 (2026-11-16): ₱90 in pesos + ¥100 worth ₱38 = ₱128 against ₱100. Over, so never celebrated
        // (leaving the yen out read ₱90 and celebrated it).
        assertNull(ack(budgets, listOf(spend(9_000, "2026-09-10"), yen(100, 3_800, "2026-09-12"))))
        // ₱50 + ₱38 = ₱88: kept within the budget.
        assertEquals(YearMonth.of(2026, 9), ack(budgets, listOf(spend(5_000, "2026-09-10"), yen(100, 3_800, "2026-09-12")))?.month)
    }

    @Test fun onlyTheMatchingCurrencyAndNeverALegacyOverallRow() {
        assertNull(ack(budgets, listOf(spend(1, "2026-09-10")), currency = "USD"))
        assertNull(ack(listOf(Budget(9, null, Money(10_000, "PHP"), createdOn = created)), listOf(spend(1, "2026-09-10"))))
    }
}
