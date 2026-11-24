package com.lucento.app.domain.capture

import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.capture.ScanFixtures.amex
import com.lucento.app.domain.capture.ScanFixtures.bdoCard
import com.lucento.app.domain.capture.ScanFixtures.bpi
import com.lucento.app.domain.capture.ScanFixtures.cash
import com.lucento.app.domain.capture.ScanFixtures.chase
import com.lucento.app.domain.capture.ScanFixtures.context
import com.lucento.app.domain.capture.ScanFixtures.gcash
import com.lucento.app.domain.capture.ScanFixtures.today
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Statements from the United States, the Philippines and Europe (scan roadmap, owner 2026-11-22): sections, columns,
 * card conventions, financial terms, the statement's own facts, and what each line becomes.
 */
class StatementIntelligenceTest {

    private val chaseChecking = listOf(
        "CHASE",
        "JPMorgan Chase Bank, N.A.",
        "Total Checking  Account Number: 000000123456789",
        "October 01, 2026 through October 31, 2026",
        "CHECKING SUMMARY",
        "Beginning Balance  $1,520.11",
        "Deposits and Additions  2,500.00",
        "ATM & Debit Card Withdrawals  -306.45",
        "Electronic Withdrawals  -2,402.00",
        "Ending Balance  $1,311.66",
        "DEPOSITS AND ADDITIONS",
        "DATE  DESCRIPTION  AMOUNT",
        "10/15  ACME CORP PAYROLL PPD ID: 1234567890  2,500.00",
        "Total Deposits and Additions  $2,500.00",
        "ATM & DEBIT CARD WITHDRAWALS",
        "DATE  DESCRIPTION  AMOUNT",
        "10/05  CARD PURCHASE 10/04 STARBUCKS STORE 12345 SEATTLE WA CARD 4421  6.45",
        "10/12  ATM WITHDRAWAL 0123 MAIN ST SEATTLE WA  300.00",
        "Total ATM & Debit Card Withdrawals  $306.45",
        "ELECTRONIC WITHDRAWALS",
        "DATE  DESCRIPTION  AMOUNT",
        "10/01  ZELLE PAYMENT TO JOHN SMITH  400.00",
        "10/20  PG&E WEB ONLINE PAYMENT  2,002.00",
        "Total Electronic Withdrawals  $2,402.00",
    )

    @Test fun usSectionsGiveEachLineItsDirection() {
        val reading = StatementParser.parse(chaseChecking, today, expectedCurrency = "USD")
        assertEquals(listOf("2500.00", "6.45", "300.00", "400.00", "2002.00").map(::BigDecimal), reading.lines.map { it.amount })
        assertEquals(listOf(StatementFlow.IN, StatementFlow.OUT, StatementFlow.OUT, StatementFlow.OUT, StatementFlow.OUT), reading.lines.map { it.flow })
        assertEquals(LineKind.PAYROLL, reading.lines[0].kind)
        assertEquals(LineKind.ATM, reading.lines[2].kind)
        assertEquals(LineKind.TRANSFER, reading.lines[3].kind)
        assertEquals(BigDecimal("1520.11"), reading.summary.opening)
        assertEquals(BigDecimal("1311.66"), reading.summary.closing)
        assertEquals(true, reading.addsUp)
        assertEquals(LocalDate.parse("2026-10-01"), reading.summary.periodStart)
        assertEquals(LocalDate.parse("2026-10-31"), reading.summary.periodEnd)
    }

    @Test fun usStatementRowsUseMerchantsAndTheirOwnAccounts() = runBlocking {
        val result = LocalTransactionInterpreter().interpretDocument(ScanDocument.ofLines(chaseChecking), context(defaultWalletId = cash.id)) as Interpretation.Candidates
        val rows = result.items
        // The heading names Chase: the rows go to the person's Chase account, though Cash was active.
        assertTrue(rows.all { it.walletId == chase.id || it.toWalletId == chase.id })
        assertEquals("Starbucks", rows[1].description)
        assertEquals(TransactionType.INCOME, rows[0].type)
        assertEquals(ScanFixtures.salary.id, rows[0].categoryId)
        assertEquals(ScanFixtures.utilities.id, rows.first { it.description == "PG&E" }.categoryId)
        assertEquals(1, result.statements.size)
    }

