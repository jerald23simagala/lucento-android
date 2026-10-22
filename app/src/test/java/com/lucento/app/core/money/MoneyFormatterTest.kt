package com.lucento.app.core.money

import org.junit.Assert.assertEquals
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
}
