package com.lucento.app.domain

/**
 * The currency the month's money figures are shown in (Home's budget card, the Budget page and the Transactions totals
 * all use it, so they describe the same money): the main currency when the user holds or records anything in it,
 * otherwise the first wallet's. Nothing is ever converted; other currencies are listed separately.
 */
object FocusCurrency {

    fun of(primaryCurrency: String, wallets: List<Wallet>, transactions: List<Transaction>): String = when {
        wallets.any { it.currencyCode == primaryCurrency } -> primaryCurrency
        transactions.any { it.currencyCode == primaryCurrency } -> primaryCurrency
        else -> wallets.firstOrNull()?.currencyCode ?: primaryCurrency
    }
}