    private val amexStatement = listOf(
        "American Express",
        "Blue Cash Everyday Card  Account Ending 1-41004",
        "Closing Date 11/15/26",
        "Previous Balance  $1,200.00",
        "New Balance  $268.64",
        "Minimum Payment Due  $40.00",
        "Payment Due Date  12/10/26",
        "Credit Limit  $10,000.00",
        "Available Credit  $9,731.36",
        "Payments and Credits",
        "10/20/26  AUTOPAY PAYMENT RECEIVED - THANK YOU  -$1,200.00",
        "10/28/26  WALMART.COM REFUND  -$45.99",
        "New Charges",
        "10/18/26  WHOLEFDS MKT 10234 SEATTLE WA  $82.15",
        "10/25/26  NETFLIX.COM LOS GATOS CA  $15.49",
        "11/02/26  CASH ADVANCE  $200.00",
        "Fees",
        "11/01/26  FOREIGN TRANSACTION FEE  $2.99",
        "Interest Charged",
        "11/15/26  INTEREST CHARGE ON PURCHASES  $14.00",
    )

    @Test fun aCardStatementTellsPaymentsRefundsChargesFeesAndInterestApart() {
        val reading = StatementParser.parse(amexStatement, today, expectedCurrency = "USD", accountIsCard = true)
        val byAmount = reading.lines.associateBy { it.amount }
        assertEquals(StatementFlow.IN, byAmount.getValue(BigDecimal("1200.00")).flow)
        assertEquals(LineKind.PAYMENT_TO_CARD, byAmount.getValue(BigDecimal("1200.00")).kind)
        assertEquals(LineKind.REFUND, byAmount.getValue(BigDecimal("45.99")).kind)
        assertEquals(StatementFlow.IN, byAmount.getValue(BigDecimal("45.99")).flow)
        assertEquals(StatementFlow.OUT, byAmount.getValue(BigDecimal("82.15")).flow)
        assertEquals(LineKind.CASH_ADVANCE, byAmount.getValue(BigDecimal("200.00")).kind)
        assertEquals(LineKind.FEE, byAmount.getValue(BigDecimal("2.99")).kind)
        assertEquals(LineKind.INTEREST_CHARGE, byAmount.getValue(BigDecimal("14.00")).kind)
        val s = reading.summary
        assertEquals(BigDecimal("1200.00"), s.opening)
        assertEquals(BigDecimal("268.64"), s.closing)
        assertEquals(BigDecimal("40.00"), s.minimumDue)
        assertEquals(LocalDate.parse("2026-12-10"), s.dueDate)
        assertEquals(BigDecimal("10000.00"), s.creditLimit)
        assertEquals(BigDecimal("9731.36"), s.availableCredit)
        assertEquals(true, reading.addsUp)
    }

    @Test fun onACardARefundIsIncomeAndOnlyAPaymentIsATransfer() = runBlocking {
        val result = LocalTransactionInterpreter().interpretDocument(ScanDocument.ofLines(amexStatement), context(defaultWalletId = chase.id)) as Interpretation.Candidates
        val rows = result.items.associateBy { it.amount }
        val payment = rows.getValue(BigDecimal("1200.00"))
        assertEquals(TransactionType.TRANSFER_OUT, payment.type)
        assertEquals(amex.id, payment.toWalletId)
        val refund = rows.getValue(BigDecimal("45.99"))
        assertEquals(TransactionType.INCOME, refund.type)
        assertEquals(amex.id, refund.walletId)
        assertEquals(ScanFixtures.refunds.id, refund.categoryId)
        assertEquals(ScanFixtures.bankFees.id, rows.getValue(BigDecimal("2.99")).categoryId)
        assertEquals(TransactionType.EXPENSE, rows.getValue(BigDecimal("14.00")).type)
    }

    @Test fun cashInViaABankIsATransferFromThatBankNotTheCashWallet() = runBlocking {
        val history = listOf(
            "Cash In via BPI  +₱2,000.00", "Nov 18, 2026 8:01 PM",
            "Send Money  -₱500.00", "Nov 19, 2026 10:32 AM",
            "Pay Bills Meralco  -₱1,234.50", "Nov 20, 2026 9:15 AM",
        )
        val result = LocalTransactionInterpreter().interpretDocument(ScanDocument.ofLines(history), context(defaultWalletId = gcash.id)) as Interpretation.Candidates
        val cashIn = result.items.first { it.amount == BigDecimal("2000.00") }
        assertEquals(TransactionType.TRANSFER_OUT, cashIn.type)
        assertEquals(bpi.id, cashIn.walletId)
        assertEquals(gcash.id, cashIn.toWalletId)
        assertTrue(cashIn.walletId != cash.id)
        assertEquals("Meralco", result.items.first { it.amount == BigDecimal("1234.50") }.description)
    }

