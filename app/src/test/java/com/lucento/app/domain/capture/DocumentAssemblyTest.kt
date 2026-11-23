package com.lucento.app.domain.capture

import com.lucento.app.domain.capture.ScanFixtures.bpi
import com.lucento.app.domain.capture.ScanFixtures.context
import com.lucento.app.domain.capture.ScanFixtures.today
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Several pages and several documents (scan roadmap phase 1, owner 2026-11-22): overlapping photos read once, a PDF's
 * pages never trimmed, receipts photographed together split, a long receipt joined, pages turned upright.
 */
class DocumentAssemblyTest {

    /** A statement of [n] lines with a running balance, as printed rows. */
    private fun statementRows(n: Int): List<String> {
        var balance = BigDecimal("50000.00")
        return (1..n).map { i ->
            val amount = BigDecimal(100 + i * 7).setScale(2)
            balance -= amount
            "11/${(i % 28 + 1).toString().padStart(2, '0')}  PURCHASE NO $i  ${amount.toPlainString()}  ${"%,.2f".format(balance)}"
        }
    }

    private val header = listOf("BPI SAVINGS STATEMENT", "DATE  DESCRIPTION  DEBIT  CREDIT  BALANCE", "11/01  BEGINNING BALANCE  50,000.00")

    @Test fun overlappingPhotosOfOneStatementAreReadOnce() = runBlocking {
        val rows = statementRows(30)
        // The person photographed lines 1–17, then 15–30: three lines appear on both photos.
        val page1 = ScanPage.ofLines(header + rows.subList(0, 17), PageOrigin.PHOTO)
        val page2 = ScanPage.ofLines(listOf("DATE  DESCRIPTION  DEBIT  CREDIT  BALANCE") + rows.subList(14, 30) + listOf("Page 2 of 2"), PageOrigin.PHOTO)
        val parts = DocumentAssembler.assemble(ScanDocument(listOf(page1, page2)), today)
        assertEquals(1, parts.size)
        assertEquals(3, parts.single().overlapRemoved)
        val result = LocalTransactionInterpreter().interpretDocument(ScanDocument(listOf(page1, page2)), context(defaultWalletId = bpi.id)) as Interpretation.Candidates
        assertEquals(30, result.items.size)
        assertTrue(result.note!!.contains("3 lines that appeared on two photos were read once"))
    }

    @Test fun aPdfsPagesAreNeverTrimmed() {
        // Two equal lines across a PDF page break are two real transactions.
        val line = "11/10  COFFEE  120.00  4,880.00"
        val page1 = ScanPage.ofLines(header + listOf("11/09  LUNCH  0.00  5,000.00", line), PageOrigin.PDF_TEXT)
        val page2 = ScanPage.ofLines(listOf(line, "11/11  BUS  15.00  4,865.00"), PageOrigin.PDF_TEXT)
        val parts = DocumentAssembler.assemble(ScanDocument(listOf(page1, page2)), today)
        assertEquals(0, parts.single().overlapRemoved)
    }

    @Test fun oneEqualLineAcrossPhotosIsKeptUnlessItCarriesABalance() {
        val (_, none) = DocumentAssembler.join(listOf(LayoutRow("11/10  GRAB  150.00")), listOf(LayoutRow("11/10  GRAB  150.00")), mayOverlap = true)
        assertEquals(0, none)
        val (_, one) = DocumentAssembler.join(listOf(LayoutRow("11/10  GRAB  150.00  4,850.00")), listOf(LayoutRow("11/10  GRAB  150.00  4,850.00")), mayOverlap = true)
        assertEquals(1, one)
    }

    @Test fun separateReceiptsScannedTogetherAreSeparatePayments() = runBlocking {
        val first = ScanPage.ofLines(listOf("JOLLIBEE", "CHICKENJOY  99.00", "TOTAL  99.00", "CASH  100.00", "CHANGE  1.00"))
        val second = ScanPage.ofLines(listOf("MERCURY DRUG", "BIOGESIC  45.50", "AMOUNT DUE  45.50"))
        val result = LocalTransactionInterpreter().interpretDocument(ScanDocument(listOf(first, second)), context()) as Interpretation.Candidates
        assertEquals(listOf(BigDecimal("99.00"), BigDecimal("45.50")), result.items.map { it.amount })
        assertEquals("Read 2 receipts: each payment is its own row.", result.note)
    }

