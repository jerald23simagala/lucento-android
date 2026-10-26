package com.lucento.app.domain

import com.lucento.app.core.money.CurrencyRules
import com.lucento.app.core.money.Money
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** How a debt is paid back: in one payment, or in equal monthly instalments (amortised). */
enum class DebtRepayment { ONE_TIME, MONTHLY }

/**
 * Optional terms of a liability wallet (credit card or loan). Every field is entered by the user; Lucento never
 * fetches rates. [interestRateBps] is the yearly rate in basis points (12.5 % = 1250). [termMonths] and
 * [installment] only apply to [DebtRepayment.MONTHLY].
 */
data class DebtTerms(
    val interestRateBps: Int? = null,
    val repayment: DebtRepayment = DebtRepayment.ONE_TIME,
    val termMonths: Int? = null,
    val installment: Money? = null,
) {
    init {
        require(interestRateBps == null || interestRateBps in 0..MAX_RATE_BPS) { "Rate out of range" }
        require(termMonths == null || termMonths in 1..MAX_TERM_MONTHS) { "Term out of range" }
        require(installment == null || installment.amountMinor > 0) { "Instalment must be positive" }
    }

    companion object {
        /** 100 % a year: anything higher is almost certainly a typo. */
        const val MAX_RATE_BPS = 10_000
        /** 50 years. */
        const val MAX_TERM_MONTHS = 600
    }
}

object DebtMath {

    sealed interface RateResult {
        data object Empty : RateResult
        data class Valid(val bps: Int) : RateResult
        data object Invalid : RateResult
    }

    /**
     * Parses a yearly percentage as typed ("12", "12.5", "7,25", "3.5%") into basis points, exactly (no floating
     * point). At most two decimals and at most [DebtTerms.MAX_RATE_BPS].
     */
    fun parseRate(text: String): RateResult {
        val clean = text.trim().removeSuffix("%").trim().replace(',', '.')
        if (clean.isEmpty()) return RateResult.Empty
        if (!Regex("""\d{1,3}(\.\d{0,2})?""").matches(clean)) return RateResult.Invalid
        val bps = BigDecimal(clean.trimEnd('.')).movePointRight(2).intValueExact()
        return if (bps > DebtTerms.MAX_RATE_BPS) RateResult.Invalid else RateResult.Valid(bps)
    }

    /** "12.5%" style label for [bps]: trailing zeros dropped. */
    fun formatRate(bps: Int): String = BigDecimal(bps).movePointLeft(2).stripTrailingZeros().toPlainString() + "%"

    /**
     * The level monthly payment that repays [principal] over [months] at [rateBps] a year (standard annuity formula,
     * monthly rate = yearly ÷ 12), rounded half-up to the currency's minor unit. Without interest it is principal ÷
     * months, rounded up so the instalments never fall short. Null when [principal] isn't positive.
     */
    fun monthlyInstallment(principal: Money, months: Int, rateBps: Int?): Money? {
        if (principal.amountMinor <= 0 || months < 1) return null
        val digits = CurrencyRules.fractionDigits(principal.currencyCode)
        val p = BigDecimal(principal.amountMinor).movePointLeft(digits)
        val payment = if (rateBps == null || rateBps == 0) {
            p.divide(BigDecimal(months), digits, RoundingMode.CEILING)
        } else {
            val mc = MathContext(34)
            val r = BigDecimal(rateBps).divide(BigDecimal(120_000), mc)
            val growth = BigDecimal.ONE.add(r).pow(months, mc)
            p.multiply(r, mc).multiply(growth, mc).divide(growth.subtract(BigDecimal.ONE), mc).setScale(digits, RoundingMode.HALF_UP)
        }
        return Money(payment.movePointRight(digits).longValueExact(), principal.currencyCode)
    }

    /** One month's interest on [owed] at [rateBps] a year, rounded half-up. A hint only: the user enters the split. */
    fun monthlyInterest(owed: Money, rateBps: Int): Money {
        if (owed.amountMinor <= 0) return Money.zero(owed.currencyCode)
        val minor = BigDecimal(owed.amountMinor).multiply(BigDecimal(rateBps)).divide(BigDecimal(120_000), 0, RoundingMode.HALF_UP)
        return Money(minor.longValueExact(), owed.currencyCode)
    }

    /** "Monthly Payment - Car loan" / "One-time Payment - Car loan", capped at [maxLength]. */
    fun reminderName(repayment: DebtRepayment, debtName: String, maxLength: Int): String =
        ((if (repayment == DebtRepayment.MONTHLY) "Monthly" else "One-time") + " Payment - " + debtName.trim()).take(maxLength)

    /** Name of the expense category that debt-payment interest is recorded under (created with the first debt). */
    const val INTEREST_CATEGORY = "Debt interest"
    const val INTEREST_ICON = "debt"
}
