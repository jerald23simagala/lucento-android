package com.lucento.app.domain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Currency-aware amount extraction (audit finding 1.5). */
class ReceiptCurrencyTest {

    private val today = LocalDate.parse("2026-10-22")

    @Test fun zeroDecimalCurrencyReadsGroupedIntegers() {
        val r = ReceiptParser.parse(listOf("ラーメン屋", "Ramen  ¥980", "Gyoza  ¥450", "合計 TOTAL ¥1,430"), today, expectedCurrency = "JPY")
        assertEquals(BigDecimal("1430"), r.total)
        assertEquals(0, r.fractionDigits)
        assertEquals(TotalConfidence.LABELLED, r.totalConfidence)
    }

    @Test fun integerOnlyTotalOnALabelledLine() {
        val r = ReceiptParser.parse(listOf("Sari-sari Store", "Bread 45", "Eggs 120", "TOTAL 165"), today, expectedCurrency = "PHP")
        assertEquals(BigDecimal("165.00"), r.total)
        assertEquals(TotalConfidence.LABELLED, r.totalConfidence)
    }

    @Test fun unlabelledIntegersAreNotGuessedAsTheTotal() {
        val r = ReceiptParser.parse(listOf("Store", "Item 45", "Item 120"), today, expectedCurrency = "PHP")
        assertNull(r.total)
        assertEquals(TotalConfidence.NONE, r.totalConfidence)
    }

    @Test fun threeDecimalCurrencyFromTheReceipt() {
        val r = ReceiptParser.parse(listOf("Gulf Mart", "Milk  KD 0.750", "Rice  KD 2.250", "TOTAL  KD 3.000"), today, expectedCurrency = "USD")
        assertEquals("KWD", r.currencyCode)
        assertEquals(3, r.fractionDigits)
        assertEquals(BigDecimal("3.000"), r.total)
        assertTrue(BigDecimal("0.750") in r.amounts)
    }

    @Test fun threeDecimalCurrencyWithThousands() {
        assertEquals(listOf(BigDecimal("1250.500")), ReceiptParser.amountsIn("Total BHD 1,250.500", 3))
        assertEquals(listOf(BigDecimal("12.500")), ReceiptParser.amountsIn("Total 12.500", 3))
        assertEquals(listOf(BigDecimal("1250.000")), ReceiptParser.amountsIn("Total 1,250", 3))
    }

    @Test fun groupsOfThreeAreThousandsForTwoAndZeroDecimalCurrencies() {
        assertEquals(listOf(BigDecimal("1250.00")), ReceiptParser.amountsIn("Total 1,250", 2))
        assertEquals(listOf(BigDecimal("12500")), ReceiptParser.amountsIn("Total 12.500", 0)) // IDR / VND style
        assertEquals(listOf(BigDecimal("1234567.89")), ReceiptParser.amountsIn("TOTAL 1.234.567,89", 2))
        assertEquals(listOf(BigDecimal("1234.50")), ReceiptParser.amountsIn("Total 1'234.50", 2)) // Swiss
    }

    @Test fun decimalsOnZeroDecimalCurrencies() {
        assertEquals(listOf(BigDecimal("1250")), ReceiptParser.amountsIn("Total 1,250.00", 0))
        assertTrue(ReceiptParser.amountsIn("Rate 3.75", 0).isEmpty())
    }

    @Test fun datesTimesPhonesAndIdsAreNotAmounts() {
        assertTrue(ReceiptParser.amountsIn("Date 2026-10-21 08:14", 2, allowIntegers = true).isEmpty())
        assertTrue(ReceiptParser.amountsIn("21/10/2026 8:14 PM", 2, allowIntegers = true).isEmpty())
        assertTrue(ReceiptParser.amountsIn("Tel: (02) 8123-4567", 2, allowIntegers = true).isEmpty())
        assertTrue(ReceiptParser.amountsIn("Tel 0917 123 4567", 2, allowIntegers = true).isEmpty())
        assertTrue(ReceiptParser.amountsIn("VAT REG TIN 000-111-222-000", 2, allowIntegers = true).isEmpty())
        assertTrue(ReceiptParser.amountsIn("OR# 0012345", 2, allowIntegers = true).isEmpty())
        assertTrue(ReceiptParser.amountsIn("VAT 12%", 2, allowIntegers = true).isEmpty())
        assertEquals(listOf(BigDecimal("45.00")), ReceiptParser.amountsIn("2 x 45.00", 2))
        assertEquals(listOf(BigDecimal("150.00")), ReceiptParser.amountsIn("Latte 2 150.00", 2))
    }

    @Test fun totalIgnoresSubtotalTaxAndChange() {
        val lines = listOf(
            "Corner Bistro", "Tel 8123 4567", "2026-10-20 19:02",
            "Subtotal 1,200.00", "Service charge 10% 120.00", "VAT 144.00", "TOTAL 1,464.00", "Cash 2,000.00", "Change 536.00",
        )
        val r = ReceiptParser.parse(lines, today, "PHP")
        assertEquals(BigDecimal("1464.00"), r.total)
        assertEquals(LocalDate.parse("2026-10-20"), r.date)
    }

    @Test fun currencyDetection() {
        assertEquals("PHP", ReceiptParser.detectCurrency(listOf("TOTAL ₱1,464.00")))
        assertEquals("EUR", ReceiptParser.detectCurrency(listOf("Summe 12,30 €")))
        assertEquals("USD", ReceiptParser.detectCurrency(listOf("Total US$ 12.30")))
        assertEquals("SGD", ReceiptParser.detectCurrency(listOf("Total S$ 12.30")))
        assertEquals("MYR", ReceiptParser.detectCurrency(listOf("Jumlah RM 12.30")))
        assertNull(ReceiptParser.detectCurrency(listOf("Total $12.30"))) // $ alone is ambiguous
        assertNull(ReceiptParser.detectCurrency(listOf("Total ¥1,200"))) // JPY or CNY
        assertNull(ReceiptParser.detectCurrency(listOf("TOTAL USD 10.00", "TOTAL PHP 560.00"))) // two currencies
        assertNull(ReceiptParser.detectCurrency(listOf("ALL ITEMS ON SALE", "TOP 10"))) // not currency words
    }

    @Test fun detectedCurrencyOverridesTheExpectedOneForDecimals() {
        val r = ReceiptParser.parse(listOf("Shop", "TOTAL ₩12,000"), today, expectedCurrency = "USD")
        assertEquals("KRW", r.currencyCode)
        assertEquals(BigDecimal("12000"), r.total)
    }

    @Test fun missingInformationStaysMissing() {
        val r = ReceiptParser.parse(listOf("THANK YOU", "Come again"), today, "EUR")
        assertNull(r.total)
        assertNull(r.currencyCode)
        assertNull(r.date)
    }
}
