package com.lucento.app.core.money

import com.lucento.app.core.money.MoneyParser.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** P0-3: amounts are read the same way on every phone, ambiguity is reported, and nothing is ever multiplied. */
class MoneyParserTest {

    private val enUS = Locale.US
    private val enGB = Locale.UK
    private val enPH = Locale("en", "PH")
    private val deDE = Locale.GERMANY
    private val frFR = Locale.FRANCE
    private val esES = Locale("es", "ES")
    private val ptBR = Locale("pt", "BR")
    private val enIN = Locale("en", "IN")
    private val locales = listOf(enUS, enGB, enPH, deDE, frFR, esES, ptBR, enIN)

    private fun sep(locale: Locale) = MoneyFormatter.decimalSeparator(locale)
    private fun parse(input: String, currency: String = "USD", locale: Locale = enUS) = MoneyParser.parse(input, currency, sep(locale), locale)
    private fun minor(input: String, currency: String = "USD", locale: Locale = enUS): Long {
        val r = parse(input, currency, locale)
        assertTrue("\"$input\" $currency $locale gave $r", r is Result.Success)
        return (r as Result.Success).money.amountMinor
    }
    private fun ambiguous(input: String, currency: String, locale: Locale): List<Long> {
        val r = parse(input, currency, locale)
        assertTrue("\"$input\" $currency $locale should be ambiguous, gave $r", r is Result.Ambiguous)
        return (r as Result.Ambiguous).options.map { it.amountMinor }
    }

    @Test
    fun `the locale separators assumed by these tests`() {
        listOf(enUS, enGB, enPH, enIN).forEach { assertEquals('.', sep(it)) }
        listOf(deDE, frFR, esES, ptBR).forEach { assertEquals(',', sep(it)) }
    }

    // ------------------------------------------------------------------ the original defect

    @Test
    fun `12,50 is twelve fifty on every phone - never 1,250`() {
        locales.forEach { locale ->
            assertEquals("12,50 in $locale", 1250L, minor("12,50", "EUR", locale))
            assertEquals("12,50 in $locale", 1250L, minor("12,50", "USD", locale))
            assertNotEquals(125000L, minor("12,50", "PHP", locale))
        }
    }

    @Test
    fun `12 point 50 is twelve fifty on every phone - never 1,250`() {
        locales.forEach { locale -> assertEquals("12.50 in $locale", 1250L, minor("12.50", "EUR", locale)) }
    }

    @Test
    fun `a decimal comma or dot with one or two digits is always a decimal`() {
        locales.forEach { locale ->
            assertEquals(123L, minor("1,23", "USD", locale))
            assertEquals(120L, minor("1,2", "USD", locale))
            assertEquals(123L, minor("1.23", "USD", locale))
            assertEquals(120L, minor("1.2", "USD", locale))
        }
    }

    // ------------------------------------------------------------------ basics

    @Test
    fun `basic inputs`() {
        assertEquals(0L, minor("0"))
        assertEquals(100L, minor("1"))
        assertEquals(1200L, minor("12"))
        assertEquals(1250L, minor("12.5"))
        assertEquals(1250L, minor("12.50"))
        assertEquals(123400L, minor("1234"))
        assertEquals(1200L, minor("12."))
        assertEquals(50L, minor(".5"))
        assertEquals(50L, minor(",5"))
        assertEquals(1234L, minor("+12.34"))
        assertEquals(700L, minor("007"))
        assertEquals(1250L, minor("12.5000")) // trailing zeros past the precision, and no thousands reading possible
    }

    @Test
    fun `both separators - the last one is the decimal, on every phone`() {
        locales.forEach { locale ->
            assertEquals(123456L, minor("1,234.56", "EUR", locale))
            assertEquals(123456L, minor("1.234,56", "EUR", locale))
            assertEquals(123456789L, minor("1,234,567.89", "EUR", locale))
            assertEquals(123456789L, minor("1.234.567,89", "EUR", locale))
        }
    }

    @Test
    fun `spaces, no-break spaces and apostrophes group thousands`() {
        locales.forEach { locale ->
            assertEquals(123456L, minor("1 234,56", "EUR", locale))
            assertEquals(123456L, minor("1 234.56", "EUR", locale))
            assertEquals(123456L, minor("1 234,56", "EUR", locale))
            assertEquals(123456L, minor("1 234,56", "EUR", locale))
            assertEquals(123456L, minor("1 234,56", "EUR", locale))
            assertEquals(12345000L, minor("123'450", "CHF", locale))
            assertEquals(123450L, minor("1'234.50", "CHF", locale))
            assertEquals(123450L, minor("1’234.50", "CHF", locale))
            assertEquals(100000000L, minor("1 000 000", "EUR", locale))
        }
    }

