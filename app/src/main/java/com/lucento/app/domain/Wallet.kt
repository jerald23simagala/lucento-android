package com.lucento.app.domain

import com.lucento.app.core.money.Money

/**
 * Kinds of manually maintained accounts.
 *
 * [isLiability] accounts (credit cards, loans) track the amount owed as a positive balance; they reduce
 * net worth. [isSpendable] accounts hold money available for everyday spending (used by Safe to Spend);
 * savings are deliberately excluded so money set aside is not presented as spendable.
 *
 * [isValuedManually] assets (a home, a car, investments) have no money movements: their balance is a valuation the
 * user enters and updates. They count towards net worth but never appear where a transaction, transfer or
 * recurring payment picks an account, and the repositories refuse to post movements to them.
 */
enum class WalletType(val isLiability: Boolean, val isSpendable: Boolean, val isValuedManually: Boolean = false) {
    CASH(isLiability = false, isSpendable = true),
    BANK(isLiability = false, isSpendable = true),
    E_WALLET(isLiability = false, isSpendable = true),
    SAVINGS(isLiability = false, isSpendable = false),
    CREDIT_CARD(isLiability = true, isSpendable = false),
    LOAN(isLiability = true, isSpendable = false),
    OTHER_ASSET(isLiability = false, isSpendable = false, isValuedManually = true),
    ;

    /** True for accounts that money moves through: everything except manually valued assets. */
    val acceptsTransactions: Boolean get() = !isValuedManually
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
    /** Interest rate and repayment plan of a card or loan, when the user entered them. Always null for assets. */
    val debt: DebtTerms? = null,
) {
    val currencyCode: String get() = balance.currencyCode

    /** Signed effect of this wallet on net worth. */
    val netWorthContribution: Money get() = if (type.isLiability) -balance else balance
}

/** Wallets that can be picked for a transaction, transfer, recurring payment or goal (manually valued assets excluded). */
fun List<Wallet>.transactable(): List<Wallet> = filter { it.type.acceptsTransactions }

/** What still depends on a wallet: posted transactions (including transfer legs) and recurring schedules. */
data class WalletUsage(val transactionCount: Int, val scheduleCount: Int) {
    val isUsed: Boolean get() = transactionCount > 0 || scheduleCount > 0

    companion object {
        val Unused = WalletUsage(0, 0)
    }
}

/** Why an edit to an existing wallet was refused. Nothing is changed when an edit is refused. */
sealed interface WalletChangeViolation {
    val usage: WalletUsage

    /**
     * Switching between an asset type (cash, bank, e-wallet, savings) and a liability type (credit card, loan)
     * would reinterpret every recorded movement: an expense lowers an asset's balance but raises what a card
     * owes. Refused while anything references the wallet.
     */
    data class TypeLocked(override val usage: WalletUsage) : WalletChangeViolation

    /**
     * Every transaction and recurring schedule stores the wallet's currency. Changing it would make existing
     * records disagree with their wallet (and make a schedule impossible to pay), and Lucento never converts.
     */
    data class CurrencyLocked(override val usage: WalletUsage) : WalletChangeViolation
}

/**
 * Single source of truth for which edits an existing wallet accepts. Used by [com.lucento.app.data.repository.WalletRepository];
 * screens may call it to explain a lock up front, but the repository always enforces it.
 *
 * - Name and opening balance can always change (an explicit correction by the user).
 * - Type changes within the same side (cash ↔ bank, credit card ↔ loan) keep the balance formula and are allowed.
 * - Asset ↔ liability changes and currency changes are allowed only for a wallet nothing references yet.
 */
object WalletChangePolicy {

    /**
     * True when [from] → [to] changes how the balance is formed: asset ↔ liability, or a money account ↔ a manually
     * valued asset (whose balance is a valuation, not the sum of movements).
     */
    fun crossesBalanceSide(from: WalletType, to: WalletType): Boolean =
        from.isLiability != to.isLiability || from.isValuedManually != to.isValuedManually

    fun check(
        currentType: WalletType,
        currentCurrency: String,
        newType: WalletType,
        newCurrency: String,
        usage: WalletUsage,
    ): WalletChangeViolation? = when {
        !usage.isUsed -> null
        currentCurrency != newCurrency -> WalletChangeViolation.CurrencyLocked(usage)
        crossesBalanceSide(currentType, newType) -> WalletChangeViolation.TypeLocked(usage)
        else -> null
    }
}
