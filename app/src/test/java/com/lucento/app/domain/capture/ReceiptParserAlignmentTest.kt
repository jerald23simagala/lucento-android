package com.lucento.app.domain.capture

import com.lucento.app.core.money.MoneyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * P0-3 follow-up: receipts keep one deliberate convention of their own (a lone mark followed by three digits is a
 * thousands separator — "Rp 25.000" — unless the currency has three decimals), but every value they produce must be a
 * reading [MoneyParser] itself allows under one of the two separator conventions. Nothing a receipt yields can be a
 * value the central rules would never produce (×10, ×100, ×1000, or more decimals than the currency has).
 */
class ReceiptParserAlignmentTest {

    private fun centralReadings(token: String, digits: Int): Set<BigDecimal> =
        listOf('.', ',').flatMap { sep ->
            when (val r = MoneyParser.readNumber(token, sep, digits)) {
                is MoneyParser.Reading.Value -> listOf(r.value)
                is MoneyParser.Reading.Choice -> r.values
                else -> emptyList()
            }
        }.map { it.stripTrailingZeros() }.toSet()

    private val tokens = listOf(
        "12.50", "12,50", "1,250", "1.250", "25.000", "25,000", "1,250.50", "1.250,50", "1,234,567.89", "1.234.567,89",
        "0,500", "0.500", "1.5", "12.345", "1'234.50", "1 234,56", "999", "100.00", "0.05", "12.5000",
    )

    @Test
    fun `every receipt reading is one the central parser allows`() {
        var compared = 0
        listOf(0, 2, 3).forEach { digits ->
            tokens.forEach { token ->
                val receipt = ReceiptParser.interpret(token, digits, allowIntegers = true) ?: return@forEach
                val allowed = centralReadings(token, digits)
                assertTrue("receipt read \"$token\" ($digits decimals) as $receipt; central readings $allowed",
                    receipt.stripTrailingZeros() in allowed)
                compared++
            }
        }
        // Not vacuous: a receipt parser that refused everything would otherwise pass (QA test gap).
        assertTrue("only $compared receipt readings were compared", compared >= 30)
    }

    @Test
    fun `the receipt convention is limited to what it was made for`() {
        // Thousands on a receipt (the documented difference): "Rp 25.000" is 25,000, "1,250" is 1,250.
        assertEquals(BigDecimal("25000.00"), ReceiptParser.interpret("25.000", 2, allowIntegers = true))
        assertEquals(BigDecimal("1250.00"), ReceiptParser.interpret("1,250", 2, allowIntegers = true))
        // Three-decimal currencies read the dot as decimals.
        assertEquals(BigDecimal("1.250"), ReceiptParser.interpret("1.250", 3, allowIntegers = true))
        // Never ×1000 behind a leading zero; never more decimals than the currency has.
        assertEquals(BigDecimal("0.50"), ReceiptParser.interpret("0,500", 2, allowIntegers = true))
        assertNull(ReceiptParser.interpret("1.2345", 2, allowIntegers = true))
    }
}