    @Test
    fun `repeated separators are thousands only where the grouping is valid`() {
        assertEquals(123456700L, minor("1,234,567", "USD", enUS))
        assertEquals(123456700L, minor("1.234.567", "EUR", deDE))
        assertEquals(123456700L, minor("1.234.567", "EUR", frFR))
        // The phone's own decimal mark can't repeat.
        assertEquals(Result.Invalid, parse("1.234.567", "USD", enUS))
        assertEquals(Result.Invalid, parse("1,234,567", "EUR", deDE))
        assertEquals(Result.Invalid, parse("1.2.3", "USD", enUS))
        assertEquals(Result.Invalid, parse("1,2,3", "USD", enUS))
        assertEquals(Result.Invalid, parse("1,2345,678", "USD", enUS))
        assertEquals(Result.Invalid, parse("12,34,5678", "USD", enUS))
        assertEquals(Result.Invalid, parse("1,,234", "USD", enUS))
        assertEquals(Result.Invalid, parse("12 50", "USD", enUS)) // was 1250 before P0-3
        assertEquals(Result.Invalid, parse("1 2345", "USD", enUS))
        assertEquals(Result.Invalid, parse("1 234.567,89", "EUR", deDE))
        assertEquals(Result.Invalid, parse("1 234'567", "CHF", enUS))
    }

    @Test
    fun `Indian lakh grouping`() {
        assertEquals(12345678L, minor("1,23,456.78", "INR", enIN))
        assertEquals(123456700L, minor("12,34,567", "INR", enIN))
        assertEquals(1234567800L, minor("1,23,45,678", "INR", enIN))
        assertEquals(12345678L, minor("1,23,456.78", "INR", enUS))
    }

    // ------------------------------------------------------------------ ambiguity

    @Test
    fun `three digits after the phone's own decimal mark, readable as thousands, is ambiguous`() {
        assertEquals(listOf(100L, 100000L), ambiguous("1.000", "USD", enUS))
        assertEquals(listOf(1250L, 1250000L), ambiguous("12.500", "USD", enUS))
        assertEquals(listOf(100L, 100000L), ambiguous("1,000", "EUR", deDE))
        assertEquals(listOf(350L, 350000L), ambiguous("3,500", "EUR", ptBR))
        assertEquals(listOf(1L, 1000L), ambiguous("1.000", "JPY", enUS))
    }

    @Test
    fun `three digits after the other mark is thousands when the currency can't have three decimals`() {
        assertEquals(100000L, minor("1,000", "USD", enUS))
        assertEquals(123400L, minor("1,234", "USD", enUS))
        assertEquals(1250000L, minor("12,500", "USD", enPH))
        assertEquals(100000L, minor("1.000", "EUR", deDE))
        assertEquals(100000L, minor("1.000", "EUR", frFR))
        assertEquals(1000L, minor("1,000", "JPY", enUS))
        assertEquals(123400L, minor("1.234", "BRL", ptBR))
    }

    @Test
    fun `three-decimal currencies make the other mark ambiguous`() {
        assertEquals(listOf(1234L, 1234000L), ambiguous("1,234", "BHD", enUS))
        assertEquals(listOf(1234L, 1234000L), ambiguous("1.234", "KWD", deDE))
        assertEquals(1234L, minor("1.234", "BHD", enUS)) // the phone's own decimal mark
        assertEquals(1234L, minor("1,234", "BHD", deDE))
    }

    @Test
    fun `a leading zero is never a thousands group`() {
        locales.forEach { locale ->
            assertNotEquals(50000L, (parse("0,500", "USD", locale) as? Result.Success)?.money?.amountMinor)
            assertNotEquals(50000L, (parse("0.500", "USD", locale) as? Result.Success)?.money?.amountMinor)
        }
        assertEquals(50L, minor("0.500", "USD", enUS))
        assertEquals(50L, minor("0,500", "EUR", deDE))
        assertEquals(Result.TooManyDecimals(2), parse("0,500", "USD", enUS))
        assertEquals(Result.TooManyDecimals(2), parse("00,500", "USD", enUS))
        assertEquals(Result.Invalid, parse("0,000,500", "USD", enUS))
        assertEquals(500L, minor("0,500", "BHD", enUS))
    }

    @Test
    fun `space grouping with the other mark keeps every digit`() {
        assertEquals(Result.TooManyDecimals(2), parse("1 000,000", "USD", enUS))
        assertEquals(Result.TooManyDecimals(2), parse("1 234,500", "USD", enUS))
        assertEquals(123450L, minor("1 234.500", "USD", enUS)) // the phone's own mark: zeros past the precision are harmless
        assertEquals(123456L, minor("1 234,56", "USD", enUS))
    }

