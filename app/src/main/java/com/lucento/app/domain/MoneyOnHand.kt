package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * Safe to spend's second figure (tester round, 2026-11-17): what the person's spending money allows for the rest of the
 * month, so a comfortable budget never hides a nearly empty account. docs/CALCULATIONS.md "Safe to spend".
 *
 * [sources] are the spending wallets in the currency ([Wallet.isSpendingMoney]) with what each adds: an account's
 * balance, or for a card the part of its credit the person chose, never more than is still available. [deductions] are
 * what leaves that money by the month's end, overdue included: bills paid from those wallets, loan and card payments
 * made from them (in full — the cash leaves either way), and scheduled transfers from them into money that isn't for
 * spending. Expected income is never added. Pure.
 */
data class MoneyOnHand(
    val currencyCode: String,
    val sources: List<Source>,
    val deductions: List<Deduction>,
) {
    /** A spending wallet and what it adds ([credit]: a card's chosen part of its available credit). */
    data class Source(val walletId: Long, val name: String, val amount: Money, val credit: Boolean)

    enum class Kind { BILLS, DEBT_PAYMENT, CARD_PAYMENT, TRANSFER }

    /** What leaves the spending money by the month's end; [date] is the first one due (null for the bills, summed). */
    data class Deduction(val kind: Kind, val name: String, val date: LocalDate?, val amount: Money, val count: Int = 1)

    val total: Money get() = Money.sum(currencyCode, sources.map { it.amount })

    val deducted: Money get() = Money.sum(currencyCode, deductions.map { it.amount })

    /** What the spending money allows: [total] − [deducted]; negative when more is due than is there. */
    val allows: Money get() = total - deducted
}

object MoneyOnHandCalculator {

    /**
     * The money on hand in [currencyCode] for the month of [today], or null when no wallet in that currency is spending
     * money. [schedules] should already leave out a switched-off Recurring module, as Home's do.
     */
    fun compute(
        currencyCode: String,
        today: LocalDate,
        wallets: List<Wallet>,
        transactions: List<Transaction>,
        schedules: List<RecurringSchedule>,
    ): MoneyOnHand? {
        val monthEnd = YearMonth.from(today).atEndOfMonth()
        val sources = wallets
            .filter { it.currencyCode == currencyCode && it.isSpendingMoney }
            .mapNotNull { w -> contribution(w)?.let { MoneyOnHand.Source(w.id, w.name, it, credit = w.type == WalletType.CREDIT_CARD) } }
        if (sources.isEmpty()) return null
        val ids = sources.map { it.walletId }.toSet()
        val due = schedules
            .filter { it.currencyCode == currencyCode && it.walletId in ids }
            .map { it to RecurringMath.countDueThrough(it, monthEnd) }
            .filter { (_, count) -> count > 0 }
        val deductions = mutableListOf<MoneyOnHand.Deduction>()

        // Bills from spending money: one line (the budget's Bills due lists them one by one).
        val bills = due.filter { (s, _) -> s.type == TransactionType.EXPENSE && !s.isDebtPayment }
        if (bills.isNotEmpty()) {
            val amount = Money.sum(currencyCode, bills.map { (s, count) -> times(s.total, count) })
            if (amount.amountMinor > 0L) {
                deductions += MoneyOnHand.Deduction(MoneyOnHand.Kind.BILLS, "Bills", null, amount, bills.sumOf { it.second })
            }
        }
        // Loan and card payments planned as schedules: principal and interest both leave the account.
        due.filter { (s, _) -> s.isDebtPayment }.forEach { (s, count) ->
            deductions += MoneyOnHand.Deduction(MoneyOnHand.Kind.DEBT_PAYMENT, s.name, s.nextDue, times(s.total, count), count)
        }
        // A card's payment from its billing days, paid from spending money — unless a scheduled payment to the card due
        // this month already stands for it.
        val scheduledCards = schedules
            .filter { it.isDebtPayment && RecurringMath.countDueThrough(it, monthEnd) > 0 }
            .mapNotNull { it.toWalletId }
            .toSet()
        CreditCardMath.dues(wallets.filter { it.currencyCode == currencyCode }, transactions, today, monthEnd)
            .filter { it.wallet.id !in scheduledCards && it.payFromWalletId in ids }
            .forEach { d ->
                deductions += MoneyOnHand.Deduction(MoneyOnHand.Kind.CARD_PAYMENT, "${d.wallet.name} payment", d.cycle.dueDate, d.cycle.amountDue)
            }
        // Scheduled transfers out of spending money (into savings, an investment…). Between two spending wallets the
        // money stays on hand; into one, it isn't counted until it arrives.
        due.filter { (s, _) -> s.isTransfer && s.toWalletId !in ids }.forEach { (s, count) ->
            deductions += MoneyOnHand.Deduction(MoneyOnHand.Kind.TRANSFER, s.name, s.nextDue, times(s.amount, count), count)
        }
        return MoneyOnHand(currencyCode, sources, deductions)
    }

    /**
     * What [wallet] adds to the money on hand: an account's balance (below zero when overdrawn); for a card, the credit the
     * person chose to count, capped at what is still available and never below zero — nothing without a credit limit.
     * Null for a wallet that can't be spending money.
     */
    fun contribution(wallet: Wallet): Money? = when {
        !wallet.type.canBeSpendingMoney -> null
        wallet.type == WalletType.CREDIT_CARD -> {
            val chosen = wallet.spendableCredit?.takeIf { it.currencyCode == wallet.currencyCode && it.amountMinor > 0L }
            val available = CreditCardMath.available(wallet)
            if (chosen == null || available == null) null
            else minOf(chosen, if (available.isNegative) Money.zero(wallet.currencyCode) else available)
        }
        wallet.type.isLiability -> null
        else -> wallet.balance
    }

    private fun times(money: Money, count: Int): Money = Money(Math.multiplyExact(money.amountMinor, count.toLong()), money.currencyCode)
}
