package com.lucento.app.domain.capture

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Scans: one receipt or a statement's many lines (owner, 2026-11-04). The rows below are what on-device OCR returns
 * once [ReceiptParser.mergeRows] has joined each printed line's columns.
 */
class StatementScanTest {

    private val today = LocalDate.parse("2026-11-04")

    private val bankStatement = listOf(
        "BPI SAVINGS ACCOUNT STATEMENT",
        "Statement Period: Oct 01, 2026 - Oct 31, 2026",
        "DATE  DESCRIPTION  DEBIT  CREDIT  BALANCE",
        "10/01  BEGINNING BALANCE  25,000.00",
        "10/03  ATM WITHDRAWAL  2,000.00  23,000.00",
        "10/05  PAYROLL CREDIT  30,000.00  53,000.00",
        "10/12  BILLS PAYMENT MERALCO  3,456.78  49,543.22",
        "10/15  INSTAPAY TRANSFER  1,000.00  48,543.22",
        "TO JUAN DELA CRUZ",
        "10/31  INTEREST EARNED  12.34  48,555.56",
        "10/31  ENDING BALANCE  48,555.56",
    )

    private val cardStatement = listOf(
        "SALE DATE  POST DATE  DESCRIPTION  AMOUNT",
        "10/02  10/03  LAZADA PH  1,299.00",
        "10/05  10/06  GRAB *FOOD  356.50",
        "10/10  10/10  PAYMENT - THANK YOU  5,000.00-",
        "10/15  10/16  NETFLIX.COM  549.00",
    )

    private val eWalletHistory = listOf(
        "Send Money  -₱500.00",
        "Nov 3, 2026 10:32 AM",
        "Cash In  +₱2,000.00",
        "Nov 2, 2026 8:01 PM",
        "Bills Payment  -₱1,234.50",
        "Nov 1, 2026 9:15 AM",
    )

    private val receipt = listOf(
        "JOLLIBEE", "SM MEGAMALL", "OR# 123456", "11/03/2026 12:31 PM",
        "1 Chickenjoy 1pc  99.00", "1 Jolly Spaghetti  60.00", "2 Coke Float  110.00",
        "SUBTOTAL  269.00", "VAT 12%  28.82", "TOTAL  269.00", "CASH  500.00", "CHANGE  231.00",
    )

    // ------------------------------------------------------------------ receipt or statement

    @Test fun receiptsAndStatementsAreToldApart() {
        assertEquals(ScanKind.STATEMENT, StatementParser.classify(bankStatement))
        assertEquals(ScanKind.STATEMENT, StatementParser.classify(cardStatement))
        assertEquals(ScanKind.STATEMENT, StatementParser.classify(eWalletHistory))
        assertEquals(ScanKind.RECEIPT, StatementParser.classify(receipt))
        // A bill: one amount due, a due date, a previous payment — still one payment.
        val bill = listOf(
            "MERALCO", "Billing period 10/01/2026 - 10/31/2026", "Previous balance  1,000.00",
            "Payment received 10/15/2026  1,000.00", "Amount due  2,345.60", "Due date Nov 15, 2026",
        )
        assertEquals(ScanKind.RECEIPT, StatementParser.classify(bill))
    }

    // ------------------------------------------------------------------ printed bank statement

    @Test fun aBankStatementsRunningBalanceDecidesMoneyInAndOut() {
        val reading = StatementParser.parse(bankStatement, today, expectedCurrency = "PHP")
        assertEquals(5, reading.lines.size)
        assertTrue(reading.balanceChecked)
        assertEquals(
            listOf(StatementFlow.OUT, StatementFlow.IN, StatementFlow.OUT, StatementFlow.OUT, StatementFlow.IN),
            reading.lines.map { it.flow },
        )
        assertTrue(reading.lines.all { it.evidence == FlowEvidence.BALANCE })
        assertEquals(
            listOf("2,000.00", "30,000.00", "3,456.78", "1,000.00", "12.34").map { BigDecimal(it.replace(",", "")) },
            reading.lines.map { it.amount },
        )
        // Month first (a day above 12 settles it); the year is the statement's.
        assertEquals(
            listOf("2026-10-03", "2026-10-05", "2026-10-12", "2026-10-15", "2026-10-31").map(LocalDate::parse),
            reading.lines.map { it.date },
        )
        assertTrue(reading.lines.none { it.dateAmbiguous })
        assertEquals("ATM Withdrawal", reading.lines[0].description)
        assertEquals("Bills Payment Meralco", reading.lines[2].description)
        // A wrapped second line belongs to the line above it.
        assertEquals("Instapay Transfer TO Juan Dela Cruz", reading.lines[3].description)
        assertEquals(BigDecimal("48543.22"), reading.lines[3].balance)
    }