    @Test fun aLongReceiptPhotographedInTwoPartsIsOnePayment() = runBlocking {
        val top = ScanPage.ofLines(listOf("PUREGOLD", "RICE 5KG  289.00", "EGGS  105.00", "MILK  98.50"), PageOrigin.PHOTO)
        val bottom = ScanPage.ofLines(listOf("MILK  98.50", "BREAD  65.00", "TOTAL  557.50", "CASH  600.00", "CHANGE  42.50"), PageOrigin.PHOTO)
        val result = LocalTransactionInterpreter().interpretDocument(ScanDocument(listOf(top, bottom)), context()) as Interpretation.Candidates
        assertEquals(1, result.items.size)
        assertEquals(BigDecimal("557.50"), result.items.single().amount)
        assertEquals("Puregold", result.items.single().description)
    }

    private fun receiptFragments(lines: List<Pair<String, String?>>, left: Int, top: Int = 100): List<TextFragment> =
        lines.flatMapIndexed { i, (label, amount) ->
            val y = top + i * 40
            listOfNotNull(
                TextFragment(label, left, y, left + label.length * 16, y + 28),
                amount?.let { TextFragment(it, left + 420, y, left + 420 + it.length * 16, y + 28) },
            )
        }

    private val jollibee = listOf("JOLLIBEE" to null, "SM MEGAMALL" to null, "CHICKENJOY" to "99.00", "SPAGHETTI" to "60.00", "TOTAL" to "159.00", "CASH" to "200.00", "CHANGE" to "41.00")
    private val mercury = listOf("MERCURY DRUG" to null, "ORTIGAS BRANCH" to null, "BIOGESIC" to "45.50", "VITAMIN C" to "120.00", "AMOUNT DUE" to "165.50")

    @Test fun twoReceiptsSideBySideInOnePhotoAreSplit() = runBlocking {
        val fragments = receiptFragments(jollibee, left = 40) + receiptFragments(mercury, left = 800)
        assertEquals(2, DocumentLayout.splitReceipts(fragments, today).size)
        val page = ScanPage.ofFragments(fragments, PageOrigin.PHOTO)
        val result = LocalTransactionInterpreter().interpretDocument(ScanDocument(listOf(page)), context()) as Interpretation.Candidates
        assertEquals(listOf(BigDecimal("159.00"), BigDecimal("165.50")), result.items.map { it.amount })
    }

    @Test fun twoReceiptsOneAboveTheOtherAreSplit() {
        val fragments = receiptFragments(jollibee, left = 40, top = 100) + receiptFragments(mercury, left = 40, top = 900)
        assertEquals(2, DocumentLayout.splitReceipts(fragments, today).size)
    }

    @Test fun oneReceiptsItemAndPriceColumnsNeverSplit() {
        assertEquals(1, DocumentLayout.splitReceipts(receiptFragments(jollibee, left = 40), today).size)
    }

    @Test fun aPageTurnedSidewaysIsReadUpright() {
        val upright = receiptFragments(mercury, left = 40)
        // The same receipt photographed turned a quarter clockwise: (x, y) → (H − y, x), text angle 90°.
        val height = 2000
        val turned = upright.map { f -> TextFragment(f.text, left = height - f.bottom, top = f.left, right = height - f.top, bottom = f.right, angle = 90f) }
        assertEquals(ReceiptParser.mergeRows(upright), ReceiptParser.mergeRows(DocumentLayout.upright(turned)))
        val upsideDown = upright.map { f -> TextFragment(f.text, left = 3000 - f.right, top = height - f.bottom, right = 3000 - f.left, bottom = height - f.top, angle = 180f) }
        assertEquals(ReceiptParser.mergeRows(upright), ReceiptParser.mergeRows(DocumentLayout.upright(upsideDown)))
        val anticlockwise = upright.map { f -> TextFragment(f.text, left = f.top, top = 3000 - f.right, right = f.bottom, bottom = 3000 - f.left, angle = -90f) }
        assertEquals(ReceiptParser.mergeRows(upright), ReceiptParser.mergeRows(DocumentLayout.upright(anticlockwise)))
    }
}
