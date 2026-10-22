package com.lucento.app.core.money

import java.math.BigDecimal
import java.util.Currency

/**
 * An exact monetary amount stored as a signed count of the currency's minor units
 * (cents for USD, yen for JPY, fils for BHD). Lucento never uses floating point for money.
 */
data class Money(val amountMinor: Long, val currencyCode: String) : Comparable<Money> {

    init {
        require(currencyCode.length == 3 && currencyCode.all { it in 'A'..'Z' }) {
            "Invalid ISO 4217 currency code: $currencyCode"
        }
    }

    val fractionDigits: Int get() = CurrencyRules.fractionDigits(currencyCode)

    val isZero: Boolean get() = amountMinor == 0L
    val isNegative: Boolean get() = amountMinor < 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(amountMinor, other.amountMinor), currencyCode)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(amountMinor, other.amountMinor), currencyCode)
    }

    operator fun unaryMinus(): Money = Money(Math.negateExact(amountMinor), currencyCode)

    fun abs(): Money = if (amountMinor < 0) -this else this

    /** Exact decimal value in major units, e.g. 1234 USD minor units -> 12.34. */
    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(amountMinor, fractionDigits)

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    private fun requireSameCurrency(other: Money) {
        require(currencyCode == other.currencyCode) {
            "Currency mismatch: $currencyCode vs ${other.currencyCode}"
        }
    }

    companion object {
        fun zero(currencyCode: String) = Money(0L, currencyCode)

        fun sum(currencyCode: String, items: Iterable<Money>): Money =
            items.fold(zero(currencyCode)) { acc, m -> acc + m }
    }
}

object CurrencyRules {
    /** Number of minor-unit digits for an ISO 4217 code. Falls back to 2 for unknown codes. */
    fun fractionDigits(currencyCode: String): Int {
        val digits = runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }.getOrNull()
        return if (digits == null || digits < 0) 2 else digits
    }

    fun isSupported(currencyCode: String): Boolean =
        runCatching { Currency.getInstance(currencyCode) }.isSuccess
}
