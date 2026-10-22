package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.LocalDate

/**
 * Direction of a recorded movement of money.
 *
 * Income and expense change what the user has. Transfers move money between the user's own wallets and
 * are stored as two linked legs ([TRANSFER_OUT] on the source, [TRANSFER_IN] on the destination) sharing a
 * transfer group. Transfers are never counted as income or spending.
 */
enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER_OUT,
    TRANSFER_IN;

    val isTransfer: Boolean get() = this == TRANSFER_OUT || this == TRANSFER_IN

    /** True when this movement adds money to the wallet it belongs to. */
    val isInflow: Boolean get() = this == INCOME || this == TRANSFER_IN
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
) {
    val currencyCode: String get() = amount.currencyCode

    val isTransfer: Boolean get() = type.isTransfer

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
