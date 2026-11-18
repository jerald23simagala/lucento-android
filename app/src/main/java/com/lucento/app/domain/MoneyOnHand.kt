package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * Safe to spend's second figure (tester round, 2026-11-17): what the person's spending money allows for the rest of the
 * month, so a comfortable budget never hides a nearly empty account. docs/CALCULATIONS.md "Safe to spend".
 *
 * [sources] are the spending wallets in the currency ([Wallet.isSpendingMoney]) with what each adds: an account's
 * balance, or for a card the credit the person chose less what this month has already put on it, never more than is
 * still available. [deductions] are what leaves that money by the month's end, overdue included: bills paid from those
 * wallets, loan and card payments made from them (in full — the cash leaves either way — but never more than is owed),
 * and scheduled transfers from them into money that isn't for spending. Expected income is never added. Pure.
 */
data class MoneyOnHand(
    val currencyCode: String,
    val sources: List<Source>,
    val deductions: List<Deduction>,
    /**
     * Whether any source has had money recorded in it ([MoneyOnHandCalculator.hasMoneyRecorded]). When none has, the
     * wallets only ever had spending taken from them, so their balances say nothing about the money on hand and Safe to
     * spend follows the budget alone.
     */
    val recorded: Boolean = true,
) {
    /**
     * A spending wallet and what it adds. For a card ([credit]): [chosen] is the credit the person counts and [used] what
     * this month has put on the card since; [amount] is what is left of it, capped at the credit still available.
     */
    data class Source(
        val walletId: Long,
        val name: String,
        val amount: Money,
        val credit: Boolean,
        val chosen: Money? = null,
        val used: Money? = null,
    )

    enum class Kind { BILLS, DEBT_PAYMENT, CARD_PAYMENT, TRANSFER }

    /**
     * What leaves the spending money by the month's end: [count] payments, the first due on [date] (null for the bills,
     * summed into one line).
     */
    data class Deduction(val kind: Kind, val name: String, val date: LocalDate?, val amount: Money, val count: Int = 1)

    val total: Money get() = Money.sum(currencyCode, sources.map { it.amount })

    val deducted: Money get() = Money.sum(currencyCode, deductions.map { it.amount })

    /** What the spending money allows: [total] − [deducted]; negative when more is due than is there. */
    val allows: Money get() = total - deducted
}

object MoneyOnHandCalculator {

    /**
     * The money on hand in [currencyCode] for the month of [today], or null when no wallet in that currency is spending
     * money. [schedules] should already leave out a switched-off Recurring module, as Home's do, and be valued in
     * [currencyCode] where they are paid from a foreign wallet (`FxTranslation.valueSchedules`).
     */
    fun compute(
        currencyCode: String,
        today: LocalDate,
        wallets: List<Wallet>,
        transactions: List<Transaction>,
        schedules: List<RecurringSchedule>,
    ): MoneyOnHand? {
        val monthEnd = YearMonth.from(today).atEndOfMonth()
        val spending = wallets.filter { it.currencyCode == currencyCode && it.isSpendingMoney }
        val sources = spending.mapNotNull { w -> source(w, transactions, today) }
        if (sources.isEmpty()) return null
        val ids = sources.map { it.walletId }.toSet()
        val byId = wallets.associateBy { it.id }
        val recorded = sources.any { s -> s.credit || byId[s.walletId]?.let { hasMoneyRecorded(it, transactions) } == true }
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
        // Loan and card payments planned as schedules: principal and interest both leave the account, but never more
        // than the debt still owes — months of payments left unmarked don't take more than could be paid.
        due.filter { (s, _) -> s.isDebtPayment }.forEach { (s, count) ->
            val owed = s.toWalletId?.let(byId::get)?.takeIf { it.type.isLiability && it.currencyCode == currencyCode }?.balance
            val planned = times(s.total, count)
            val amount = if (owed == null) planned else minOf(planned, if (owed.isNegative) Money.zero(currencyCode) else owed)
            if (amount.amountMinor > 0L) {
                deductions += MoneyOnHand.Deduction(MoneyOnHand.Kind.DEBT_PAYMENT, s.name, s.nextDue, amount, count)
            }
        }
        // A card's payment from its billing days, paid from spending money — unless a scheduled payment to the card due
        // this month already stands for it (a transfer schedule into a card, which only a synced row could be, too).
        val scheduledCards = schedules
            .filter { (it.isDebtPayment || it.isTransfer) && RecurringMath.countDueThrough(it, monthEnd) > 0 }
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
        return MoneyOnHand(currencyCode, sources, deductions, recorded)
    }

