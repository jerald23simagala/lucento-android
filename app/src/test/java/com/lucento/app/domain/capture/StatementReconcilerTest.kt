package com.lucento.app.domain.capture

import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.capture.ScanFixtures.bpi
import com.lucento.app.domain.capture.ScanFixtures.cash
import com.lucento.app.domain.capture.ScanFixtures.context
import com.lucento.app.domain.capture.ScanFixtures.saved
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A statement checked against the ledger (scan roadmap phase 2, owner 2026-11-22): lines already saved are held back so
 * nothing is saved twice, near-matches are marked, the balance is compared, refunds and reversals are paired — and no
 * saved transaction is ever changed.
 */
class StatementReconcilerTest {

    private val statement = listOf(
        "BPI SAVINGS ACCOUNT STATEMENT",
        "Statement Period: Nov 01, 2026 - Nov 20, 2026",
        "DATE  DESCRIPTION  DEBIT  CREDIT  BALANCE",
        "11/01  BEGINNING BALANCE  10,000.00",
        "11/03  POS PURCHASE SM SUPERMARKET  1,845.50  8,154.50",
        "11/05  PAYROLL ACME INC  15,000.00  23,154.50",
        "11/09  BILLS PAYMENT MERALCO  2,345.67  20,808.83",
        "11/12  ONLINE PURCHASE LAZADA  999.00  19,809.83",
        "11/14  REFUND LAZADA  999.00  20,808.83",
        "11/18  SERVICE CHARGE  50.00  20,758.83",
        "11/20  ENDING BALANCE  20,758.83",
    )

    private fun read(history: List<Transaction>) = runBlocking {
        LocalTransactionInterpreter().interpretDocument(ScanDocument.ofLines(statement), context(defaultWalletId = bpi.id, history = history)) as Interpretation.Candidates
    }

    @Test fun linesAlreadySavedAreHeldBackAndTheRestAdded() {
        val history = listOf(
            saved(TransactionType.EXPENSE, 184_550, bpi, "2026-11-02", "Groceries at SM", ScanFixtures.groceries),
            saved(TransactionType.INCOME, 1_500_000, bpi, "2026-11-05", "Salary", ScanFixtures.salary),
        )
        val result = read(history)
        assertEquals(2, result.held.size)
        assertEquals(setOf(BigDecimal("1845.50"), BigDecimal("15000.00")), result.held.map { it.candidate.amount }.toSet())
        assertEquals(4, result.items.size)
        assertTrue(result.items.all { CandidateFlag.RECONCILED in it.flags })
        val report = result.statements.single()
        assertEquals(2, report.recorded)
        assertEquals(6, report.lines)
    }

    @Test fun aNearMatchIsAddedButMarked() {
        // The same amount saved six days later is not surely the same payment: added, with a note.
        val history = listOf(saved(TransactionType.EXPENSE, 234_567, bpi, "2026-11-15", "Meralco bill", ScanFixtures.utilities))
        val meralco = read(history).items.first { it.amount == BigDecimal("2345.67") }
        assertNotNull(meralco.hint)
        assertTrue(meralco.hint!!.startsWith("Possibly saved already"))
    }

    @Test fun theSameAmountSavedInAnotherAccountIsMarked() {
        val history = listOf(saved(TransactionType.EXPENSE, 5_000, cash, "2026-11-18", "Bank charge"))
        val fee = read(history).items.first { it.amount == BigDecimal("50.00") }
        assertTrue(fee.hint!!.contains("in Cash"))
    }

    @Test fun aRefundAndItsPurchaseOnOneStatementCancelOut() {
        val result = read(emptyList())
        val purchase = result.items.first { it.amount == BigDecimal("999.00") && it.type == TransactionType.EXPENSE }
        val refund = result.items.first { it.amount == BigDecimal("999.00") && it.type == TransactionType.INCOME }
        assertTrue(refund.hint!!.startsWith("Cancels out"))
        assertTrue(purchase.hint!!.startsWith("Cancelled by"))
        assertEquals(ScanFixtures.refunds.id, refund.categoryId)
        assertEquals(1, result.statements.single().cancelling)
    }