    @Test fun newestFirstStatementsAreCheckedUpwards() {
        val lines = listOf(
            "DATE  DESCRIPTION  AMOUNT  BALANCE",
            "11/03  GRAB  350.00  9,650.00",
            "11/02  SALARY  5,000.00  10,000.00",
            "11/01  NETFLIX  549.00  5,000.00",
        )
        val reading = StatementParser.parse(lines, today)
        assertEquals(listOf(StatementFlow.OUT, StatementFlow.IN), reading.lines.take(2).map { it.flow })
        assertTrue(reading.balanceChecked)
        // The oldest line has nothing below it to compare with, and no words that say: it's left for the user.
        assertEquals(StatementFlow.UNKNOWN, reading.lines[2].flow)
    }

    // ------------------------------------------------------------------ card statement

    @Test fun aCardStatementsLinesAreChargesUnlessMarkedAsPayments() {
        val reading = StatementParser.parse(cardStatement, today, expectedCurrency = "PHP")
        assertTrue(reading.looksLikeCard)
        assertEquals(4, reading.lines.size)
        assertEquals(
            listOf(StatementFlow.OUT, StatementFlow.OUT, StatementFlow.IN, StatementFlow.OUT),
            reading.lines.map { it.flow },
        )
        // A trailing minus on a card statement is a credit.
        assertEquals(FlowEvidence.MARKER, reading.lines[2].evidence)
        // The sale date, not the posting date.
        assertEquals(LocalDate.parse("2026-10-02"), reading.lines[0].date)
        assertEquals("Lazada PH", reading.lines[0].description)
        assertEquals("Grab Food", reading.lines[1].description)
        assertEquals(BigDecimal("356.50"), reading.lines[1].amount)
    }

    // ------------------------------------------------------------------ app histories

    @Test fun anEWalletHistoryTakesTheDateUnderEachLineAndItsSigns() {
        val reading = StatementParser.parse(eWalletHistory, today, expectedCurrency = "PHP")
        assertEquals(3, reading.lines.size)
        assertEquals(listOf("Send Money", "Cash In", "Bills Payment"), reading.lines.map { it.description })
        assertEquals(listOf(StatementFlow.OUT, StatementFlow.IN, StatementFlow.OUT), reading.lines.map { it.flow })
        assertEquals(listOf("2026-11-03", "2026-11-02", "2026-11-01").map(LocalDate::parse), reading.lines.map { it.date })
        assertEquals(listOf("500.00", "2000.00", "1234.50").map(::BigDecimal), reading.lines.map { it.amount })
        assertEquals("PHP", reading.currencyCode)
    }

    @Test fun dateHeadingsApplyToTheLinesBelowThem() {
        val lines = listOf(
            "Today",
            "Starbucks  - ₱ 210.00",
            "Grab  - ₱ 180.00",
            "Yesterday",
            "Received from Ana  + ₱ 1,000.00",
        )
        assertEquals(ScanKind.STATEMENT, StatementParser.classify(lines))
        val reading = StatementParser.parse(lines, today)
        assertEquals(listOf("Starbucks", "Grab", "Received from Ana"), reading.lines.map { it.description })
        assertEquals(listOf(today, today, today.minusDays(1)), reading.lines.map { it.date })
        assertEquals(listOf(StatementFlow.OUT, StatementFlow.OUT, StatementFlow.IN), reading.lines.map { it.flow })
    }

    @Test fun wordsDecideWhenThereIsNoBalanceOrSign() {
        val lines = listOf(
            "DATE  DESCRIPTION  AMOUNT",
            "11/01  CASH DEPOSIT  5,000.00",
            "11/02  POS PURCHASE PUREGOLD  1,234.00",
            "11/03  MISC  500.00",
        )
        val reading = StatementParser.parse(lines, today)
        assertEquals(listOf(StatementFlow.IN, StatementFlow.OUT, StatementFlow.UNKNOWN), reading.lines.map { it.flow })
        assertEquals(listOf(FlowEvidence.WORDS, FlowEvidence.WORDS, FlowEvidence.NONE), reading.lines.map { it.evidence })
    }

