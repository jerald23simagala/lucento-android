package com.lucento.app.domain

import com.lucento.app.core.money.Money

/**
 * A recorded debt payment is two things written together: a transfer of principal + interest into the debt (its
 * transfer group is the occurrence key) and an interest expense on the debt keyed `<occurrence key>-interest`. This
 * links them back, so the debt's history can show the payment's principal and its interest separately.
 */
object DebtPaymentLink {
    private const val INTEREST_SUFFIX = "-interest"

    fun interestKey(occurrenceKey: String): String = occurrenceKey + INTEREST_SUFFIX

    /** Interest paid inside each payment, by the payment's transfer group. Only interest rows on [walletId] count. */
    fun interestByPayment(history: List<Transaction>, walletId: Long): Map<String, Money> {
        val groups = history.mapNotNullTo(HashSet()) { t -> t.transferGroup?.takeIf { t.walletId == walletId && t.type == TransactionType.TRANSFER_IN } }
        return history.mapNotNull { t ->
            val group = t.clientUuid?.takeIf { t.walletId == walletId && t.type == TransactionType.EXPENSE && it.endsWith(INTEREST_SUFFIX) }
                ?.removeSuffix(INTEREST_SUFFIX)
            if (group != null && group in groups) group to t.amount else null
        }.toMap()
    }

    /** The principal part of a payment leg: what arrived minus the interest paid inside it (never below zero). */
    fun principalOf(leg: Transaction, interestByPayment: Map<String, Money>): Money {
        val interest = leg.transferGroup?.let { interestByPayment[it] } ?: return leg.amount
        if (interest.currencyCode != leg.amount.currencyCode) return leg.amount
        val principal = leg.amount - interest
        return if (principal.isNegative) leg.amount else principal
    }
}
