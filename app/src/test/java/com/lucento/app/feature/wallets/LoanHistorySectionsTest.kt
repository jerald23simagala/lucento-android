package com.lucento.app.feature.wallets

import com.lucento.app.core.money.Money
import com.lucento.app.domain.DebtPaymentLink
import com.lucento.app.domain.LoanRelease
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * A loan's history sections (tester round, 2026-11-27): the charges deducted when it was borrowed count as borrowed, so
 * they sit under Principal with the proceeds, and the section adds up to the note's "borrowed" figure.
 */
class LoanHistorySectionsTest {

    private var nextId = 1L
    private fun tx(type: TransactionType, minor: Long, group: String? = null, key: String? = null, wallet: Long = 7) = Transaction(
        id = nextId++, type = type, amount = Money(minor, "PHP"), walletId = wallet, categoryId = null,
        date = LocalDate.parse("2026-11-27"), note = "", createdAtEpochMillis = 0, transferGroup = group, clientUuid = key,
    )

    @Test fun releaseChargesSitWithThePrincipalAndPaymentInterestApart() {
        val proceeds = tx(TransactionType.TRANSFER_OUT, 95_000_00, group = "g1", key = "g1-out")
        val charges = tx(TransactionType.EXPENSE, 5_000_00, key = LoanRelease.chargesKey("g1"))
        val payment = tx(TransactionType.TRANSFER_IN, 9_000_00, group = "p1", key = "p1-in")
        val interest = tx(TransactionType.EXPENSE, 1_000_00, key = DebtPaymentLink.interestKey("p1"))
        val lateFee = tx(TransactionType.EXPENSE, 500_00, key = "fee")
        val history = listOf(lateFee, interest, payment, charges, proceeds)
        val parts = loanHistorySections(history, walletId = 7, paidInterestKeys = setOf(DebtPaymentLink.interestKey("p1")))
        assertEquals(listOf(payment, charges, proceeds), parts.principal)
        assertEquals(listOf(interest), parts.interestPaid)
        assertEquals(listOf(lateFee), parts.interestAndFees)
    }

    @Test fun withoutAReleaseEveryExpenseIsInterestOrFees() {
        val fee = tx(TransactionType.EXPENSE, 500_00, key = "fee")
        // A charges key whose transfer isn't on this loan doesn't count as borrowed.
        val stray = tx(TransactionType.EXPENSE, 300_00, key = LoanRelease.chargesKey("elsewhere"))
        val parts = loanHistorySections(listOf(fee, stray), walletId = 7, paidInterestKeys = emptySet())
        assertEquals(emptyList<Transaction>(), parts.principal)
        assertEquals(listOf(fee, stray), parts.interestAndFees)
    }
}