    private fun source(wallet: Wallet, transactions: List<Transaction>, today: LocalDate): MoneyOnHand.Source? {
        val amount = contribution(wallet, transactions, today) ?: return null
        val card = wallet.type == WalletType.CREDIT_CARD
        return MoneyOnHand.Source(
            wallet.id, wallet.name, amount, credit = card,
            chosen = if (card) wallet.spendableCredit else null,
            used = if (card) creditUsed(wallet, transactions, today) else null,
        )
    }

    /**
     * What [wallet] adds to the money on hand: an account's balance (below zero when overdrawn). For a card, the credit
     * the person chose to count less [creditUsed] — so spending on the card, or a bill from it marked paid, uses the
     * counted credit up — capped at what is still available (limit − owed, never below zero). It goes below zero once
     * more was put on the card this month than they chose to count. Nothing without a credit limit. Null for a wallet
     * that can't be spending money. Without [today], nothing counts as used.
     */
    fun contribution(wallet: Wallet, transactions: List<Transaction> = emptyList(), today: LocalDate? = null): Money? = when {
        !wallet.type.canBeSpendingMoney -> null
        wallet.type == WalletType.CREDIT_CARD -> {
            val chosen = wallet.spendableCredit?.takeIf { it.currencyCode == wallet.currencyCode && it.amountMinor > 0L }
            val available = CreditCardMath.available(wallet)
            if (chosen == null || available == null) {
                null
            } else {
                val left = if (today == null) chosen else chosen - creditUsed(wallet, transactions, today)
                minOf(left, if (available.isNegative) Money.zero(wallet.currencyCode) else available)
            }
        }
        wallet.type.isLiability -> null
        else -> wallet.balance
    }

    /**
     * What the month of [today] has put on [card] that no statement due this month already covers: its charges (spending
     * and money moved off the card) less refunds, never below zero. It counts from the later of the month's first day and
     * the day after this month's statement when that statement's payment is due by the month's end — those charges are
     * that payment's, which is set aside from the paying account instead (counting them twice would understate).
     */
    fun creditUsed(card: Wallet, transactions: List<Transaction>, today: LocalDate): Money {
        val month = YearMonth.from(today)
        val code = card.currencyCode
        val terms = card.debt
        val statementDay = terms?.statementDay
        val dueDay = terms?.dueDay
        val billed = if (statementDay != null && dueDay != null) {
            CreditCardMath.lastStatementDate(statementDay, today).takeIf {
                YearMonth.from(it) == month && !CreditCardMath.dueDateAfter(it, dueDay).isAfter(month.atEndOfMonth())
            }
        } else {
            null
        }
        val from = billed?.plusDays(1) ?: month.atDay(1)
        var used = 0L
        transactions.forEach { t ->
            if (t.walletId != card.id || t.amount.currencyCode != code || t.date.isBefore(from) || YearMonth.from(t.date) != month) return@forEach
            used = when (t.type) {
                TransactionType.EXPENSE, TransactionType.TRANSFER_OUT -> Math.addExact(used, t.amount.amountMinor)
                TransactionType.INCOME -> Math.subtractExact(used, t.amount.amountMinor)
                else -> used
            }
        }
        return Money(maxOf(used, 0L), code)
    }

    /**
     * Whether money has ever been recorded in [wallet]: a starting balance, or anything that put money in or set its
     * balance (income, a transfer in, a balance adjustment). A wallet that only ever had spending taken from it holds no
     * meaningful balance — its figure is minus everything spent, not the money on hand.
     */
    fun hasMoneyRecorded(wallet: Wallet, transactions: List<Transaction>): Boolean =
        !wallet.openingBalance.isZero ||
            transactions.any { it.walletId == wallet.id && (it.type.isInflow || it.type.isAdjustment) }

    private fun times(money: Money, count: Int): Money = Money(Math.multiplyExact(money.amountMinor, count.toLong()), money.currencyCode)
}
