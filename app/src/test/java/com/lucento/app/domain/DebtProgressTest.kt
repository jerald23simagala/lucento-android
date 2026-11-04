package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Credit card billing cycles and instalment-loan progress (docs/CALCULATIONS.md). */
class DebtProgressTest {

    private fun usd(minor: Long) = Money(minor, "USD")
    private val today = LocalDate.parse("2026-11-03")

    private fun card(owed: Long, statementDay: Int? = 15, dueDay: Int? = 5, limit: Long? = 500_000, id: Long = 1) = Wallet(
        id = id, name = "Visa", type = WalletType.CREDIT_CARD, balance = usd(owed), createdAtEpochMillis = 0,
        openingBalance = usd(owed), debt = DebtTerms(interestRateBps = 2400, creditLimit = limit?.let(::usd), statementDay = statementDay, dueDay = dueDay),
    )

    private var nextId = 1L
    private fun tx(type: TransactionType, minor: Long, date: String, wallet: Long = 1) = Transaction(
        id = nextId++, type = type, amount = usd(minor), walletId = wallet, categoryId = null, date = LocalDate.parse(date), note = "",
        createdAtEpochMillis = 0,
    )

    @Test fun billingDaysClampToShortMonthsAndTheDueDateFollowsTheStatement() {
        assertEquals(LocalDate.parse("2026-02-28"), CreditCardMath.dayOf(YearMonth.of(2026, 2), 31))
        assertEquals(LocalDate.parse("2028-02-29"), CreditCardMath.dayOf(YearMonth.of(2028, 2), 30))
        // Statement on the 15th: before the 15th the last statement is last month's.
        assertEquals(LocalDate.parse("2026-10-15"), CreditCardMath.lastStatementDate(15, today))
        assertEquals(LocalDate.parse("2026-11-03"), CreditCardMath.lastStatementDate(3, today))
        assertEquals(LocalDate.parse("2026-10-31"), CreditCardMath.lastStatementDate(31, today))
        // Due on the 5th after an Oct 15 statement: Nov 5. Due on the 25th after it: Oct 25 (same month, later day).
        assertEquals(LocalDate.parse("2026-11-05"), CreditCardMath.dueDateAfter(LocalDate.parse("2026-10-15"), 5))
        assertEquals(LocalDate.parse("2026-10-25"), CreditCardMath.dueDateAfter(LocalDate.parse("2026-10-15"), 25))
        // The same day as the statement is next month's.
        assertEquals(LocalDate.parse("2026-11-15"), CreditCardMath.dueDateAfter(LocalDate.parse("2026-10-15"), 15))
        // A Jan 31 statement with a 30th due day lands on the last day of February.
        assertEquals(LocalDate.parse("2026-02-28"), CreditCardMath.dueDateAfter(LocalDate.parse("2026-01-31"), 30))
    }

    @Test fun amountDueIsTheStatementBalanceLessPaymentsSince() {
        // Owed now 1,000.00. Since the Oct 15 statement: 300.00 of purchases and a 200.00 payment.
        val wallet = card(owed = 100_000)
        val history = listOf(
            tx(TransactionType.EXPENSE, 45_000, "2026-10-10"), // before the statement: on it
            tx(TransactionType.EXPENSE, 30_000, "2026-10-20"),
            tx(TransactionType.TRANSFER_IN, 20_000, "2026-10-28"),
            tx(TransactionType.EXPENSE, 99_999, "2026-10-20", wallet = 2), // another wallet: ignored
        )
        val cycle = CreditCardMath.cycle(wallet, history, today)!!
        assertEquals(LocalDate.parse("2026-10-15"), cycle.statementDate)
        assertEquals(LocalDate.parse("2026-11-05"), cycle.dueDate)
        // On the statement: 1,000 − 300 + 200 = 900. Due now: 900 − 200 paid = 700.
        assertEquals(usd(90_000), cycle.statementBalance)
        assertEquals(usd(70_000), cycle.amountDue)
        assertFalse(cycle.isOverdue(today))
        assertTrue(cycle.isOverdue(LocalDate.parse("2026-11-06")))
    }

    @Test fun overpayingLeavesNothingDueAndNothingOverdue() {
        val wallet = card(owed = 10_000)
        val history = listOf(tx(TransactionType.EXPENSE, 30_000, "2026-10-20"), tx(TransactionType.TRANSFER_IN, 50_000, "2026-10-25"))
        val cycle = CreditCardMath.cycle(wallet, history, today)!!
        assertEquals(usd(0), cycle.amountDue)
        assertFalse(cycle.isOverdue(LocalDate.parse("2026-12-01")))
        assertTrue(CreditCardMath.dues(listOf(wallet), history, today, today.plusDays(30)).isEmpty())
    }

