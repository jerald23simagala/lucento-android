package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One month of the dashboard's balance history: net wallet balance at the end of [month] in one currency. */
data class MonthBalance(val month: YearMonth, val netWorth: Money)

/**
 * Balances as they stood at the end of a past day, rebuilt from each wallet's opening balance and the transactions
 * dated on or before that day — the same rules [WalletBalanceCalculator] applies to today's balances, so the current
 * month always matches the Wallets screen. Pure, so it is tested on the JVM.
 *
 * Limits, by design: a wallet created after the day is left out (it didn't exist yet), and archived wallets are left
 * out (as they are from today's net worth). Nothing is converted between currencies.
 */
object BalanceHistory {

    /** Each wallet that existed on [date], with its balance as of the end of [date]. */
    fun walletsAt(
        wallets: List<Wallet>,
        transactions: List<Transaction>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Wallet> {
        val byWallet = transactions.filter { !it.date.isAfter(date) }.groupBy { it.walletId }
        return wallets
            .filter { !Instant.ofEpochMilli(it.createdAtEpochMillis).atZone(zone).toLocalDate().isAfter(date) }
            .map { wallet ->
                val rows = byWallet[wallet.id].orEmpty().filter { it.currencyCode == wallet.currencyCode }
                fun sum(type: TransactionType) = rows.filter { it.type == type }.fold(0L) { acc, t -> Math.addExact(acc, t.amount.amountMinor) }
                wallet.copy(
                    balance = WalletBalanceCalculator.currentBalance(
                        opening = wallet.openingBalance,
                        type = wallet.type,
                        incomeMinor = sum(TransactionType.INCOME),
                        expenseMinor = sum(TransactionType.EXPENSE),
                        transferInMinor = sum(TransactionType.TRANSFER_IN),
                        transferOutMinor = sum(TransactionType.TRANSFER_OUT),
                        adjustmentInMinor = sum(TransactionType.ADJUSTMENT_IN),
                        adjustmentOutMinor = sum(TransactionType.ADJUSTMENT_OUT),
                    ),
                )
            }
    }

    /** Assets, what is owed and their difference at the end of [month] (today's balances for the current month). */
    fun compositionAt(
        wallets: List<Wallet>,
        transactions: List<Transaction>,
        currency: String,
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): NetWorthComposition =
        if (month >= YearMonth.from(today)) NetWorthComposer.compose(wallets, currency)
        else NetWorthComposer.compose(walletsAt(wallets, transactions, month.atEndOfMonth(), zone), currency)

    /**
     * Net wallet balance at the end of each of the [count] months up to [endMonth], oldest first. Months before any
     * wallet in [currency] existed are left out rather than shown as zero (there was nothing to measure).
     */
    fun lastMonths(
        wallets: List<Wallet>,
        transactions: List<Transaction>,
        currency: String,
        endMonth: YearMonth,
        today: LocalDate,
        count: Int = 3,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MonthBalance> = (count - 1 downTo 0).mapNotNull { back ->
        val month = endMonth.minusMonths(back.toLong())
        val existed = month >= YearMonth.from(today) ||
            walletsAt(wallets, transactions, month.atEndOfMonth(), zone).any { it.currencyCode == currency }
        if (!existed) null else MonthBalance(month, compositionAt(wallets, transactions, currency, month, today, zone).netWorth)
    }

    /**
     * The change from the first month to the last as a whole percentage, only when it means something: the first
     * month's balance must be above zero (a percentage of a negative or zero balance reads backwards). Null otherwise.
     */
    fun trendPercent(history: List<MonthBalance>): Int? {
        val first = history.firstOrNull()?.netWorth ?: return null
        val last = history.last().netWorth
        if (first.amountMinor <= 0L || history.size < 2) return null
        val change = (last.amountMinor - first.amountMinor).toDouble() / first.amountMinor * 100
        return Math.round(change).toInt()
    }

    /**
     * [trendPercent], but only when both months measure the same wallets: a wallet in [currency] added after the first
     * month ended would count its opening balance as growth, so no percentage is shown then.
     */
    fun comparableTrendPercent(
        history: List<MonthBalance>,
        wallets: List<Wallet>,
        currency: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int? {
        if (history.size < 2) return null
        val start = history.first().month.atEndOfMonth()
        val end = history.last().month.atEndOfMonth()
        val added = wallets.any { w ->
            val created = Instant.ofEpochMilli(w.createdAtEpochMillis).atZone(zone).toLocalDate()
            w.currencyCode == currency && created.isAfter(start) && !created.isAfter(end)
        }
        return if (added) null else trendPercent(history)
    }
}
