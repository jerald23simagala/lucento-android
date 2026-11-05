package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.LocalDate

/**
 * How a scheduled item moves money: [INCOME] in, an [EXPENSE] (a bill: spending), or a [TRANSFER] to a card or loan —
 * money leaving an account to pay down a debt, which is not spending — or from a receivable, an instalment someone
 * repays the user, which is not income (docs/CALCULATIONS.md "Scheduled outlook").
 */
enum class ScheduledFlow { INCOME, EXPENSE, TRANSFER }

/**
 * What one occurrence moves, signed from the user's side: + for income and for a collection (an instalment coming back
 * from a receivable), − for a bill or a debt payment (money leaving an account).
 */
val RecurringSchedule.signedOccurrence: Money
    get() = when {
        isCollection -> total
        type == TransactionType.INCOME -> amount
        else -> -total
    }

/** A debt payment or a collection is a transfer; any other expense schedule is a bill, any other income income. */
val RecurringSchedule.flow: ScheduledFlow
    get() = when {
        isLinked -> ScheduledFlow.TRANSFER
        type == TransactionType.INCOME -> ScheduledFlow.INCOME
        else -> ScheduledFlow.EXPENSE
    }

/**
 * One dated entry on the scheduled agenda: an unpaid occurrence of a schedule, or a credit card's payment due (derived
 * from its billing days, [CreditCardMath]). [amount] is always positive; [flow] says which way it goes. Listing an item
 * never records it — only the user's "Mark paid" does.
 */
sealed interface ScheduledItem {
    val date: LocalDate
    val flow: ScheduledFlow
    val amount: Money
    val overdue: Boolean

    /** Occurrence [index] of [schedule]. Only the next unpaid one ([isNext]) can be marked paid; later ones are projections. */
    data class Occurrence(
        val schedule: RecurringSchedule,
        val index: Int,
        override val date: LocalDate,
        override val overdue: Boolean,
    ) : ScheduledItem {
        override val flow: ScheduledFlow get() = schedule.flow
        /** Income as planned; a bill, debt payment or collection in full (its interest included): what moves. */
        override val amount: Money get() = if (schedule.type == TransactionType.INCOME && !schedule.isLinked) schedule.amount else schedule.total
        val isNext: Boolean get() = index == schedule.nextIndex
    }

    /** A card's amount due for its current statement; always a transfer (paying the card is not new spending). */
    data class CardPayment(val due: CardDue) : ScheduledItem {
        override val date: LocalDate get() = due.cycle.dueDate
        override val flow: ScheduledFlow get() = ScheduledFlow.TRANSFER
        override val amount: Money get() = due.cycle.amountDue
        override val overdue: Boolean get() = due.overdue
    }
}

/** Scheduled income, bills and transfers in one currency, never converted. */
data class FlowTotals(
    val currencyCode: String,
    val income: Money,
    val expenses: Money,
    val transfers: Money,
    val count: Int,
    val overdueCount: Int,
) {
    /**
     * Net flow: income − bills. Transfers (paying a card or loan from your own money) move money between your own wallets,
     * so they are shown apart and never change the net (owner, 2026-11-03).
     */
    val net: Money get() = income - expenses

    val isEmpty: Boolean get() = count == 0

    companion object {
        fun zero(currencyCode: String) = FlowTotals(currencyCode, Money.zero(currencyCode), Money.zero(currencyCode), Money.zero(currencyCode), 0, 0)
    }
}

/**
 * The scheduled outlook (docs/CALCULATIONS.md): unpaid occurrences of active schedules in a date range, their totals by
 * flow. Pure and unit-tested. Paused and finished schedules have no occurrences.
 */
object ScheduleOutlook {

    /** A custom range is at most this long, so a weekly plan can't list thousands of rows. */
    const val MAX_RANGE_DAYS = 366L

    /**
     * Unpaid occurrences of [schedule] dated [from]..[to], oldest first. When the range starts on or before [today],
     * every overdue occurrence (before [from] too) is included and flagged: it is still owed.
     */
    fun occurrences(schedule: RecurringSchedule, today: LocalDate, from: LocalDate, to: LocalDate): List<ScheduledItem.Occurrence> {
        if (schedule.paused || to.isBefore(from)) return emptyList()
        val includeOverdue = !from.isAfter(today)
        val result = ArrayList<ScheduledItem.Occurrence>()
        var index = schedule.nextIndex
        var steps = 0
        // Safety stops far above anything a year-long weekly range (or a long-overdue plan) can produce.
        while (result.size < MAX_OCCURRENCES && steps++ < MAX_STEPS) {
            val date = RecurringMath.occurrence(schedule, index) ?: break
            if (date.isAfter(to) || (schedule.endDate != null && date.isAfter(schedule.endDate))) break
            val overdue = date.isBefore(today)
            if (!date.isBefore(from) || (overdue && includeOverdue)) {
                result.add(ScheduledItem.Occurrence(schedule, index, date, overdue))
            }
            index++
            if (index < 0) break
        }
        return result
    }

    /**
     * The agenda for [from]..[to]: every schedule's unpaid occurrences in the range (overdue ones included when the range
     * starts on or before [today]) and the card payments due in it, by date, then name.
     */
    fun items(
        schedules: List<RecurringSchedule>,
        cardDues: List<CardDue>,
        today: LocalDate,
        from: LocalDate,
        to: LocalDate,
    ): List<ScheduledItem> {
        val includeOverdue = !from.isAfter(today)
        val occurrences = schedules.flatMap { occurrences(it, today, from, to) }
        val cards = cardDues
            .filter { !it.cycle.dueDate.isAfter(to) && (!it.cycle.dueDate.isBefore(from) || (it.overdue && includeOverdue)) }
            .map { ScheduledItem.CardPayment(it) }
        return (occurrences + cards).sortedWith(compareBy<ScheduledItem> { it.date }.thenBy { it.sortName() })
    }

    /** Totals of [items] in [currencyCode]; items in other currencies are left out (never converted). */
    fun totals(items: List<ScheduledItem>, currencyCode: String): FlowTotals {
        val inCurrency = items.filter { it.amount.currencyCode == currencyCode }
        fun sum(flow: ScheduledFlow) = Money.sum(currencyCode, inCurrency.filter { it.flow == flow }.map { it.amount })
        return FlowTotals(
            currencyCode = currencyCode,
            income = sum(ScheduledFlow.INCOME),
            expenses = sum(ScheduledFlow.EXPENSE),
            transfers = sum(ScheduledFlow.TRANSFER),
            count = inCurrency.size,
            overdueCount = inCurrency.count { it.overdue },
        )
    }

    /** Currencies other than [currencyCode] that [items] hold, so the page can say "also scheduled in EUR". */
    fun otherCurrencies(items: List<ScheduledItem>, currencyCode: String): List<String> =
        items.map { it.amount.currencyCode }.filter { it != currencyCode }.distinct().sorted()

    private fun ScheduledItem.sortName(): String = when (this) {
        is ScheduledItem.Occurrence -> schedule.name
        is ScheduledItem.CardPayment -> due.wallet.name
    }

    private const val MAX_OCCURRENCES = 1_000
    private const val MAX_STEPS = 100_000
}
