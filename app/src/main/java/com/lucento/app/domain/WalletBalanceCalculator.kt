package com.lucento.app.domain

import com.lucento.app.core.money.Money

/** Derives a wallet's current balance from its opening balance and recorded movements. */
object WalletBalanceCalculator {

    /**
     * Assets: opening + income + transfers in − expense − transfers out.
     *
     * Liabilities (credit cards, loans) track the amount owed:
     * opening + expense + transfers out − income − transfers in.
     * A purchase on a card raises what you owe; paying the card from a bank account is a transfer *into*
     * the card that lowers it (and is not spending, so the purchase is never counted twice). Drawing loan
     * proceeds into a bank account is a transfer *out of* the loan, raising what is owed — never income.
     *
     * Arithmetic is exact; overflow throws [ArithmeticException].
     */
    fun currentBalance(
        opening: Money,
        type: WalletType,
        incomeMinor: Long,
        expenseMinor: Long,
        transferInMinor: Long = 0L,
        transferOutMinor: Long = 0L,
    ): Money {
        val code = opening.currencyCode
        val inflow = Money(incomeMinor, code) + Money(transferInMinor, code)
        val outflow = Money(expenseMinor, code) + Money(transferOutMinor, code)
        return if (type.isLiability) opening + outflow - inflow else opening + inflow - outflow
    }
}
