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
     * A hidden amount, for screens where the user hid their figures (owner, 2026-11-04): the currency written as usual,
     * the figure replaced by [HIDDEN_FIGURE] ("₱••••", "•••• €"). The same for every amount, so nothing about its size,
     * sign or precision shows.
     */
    fun masked(currencyCode: String, locale: Locale = Locale.getDefault()): String =
        format(Money.zero(currencyCode), locale).replace(DIGIT_RUN, HIDDEN_FIGURE)

    const val HIDDEN_FIGURE = "••••"

    /**
     * [text] with every amount written next to a currency hidden ("You spent ₱1,234.50" → "You spent ₱••••"): a currency
     * symbol or ISO code before or after a figure. For prose that quotes amounts (Ask Lucento's answers) while the user
     * has hidden them. Dates, counts and percentages carry no currency and are left alone.
     */
    fun maskAmountsIn(text: String): String =
        text.replace(CURRENCY_BEFORE) { it.groupValues[1] + it.groupValues[2] + HIDDEN_FIGURE }
            .replace(CURRENCY_AFTER) { HIDDEN_FIGURE + it.groupValues[1] + it.groupValues[2] }

    // An ISO code is three capitals not inside a longer word ("PHP6,567.00", "1.234,56 EUR").
    private val CURRENCY_BEFORE =
        Regex("([−\\-+]?(?:\\p{Sc}|(?<![A-Za-z])[A-Z]{3}(?![A-Za-z])))(\\s?)\\d(?:[\\d\\s  .,'’]*\\d)?")
    private val CURRENCY_AFTER = Regex("\\d(?:[\\d  .,'’]*\\d)?(\\s?)(\\p{Sc}|(?<![A-Za-z])[A-Z]{3}(?![A-Za-z]))")

    /** A formatted figure: digits with their grouping and decimal marks (spaces, NBSP, narrow NBSP, apostrophes). */
    private val DIGIT_RUN = Regex("\\d[\\d\\s\u00A0\u202F.,'\u2019]*\\d|\\d")

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

    /**
     * Plain number without currency symbol or grouping, suitable for pre-filling an input field: ASCII digits and the
     * same decimal mark [decimalSeparator] gives the parser, so a prefilled amount always parses back to itself (P0-3).
     */
    fun formatPlain(money: Money, locale: Locale = Locale.getDefault()): String =
        money.toBigDecimal().toPlainString().replace('.', decimalSeparator(locale))

    fun decimalSeparator(locale: Locale = Locale.getDefault()): Char {
        val sep = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return if (sep == ',') ',' else '.'
    }

    fun currencySymbol(currencyCode: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(currencyCode).getSymbol(locale) }.getOrDefault(currencyCode)

    fun currencyName(currencyCode: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(currencyCode).getDisplayName(locale) }.getOrDefault(currencyCode)
}