    @Test fun pendingLinesAreMarkedAndDeclinedOnesAreNotTransactions() {
        val lines = listOf(
            "Transactions",
            "11/18  UBER TRIP  PENDING  -24.10",
            "11/17  DOORDASH DECLINED  -31.00",
            "11/16  TRADER JOE'S #123  -52.40",
        )
        val reading = StatementParser.parse(lines, today, expectedCurrency = "USD")
        assertEquals(listOf(BigDecimal("24.10"), BigDecimal("52.40")), reading.lines.map { it.amount })
        assertEquals(LineStatus.PENDING, reading.lines[0].status)
        assertEquals(1, reading.declined)
    }

    @Test fun aGermanStatementWithMinusSignsAndCommaDecimals() {
        val lines = listOf(
            "Sparkasse  Kontoauszug",
            "Buchungstag  Verwendungszweck  Betrag",
            "03.11.2026  REWE SAGT DANKE  -45,67",
            "05.11.2026  Gutschrift GEHALT ACME GMBH  2.500,00",
            "07.11.2026  Lastschrift TELEKOM DEUTSCHLAND  -39,95",
        )
        val reading = StatementParser.parse(lines, today, expectedCurrency = "EUR")
        assertEquals(listOf("45.67", "2500.00", "39.95").map(::BigDecimal), reading.lines.map { it.amount })
        assertEquals(StatementFlow.OUT, reading.lines[0].flow)
        assertEquals(LineKind.PAYROLL, reading.lines[1].kind)
        assertEquals(LocalDate.parse("2026-11-03"), reading.lines[0].date)
    }

    @Test fun columnsGiveDirectionWhenPositionsAreKnown() {
        // A Wells Fargo-style page with separate money-in and money-out columns and no running balance.
        fun f(text: String, left: Int, top: Int) = TextFragment(text, left, top, left + text.length * 18, top + 30)
        val fragments = listOf(
            f("Date", 40, 100), f("Description", 200, 100), f("Deposits/Credits", 900, 100), f("Withdrawals/Debits", 1200, 100),
            f("11/03", 40, 150), f("COSTCO WHSE #0123", 200, 150), f("214.08", 1250, 150),
            f("11/05", 40, 200), f("ONLINE TRANSFER FROM SAV 4567", 200, 200), f("500.00", 950, 200),
            f("11/07", 40, 250), f("SHELL OIL 57444", 200, 250), f("48.20", 1260, 250),
        )
        val reading = StatementParser.parseRows(ReceiptParser.layoutRows(fragments), today, expectedCurrency = "USD")
        assertEquals(listOf(StatementFlow.OUT, StatementFlow.IN, StatementFlow.OUT), reading.lines.map { it.flow })
        assertEquals(FlowEvidence.COLUMN, reading.lines[1].evidence)
    }

    @Test fun aBankStatementScannedIntoACardKeepsTheBanksReading() {
        // The active wallet was a card, but the statement is a bank's with a running balance: money out lowers it.
        val lines = listOf(
            "BDO SAVINGS STATEMENT",
            "DATE  DESCRIPTION  DEBIT  CREDIT  BALANCE",
            "11/01  BEGINNING BALANCE  10,000.00",
            "11/03  ATM WITHDRAWAL  2,000.00  8,000.00",
            "11/05  PAYROLL  15,000.00  23,000.00",
        )
        val reading = StatementParser.parse(lines, today, expectedCurrency = "PHP", accountIsCard = true)
        assertEquals(listOf(StatementFlow.OUT, StatementFlow.IN), reading.lines.map { it.flow })
    }

    @Test fun aStatementWithoutAHeadingStaysInTheActiveWallet() {
        val (account, named) = StatementCandidates.account(
            StatementParser.parse(listOf("11/03  GROCERY  500.00", "11/04  LUNCH  250.00"), today),
            listOf("11/03  GROCERY  500.00", "11/04  LUNCH  250.00"),
            context(defaultWalletId = bdoCard.id),
        )
        assertEquals(bdoCard.id, account?.id)
        assertEquals(false, named)
        assertNull(null)
    }
}
