package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BalanceAdjustmentTest {
    private val usd = "USD"
    private fun usd(minor: Long) = Money(minor, usd)
    private val day = LocalDate.parse("2026-10-20")

    private fun tx(type: TransactionType, minor: Long, date: LocalDate = day, wallet: Long = 1L, id: Long = 1L) =
        Transaction(id, type, usd(minor), wallet, null, date, "", 0L)

    @Test fun assetWithMoreThanTrackedGetsAnInflow() {
        assertEquals(
            BalanceAdjustment.Plan(TransactionType.ADJUSTMENT_IN, usd(2_500)),
            BalanceAdjustment.plan(WalletType.CASH, tracked = usd(10_000), actual = usd(12_500)),
        )
    }

    @Test fun assetWithLessThanTrackedGetsAnOutflow() {
        assertEquals(
            BalanceAdjustment.Plan(TransactionType.ADJUSTMENT_OUT, usd(3_000)),
            BalanceAdjustment.plan(WalletType.BANK, tracked = usd(1_000), actual = usd(-2_000)),
        )
    }

    @Test fun owingMoreOnACardIsAnOutflowAndOwingLessAnInflow() {
        assertEquals(
            BalanceAdjustment.Plan(TransactionType.ADJUSTMENT_OUT, usd(4_000)),
            BalanceAdjustment.plan(WalletType.CREDIT_CARD, tracked = usd(10_000), actual = usd(14_000)),
        )
        assertEquals(
            BalanceAdjustment.Plan(TransactionType.ADJUSTMENT_IN, usd(1_000)),
            BalanceAdjustment.plan(WalletType.LOAN, tracked = usd(10_000), actual = usd(9_000)),
        )
    }

    @Test fun matchingBalancesNeedNoAdjustment() {
        assertNull(BalanceAdjustment.plan(WalletType.CASH, usd(500), usd(500)))
    }

    @Test fun plannedAdjustmentBringsTheBalanceToTheActualFigure() {
        for (type in listOf(WalletType.CASH, WalletType.CREDIT_CARD)) {
            for ((tracked, actual) in listOf(usd(10_000) to usd(7_250), usd(10_000) to usd(13_100))) {
                val plan = BalanceAdjustment.plan(type, tracked, actual)!!
                val after = WalletBalanceCalculator.balanceOn(tracked, type, 1L, listOf(tx(plan.type, plan.amount.amountMinor)), day)
                assertEquals("$type $tracked -> $actual", actual, after)
            }
        }
    }

    @Test fun balanceOnIgnoresLaterDaysOtherWalletsAndCurrencies() {
        val history = listOf(
            tx(TransactionType.INCOME, 1_000, day.minusDays(3), id = 1),
            tx(TransactionType.EXPENSE, 400, day, id = 2),
            tx(TransactionType.EXPENSE, 9_999, day.plusDays(1), id = 3),
            tx(TransactionType.INCOME, 5_000, day, wallet = 2L, id = 4),
            Transaction(5, TransactionType.INCOME, Money(700, "EUR"), 1L, null, day, "", 0L),
            tx(TransactionType.ADJUSTMENT_OUT, 100, day, id = 6),
        )
        assertEquals(usd(10_500), WalletBalanceCalculator.balanceOn(usd(10_000), WalletType.BANK, 1L, history, day))
    }

    @Test fun adjustmentsMoveTheBalanceButAreNeitherIncomeNorSpending() {
        assertTrue(TransactionType.ADJUSTMENT_IN.isInflow)
        assertFalse(TransactionType.ADJUSTMENT_OUT.isInflow)
        assertFalse(TransactionType.ADJUSTMENT_IN.isCashFlow)
        assertFalse(TransactionType.ADJUSTMENT_IN.isTransfer)
        val list = listOf(
            tx(TransactionType.EXPENSE, 1_000, id = 1),
            tx(TransactionType.ADJUSTMENT_OUT, 50_000, id = 2),
            tx(TransactionType.ADJUSTMENT_IN, 20_000, id = 3),
        )
        val flow = CashflowCalculator.netFlow(usd, YearMonth.from(day), list)
        assertEquals(usd(0), flow.income)
        assertEquals(usd(1_000), flow.expense)
        assertEquals(listOf(usd(-1_000)), Insights.netByCurrency(list, usd))
    }

    @Test fun flagsOnlyBalancesThatCantBeRight() {
        fun wallet(type: WalletType, minor: Long) = Wallet(1, "W", type, usd(minor), 0L)
        assertEquals(BalanceFlag.BELOW_ZERO, wallet(WalletType.CASH, -1).balanceFlag)
        assertEquals(BalanceFlag.BELOW_ZERO, wallet(WalletType.SAVINGS, -1).balanceFlag)
        assertEquals(BalanceFlag.OVERDRAWN, wallet(WalletType.BANK, -1).balanceFlag)
        assertEquals(BalanceFlag.OWED_TO_YOU, wallet(WalletType.CREDIT_CARD, -1).balanceFlag)
        assertNull(wallet(WalletType.CASH, 0).balanceFlag)
        assertNull(wallet(WalletType.LOAN, 50_000).balanceFlag)
    }
}
