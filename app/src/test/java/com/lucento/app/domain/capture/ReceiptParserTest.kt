package com.lucento.app.domain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReceiptParserTest {

    private val today = LocalDate.parse("2026-10-22")

    @Test fun readsLabelledTotalMerchantAndDate() {
        val lines = listOf(
            "SUNRISE COFFEE CO.",
            "123 Rizal Ave, Makati",
            "VAT REG TIN 000-111-222",
            "Date: 2026-10-21 08:14",
            "Latte            150.00",
            "Croissant         95.00",
            "SUBTOTAL         245.00",
            "VAT 12%           29.40",
            "TOTAL            274.40",
            "CASH             500.00",
            "CHANGE           225.60",
        )
        val r = ReceiptParser.parse(lines, today)
        assertEquals("Sunrise Coffee Co.", r.merchant)
        assertEquals(BigDecimal("274.40"), r.total)
        assertEquals(TotalConfidence.LABELLED, r.totalConfidence)
        assertEquals(LocalDate.parse("2026-10-21"), r.date)
        assertEquals(false, r.dateAmbiguous)
    }

    @Test fun grandTotalBeatsTotalAndAmountMayBeOnNextLine() {
        val lines = listOf("Shop", "Total items 3", "Total 1,000.00", "Grand Total", "1,120.00")
        val r = ReceiptParser.parse(lines, today)
        assertEquals(BigDecimal("1120.00"), r.total)
    }

    @Test fun fallsBackToLargestAmountAndSaysSo() {
        val r = ReceiptParser.parse(listOf("Market", "Apples 45.50", "Bread 120.00"), today)
        assertEquals(BigDecimal("120.00"), r.total)
        assertEquals(TotalConfidence.LARGEST_AMOUNT, r.totalConfidence)
    }

    @Test fun nothingReadable() {
        val r = ReceiptParser.parse(listOf("~~~", ""), today)
        assertNull(r.total)
        assertNull(r.merchant)
        assertEquals(TotalConfidence.NONE, r.totalConfidence)
    }

    @Test fun datesInSeveralFormats() {
        assertEquals(LocalDate.parse("2026-03-26"), ReceiptParser.findDate(listOf("Mar 26, 2026"), today).first)
        assertEquals(LocalDate.parse("2026-03-26"), ReceiptParser.findDate(listOf("26 March 2026"), today).first)
        assertEquals(LocalDate.parse("2026-03-26"), ReceiptParser.findDate(listOf("26/03/2026"), today).first)
        assertEquals(LocalDate.parse("2026-03-26"), ReceiptParser.findDate(listOf("03/26/26"), today).first)
        val ambiguous = ReceiptParser.findDate(listOf("10/09/2026"), today)
        assertTrue(ambiguous.second)
        assertEquals(LocalDate.parse("2026-10-09"), ambiguous.first) // closest to today
        assertNull(ReceiptParser.findDate(listOf("01/01/2030"), today).first) // future rejected
    }

    @Test fun europeanAmounts() {
        assertEquals(listOf(BigDecimal("1234.50")), ReceiptParser.amountsIn("Summe 1.234,50"))
    }

    @Test fun rebuildsColumnsIntoRows() {
        val fragments = listOf(
            TextFragment("274.40", 300, 402, 360, 422),
            TextFragment("SUNRISE COFFEE", 40, 10, 260, 34),
            TextFragment("TOTAL", 20, 400, 90, 420),
            TextFragment("245.00", 300, 370, 360, 390),
            TextFragment("SUBTOTAL", 20, 371, 120, 391),
        )
        val rows = ReceiptParser.mergeRows(fragments)
        assertEquals(listOf("SUNRISE COFFEE", "SUBTOTAL  245.00", "TOTAL  274.40"), rows)
        assertEquals(BigDecimal("274.40"), ReceiptParser.parse(rows, today).total)
    }
}
