package com.lucento.app.domain.capture

import com.lucento.app.core.money.Money
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
 * Tester report 2026-11-21: a grocery receipt didn't pick up "Amount due / paid" and offered numbers that weren't the
 * payment. Photographed at a slight tilt, the amount column drifts a line, so labels met the wrong prices; and with no
 * labelled total, cash handed over, change and VAT lines were offered as the total.
 */
class ReceiptScanTiltTest {

    private val today = LocalDate.parse("2026-11-21")

    /** One printed line: a label on the left and (optionally) an amount in the right-hand column. */
    private data class Printed(val label: String, val amount: String? = null)

    private val grocery = listOf(
        Printed("PUREGOLD JR MARIKINA"),
        Printed("VAT REG TIN 000-111-222-000"),
        Printed("11/20/2026 18:42"),
        Printed("BEAR BRAND 320G", "129.50"),
        Printed("PANCIT CANTON 6S", "90.00"),
        Printed("EGGS LARGE 12S", "108.00"),
        Printed("DINORADO RICE 5KG", "345.00"),
        Printed("AMT DUE", "672.50"),
        Printed("CASH", "1,000.00"),
        Printed("CHANGE", "327.50"),
        Printed("VATable Sales", "600.45"),
        Printed("VAT Amount", "72.05"),
    )

    /**
     * What ML Kit returns for [lines] photographed turned [degrees] clockwise: each fragment's centre moves down by its
     * distance from the left edge times tan(angle), and every line reports that angle.
     */
    private fun photographed(lines: List<Printed>, degrees: Double, reportAngle: Boolean = true): List<TextFragment> {
        val slope = kotlin.math.tan(Math.toRadians(degrees))
        val pitch = 34
        val height = 24
        fun fragment(text: String, left: Int, right: Int, row: Int): TextFragment {
            val shift = ((left + right) / 2 * slope).toInt()
            val top = 100 + row * pitch + shift
            return TextFragment(text, left, top, right, top + height, if (reportAngle) degrees.toFloat() else 0f)
        }
        return lines.flatMapIndexed { row, line ->
            listOfNotNull(
                fragment(line.label, 20, 20 + line.label.length * 12, row),
                line.amount?.let { fragment(it, 600 - it.length * 12, 600, row) },
            )
        }.shuffled(java.util.Random(7))
    }

    private val context = InterpretationContext(
        wallets = listOf(Wallet(1, "Cash", WalletType.CASH, Money(0, "PHP"), 0)),
        categories = emptyList(),
        today = today,
        defaultWalletId = 1,
    )

    @Test fun tiltedReceiptKeepsEachLabelWithItsOwnAmount() {
        val rows = ReceiptParser.mergeRows(photographed(grocery, degrees = 3.0))
        assertTrue(rows.toString(), "AMT DUE  672.50" in rows)
        assertTrue(rows.toString(), "CASH  1,000.00" in rows)
        assertTrue(rows.toString(), "CHANGE  327.50" in rows)
    }

    @Test fun tiltedGroceryReceiptIsOnePaymentForTheAmountDue() = runBlocking {
        val lines = ReceiptParser.mergeRows(photographed(grocery, degrees = -2.5))
        val result = LocalTransactionInterpreter().interpretScan(lines, context) as Interpretation.Candidates
        val row = result.items.single()
        assertEquals(BigDecimal("672.50"), row.amount)
        assertFalse(CandidateFlag.TOTAL_UNCONFIRMED in row.flags)
        assertTrue(result.note.orEmpty(), "Amt due" in result.note.orEmpty())
    }

    @Test fun levelPhotosAndUnknownAnglesAreLeftAsTheyAre() {
        assertEquals(0.0, ReceiptParser.pageTilt(photographed(grocery, degrees = 0.2)), 0.0)
        assertEquals(0.0, ReceiptParser.pageTilt(photographed(grocery, degrees = 3.0, reportAngle = false)), 0.0)
        assertEquals(0.0, ReceiptParser.pageTilt(emptyList()), 0.0)
        assertEquals(0.0, ReceiptParser.pageTilt(photographed(grocery, degrees = 45.0)), 0.0)
    }

    @Test fun thermalPrinterAbbreviationsAreTotals() {
        assertEquals(BigDecimal("250.00"), ReceiptParser.parse(listOf("SHOP", "Bread  250.00", "TTL  250.00"), today).total)
        assertEquals(BigDecimal("410.00"), ReceiptParser.parse(listOf("SHOP", "Rice  410.00", "TOTAL AMT DUE  410.00"), today).total)
        val paid = ReceiptParser.parse(listOf("SHOP", "Milk  250.00", "AMT PAID  300.00", "CHANGE  50.00"), today)
        assertEquals(BigDecimal("250.00"), paid.total)
        assertEquals("Amt paid less change", paid.totalLabel)
    }

    @Test fun vatInclusiveTotalIsStillTheTotal() {
        val r = ReceiptParser.parse(listOf("SHOP", "Soap  98.00", "Shampoo  176.00", "TOTAL (VAT INCLUSIVE)  274.00", "VAT  29.36"), today)
        assertEquals(BigDecimal("274.00"), r.total)
        assertEquals(TotalConfidence.LABELLED, r.totalConfidence)
        assertEquals(BigDecimal("274.00"), ReceiptParser.parse(listOf("SHOP", "Amount due incl. VAT  274.00"), today).total)
    }

    @Test fun cashLessChangeConfirmsAnUnlabelledTotal() {
        val r = ReceiptParser.parse(listOf("MARKET", "Mangoes  120.00", "Bananas  45.00", "165.00", "CASH  500.00", "CHANGE  335.00"), today)
        assertEquals(BigDecimal("165.00"), r.total)
        assertEquals(TotalConfidence.LABELLED, r.totalConfidence)
        assertEquals("Cash less change", r.totalLabel)
    }

    @Test fun withoutATotalTheChoicesAreNeverCashChangeOrTax() {
        val lines = listOf("MARKET", "Mangoes  120.00", "Bananas  45.00", "CASH  500.00", "CHANGE  335.00", "VATable Sales  147.32", "VAT  17.68")
        val c = ReceiptCandidates.from(lines, context)!!
        assertNull(c.amount)
        assertTrue(CandidateFlag.TOTAL_UNCONFIRMED in c.flags)
        // Cash less change is offered first: what was actually paid.
        assertEquals(BigDecimal("165.00"), c.amountOptions.first())
        listOf("500.00", "335.00", "147.32", "17.68").forEach { assertFalse(it, BigDecimal(it) in c.amountOptions) }
    }

    @Test fun aPaymentMethodLineWithoutAnAmountIsSkipped() {
        val lines = listOf("PAYMENT TYPE: CASH", "Item  80.00", "Item  40.00", "AMOUNT TENDERED  200.00", "CHANGE  80.00")
        assertEquals(BigDecimal("120.00"), ReceiptParser.cashLessChange(lines, 2))
    }

    @Test fun cashBackIsNotMoneyHandedOver() {
        assertNull(ReceiptParser.cashLessChange(listOf("CASH BACK  50.00", "CHANGE  10.00"), 2))
    }
}
