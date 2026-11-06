package com.lucento.app.core.money

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * An exchange rate: how many units of [to] (in major units) one unit of [from] is worth — "1 USD = 56.1234 PHP" is
 * FxRate("USD", "PHP", 56.1234). Exact decimal, never floating point. Conversions round once, half-even, to the target
 * currency's minor units (docs/CALCULATIONS.md "Exchange rates").
 */
data class FxRate(val from: String, val to: String, val perUnit: BigDecimal) {

    init {
        require(from.length == 3 && to.length == 3) { "Currency codes are ISO 4217" }
        require(perUnit.signum() > 0) { "A rate is positive" }
    }

    /** [amount] (in [from]) in [to], rounded half-even to [to]'s minor units. Overflow throws. */
    fun convert(amount: Money): Money {
        require(amount.currencyCode == from) { "Expected $from, got ${amount.currencyCode}" }
        return Money(toMinor(amount.toBigDecimal().multiply(perUnit), to), to)
    }

    /** For display: "56.1234" — at most [MAX_SHOWN_DECIMALS] decimals, no trailing zeros. */
    fun display(): String = displayRate(perUnit)

    /** The rate the other way round (1 [to] = x [from]). */
    fun inverse(): FxRate = FxRate(to, from, BigDecimal.ONE.divide(perUnit, PRECISION))

    companion object {
        /** Significant digits kept for a rate: plenty for IDR→KWD and back. */
        val PRECISION: MathContext = MathContext(12, RoundingMode.HALF_EVEN)
        const val MAX_SHOWN_DECIMALS = 6

        /** A rate as stored ("56.1234"): canonical, exact, no exponent. */
        fun encode(rate: BigDecimal): String = rate.round(PRECISION).stripTrailingZeros().toPlainString()

        /** A stored or typed rate; null when it isn't a positive decimal within range. */
        fun parse(text: String?): BigDecimal? {
            val cleaned = text?.trim()?.replace(",", "")?.takeIf { it.isNotEmpty() } ?: return null
            if (!cleaned.matches(Regex("""\d{1,9}(\.\d{1,12})?|\.\d{1,12}"""))) return null
            val value = cleaned.toBigDecimalOrNull() ?: return null
            return value.takeIf { it.signum() > 0 }?.round(PRECISION)
        }

        /**
         * The rate two real amounts imply — what a transfer between currencies actually paid: [paid] in one currency
         * bought [received] in another. 1 unit of paid's currency = received ÷ paid.
         */
        fun implied(paid: Money, received: Money): FxRate {
            require(!paid.isZero && !received.isZero) { "Both amounts are needed" }
            val perUnit = received.toBigDecimal().abs().divide(paid.toBigDecimal().abs(), PRECISION)
            return FxRate(paid.currencyCode, received.currencyCode, perUnit)
        }

        /**
         * [value] in [currency]'s minor units, rounded half-even. Overflow throws [ArithmeticException].
         * BigDecimal.longValueExact, not BigInteger's: Android has the BigInteger one only from API 31 (min is 26).
         */
        fun toMinor(value: BigDecimal, currency: String): Long {
            val digits = CurrencyRules.fractionDigits(currency)
            return value.setScale(digits, RoundingMode.HALF_EVEN).movePointRight(digits).longValueExact()
        }

        fun displayRate(rate: BigDecimal): String {
            val rounded = rate.round(PRECISION)
            val shown = if (rounded.scale() > MAX_SHOWN_DECIMALS) rounded.setScale(MAX_SHOWN_DECIMALS, RoundingMode.HALF_EVEN) else rounded
            return shown.stripTrailingZeros().toPlainString()
        }
    }
}
