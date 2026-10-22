package com.lucento.app.domain

import com.lucento.app.core.money.Money

/** Derives a wallet's current balance from its opening balance and recorded transactions. */
object WalletBalanceCalculator {

    /**
     * Assets: opening + income - expense.
     * Liabilities (credit cards) track the amount owed: opening + expense - income
     * (a purchase raises what you owe, a payment or refund lowers it).
     * Arithmetic is exact; overflow throws [ArithmeticException].
     */
    fun currentBalance(opening: Money, type: WalletType, incomeMinor: Long, expenseMinor: Long): Money {
        val income = Money(incomeMinor, opening.currencyCode)
        val expense = Money(expenseMinor, opening.currencyCode)
        return if (type.isLiability) opening + expense - income else opening + income - expense
    }
}