    @Test
    fun `every way a locale writes the wallet's currency is accepted`() {
        assertEquals(1500L, minor("¥1,500", "JPY", enUS))
        assertEquals(1500L, minor("￥1,500", "JPY", enUS))
        assertEquals(1500L, minor("JP¥1,500", "JPY", enUS))
        assertEquals(12500L, minor("125 kr.", "DKK", Locale("da", "DK")))
        assertEquals(Result.WrongCurrency("JPY"), parse("€1,500", "JPY", enUS))
        assertEquals(150000L, minor("1.500 kr.", "DKK", Locale("da", "DK")))
    }

    @Test
    fun `a one-letter symbol never swallows a k for thousands`() {
        // Kina, kwacha and kyat are written "K": "2k" must not be read as 2 K (it would store 2 for 2,000).
        listOf("PGK", "ZMW", "MMK").forEach { code ->
            assertTrue("2k $code", parse("2k", code) !is Result.Success)
        }
        assertEquals(200L, minor("K2", "PGK", Locale("en", "PG")))
    }

    @Test
    fun `too many decimals is never re-read as thousands`() {
        assertEquals(Result.TooManyDecimals(2), parse("1,234", "EUR", deDE))
        assertEquals(Result.TooManyDecimals(2), parse("1.234", "USD", enUS))
        assertEquals(Result.TooManyDecimals(2), parse("12.345", "USD", enUS))
        assertEquals(Result.TooManyDecimals(2), parse("0,125", "EUR", esES))
        assertEquals(Result.TooManyDecimals(2), parse("1,2345", "USD", enUS))
        assertEquals(Result.TooManyDecimals(2), parse("1 234.567", "USD", enUS))
    }

    @Test
    fun `the clarification names both values and an unambiguous way to type each`() {
        val message = MoneyParser.clarification(parse("1.000", "USD", enUS), '.', enUS)!!
        assertTrue(message, message.contains("$1.00") && message.contains("$1,000.00"))
        assertTrue(message, message.contains("“1”") && message.contains("“1000”"))
        // Each suggestion parses to exactly one value.
        assertEquals(100L, minor("1", "USD", enUS))
        assertEquals(100000L, minor("1000", "USD", enUS))
        val de = MoneyParser.clarification(parse("12,500", "EUR", deDE), ',', deDE)!!
        assertTrue(de, de.contains("“12,50”") && de.contains("“12500”"))
        assertEquals(1250L, minor("12,50", "EUR", deDE))
        val bhd = (parse("1,234", "BHD", enUS) as Result.Ambiguous).options
        assertEquals(listOf("1.234", "1234"), bhd.map { MoneyParser.unambiguous(it, '.') })
        assertNull(MoneyParser.clarification(Result.Invalid, '.'))
    }

    // ------------------------------------------------------------------ currency precision

    @Test
    fun `currency precision`() {
        assertEquals(1500L, minor("1500", "JPY"))
        assertEquals(1500L, minor("1500.", "JPY"))
        assertEquals(Result.TooManyDecimals(0), parse("1500.5", "JPY"))
        assertEquals(Result.TooManyDecimals(0), parse("1500,50", "JPY"))
        assertEquals(1500L, minor("1500.00", "JPY")) // zeros only; still exact
        assertEquals(12500L, minor("12.5", "BHD"))
        assertEquals(12345L, minor("12.345", "BHD"))
        assertEquals(Result.TooManyDecimals(3), parse("12.3456", "BHD"))
        assertEquals(Result.TooManyDecimals(2), parse("12.345", "USD"))
        assertEquals(1234L, minor("12.34", "PHP", enPH))
    }

    // ------------------------------------------------------------------ invalid input

    @Test
    fun `malformed and signed input is rejected`() {
        listOf("", "   ", " ").forEach { assertEquals(Result.Empty, parse(it)) }
        listOf("-5", "−5", "(5)", "5-", "abc", "12abc", "1e5", ".", ",", "+", "++5", "1+2", "12#", "1/2", "١٫٢.٣")
            .forEach { assertEquals("\"$it\"", Result.Invalid, parse(it)) }
    }

