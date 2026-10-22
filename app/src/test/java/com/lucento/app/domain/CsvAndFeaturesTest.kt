package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvAndFeaturesTest {

    @Test fun csvIsExactEscapedAndSafe() {
        val txs = listOf(
            Transaction(2, TransactionType.EXPENSE, Money(1_250, "USD"), 1, 9, LocalDate.parse("2026-10-02"), "Lunch, \"team\"", 0),
            Transaction(1, TransactionType.INCOME, Money(100, "JPY"), 2, null, LocalDate.parse("2026-10-01"), "=SUM(A1)", 0),
            Transaction(3, TransactionType.TRANSFER_OUT, Money(5_000, "USD"), 1, null, LocalDate.parse("2026-10-03"), "", 0, transferGroup = "g1"),
        )
        val csv = CsvExporter.export(txs, mapOf(1L to "Bank", 2L to "Yen cash"), mapOf(9L to "Dining"))
        val lines = csv.split("\r\n")
        assertEquals("date,type,amount,currency,account,category,note,transfer_id", lines[0])
        assertEquals("2026-10-01,income,100,JPY,Yen cash,,'=SUM(A1),", lines[1])
        assertEquals("2026-10-02,expense,-12.50,USD,Bank,Dining,\"Lunch, \"\"team\"\"\",", lines[2])
        assertEquals("2026-10-03,transfer_out,-50.00,USD,Bank,,,g1", lines[3])
    }

    @Test fun featureDependencies() {
        val set = FeatureSet(setOf(Feature.DEBTS_LOANS, Feature.BUDGETS))
        assertTrue(set.isSwitchedOn(Feature.DEBTS_LOANS))
        assertFalse(set.isEnabled(Feature.DEBTS_LOANS)) // needs multiple wallets
        assertEquals(listOf(Feature.MULTIPLE_WALLETS), set.missingRequirements(Feature.DEBTS_LOANS))
        assertTrue(set.with(Feature.MULTIPLE_WALLETS, true).isEnabled(Feature.DEBTS_LOANS))
        assertTrue(set.isEnabled(Feature.BUDGETS))
        assertFalse(set.with(Feature.BUDGETS, false).isEnabled(Feature.BUDGETS))
    }

    @Test fun onboardingFocusMapsToFeatures() {
        val features = FinancialFocus.initialFeatures(setOf(FinancialFocus.DEBTS, FinancialFocus.BUDGETS))
        assertTrue(Feature.DEBTS_LOANS in features)
        assertTrue(Feature.MULTIPLE_WALLETS in features)
        assertTrue(Feature.BUDGETS in features)
        assertFalse(Feature.SAVINGS_GOALS in features)
        assertEquals(Feature.BUDGETS, Feature.fromKey("budgets"))
    }

    @Test fun transfersPairAndQueriesTreatThemTogether() {
        val out = Transaction(1, TransactionType.TRANSFER_OUT, Money(100, "USD"), 1, null, LocalDate.parse("2026-10-01"), "", 0, "g")
        val inn = Transaction(2, TransactionType.TRANSFER_IN, Money(100, "USD"), 2, null, LocalDate.parse("2026-10-01"), "", 0, "g")
        val exp = Transaction(3, TransactionType.EXPENSE, Money(40, "USD"), 1, null, LocalDate.parse("2026-10-01"), "", 0)
        val pair = Transfer.pair(listOf(out, inn, exp)).getValue("g")
        assertEquals(1L, pair.fromWalletId)
        assertEquals(2L, pair.toWalletId)
        val transfers = TransactionQueries.filter(listOf(out, inn, exp), TransactionFilter(type = TransactionType.TRANSFER_OUT))
        assertEquals(2, transfers.size)
        assertEquals(listOf(Money(-40, "USD")), Insights.netByCurrency(listOf(out, inn, exp), "USD"))
    }
}
