package com.lucento.app.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyFormatterTest {

    @Test
    fun `formats with currency precision`() {
        assertEquals("$1,234.56", MoneyFormatter.format(Money(123456, "USD"), Locale.US))
        assertEquals("$0.05", MoneyFormatter.format(Money(5, "USD"), Locale.US))
        assertEquals("¥1,234", MoneyFormatter.format(Money(1234, "JPY"), Locale.US))
    }

    @Test
    fun `plain format round-trips through the parser`() {
        listOf(Money(123456, "USD"), Money(7, "EUR"), Money(1234, "BHD"), Money(99, "JPY")).forEach { money ->
            listOf(Locale.US, Locale.GERMANY, Locale.FRANCE).forEach { locale ->
                val text = MoneyFormatter.formatPlain(money, locale)
                val parsed = MoneyParser.parseOrNull(text, money.currencyCode, MoneyFormatter.decimalSeparator(locale))
                assertEquals("$text in $locale", money, parsed)
            }
        }
    }

    @Test
    fun `signed format uses a typographic minus and optional plus`() {
        assertEquals("−$12.50", MoneyFormatter.formatSigned(Money(-1250, "USD"), locale = Locale.US))
        assertEquals("+$12.50", MoneyFormatter.formatSigned(Money(1250, "USD"), locale = Locale.US))
        assertEquals("$12.50", MoneyFormatter.formatSigned(Money(1250, "USD"), showPlus = false, locale = Locale.US))
        assertEquals("$0.00", MoneyFormatter.formatSigned(Money(0, "USD"), locale = Locale.US))
    }

    @Test
    fun `fraction span covers separator and minor digits`() {
        val us = MoneyFormatter.format(Money(123456, "USD"), Locale.US) // $1,234.56
        assertEquals(".56", us.substring(MoneyFormatter.fractionSpan(us, 2, Locale.US)!!))

        val signed = MoneyFormatter.formatSigned(Money(-5, "USD"), locale = Locale.US) // −$0.05
        assertEquals(".05", signed.substring(MoneyFormatter.fractionSpan(signed, 2, Locale.US)!!))

        val de = MoneyFormatter.format(Money(123456, "EUR"), Locale.GERMANY) // 1.234,56 €
        assertEquals(",56", de.substring(MoneyFormatter.fractionSpan(de, 2, Locale.GERMANY)!!))

        val bhd = MoneyFormatter.format(Money(1234567, "BHD"), Locale.US)
        assertEquals(".567", bhd.substring(MoneyFormatter.fractionSpan(bhd, 3, Locale.US)!!))
    }

    @Test
    fun `fraction span is absent for zero-decimal currencies`() {
        val jpy = MoneyFormatter.format(Money(1234, "JPY"), Locale.US)
        assertNull(MoneyFormatter.fractionSpan(jpy, 0, Locale.US))
    }

    // Settings › Money › "Show cents" off (tester round, 2026-11-17): whole units, half-up on the absolute value.

    @Test
    fun `whole units round half-up on the absolute value and keep the sign`() {
        assertEquals("$1,235", MoneyFormatter.format(Money(123450, "USD"), Locale.US, wholeUnits = true))
        assertEquals("$1,234", MoneyFormatter.format(Money(123449, "USD"), Locale.US, wholeUnits = true))
        assertEquals("−$13", MoneyFormatter.formatSigned(Money(-1250, "USD"), locale = Locale.US, wholeUnits = true))
        assertEquals("+$13", MoneyFormatter.formatSigned(Money(1250, "USD"), locale = Locale.US, wholeUnits = true))
        assertEquals("$12", MoneyFormatter.formatSigned(Money(1249, "USD"), showPlus = false, locale = Locale.US, wholeUnits = true))
        assertEquals("¥1,234", MoneyFormatter.format(Money(1234, "JPY"), Locale.US, wholeUnits = true))
        assertTrue(MoneyFormatter.format(Money(1234567, "BHD"), Locale.US, wholeUnits = true).endsWith("1,235"))
        assertTrue(MoneyFormatter.format(Money(123450, "EUR"), Locale.GERMANY, wholeUnits = true).startsWith("1.235"))
    }

    @Test
    fun `an amount that rounds to zero shows no sign`() {
        assertEquals("$0", MoneyFormatter.formatSigned(Money(-40, "USD"), locale = Locale.US, wholeUnits = true))
        assertEquals("$0", MoneyFormatter.formatSigned(Money(40, "USD"), locale = Locale.US, wholeUnits = true))
        // Half a unit still rounds away from zero, and keeps its sign.
        assertEquals("−$1", MoneyFormatter.formatSigned(Money(-50, "USD"), locale = Locale.US, wholeUnits = true))
    }

    @Test
    fun `whole units leave no fraction span to shrink`() {
        val whole = MoneyFormatter.format(Money(123456, "USD"), Locale.US, wholeUnits = true)
        assertNull(MoneyFormatter.fractionSpan(whole, 0, Locale.US))
        assertNull(MoneyFormatter.fractionSpan(whole, 2, Locale.US))
    }

    @Test
    fun `the saved choice is the default, and exact formatting can still be asked for`() {
        assertFalse(MoneyFormatter.wholeUnits)
        MoneyFormatter.wholeUnits = true
        try {
            assertEquals("$1,235", MoneyFormatter.format(Money(123450, "USD"), Locale.US))
            assertEquals("−$13", MoneyFormatter.formatSigned(Money(-1250, "USD"), locale = Locale.US))
            assertEquals("$1,234.50", MoneyFormatter.format(Money(123450, "USD"), Locale.US, wholeUnits = false))
            // Masking and plain (input) forms never depend on it.
            assertEquals("$••••", MoneyFormatter.masked("USD", Locale.US))
            assertEquals("1234.50", MoneyFormatter.formatPlain(Money(123450, "USD"), Locale.US))
        } finally {
            MoneyFormatter.wholeUnits = false
        }
    }
}