    @Test fun dayFirstDatesAndYearsAcrossNewYear() {
        // 25/12: the day comes first. The statement printed in January: December is last year's.
        val lines = listOf(
            "Statement date 05 Jan 2027",
            "25/12  SHOPEE  899.00  10,101.00",
            "02/01  SALARY  20,000.00  30,101.00",
        )
        val reading = StatementParser.parse(lines, LocalDate.parse("2027-01-06"))
        assertEquals(listOf(LocalDate.parse("2026-12-25"), LocalDate.parse("2027-01-02")), reading.lines.map { it.date })
        assertTrue(reading.lines.none { it.dateAmbiguous })
    }

    @Test fun unsettledDayMonthOrderIsMarked() {
        val lines = listOf("03/04  COFFEE  150.00", "04/03  TEA  120.00")
        val reading = StatementParser.parse(lines, today, dayFirst = false)
        assertTrue(reading.lines.all { it.dateAmbiguous })
    }

    // ------------------------------------------------------------------ to review rows

    private val pesoBank = Wallet(1, "BPI Savings", WalletType.BANK, Money(4_855_556, "PHP"), 0)
    private val visa = Wallet(2, "BPI Visa", WalletType.CREDIT_CARD, Money(500_000, "PHP"), 0)
    private val gcash = Wallet(3, "GCash", WalletType.E_WALLET, Money(100_000, "PHP"), 0)
    private val categories = listOf(
        Category(10, "Salary", CategoryKind.INCOME, "work"),
        Category(11, "Bills", CategoryKind.EXPENSE, "bills"),
        Category(12, "Shopping", CategoryKind.EXPENSE, "shopping"),
    )

    private fun context(active: Long) = InterpretationContext(listOf(pesoBank, visa, gcash), categories, today, defaultWalletId = active)

