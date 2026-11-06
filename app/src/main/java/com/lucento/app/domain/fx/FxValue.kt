package com.lucento.app.domain.fx

import com.lucento.app.core.money.Money

/**
 * A movement's value in the main currency and where it came from — what a foreign-currency transaction is saved with.
 * [base] is in the main currency. Leaving it off a movement means "follow the wallet's average" ([FxSource.AVERAGE]).
 */
data class FxValue(val base: Money, val source: FxSource) {
    init {
        require(!base.isNegative) { "A value in the main currency isn't negative" }
    }
}

/** What a new movement in a foreign wallet will be saved at: its value in the main currency, the rate, and the source. */
data class FxQuote(val value: Money, val rate: java.math.BigDecimal, val source: FxSource) {
    val asValue: FxValue get() = FxValue(value, source)
}

object FxQuotes {

    /**
     * The value a new movement of [amount] in [wallet] gets (owner, 2026-11-05): the day's [market] rate when there is
     * one; otherwise the person's offline choice — the wallet's last rate ([FxSource.LAST_RATE], a gain or loss counts
     * against the average) or its average ([FxSource.AVERAGE], none). Average falls back to the wallet's opening rate
     * while it holds nothing. Null for a main-currency wallet, or a foreign one with no rate at all yet.
     */
    fun quote(
        main: String,
        wallet: com.lucento.app.domain.Wallet,
        amount: Money,
        market: java.math.BigDecimal?,
        offline: FxSource,
        book: FxBook,
        lastRate: java.math.BigDecimal?,
    ): FxQuote? {
        if (wallet.currencyCode == main || amount.currencyCode != wallet.currencyCode) return null
        val (rate, source) = when {
            market != null -> market to FxSource.MARKET
            offline == FxSource.LAST_RATE && lastRate != null -> lastRate to FxSource.LAST_RATE
            else -> (book.positions[wallet.id]?.averageRate ?: wallet.fxBasisRate ?: return null) to FxSource.AVERAGE
        }
        return FxQuote(com.lucento.app.core.money.FxRate(wallet.currencyCode, main, rate).convert(amount).abs(), rate, source)
    }
}

object FxTransfer {

    /**
     * Each leg's value in the main currency ([main]) for a transfer of [amount] that arrives as [arriving]:
     * - within the main currency, nothing;
     * - between the main currency and another, the main-currency side *is* the value (what was really paid or received);
     * - between two other currencies (or within one), both legs carry [value] — the source's day rate, or its average when
     *   offline — the arriving leg as [FxSource.TRANSFER], so the destination takes it in at exactly what left.
     * Null legs follow their wallet's average.
     */
    fun legValues(main: String, amount: Money, arriving: Money, value: FxValue?): Pair<FxValue?, FxValue?> = when {
        amount.currencyCode == main && arriving.currencyCode == main -> null to null
        amount.currencyCode == main -> null to FxValue(amount, FxSource.TRANSFER)
        arriving.currencyCode == main -> FxValue(arriving, FxSource.TRANSFER) to null
        value == null -> null to null
        else -> value to FxValue(value.base, FxSource.TRANSFER)
    }
}
