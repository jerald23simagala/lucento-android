package com.lucento.app.core.money

import java.math.BigDecimal

/**
 * Parses user-entered amounts into minor units without floating point.
 *
 * Accepts either '.' or ',' as the decimal separator (the one passed in), ignores
 * grouping separators, whitespace and a leading currency-free plus sign.
 * Rejects negative input, more fraction digits than the currency allows, and
 * values beyond [MAX_MAJOR_UNITS].
 */
object MoneyParser {

    /** Upper bound for a single entered amount, in major units (one trillion). */
    val MAX_MAJOR_UNITS: BigDecimal = BigDecimal("1000000000000")

    sealed interface Result {
        data class Success(val money: Money) : Result
        data object Empty : Result
        data object Invalid : Result
        data class TooManyDecimals(val allowed: Int) : Result
        data object TooLarge : Result
    }

    fun parse(input: String, currencyCode: String, decimalSeparator: Char = '.'): Result {
        require(decimalSeparator == '.' || decimalSeparator == ',') { "Unsupported decimal separator" }
        val groupingSeparator = if (decimalSeparator == '.') ',' else '.'

        val compact = input
            .filterNot { it.isWhitespace() || it == ' ' || it == ' ' || it == '\'' }
            .removePrefix("+")
        if (compact.isEmpty()) return Result.Empty

        val normalized = compact
            .replace(groupingSeparator.toString(), "")
            .replace(decimalSeparator, '.')

        if (!PATTERN.matches(normalized)) return Result.Invalid

        val raw = BigDecimal(if (normalized.startsWith(".")) "0$normalized" else normalized)
        val digits = CurrencyRules.fractionDigits(currencyCode)
        // "12.500" is acceptable for a 2-digit currency; "12.505" is not.
        val value = if (raw.scale() > digits) raw.stripTrailingZeros() else raw
        if (value.scale() > digits) return Result.TooManyDecimals(digits)
        if (value > MAX_MAJOR_UNITS) return Result.TooLarge

        val minor = value.movePointRight(digits).longValueExact()
        return Result.Success(Money(minor, currencyCode))
    }

    fun parseOrNull(input: String, currencyCode: String, decimalSeparator: Char = '.'): Money? =
        (parse(input, currencyCode, decimalSeparator) as? Result.Success)?.money

    private val PATTERN = Regex("""^(\d+(\.\d*)?|\.\d+)$""")
}
