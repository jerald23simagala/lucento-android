package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtPaymentLinkTest {
    private val loan = 7L
    private val day = LocalDate.of(2026, 10, 1)

    private fun row(id: Long, type: TransactionType, minor: Long, wallet: Long = loan, group: String? = null, key: String? = null) =
        Transaction(id, type, Money(minor, "PHP"), wallet, null, day, "", 0L, transferGroup = group, clientUuid = key)

    @Test
    fun `a payment shows its principal and its interest apart`() {
        val key = "recurring-abc-on-20360"
        val leg = row(1, TransactionType.TRANSFER_IN, 10_500_00, group = key, key = "$key-in")
        val interest = row(2, TransactionType.EXPENSE, 500_00, key = DebtPaymentLink.interestKey(key))
        val links = DebtPaymentLink.interestByPayment(listOf(leg, interest), loan)
        assertEquals(mapOf(key to Money(500_00, "PHP")), links)
        assertEquals(Money(10_000_00, "PHP"), DebtPaymentLink.principalOf(leg, links))
    }

    @Test
    fun `plain repayments and manual interest stay as they are`() {
        val repay = row(1, TransactionType.TRANSFER_IN, 2_000_00, group = "g1", key = "g1-in")
        val fee = row(2, TransactionType.EXPENSE, 150_00, key = "manual-fee")
        val links = DebtPaymentLink.interestByPayment(listOf(repay, fee), loan)
        assertTrue(links.isEmpty())
        assertEquals(Money(2_000_00, "PHP"), DebtPaymentLink.principalOf(repay, links))
    }

    @Test
    fun `interest on another wallet or larger than the payment is never subtracted`() {
        val key = "k"
        val leg = row(1, TransactionType.TRANSFER_IN, 100_00, group = key)
        val elsewhere = row(2, TransactionType.EXPENSE, 50_00, wallet = 99, key = DebtPaymentLink.interestKey(key))
        assertTrue(DebtPaymentLink.interestByPayment(listOf(leg, elsewhere), loan).isEmpty())
        val tooBig = mapOf(key to Money(200_00, "PHP"))
        assertEquals(Money(100_00, "PHP"), DebtPaymentLink.principalOf(leg, tooBig))
    }
}
