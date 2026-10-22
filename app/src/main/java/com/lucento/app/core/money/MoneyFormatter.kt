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
