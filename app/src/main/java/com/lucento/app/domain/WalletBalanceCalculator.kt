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
     * Balance adjustments move the balance like income (in) and expense (out) do, without being either.
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
        adjustmentInMinor: Long = 0L,
        adjustmentOutMinor: Long = 0L,
    ): Money {
        val code = opening.currencyCode
        val inflow = Money(incomeMinor, code) + Money(transferInMinor, code) + Money(adjustmentInMinor, code)
        val outflow = Money(expenseMinor, code) + Money(transferOutMinor, code) + Money(adjustmentOutMinor, code)
        return if (type.isLiability) opening + outflow - inflow else opening + inflow - outflow
    }

    /**
     * The balance at the end of [date]: [opening] adjusted by the wallet's own [transactions] dated on or before it.
     * Transactions from other wallets or in another currency are ignored.
     */
    fun balanceOn(opening: Money, type: WalletType, walletId: Long, transactions: List<Transaction>, date: java.time.LocalDate): Money {
        val code = opening.currencyCode
        val mine = transactions.filter { it.walletId == walletId && it.currencyCode == code && !it.date.isAfter(date) }
        fun sum(t: TransactionType) = mine.filter { it.type == t }.fold(0L) { acc, it -> Math.addExact(acc, it.amount.amountMinor) }
        return currentBalance(
            opening = opening,
            type = type,
            incomeMinor = sum(TransactionType.INCOME),
            expenseMinor = sum(TransactionType.EXPENSE),
            transferInMinor = sum(TransactionType.TRANSFER_IN),
            transferOutMinor = sum(TransactionType.TRANSFER_OUT),
            adjustmentInMinor = sum(TransactionType.ADJUSTMENT_IN),
            adjustmentOutMinor = sum(TransactionType.ADJUSTMENT_OUT),
        )
    }
}
