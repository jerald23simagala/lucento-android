package com.lucento.app.domain.fx

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Wallet

/**
 * What every wallet and movement in another currency is worth in the main currency, for the "≈ ₱…" line shown under
 * them wherever they appear (owner, 2026-11-06). One app-wide answer, from the same average-method book as the totals
 * ([FxLedger.build]), so a wallet's row, its card and its page always say the same thing.
 */
data class FxDisplay(
    val mainCurrency: String? = null,
    /** Carrying value by wallet id, signed like the wallet's balance (what is held, or owed on a card or loan). */
    val wallets: Map<Long, Money> = emptyMap(),
    /** Each movement's value by transaction id (positive). */
    val transactions: Map<Long, Money> = emptyMap(),
) {
    /** [wallet]'s value signed like its effect on net worth (a debt negative); null for a main-currency or unvalued wallet. */
    fun netWorthValue(wallet: Wallet): Money? = wallets[wallet.id]?.let { if (wallet.type.isLiability) -it else it }

    /** [transaction]'s value with [sign] (+1, −1, or 0 for unsigned), or null when it is in the main currency. */
    fun value(transaction: Transaction, sign: Int): Money? =
        transactions[transaction.id]?.takeIf { transaction.currencyCode != mainCurrency }?.let { if (sign < 0) -it else it }

    companion object {
        val NONE = FxDisplay()

        fun of(mainCurrency: String, wallets: List<Wallet>, transactions: List<Transaction>): FxDisplay {
            if (wallets.none { it.currencyCode != mainCurrency }) return FxDisplay(mainCurrency)
            val book = FxLedger.build(mainCurrency, wallets, transactions)
            return FxDisplay(mainCurrency, book.positions.mapValues { it.value.carrying }, book.values)
        }
    }
}