    @Test
    fun `currency marks are accepted only for the wallet's own currency`() {
        assertEquals(1250L, minor("$12.50", "USD", enUS))
        assertEquals(1250L, minor("12.50 USD", "USD", enUS))
        assertEquals(1250L, minor("usd 12.50", "USD", enUS))
        assertEquals(1250L, minor("12,50 €", "EUR", deDE))
        assertEquals(1250L, minor("€12,50", "EUR", enUS))
        assertEquals(10000L, minor("₱100", "PHP", enPH))
        assertEquals(10000L, minor("₱ 100", "PHP", enUS))
        assertEquals(Result.WrongCurrency("EUR"), parse("$12", "EUR", deDE))
        assertEquals(Result.WrongCurrency("USD"), parse("£5", "USD", enUS))
        assertEquals(Result.WrongCurrency("PHP"), parse("12 EUR", "PHP", enPH))
        assertEquals(Result.Invalid, parse("$12$", "USD", enUS))
        assertNotNull(MoneyParser.clarification(Result.WrongCurrency("EUR"), ','))
    }

    @Test
    fun `Unicode digits and Arabic separators`() {
        assertEquals(12300L, minor("١٢٣")) // Arabic-Indic
        assertEquals(123400L, minor("१२३४")) // Devanagari
        assertEquals(1250L, minor("١٢٫٥٠")) // Arabic decimal separator, whatever the phone uses
        assertEquals(1250L, minor("١٢٫٥٠", "USD", deDE))
        assertEquals(Result.TooManyDecimals(2), parse("١٫٢٣٤")) // never read as 1,234
        assertEquals(123400L, minor("١٬٢٣٤")) // Arabic thousands separator
        assertEquals(123456L, minor("۱۲۳۴٫۵۶")) // Extended Arabic-Indic (Persian)
    }

    @Test
    fun `huge amounts`() {
        assertEquals(100000000000000L, minor("1000000000000"))
        assertEquals(Result.TooLarge, parse("1000000000000.01"))
        assertEquals(Result.TooLarge, parse("999,999,999,999,999"))
        assertEquals(Result.TooLarge, parse("9".repeat(30)))
        assertEquals(Result.TooLarge, parse("1" + "0".repeat(100)))
        assertEquals(1000000000000000L, minor("1000000000000", "BHD")) // 3-digit currency, still a Long
        assertEquals(1000000000000L, minor("1000000000000", "JPY"))
    }

    // ------------------------------------------------------------------ round trips

    private val samples = listOf(
        Money(0, "USD"), Money(5, "USD"), Money(1250, "USD"), Money(123456, "USD"), Money(100000, "USD"),
        Money(123456789, "EUR"), Money(7, "EUR"), Money(150000, "PHP"), Money(99, "JPY"), Money(1500, "JPY"),
        Money(1_000_000, "JPY"), Money(1234, "BHD"), Money(1, "BHD"), Money(1234567, "KWD"), Money(12345678, "INR"),
        Money(999_999, "BRL"), Money(100_000_000_000_000, "USD"),
    )

    @Test
    fun `input text to minor units to plain text and back is lossless`() {
        samples.forEach { money ->
            (locales + Locale("ar", "EG")).forEach { locale ->
                val text = MoneyFormatter.formatPlain(money, locale)
                assertEquals("$text in $locale", money, MoneyParser.parseOrNull(text, money.currencyCode, sep(locale)))
            }
        }
    }

    @Test
    fun `formatted display text parses back to the same amount`() {
        samples.forEach { money ->
            locales.forEach { locale ->
                val shown = MoneyFormatter.format(money, locale)
                val r = MoneyParser.parse(shown, money.currencyCode, sep(locale), locale)
                assertEquals("\"$shown\" in $locale", Result.Success(money), r)
            }
        }
    }

    @Test
    fun `the composer's review text round trip is lossless`() {
        // BatchReview writes a BigDecimal with the phone's decimal mark and no grouping, then parses it on save.
        listOf("0.001" to "BHD", "1.5" to "USD", "1500" to "JPY", "0" to "USD", "1234.5" to "EUR", "12.5" to "BHD").forEach { (value, code) ->
            locales.forEach { locale ->
                val text = java.math.BigDecimal(value).stripTrailingZeros().toPlainString().replace('.', sep(locale))
                val expected = java.math.BigDecimal(value).movePointRight(CurrencyRules.fractionDigits(code)).longValueExact()
                assertEquals("$text $code $locale", expected, minor(text, code, locale))
            }
        }
    }

    // ------------------------------------------------------------------ the shared reader (composer)

    @Test
    fun `the shared reader gives one value, a choice, or nothing`() {
        assertEquals(MoneyParser.Reading.Value(java.math.BigDecimal("12.50")), MoneyParser.readNumber("12,50", '.', 2))
        assertEquals(MoneyParser.Reading.Value(java.math.BigDecimal("1500")), MoneyParser.readNumber("1,500", '.', 2))
        assertTrue(MoneyParser.readNumber("1.500", '.', 2) is MoneyParser.Reading.Choice)
        assertEquals(MoneyParser.Reading.TooPrecise, MoneyParser.readNumber("1.234", '.', 2))
    }
}
