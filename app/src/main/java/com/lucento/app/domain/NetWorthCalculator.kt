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

/** One slice of net worth: the summed balances of one account type in one currency. */
data class NetWorthComponent(val type: WalletType, val amount: Money, val walletCount: Int)

/**
 * What net worth is made of, in one currency: assets by account type, liabilities (amounts owed) by account type,
 * and their difference. Built from the same wallet balances as [NetWorthCalculator.summarize], so the totals always
 * agree. Savings goals are not added: goal money already sits inside these balances.
 */
data class NetWorthComposition(
    val currencyCode: String,
    val assets: List<NetWorthComponent>,
    val liabilities: List<NetWorthComponent>,
    val totalAssets: Money,
    val totalLiabilities: Money,
) {
    val netWorth: Money get() = totalAssets - totalLiabilities
    val isEmpty: Boolean get() = assets.isEmpty() && liabilities.isEmpty()
}

object NetWorthComposer {

    fun compose(wallets: List<Wallet>, currencyCode: String): NetWorthComposition {
        val inCurrency = wallets.filter { it.currencyCode == currencyCode }
        fun components(liability: Boolean) = WalletType.entries
            .filter { it.isLiability == liability }
            .mapNotNull { type ->
                val list = inCurrency.filter { it.type == type }
                if (list.isEmpty()) null else NetWorthComponent(type, Money.sum(currencyCode, list.map { it.balance }), list.size)
            }
            .sortedByDescending { it.amount.amountMinor }
        val assets = components(liability = false)
        val liabilities = components(liability = true)
        return NetWorthComposition(
            currencyCode = currencyCode,
            assets = assets,
            liabilities = liabilities,
            totalAssets = Money.sum(currencyCode, assets.map { it.amount }),
            totalLiabilities = Money.sum(currencyCode, liabilities.map { it.amount }),
        )
    }

    /** Currencies that have at least one wallet, [preferred] first. */
    fun currencies(wallets: List<Wallet>, preferred: String): List<String> =
        wallets.map { it.currencyCode }.distinct().sortedWith(compareBy<String> { it != preferred }.thenBy { it })
}
