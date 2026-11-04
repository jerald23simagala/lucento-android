package com.lucento.app.domain.ask

import com.lucento.app.domain.ask.AskFixtures.usd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Ask Lucento's preset reports (docs/CALCULATIONS.md "Ask Lucento reports"): exact, per currency, never converted. */
class ReportBuilderTest {

    private val inputs = AskFixtures.inputs()
    private val november = YearMonth.of(2026, 11)

    private fun Report.section(title: String) = sections.first { it.title == title }

    @Test fun netWorthListsEachWalletAndTheChangeSinceLastMonthEnd() {
        val report = ReportBuilder.netWorth(inputs, "USD", november)
        // 5,000 + 200 − 450 − 9,000.
        assertEquals(usd(-425_000), report.headline.values.single())
        assertEquals(listOf("Everyday bank", "Cash"), report.section("Assets").lines.map { it.label })
        assertEquals(usd(520_000), report.section("Assets").total!!.values.single())
        assertEquals(usd(945_000), report.section("Liabilities").total!!.values.single())
        // October 31: bank 4,000 + 3,000 − 250 = 6,750; cash 150; card 450 and loan 10,000 owed → −3,550. Change −700.
        val change = report.section("Net worth").lines.first { it.label.startsWith("Change since") }
        assertEquals(usd(-70_000), change.values.single())
        // The euro wallet is named, not added.
        assertTrue(report.notes.any { "EUR" in it && "never converted" in it })
    }

    @Test fun aPastMonthsNetWorthIsAsOfItsLastDay() {
        val report = ReportBuilder.netWorth(inputs, "USD", YearMonth.of(2026, 10))
        assertEquals(usd(675_000 + 15_000 - 45_000 - 1_000_000), report.headline.values.single())
        assertEquals(YearMonth.of(2026, 10), report.month)
    }

    @Test fun aMonthBeforeAnyWalletExistedSaysSoInsteadOfZeros() {
        val created = java.time.LocalDate.parse("2026-11-10").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val late = inputs.copy(wallets = inputs.wallets.map { it.copy(createdAtEpochMillis = created) })
        val report = ReportBuilder.netWorth(late, "USD", YearMonth.of(2026, 10))
        assertTrue(report.headline.detail!!, report.headline.detail!!.startsWith("No USD wallets existed"))
        assertEquals(listOf("Wallets"), report.sections.map { it.title })
    }

    @Test fun cashFlowKeepsTransfersAndAdjustmentsOutOfNetFlow() {
        val report = ReportBuilder.cashFlow(inputs, "USD", november)
        assertEquals("Net inflow", report.headline.label)
        // Income 3,000 − expenses (1,200 + 300 + 80 + 20) = 1,400; the loan payment and adjustment don't count.
        assertEquals(usd(140_000), report.headline.values.single())
        assertEquals(listOf("Rent", "Groceries", "Dining", "Uncategorized"), report.section("Expenses").lines.map { it.label })
        assertEquals("75% of expenses", report.section("Expenses").lines.first().detail)
        assertEquals(usd(160_000), report.section("Expenses").total!!.values.single())
        val apart = report.section("Not in net flow").lines
        assertEquals(usd(50_000), apart.first { it.label.startsWith("Transfers") }.values.single())
        assertEquals(usd(5_000), apart.first { it.label == "Balance adjustments" }.values.single())
    }

    @Test fun budgetVsActualIsTheBudgetScreensOwnPlan() {
        val report = ReportBuilder.budgetVsActual(inputs, "USD", november)
        val budgeted = report.section("Budgeted categories")
        val groceries = budgeted.lines.first { it.label == "Groceries" }
        assertEquals(listOf(usd(40_000), usd(30_000), usd(10_000)), groceries.values)
        // Budget 1,600 = groceries 400 + rent 1,200; spent counts every expense, budgeted or not (1,600).
        assertEquals(listOf(usd(160_000), usd(160_000), usd(0)), report.section("Month").total!!.values)
        assertEquals(listOf("Dining", "Uncategorized"), report.section("Spending without a budget").lines.map { it.label })
        assertEquals("Left to spend", report.headline.label)
    }

    @Test fun debtsShowRateProgressAndTheNextPayment() {
        val report = ReportBuilder.debts(inputs, "USD")
        assertEquals(usd(945_000), report.headline.values.single())
        val loan = report.section("Loans").lines.single()
        assertEquals(usd(900_000), loan.values.single())
        val detail = assertNotNull(loan.detail).let { loan.detail!! }
        assertTrue(detail, "6% a year" in detail)
        assertTrue(detail, "1 of 24 paid" in detail)
        assertTrue(detail, "next payment" in detail)
        val card = report.section("Credit cards").lines.single()
        assertTrue(card.detail!!, "24% a year" in card.detail!!)
    }

    @Test fun topCategoriesTakeTheTopNAndGroupTheRest() {
        val report = ReportBuilder.topCategories(inputs, "USD", november, top = 2)
        val lines = report.sections.single().lines
        assertEquals(listOf("1. Rent", "2. Groceries", "Everything else"), lines.map { it.label })
        assertEquals(usd(10_000), lines.last().values.single())
        assertEquals(usd(160_000), report.headline.values.single())
    }

    @Test fun localAnswersSummariseTheReportInOneSentence() {
        val top = ReportBuilder.topCategories(inputs, "USD", november, top = 2)
        val text = LocalAnswers.summary(top)
        assertTrue(text, text.startsWith("Rent leads your spending for November 2026 so far"))
        assertTrue(text, "(75%)" in text && "top 2 are below" in text)
        val flow = LocalAnswers.summary(ReportBuilder.cashFlow(inputs, "USD", november))
        assertTrue(flow, flow.startsWith("In November 2026 so far,") && "net inflow" in flow)
        val debts = LocalAnswers.summary(ReportBuilder.debts(inputs, "USD"))
        assertTrue(debts, debts.startsWith("You owe"))
    }
}