    @Test fun onlyCardsWithBothBillingDaysHaveACycle() {
        assertNull(CreditCardMath.cycle(card(owed = 1_000, statementDay = null), emptyList(), today))
        assertNull(CreditCardMath.cycle(card(owed = 1_000, dueDay = null), emptyList(), today))
        val loan = card(owed = 1_000).copy(type = WalletType.LOAN)
        assertNull(CreditCardMath.cycle(loan, emptyList(), today))
    }

    @Test fun availableCreditIsTheLimitLessWhatIsOwedAndGoesNegativeOverTheLimit() {
        assertEquals(usd(400_000), CreditCardMath.available(card(owed = 100_000)))
        assertEquals(usd(-5_000), CreditCardMath.available(card(owed = 505_000)))
        assertNull(CreditCardMath.available(card(owed = 100_000, limit = null)))
    }

    @Test fun duesListCardsWithSomethingToPayByTheDateSoonestFirst() {
        val visa = card(owed = 50_000, statementDay = 15, dueDay = 5, id = 1) // due Nov 5
        val amex = card(owed = 20_000, statementDay = 1, dueDay = 20, id = 2) // statement Nov 1, due Nov 20
        val later = card(owed = 20_000, statementDay = 2, dueDay = 28, id = 3).copy(name = "Late") // due Nov 28
        val dues = CreditCardMath.dues(listOf(later, amex, visa), emptyList(), today, LocalDate.parse("2026-11-25"))
        assertEquals(listOf(1L, 2L), dues.map { it.wallet.id })
        assertEquals(usd(50_000), dues.first().cycle.amountDue)
        // An unpaid statement past its due day is listed as overdue.
        val overdue = CreditCardMath.dues(listOf(visa), emptyList(), LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-30")).single()
        assertTrue(overdue.overdue)
    }

    private fun loan(opening: Long, owed: Long, months: Int? = 24, repayment: DebtRepayment = DebtRepayment.MONTHLY) = Wallet(
        id = 7, name = "Car loan", type = WalletType.LOAN, balance = usd(owed), createdAtEpochMillis = 0, openingBalance = usd(opening),
        debt = DebtTerms(interestRateBps = 2400, repayment = repayment, termMonths = months, installment = if (repayment == DebtRepayment.MONTHLY) usd(63_444) else null),
    )

    @Test fun loanProgressCountsRepaymentsAndPrincipalRepaid() {
        val repayments = (1..3).map { tx(TransactionType.TRANSFER_IN, 40_000, "2026-0$it-01", wallet = 7) } +
            tx(TransactionType.EXPENSE, 5_000, "2026-03-01", wallet = 7) // interest added: not an instalment
        val progress = LoanMath.progress(loan(opening = 1_200_000, owed = 1_085_000), repayments)!!
        assertEquals(usd(1_200_000), progress.principal)
        assertEquals(usd(115_000), progress.paid)
        assertEquals(usd(1_085_000), progress.remaining)
        assertEquals(progress.principal - progress.paid, progress.remaining)
        assertEquals(3, progress.installmentsPaid)
        assertEquals(24, progress.installments)
    }

    @Test fun loanProgressCapsAtTheTermAndAPaidOffLoanIsFullyPaid() {
        val many = (1..30).map { tx(TransactionType.TRANSFER_IN, 1_000, "2026-01-01", wallet = 7) }
        assertEquals(24, LoanMath.progress(loan(1_200_000, 600_000), many)!!.installmentsPaid)
        val paidOff = LoanMath.progress(loan(1_200_000, 0), emptyList())!!
        assertEquals(24, paidOff.installmentsPaid)
        assertEquals(usd(1_200_000), paidOff.paid)
        // More owed than at the start (charges added): nothing counts as repaid, never a negative figure.
        assertEquals(usd(0), LoanMath.progress(loan(1_000, 1_500), emptyList())!!.paid)
    }

    @Test fun onlyInstalmentLoansHaveProgress() {
        assertNull(LoanMath.progress(loan(1_000, 500, repayment = DebtRepayment.ONE_TIME), emptyList()))
        assertNull(LoanMath.progress(loan(1_000, 500, months = null), emptyList()))
        assertNull(LoanMath.progress(card(owed = 1_000), emptyList()))
    }

    @Test fun termsRejectOutOfRangeCardValues() {
        listOf(
            { DebtTerms(creditLimit = usd(0)) },
            { DebtTerms(statementDay = 0) },
            { DebtTerms(dueDay = 32) },
        ).forEach { make -> assertTrue(runCatching(make).isFailure) }
        assertTrue(DebtTerms(statementDay = 31, dueDay = 1).hasBillingCycle)
        assertFalse(DebtTerms(statementDay = 31).hasBillingCycle)
    }
}
