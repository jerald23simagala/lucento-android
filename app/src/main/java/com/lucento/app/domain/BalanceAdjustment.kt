package com.lucento.app.domain

import com.lucento.app.core.money.Money

/**
 * Overriding a wallet's balance when the user can't reconcile it. Lucento never edits history to make a number fit:
 * the gap between what it tracked and what the user says is really there is recorded as one balance adjustment
 * (see docs/CALCULATIONS.md, "Balance adjustments").
 */
object BalanceAdjustment {

    /** The movement that turns the tracked balance into the actual one: its type and (always positive) amount. */
    data class Plan(val type: TransactionType, val amount: Money)

    /**
     * What to record so a wallet of [walletType] reads [actual] instead of [tracked], or null when they already agree.
     * For liabilities both figures are the amount owed: owing more is an outflow (like an unrecorded purchase), owing
     * less an inflow. Currencies must match; arithmetic is exact.
     */
    fun plan(walletType: WalletType, tracked: Money, actual: Money): Plan? {
        require(tracked.currencyCode == actual.currencyCode) { "Currencies differ" }
        val gap = actual - tracked
        if (gap.isZero) return null
        val raises = gap.amountMinor > 0
        val type = when {
            walletType.isLiability == raises -> TransactionType.ADJUSTMENT_OUT
            else -> TransactionType.ADJUSTMENT_IN
        }
        return Plan(type, if (raises) gap else -gap)
    }
}

/** Why a wallet's balance looks wrong. Lucento doesn't connect to banks, so a gap usually means something unrecorded. */
enum class BalanceFlag {
    /** Cash, an e-wallet or savings below zero: money that can't physically go negative. */
    BELOW_ZERO,

    /** A bank account below zero: fine if it really is overdrawn, otherwise something is missing. */
    OVERDRAWN,

    /** A card or loan that shows you are owed money: usually an overpayment or a purchase not recorded. */
    OWED_TO_YOU,
}

/** The flag for this wallet's balance, or null when it looks normal. Manually valued assets are never flagged. */
val Wallet.balanceFlag: BalanceFlag?
    get() = when {
        !balance.isNegative || type.isValuedManually -> null
        type.isLiability -> BalanceFlag.OWED_TO_YOU
        type == WalletType.BANK -> BalanceFlag.OVERDRAWN
        else -> BalanceFlag.BELOW_ZERO
    }
