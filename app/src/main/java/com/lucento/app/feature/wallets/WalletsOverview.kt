package com.lucento.app.feature.wallets

import com.lucento.app.core.money.Money
import com.lucento.app.domain.BalanceHistory
import com.lucento.app.domain.Insights
import com.lucento.app.domain.MonthBalance
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Which half of net worth the Wallets list shows. */
enum class WalletSide(val label: String) {
    ALL("All"),
    ASSETS("Assets"),
    LIABILITIES("Liabilities"),
}

/** Net worth over the last months for the Wallets hero, and its month-on-month change. */
data class NetWorthTrend(
    /** Net worth at the end of each month, oldest first (the current month is today's); months before any wallet are left out. */
    val history: List<MonthBalance>,
    /** Change from last month to this one in whole percent; null when it would mislead (see [WalletsOverview.trend]). */
    val monthOnMonth: Int?,
)

/** Pure helpers behind the Wallets screen: filters, the asset/liability split and the trend. Nothing is converted. */
object WalletsOverview {

    /** How many months the hero's trend line covers. */
    const val TREND_MONTHS = 6

    /** The wallets on [side] and, when [type] is set, of that type, in their original order. Display filtering only. */
    fun filter(wallets: List<Wallet>, side: WalletSide, type: WalletType?): List<Wallet> = wallets.filter { w ->
        val onSide = when (side) {
            WalletSide.ALL -> true
            WalletSide.ASSETS -> !w.type.isLiability
            WalletSide.LIABILITIES -> w.type.isLiability
        }
        onSide && (type == null || w.type == type)
    }

    /** The type chips for [side]: only types the user actually has there, in the usual type order. */
    fun typesOn(wallets: List<Wallet>, side: WalletSide): List<WalletType> =
        filter(wallets, side, null).map { it.type }.distinct().sortedBy { it.ordinal }

    /**
     * Assets' share of everything tracked in one currency (assets + what is owed), in whole percent; liabilities take the
     * rest, so the two always add up to 100. Null when either side is zero or below (an overpaid card, an overdrawn
     * account): a split of a negative total reads backwards, so no bar is drawn.
     */
    fun assetShare(assets: Money, owed: Money): Int? {
        if (assets.amountMinor <= 0L || owed.amountMinor <= 0L) return null
        return Insights.percentOf(assets, assets + owed)
    }

    /**
     * Net worth at the end of each of the last [months] months in [currency] ([BalanceHistory.lastMonths], so the last
     * point equals today's net worth), and the change from last month — only when both months measure the same wallets
     * and last month was above zero ([BalanceHistory.comparableTrendPercent]): a new wallet's opening balance is not growth.
     */
    fun trend(
        wallets: List<Wallet>,
        transactions: List<Transaction>,
        currency: String,
        today: LocalDate,
        months: Int = TREND_MONTHS,
        zone: ZoneId = ZoneId.systemDefault(),
    ): NetWorthTrend {
        val history = BalanceHistory.lastMonths(wallets, transactions, currency, YearMonth.from(today), today, count = months, zone = zone)
        val lastTwo = history.takeLast(2)
        val change = if (lastTwo.size < 2) null else BalanceHistory.comparableTrendPercent(lastTwo, wallets, currency, zone)
        return NetWorthTrend(history, change)
    }
}

/** The one-word name a type takes on a compact wallet face, where "Bank account" or "Credit card" would not fit. */
val WalletType.shortLabel: String
    get() = when (this) {
        WalletType.CASH -> "Cash"
        WalletType.BANK -> "Bank"
        WalletType.E_WALLET -> "E-wallet"
        WalletType.SAVINGS -> "Savings"
        WalletType.CREDIT_CARD -> "Card"
        WalletType.LOAN -> "Loan"
        WalletType.OTHER_ASSET -> "Asset"
    }

/** The short, plural name a type takes on a filter chip ("Cards", "Loans"). */
val WalletType.chipLabel: String
    get() = when (this) {
        WalletType.CASH -> "Cash"
        WalletType.BANK -> "Bank"
        WalletType.E_WALLET -> "E-wallet"
        WalletType.SAVINGS -> "Savings"
        WalletType.CREDIT_CARD -> "Cards"
        WalletType.LOAN -> "Loans"
        WalletType.OTHER_ASSET -> "Other assets"
    }
