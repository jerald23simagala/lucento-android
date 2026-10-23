package com.lucento.app.core.money

import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object MoneyFormatter {

    /** Locale-aware currency string, e.g. "$1,234.56" (en-US) or "1.234,56 €" (de-DE). */
    fun format(money: Money, locale: Locale = Locale.getDefault()): String {
        val nf = NumberFormat.getCurrencyInstance(locale)
        runCatching { nf.currency = Currency.getInstance(money.currencyCode) }
        nf.minimumFractionDigits = money.fractionDigits
        nf.maximumFractionDigits = money.fractionDigits
        return nf.format(money.toBigDecimal())
    }

    /**
     * Formats with an explicit, typographic sign so direction never depends on colour alone:
     * "−$12.50" (U+2212 minus) for negative values and, when [showPlus], "+$12.50" for positive ones.
     * Zero is shown unsigned.
     */
    fun formatSigned(money: Money, showPlus: Boolean = true, locale: Locale = Locale.getDefault()): String {
        val body = format(money.abs(), locale)
        return when {
            money.isNegative -> "$MINUS$body"
            showPlus && !money.isZero -> "+$body"
            else -> body
        }
    }

    /**
     * Locates the decimal separator and fraction digits inside [formatted] (output of [format] or
     * [formatSigned]) so the UI can typeset cents smaller than whole units. Returns the half-open
     * index range `[start, end)` covering separator + digits, or null when the currency has no minor
     * unit or the pattern can't be found reliably.
     */
    fun fractionSpan(formatted: String, fractionDigits: Int, locale: Locale = Locale.getDefault()): IntRange? {
        if (fractionDigits <= 0) return null
        val separator = DecimalFormatSymbols.getInstance(locale).monetaryDecimalSeparator
        var index = formatted.lastIndexOf(separator)
        while (index >= 0) {
            val end = index + 1 + fractionDigits
            val digitsFollow = end <= formatted.length &&
                (index + 1 until end).all { Character.isDigit(formatted[it]) } &&
                (end == formatted.length || !Character.isDigit(formatted[end]))
            if (digitsFollow) return index until end
            index = if (index == 0) -1 else formatted.lastIndexOf(separator, index - 1)
        }
        return null
    }

    /** U+2212 MINUS SIGN: same width as "+" in Plus Jakarta Sans, unlike the hyphen-minus. */
    const val MINUS = "−"

    /** Plain number without currency symbol, suitable for pre-filling an input field. */
    fun formatPlain(money: Money, locale: Locale = Locale.getDefault()): String {
        val nf = NumberFormat.getNumberInstance(locale)
        nf.isGroupingUsed = false
        nf.minimumFractionDigits = money.fractionDigits
        nf.maximumFractionDigits = money.fractionDigits
        return nf.format(money.toBigDecimal())
    }

    fun decimalSeparator(locale: Locale = Locale.getDefault()): Char {
        val sep = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return if (sep == ',') ',' else '.'
    }

    fun currencySymbol(currencyCode: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(currencyCode).getSymbol(locale) }.getOrDefault(currencyCode)

    fun currencyName(currencyCode: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(currencyCode).getDisplayName(locale) }.getOrDefault(currencyCode)
}