    @Test fun statementLinesBecomeRowsInTheScannedAccount() {
        val items = StatementCandidates.from(StatementParser.parse(bankStatement, today, "PHP"), context(active = 1))
        assertEquals(5, items.size)
        assertTrue(items.all { it.walletId == 1L && CandidateFlag.FROM_STATEMENT in it.flags })
        assertEquals(
            listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.EXPENSE, TransactionType.INCOME),
            items.map { it.type },
        )
        assertEquals(BigDecimal("30000.00"), items[1].amount)
        assertTrue(items.none { CandidateFlag.DIRECTION_UNCERTAIN in it.flags })
    }

    @Test fun anAtmWithdrawalMovesMoneyIntoTheOneCashWallet() {
        val cash = Wallet(4, "Wallet cash", WalletType.CASH, Money(50_000, "PHP"), 0)
        val withCash = InterpretationContext(listOf(pesoBank, visa, gcash, cash), categories, today, defaultWalletId = 1)
        val items = StatementCandidates.from(StatementParser.parse(bankStatement, today, "PHP"), withCash)
        assertEquals(TransactionType.TRANSFER_OUT, items[0].type)
        assertEquals(1L, items[0].walletId)
        assertEquals(4L, items[0].toWalletId)
        // Two cash wallets: Lucento can't tell which, so it stays an expense for the user to decide.
        val twoCash = withCash.copy(wallets = withCash.wallets + cash.copy(id = 5, name = "Coins"))
        assertEquals(TransactionType.EXPENSE, StatementCandidates.from(StatementParser.parse(bankStatement, today, "PHP"), twoCash)[0].type)
    }

    @Test fun aPaymentIntoTheCardIsATransferAndANamedAccountIsItsSource() {
        val lines = cardStatement + "10/20  10/21  PAYMENT FROM GCASH  1,000.00-"
        val items = StatementCandidates.from(StatementParser.parse(lines, today, "PHP", accountIsCard = true), context(active = 2))
        val payment = items[2]
        assertEquals(TransactionType.TRANSFER_OUT, payment.type)
        assertEquals(2L, payment.toWalletId)
        assertNull(payment.walletId) // the user picks where it came from
        val fromGcash = items[4]
        assertEquals(TransactionType.TRANSFER_OUT, fromGcash.type)
        assertEquals(3L, fromGcash.walletId)
        assertEquals(2L, fromGcash.toWalletId)
        assertEquals(TransactionType.EXPENSE, items[0].type)
        assertEquals(2L, items[0].walletId)
        val note = StatementCandidates.note(StatementParser.parse(lines, today, "PHP", accountIsCard = true), items, java.util.Locale.US)
        assertTrue(note, note.startsWith("Read as a statement: 5 transactions, Oct 2 – Oct 20, each its own row."))
        assertTrue(note, note.contains("Pick the account each payment came from"))
    }

    @Test fun theInterpreterReadsAReceiptAsOnePaymentAndAStatementAsManyRows() = runBlocking {
        val interpreter = LocalTransactionInterpreter()
        val statement = interpreter.interpretScan(eWalletHistory, context(active = 3)) as Interpretation.Candidates
        assertEquals(3, statement.items.size)
        assertTrue(statement.note!!.startsWith("Read as a statement: 3 transactions"))
        val one = interpreter.interpretScan(receipt, context(active = 3)) as Interpretation.Candidates
        assertEquals(1, one.items.size)
        assertEquals(BigDecimal("269.00"), one.items.single().amount)
        assertEquals("Read as one receipt: one payment for the “Total” line, not each item.", one.note)
    }

    // ------------------------------------------------------------------ receipts: amount due / paid

    @Test fun amountDueBeatsAmountPaidAndPaidLessChangeIsTheTotal() {
        val due = ReceiptParser.parse(listOf("MERALCO", "Date: 11/02/2026", "Amount Due  2,345.60", "Amount Paid  2,500.00", "Change  154.40"), today)
        assertEquals(BigDecimal("2345.60"), due.total)
        assertEquals("Amount due", due.totalLabel)
        val paid = ReceiptParser.parse(listOf("CAFE UNO", "Amount Paid  1,000.00", "Change  150.00"), today)
        assertEquals(BigDecimal("850.00"), paid.total)
        assertEquals(TotalConfidence.LABELLED, paid.totalConfidence)
        assertEquals("Amount paid less change", paid.totalLabel)
        val onlyPaid = ReceiptParser.parse(listOf("WATER DISTRICT", "Amount Paid  845.00"), today)
        assertEquals(BigDecimal("845.00"), onlyPaid.total)
        // "Minimum amount due" is never the total.
        val minimum = ReceiptParser.parse(listOf("CARD", "Minimum amount due  500.00", "Total amount due  9,000.00"), today)
        assertEquals(BigDecimal("9000.00"), minimum.total)
    }

    // ------------------------------------------------------------------ "already saved?"

    @Test fun statementRowsMatchSavedTransactionsAFewDaysApartWithOtherWords() {
        val saved = Transaction(
            id = 1, type = TransactionType.EXPENSE, amount = Money(345_678, "PHP"), walletId = 1, categoryId = 11,
            date = LocalDate.parse("2026-10-10"), note = "Electricity", createdAtEpochMillis = 0,
        )
        val items = StatementCandidates.from(StatementParser.parse(bankStatement, today, "PHP"), context(active = 1))
        val rows = items.mapIndexed { i, c -> ReviewRow.from(c, "k$i", 1, CaptureSource.RECEIPT, '.') }
        val setup = ReviewSetup(listOf(pesoBank, visa, gcash), categories, '.', transfersAllowed = true, history = listOf(saved))
        val meralco = BatchReview.check(rows[2].copy(categoryId = 11), setup)
        assertTrue(meralco.issues.any { it.message.startsWith("Looks already recorded") && !it.blocking })
        val atm = BatchReview.check(rows[0].copy(categoryId = 12), setup)
        assertFalse(atm.issues.any { it.message.startsWith("Looks already recorded") })
    }

    @Test fun choosingTheTypeAnswersMoneyInOrOut() {
        val lines = listOf("DATE  DESCRIPTION  AMOUNT", "11/01  MISC  500.00", "11/02  OTHER  20.00")
        val row = ReviewRow.from(
            StatementCandidates.from(StatementParser.parse(lines, today), context(active = 1)).first(), "k", 1, CaptureSource.RECEIPT, '.',
        )
        assertTrue(CandidateFlag.DIRECTION_UNCERTAIN in row.flags)
        val setup = ReviewSetup(listOf(pesoBank), categories, '.', transfersAllowed = false)
        assertTrue(BatchReview.check(row, setup).issues.any { it.field == RowField.TYPE && !it.blocking })
        val confirmed = BatchReview.apply(row, RowEdit.Type(TransactionType.EXPENSE), categories, '.')
        assertFalse(CandidateFlag.DIRECTION_UNCERTAIN in confirmed.flags)
        assertFalse(BatchReview.check(confirmed, setup).issues.any { it.field == RowField.TYPE })
    }
}
