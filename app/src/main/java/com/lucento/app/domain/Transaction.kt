package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.LocalDate

/**
 * Direction of a recorded movement of money.
 *
 * Income and expense change what the user has. Transfers move money between the user's own wallets and
 * are stored as two linked legs ([TRANSFER_OUT] on the source, [TRANSFER_IN] on the destination) sharing a
 * transfer group. Transfers are never counted as income or spending.
 *
 * Balance adjustments ([ADJUSTMENT_IN] raises the money in a wallet, [ADJUSTMENT_OUT] lowers it) are written only by
 * [com.lucento.app.data.repository.TransactionRepository.adjustBalance] when the user overrides a wallet's balance.
 * They move the wallet balance but are never income or spending (see docs/CALCULATIONS.md).
 */
enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER_OUT,
    TRANSFER_IN,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT;

    val isTransfer: Boolean get() = this == TRANSFER_OUT || this == TRANSFER_IN

    val isAdjustment: Boolean get() = this == ADJUSTMENT_IN || this == ADJUSTMENT_OUT

    /** Income or spending: what budgets, net flow and insights count. */
    val isCashFlow: Boolean get() = this == INCOME || this == EXPENSE

    /** True when this movement adds money to the wallet it belongs to. */
    val isInflow: Boolean get() = this == INCOME || this == TRANSFER_IN || this == ADJUSTMENT_IN
}

/**
 * A single movement of money in one wallet. [amount] is always positive; direction comes from [type].
 * [date] is a calendar date (not an instant) so it never shifts when the user changes time zone.
 * [transferGroup] links the two legs of a transfer; null for income and expense.
 */
data class Transaction(
    val id: Long,
    val type: TransactionType,
    val amount: Money,
    val walletId: Long,
    val categoryId: Long?,
    val date: LocalDate,
    val note: String,
    val createdAtEpochMillis: Long,
    val transferGroup: String? = null,
    /** The row's stable idempotency key; links a debt payment's interest row to its transfer ([DebtPaymentLink]). */
    val clientUuid: String? = null,
    /** Lucento Shared: the account that added / last changed it (see [PeopleDirectory.attribution]); null when unknown. */
    val createdBy: String? = null,
    val updatedBy: String? = null,
    /**
     * v13, multicurrency (docs/CALCULATIONS.md "Exchange rates"): this movement's value in the main currency, for a
     * wallet in another currency — what the rate in [fxSource] made of [amount]. Null in a main-currency wallet, and for
     * rows written before multicurrency or without a rate (they follow the wallet's average, [com.lucento.app.domain.fx.FxSource.AVERAGE]).
     */
    val baseAmount: Money? = null,
    val fxSource: com.lucento.app.domain.fx.FxSource? = null,
    /**
     * Not a recorded movement: a realized FX gain (INCOME) or loss (EXPENSE) the average method worked out
     * ([com.lucento.app.domain.fx.FxTranslation]). Shown in Insights and reports, never in budgets or the transaction list.
     */
    val fxResult: Boolean = false,
) {
    val currencyCode: String get() = amount.currencyCode

    val isTransfer: Boolean get() = type.isTransfer

    val isAdjustment: Boolean get() = type.isAdjustment

    /** Positive for money arriving in the wallet, negative for money leaving it. */
    val signedAmount: Money get() = if (type.isInflow) amount else -amount
}

/** A transfer reconstructed from its two legs. */
data class Transfer(
    val group: String,
    val outLeg: Transaction,
    val inLeg: Transaction,
) {
    val amount: Money get() = outLeg.amount
    val fromWalletId: Long get() = outLeg.walletId
    val toWalletId: Long get() = inLeg.walletId
    val date: LocalDate get() = outLeg.date
    val note: String get() = outLeg.note

    companion object {
        /** Pairs transfer legs by group. Incomplete groups (which the repository never writes) are skipped. */
        fun pair(transactions: List<Transaction>): Map<String, Transfer> =
            transactions.asSequence()
                .filter { it.transferGroup != null && it.isTransfer }
                .groupBy { it.transferGroup!! }
                .mapNotNull { (group, legs) ->
                    val out = legs.firstOrNull { it.type == TransactionType.TRANSFER_OUT }
                    val inn = legs.firstOrNull { it.type == TransactionType.TRANSFER_IN }
                    if (out != null && inn != null) group to Transfer(group, out, inn) else null
                }
                .toMap()
    }
}
