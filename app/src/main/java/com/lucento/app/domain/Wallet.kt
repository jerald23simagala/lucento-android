package com.lucento.app.domain

import com.lucento.app.core.money.Money

enum class WalletType(val isLiability: Boolean) {
    CASH(false),
    BANK(false),
    E_WALLET(false),
    SAVINGS(false),
    CREDIT_CARD(true),
}

/**
 * A manually maintained account. For liabilities (credit cards) [balance] is the amount owed,
 * entered as a positive number; it reduces net worth.
 */
data class Wallet(
    val id: Long,
    val name: String,
    val type: WalletType,
    val balance: Money,
    val createdAtEpochMillis: Long,
) {
    val currencyCode: String get() = balance.currencyCode

    /** Signed effect of this wallet on net worth. */
    val netWorthContribution: Money get() = if (type.isLiability) -balance else balance
}
