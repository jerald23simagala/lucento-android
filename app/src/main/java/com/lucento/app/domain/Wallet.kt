package com.lucento.app.domain

import com.lucento.app.core.money.Money

/**
 * Kinds of manually maintained accounts.
 *
 * [isLiability] accounts (credit cards, loans) track the amount owed as a positive balance; they reduce
 * net worth. [isSpendable] accounts hold money available for everyday spending (used by Safe to Spend);
 * savings are deliberately excluded so money set aside is not presented as spendable.
 */
enum class WalletType(val isLiability: Boolean, val isSpendable: Boolean) {
    CASH(isLiability = false, isSpendable = true),
    BANK(isLiability = false, isSpendable = true),
    E_WALLET(isLiability = false, isSpendable = true),
    SAVINGS(isLiability = false, isSpendable = false),
    CREDIT_CARD(isLiability = true, isSpendable = false),
    LOAN(isLiability = true, isSpendable = false),
}

/**
 * A manually maintained account. [balance] is the current balance: [openingBalance] adjusted by
 * recorded transactions (see [WalletBalanceCalculator]). For liabilities (credit cards, loans) it is the
 * amount owed, entered as a positive number; it reduces net worth.
 */
data class Wallet(
    val id: Long,
    val name: String,
    val type: WalletType,
    val balance: Money,
    val createdAtEpochMillis: Long,
    val openingBalance: Money = balance,
) {
    val currencyCode: String get() = balance.currencyCode

    /** Signed effect of this wallet on net worth. */
    val netWorthContribution: Money get() = if (type.isLiability) -balance else balance
}
