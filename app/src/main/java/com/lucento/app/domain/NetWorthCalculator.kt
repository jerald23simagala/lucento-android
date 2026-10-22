package com.lucento.app.domain

import com.lucento.app.core.money.Money

/** Deterministic net worth calculation. Currencies are never mixed or converted implicitly. */
object NetWorthCalculator {

    data class Summary(
        /** Net worth per currency, primary currency first, then alphabetical. */
        val totals: List<Money>,
        val assets: Map<String, Money>,
        val liabilities: Map<String, Money>,
    ) {
        fun totalFor(currencyCode: String): Money? = totals.firstOrNull { it.currencyCode == currencyCode }
    }

    fun summarize(wallets: List<Wallet>, primaryCurrency: String): Summary {
        val byCurrency = wallets.groupBy { it.currencyCode }
        val assets = byCurrency.mapValues { (code, list) ->
            Money.sum(code, list.filterNot { it.type.isLiability }.map { it.balance })
        }
        val liabilities = byCurrency.mapValues { (code, list) ->
            Money.sum(code, list.filter { it.type.isLiability }.map { it.balance })
        }
        val totals = byCurrency.keys
            .sortedWith(compareBy<String> { it != primaryCurrency }.thenBy { it })
            .map { code -> assets.getValue(code) - liabilities.getValue(code) }
        return Summary(totals = totals, assets = assets, liabilities = liabilities)
    }
}