    @Test fun aRefundOfAPurchaseSavedEarlierSaysSo() {
        val lines = listOf("BPI", "11/14  REFUND ZALORA ORDER  1,299.00  5,000.00", "11/15  FOOD  200.00  4,800.00")
        val history = listOf(saved(TransactionType.EXPENSE, 129_900, bpi, "2026-10-28", "Zalora shoes", ScanFixtures.shopping))
        val result = runBlocking {
            LocalTransactionInterpreter().interpretDocument(ScanDocument.ofLines(lines), context(defaultWalletId = bpi.id, history = history)) as Interpretation.Candidates
        }
        val refund = result.items.first { it.amount == BigDecimal("1299.00") }
        assertTrue(refund.hint!!.startsWith("Refund of your Oct 28 purchase"))
    }

    @Test fun theStatementBalanceIsComparedWithLucentosAfterSaving() {
        // BPI opened with ₱10,000 and nothing saved: saving every row offered lands exactly on the statement's balance.
        val opened = bpi.copy(balance = com.lucento.app.core.money.Money(1_000_000, "PHP"), openingBalance = com.lucento.app.core.money.Money(1_000_000, "PHP"))
        val wallets = ScanFixtures.wallets.map { if (it.id == bpi.id) opened else it }
        val result = runBlocking {
            LocalTransactionInterpreter().interpretDocument(ScanDocument.ofLines(statement), context(defaultWalletId = bpi.id, wallets = wallets)) as Interpretation.Candidates
        }
        val balance = result.statements.single().balance!!
        assertEquals(LocalDate.parse("2026-11-20"), balance.date)
        assertEquals(BigDecimal("20758.83"), balance.statement)
        assertEquals(BigDecimal("10000.00"), balance.beforeSaving)
        assertTrue(balance.matches)
    }

    @Test fun aBalanceDifferenceIsReportedNotFixed() {
        val opened = bpi.copy(balance = com.lucento.app.core.money.Money(900_000, "PHP"), openingBalance = com.lucento.app.core.money.Money(900_000, "PHP"))
        val wallets = ScanFixtures.wallets.map { if (it.id == bpi.id) opened else it }
        val history = listOf(saved(TransactionType.EXPENSE, 10_000, bpi, "2026-10-30", "Old"))
        val before = history.toList()
        val result = runBlocking {
            LocalTransactionInterpreter().interpretDocument(ScanDocument.ofLines(statement), context(defaultWalletId = bpi.id, wallets = wallets, history = history)) as Interpretation.Candidates
        }
        val balance = result.statements.single().balance!!
        assertFalse(balance.matches)
        assertEquals(BigDecimal("1100.00"), balance.difference)
        // Nothing saved was touched: the ledger passed in is exactly as it was.
        assertEquals(before, history)
    }

    @Test fun savedTransactionsMissingFromAWholeStatementAreListed() {
        val history = listOf(saved(TransactionType.EXPENSE, 75_000, bpi, "2026-11-10", "Dinner"))
        val report = read(history).statements.single()
        assertEquals(listOf("Dinner"), report.notOnStatement.map { it.note })
    }

    @Test fun aLineTwiceOnTheStatementIsMarked() {
        val lines = listOf("BPI", "11/10  GRAB RIDE  150.00", "11/10  GRAB RIDE  150.00", "11/11  COFFEE  120.00")
        val result = runBlocking {
            LocalTransactionInterpreter().interpretDocument(ScanDocument.ofLines(lines), context(defaultWalletId = bpi.id)) as Interpretation.Candidates
        }
        assertEquals(3, result.items.size)
        assertTrue(result.items[1].hint!!.contains("appears twice"))
        assertEquals(1, result.statements.single().repeated)
    }

    @Test fun aSecondScanOfTheSameLinesNeverAddsThemTwice() {
        val first = read(emptyList()).items.mapIndexed { i, c -> ReviewRow.from(c, "k$i", 1, CaptureSource.RECEIPT, '.') }
        val again = read(emptyList()).items
        assertEquals(again.indices.toSet(), BatchReview.repeatsInBatch(again, first, '.'))
    }

    @Test fun reviewShowsTheHintAndSkipsTheLooserCheckForReconciledRows() {
        val history = listOf(saved(TransactionType.EXPENSE, 234_567, bpi, "2026-11-15", "Meralco bill", ScanFixtures.utilities))
        val row = read(history).items.first { it.amount == BigDecimal("2345.67") }.let { ReviewRow.from(it, "k", 1, CaptureSource.RECEIPT, '.') }
        val status = BatchReview.check(row, ReviewSetup(ScanFixtures.wallets, ScanFixtures.categories, '.', transfersAllowed = true, history = history))
        assertEquals(1, status.issues.count { it.message.startsWith("Possibly saved already") })
        assertTrue(status.issues.none { it.message.startsWith("Looks already recorded") })
    }
}
