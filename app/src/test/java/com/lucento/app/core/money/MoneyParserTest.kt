package com.lucento.app.core.money

import com.lucento.app.core.money.MoneyParser.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyParserTest {

    private fun ok(input: String, currency: String = "USD", sep: Char = '.') =
        (MoneyParser.parse(input, currency, sep) as Result.Success).money.amountMinor

    @Test
    fun `parses common dot-decimal inputs`() {
        assertEquals(1234L, ok("12.34"))
        assertEquals(1250L, ok("12.5"))
        assertEquals(1200L, ok("12"))
        assertEquals(1200L, ok("12."))
        assertEquals(50L, ok(".5"))
        assertEquals(123456789L, ok("1,234,567.89"))
        assertEquals(1234L, ok("  12.34  "))
        assertEquals(1234L, ok("+12.34"))
    }

    @Test
    fun `parses comma-decimal locales`() {
        assertEquals(123456L, ok("1.234,56", "EUR", ','))
        assertEquals(1250L, ok("12,5", "EUR", ','))
        assertEquals(123456L, ok("1 234,56", "EUR", ','))
    }

    @Test
    fun `respects currency precision`() {
        assertEquals(1500L, ok("1500", "JPY"))
        assertEquals(Result.TooManyDecimals(0), MoneyParser.parse("1500.5", "JPY"))
        assertEquals(1234L, ok("1.234", "BHD"))
        assertEquals(Result.TooManyDecimals(2), MoneyParser.parse("12.345", "USD"))
        // Trailing zeros beyond the precision are harmless.
        assertEquals(1250L, ok("12.500"))
    }

    @Test
    fun `rejects invalid input`() {
        assertEquals(Result.Empty, MoneyParser.parse("", "USD"))
        assertEquals(Result.Empty, MoneyParser.parse("   ", "USD"))
        assertEquals(Result.Invalid, MoneyParser.parse("-5", "USD"))
        assertEquals(Result.Invalid, MoneyParser.parse("abc", "USD"))
        assertEquals(Result.Invalid, MoneyParser.parse("1.2.3", "USD"))
        assertEquals(Result.Invalid, MoneyParser.parse(".", "USD"))
        assertEquals(Result.Invalid, MoneyParser.parse("1e5", "USD"))
    }

    @Test
    fun `rejects absurdly large amounts`() {
        assertEquals(Result.TooLarge, MoneyParser.parse("1000000000000.01", "USD"))
        assertEquals(100000000000000L, ok("1000000000000"))
    }
}
