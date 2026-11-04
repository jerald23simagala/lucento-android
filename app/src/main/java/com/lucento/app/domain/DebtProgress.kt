package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * A credit card's current billing cycle, from the statement and due days the user entered (docs/CALCULATIONS.md).
 * [statementBalance] is what was owed at the end of [statementDate]; [amountDue] is that less every payment or credit
 * recorded since, never below zero. Lucento has no access to the bank: both follow the transactions recorded on the card.
 */
data class CardCycle(
    val statementDate: LocalDate,
    val dueDate: LocalDate,
    val statementBalance: Money,
    val amountDue: Money,
) {
    fun isOverdue(today: LocalDate): Boolean = amountDue.amountMinor > 0L && dueDate.isBefore(today)
}

/**
 * A credit card's payment reminder: derived from its billing days and transactions every time, never stored as a
 * schedule, and never part of Safe to spend — the card's purchases were already counted as spending, so paying the card
 * is a transfer.
 */
data class CardDue(val wallet: Wallet, val cycle: CardCycle, val overdue: Boolean)

object CreditCardMath {

    /** [day] of [month], or the month's last day when the month is shorter (31 in February is the 28th or 29th). */
    fun dayOf(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    /** The latest statement closing date on or before [today]. */
    fun lastStatementDate(statementDay: Int, today: LocalDate): LocalDate {
        val month = YearMonth.from(today)
        val thisMonth = dayOf(month, statementDay)
        return if (thisMonth.isAfter(today)) dayOf(month.minusMonths(1), statementDay) else thisMonth
    }

    /** The first [dueDay] after [statementDate]: when that statement's payment is due. */
    fun dueDateAfter(statementDate: LocalDate, dueDay: Int): LocalDate {
        val month = YearMonth.from(statementDate)
        val sameMonth = dayOf(month, dueDay)
        return if (sameMonth.isAfter(statementDate)) sameMonth else dayOf(month.plusMonths(1), dueDay)
    }

    /**
     * The card's current cycle, or null for anything but a credit card with both billing days set. Only [wallet]'s own
     * rows in [transactions] count. Charges dated after the statement belong to the next statement; payments and
     * credits dated after it reduce what is due now:
     * statement balance = owed now − charges since + credits since; amount due = owed now − charges since (≥ 0).
     */
    fun cycle(wallet: Wallet, transactions: List<Transaction>, today: LocalDate): CardCycle? {
        if (wallet.type != WalletType.CREDIT_CARD) return null
        val terms = wallet.debt ?: return null
        val statementDay = terms.statementDay ?: return null
        val dueDay = terms.dueDay ?: return null
        val statementDate = lastStatementDate(statementDay, today)
        val code = wallet.currencyCode
        val since = transactions.filter { it.walletId == wallet.id && it.date.isAfter(statementDate) && it.amount.currencyCode == code }
        val charges = Money.sum(code, since.filterNot { it.type.isInflow }.map { it.amount })
        val credits = Money.sum(code, since.filter { it.type.isInflow }.map { it.amount })
        val due = wallet.balance - charges
        return CardCycle(
            statementDate = statementDate,
            dueDate = dueDateAfter(statementDate, dueDay),
            statementBalance = wallet.balance - charges + credits,
            amountDue = if (due.isNegative) Money.zero(code) else due,
        )
    }

    /** Credit still available: the limit less what is owed now; negative when over the limit. Null without a limit. */
    fun available(wallet: Wallet): Money? =
        wallet.debt?.creditLimit?.takeIf { wallet.type == WalletType.CREDIT_CARD && it.currencyCode == wallet.currencyCode }
            ?.let { it - wallet.balance }

    /** Cards with something to pay that is due by [until] (overdue ones included), soonest first. */
    fun dues(wallets: List<Wallet>, transactions: List<Transaction>, today: LocalDate, until: LocalDate): List<CardDue> =
        wallets.mapNotNull { wallet ->
            cycle(wallet, transactions, today)
                ?.takeIf { it.amountDue.amountMinor > 0L && !it.dueDate.isAfter(until) }
                ?.let { CardDue(wallet, it, overdue = it.isOverdue(today)) }
        }.sortedBy { it.cycle.dueDate }
}

/**
 * An instalment loan's progress (docs/CALCULATIONS.md). [principal] is what was owed when the loan was added; [paid] the
 * principal repaid since — principal − remaining, never below zero, the same figure as the loan history's "principal
 * repaid"; [remaining] what is owed now. [installmentsPaid] counts recorded repayments (transfers into the loan), at most
 * [installments]; a loan owed down to zero counts as fully paid.
 */
data class LoanProgress(
    val principal: Money,
    val paid: Money,
    val remaining: Money,
    val installmentsPaid: Int,
    val installments: Int,
)

object LoanMath {

    /** Null for anything but a loan repaid in monthly instalments with a term. */
    fun progress(wallet: Wallet, transactions: List<Transaction>): LoanProgress? {
        if (wallet.type != WalletType.LOAN) return null
        val terms = wallet.debt?.takeIf { it.repayment == DebtRepayment.MONTHLY } ?: return null
        val installments = terms.termMonths ?: return null
        val principal = wallet.openingBalance
        val remaining = wallet.balance
        val repaid = principal - remaining
        val payments = transactions.count { it.walletId == wallet.id && it.type == TransactionType.TRANSFER_IN }
        return LoanProgress(
            principal = principal,
            paid = if (repaid.isNegative) Money.zero(principal.currencyCode) else repaid,
            remaining = remaining,
            installmentsPaid = if (remaining.amountMinor <= 0L) installments else payments.coerceAtMost(installments),
            installments = installments,
        )
    }
}
