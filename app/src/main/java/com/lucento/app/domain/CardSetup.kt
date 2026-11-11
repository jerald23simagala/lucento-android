package com.lucento.app.domain

import com.lucento.app.core.money.Money

/**
 * A new credit card's opening balance split into what is due now and what isn't billed yet (tester round 3).
 *
 * The balance entered at setup is everything owed on the card; usually only part of it is on the latest statement, and
 * the rest is billed next time. [CreditCardMath.cycle] treats charges dated after the statement as next statement's, so
 * the part not yet due is recorded as one balance adjustment ([NOTE]) dated the setup day: the card still owes the full
 * balance, Payments and Home's reminders ask only for the amount due, and the rest joins the next statement by itself.
 * No new field is stored and nothing counts as spending.
 */
object CardSetup {
    const val NOTE = "Not yet due at setup"

    sealed interface Split {
        /** Everything is due now (no amount due given, or it equals the balance): nothing extra is recorded. */
        data object AllDue : Split

        /** [unbilled] of the balance isn't due yet: the card opens at the amount due, plus [unbilled] dated the setup day. */
        data class PartlyDue(val dueNow: Money, val unbilled: Money) : Split

        /** The amount due is more than is owed on the card, or below zero. */
        data object TooHigh : Split
    }

    /** How [balance] (what is owed on the card) splits when [amountDue] of it is due now; null [amountDue] means all of it. */
    fun split(balance: Money, amountDue: Money?): Split {
        if (amountDue == null) return Split.AllDue
        require(balance.currencyCode == amountDue.currencyCode) { "Currencies differ" }
        if (amountDue.isNegative || amountDue.amountMinor > balance.amountMinor) return Split.TooHigh
        if (amountDue.amountMinor == balance.amountMinor) return Split.AllDue
        return Split.PartlyDue(dueNow = amountDue, unbilled = balance - amountDue)
    }

    /** True for the adjustment [split] records: an outflow (more owed) on a card, with [NOTE]. */
    fun isUnbilledOpening(transaction: Transaction): Boolean =
        transaction.type == TransactionType.ADJUSTMENT_OUT && transaction.note == NOTE
}
