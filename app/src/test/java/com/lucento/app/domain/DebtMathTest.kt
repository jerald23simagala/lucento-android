package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DebtMathTest {

    @Test fun parsesYearlyRatesExactlyIntoBasisPoints() {
        assertEquals(DebtMath.RateResult.Valid(1250), DebtMath.parseRate("12.5"))
        assertEquals(DebtMath.RateResult.Valid(725), DebtMath.parseRate(" 7,25 % "))
        assertEquals(DebtMath.RateResult.Valid(1200), DebtMath.parseRate("12"))
        assertEquals(DebtMath.RateResult.Valid(0), DebtMath.parseRate("0"))
        assertEquals(DebtMath.RateResult.Valid(10_000), DebtMath.parseRate("100"))
        assertEquals(DebtMath.RateResult.Empty, DebtMath.parseRate("  "))
    }

    @Test fun rejectsRatesThatCantBeRight() {
        for (bad in listOf("100.01", "250", "1.234", "-3", "abc", "1.2.3", "1e2")) {
            assertEquals(bad, DebtMath.RateResult.Invalid, DebtMath.parseRate(bad))
        }
    }

    @Test fun formatsRatesWithoutTrailingZeros() {
        assertEquals("12.5%", DebtMath.formatRate(1250))
        assertEquals("12%", DebtMath.formatRate(1200))
        assertEquals("0.75%", DebtMath.formatRate(75))
    }

    @Test fun amortisesWithTheStandardAnnuityFormula() {
        // 10,000.00 at 12 % a year (1 % a month) over 12 months: 888.49 a month.
        assertEquals(Money(88_849, "USD"), DebtMath.monthlyInstallment(Money(1_000_000, "USD"), 12, 1200))
        // 5,000.00 at 12.5 % over 24 months: 236.54.
        assertEquals(Money(23_654, "USD"), DebtMath.monthlyInstallment(Money(500_000, "USD"), 24, 1250))
    }

    @Test fun withoutInterestInstalmentsRoundUpSoTheyNeverFallShort() {
        assertEquals(Money(33_334, "USD"), DebtMath.monthlyInstallment(Money(1_000_000, "USD"), 30, null))
        assertEquals(Money(33_334, "USD"), DebtMath.monthlyInstallment(Money(1_000_000, "USD"), 30, 0))
        assertEquals(Money(3_334, "JPY"), DebtMath.monthlyInstallment(Money(10_000, "JPY"), 3, null))
    }

    @Test fun noInstalmentForNothingOwed() {
        assertNull(DebtMath.monthlyInstallment(Money(0, "USD"), 12, 1200))
        assertNull(DebtMath.monthlyInstallment(Money(-100, "USD"), 12, 1200))
    }

    @Test fun estimatesAMonthsInterestHalfUp() {
        assertEquals(Money(5_208, "USD"), DebtMath.monthlyInterest(Money(500_000, "USD"), 1250))
        assertEquals(Money(0, "USD"), DebtMath.monthlyInterest(Money(-500, "USD"), 1250))
    }

    @Test fun namesTheReminderAfterTheFrequencyAndDebt() {
        assertEquals("Monthly Payment - Car loan", DebtMath.reminderName(DebtRepayment.MONTHLY, " Car loan ", 40))
        assertEquals("One-time Payment - Visa", DebtMath.reminderName(DebtRepayment.ONE_TIME, "Visa", 40))
        assertEquals(10, DebtMath.reminderName(DebtRepayment.MONTHLY, "Car loan", 10).length)
    }

    @Test fun debtPaymentsCountInFullForCashButOnlyInterestAsSpending() {
        val start = LocalDate.parse("2026-11-01")
        val payment = RecurringSchedule(
            id = 1, name = "Loan", type = TransactionType.EXPENSE, amount = Money(20_000, "USD"), walletId = 1, categoryId = 9,
            frequency = Frequency.MONTHLY, interval = 1, startDate = start, endDate = null, nextIndex = 0, paused = false,
            toWalletId = 2, interest = Money(5_000, "USD"),
        )
        assertEquals(Money(25_000, "USD"), RecurringMath.committedExpenses(listOf(payment), "USD", start))
        assertEquals(Money(5_000, "USD"), RecurringMath.committedExpenses(listOf(payment), "USD", start, spendingOnly = true))
    }
}
