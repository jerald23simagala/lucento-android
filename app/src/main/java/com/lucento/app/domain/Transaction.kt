package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.LocalDate

enum class TransactionType { EXPENSE, INCOME }

/**
 * A single income or expense. [amount] is always positive; direction comes from [type].
 * [date] is a calendar date (not an instant) so it never shifts when the user changes time zone.
 * Transfers between wallets arrive in M2 as a separate linked-leg concept.
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
) {
    val currencyCode: String get() = amount.currencyCode

    /** Positive for income, negative for expense. */
    val signedAmount: Money get() = if (type == TransactionType.INCOME) amount else -amount
}
