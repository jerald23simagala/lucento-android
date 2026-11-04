package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The Scheduled page's agenda and totals (docs/CALCULATIONS.md "Scheduled outlook"). */
class ScheduleOutlookTest {

    private fun usd(minor: Long) = Money(minor, "USD")
    private val today = LocalDate.parse("2026-11-03")

    private fun schedule(
        id: Long,
        name: String,
        type: TransactionType,
        minor: Long,
        start: String,
        frequency: Frequency = Frequency.MONTHLY,
        nextIndex: Int = 0,
        paused: Boolean = false,
        toWalletId: Long? = null,
        interest: Long = 0,
        end: String? = null,
        currency: String = "USD",
    ) = RecurringSchedule(
        id, name, type, Money(minor, currency), walletId = 1, categoryId = 7, frequency = frequency, interval = 1,
        startDate = LocalDate.parse(start), endDate = end?.let(LocalDate::parse), nextIndex = nextIndex, paused = paused,
        toWalletId = toWalletId, interest = Money(interest, currency),
    )

    private val salary = schedule(1, "Salary", TransactionType.INCOME, 300_000, "2026-11-15")
    private val rent = schedule(2, "Rent", TransactionType.EXPENSE, 120_000, "2026-11-01") // overdue since Nov 1
    private val gym = schedule(3, "Gym", TransactionType.EXPENSE, 2_500, "2026-11-04", frequency = Frequency.WEEKLY)
    private val loan = schedule(4, "Car loan", TransactionType.EXPENSE, 40_000, "2026-11-20", toWalletId = 9, interest = 5_000)

    private fun cardDue(dueDate: String, minor: Long, overdue: Boolean = false): CardDue {
        val card = Wallet(
            id = 8, name = "Visa", type = WalletType.CREDIT_CARD, balance = usd(minor), createdAtEpochMillis = 0,
            debt = DebtTerms(interestRateBps = 2400, statementDay = 20, dueDay = 10),
        )
        return CardDue(card, CardCycle(LocalDate.parse("2026-10-20"), LocalDate.parse(dueDate), usd(minor), usd(minor)), overdue)
    }

    @Test fun flowSaysIncomeBillOrTransfer() {
        assertEquals(ScheduledFlow.INCOME, salary.flow)
        assertEquals(ScheduledFlow.EXPENSE, rent.flow)
        assertEquals(ScheduledFlow.TRANSFER, loan.flow)
    }

    @Test fun occurrencesListEveryUnpaidDateInRangeAndKeepOverdueOnes() {
        val weekly = ScheduleOutlook.occurrences(gym, today, today, LocalDate.parse("2026-11-30"))
        assertEquals(listOf("2026-11-04", "2026-11-11", "2026-11-18", "2026-11-25").map(LocalDate::parse), weekly.map { it.date })
        assertTrue(weekly.first().isNext)
        assertTrue(weekly.drop(1).none { it.isNext })
        // Rent was due Nov 1 and isn't paid: it's in this month's list, flagged, though the range starts today.
        val overdue = ScheduleOutlook.occurrences(rent, today, today, LocalDate.parse("2026-11-30"))
        assertEquals(1, overdue.size)
        assertTrue(overdue.single().overdue)
        // A range that starts later leaves overdue ones out: it is a look ahead.
        assertTrue(ScheduleOutlook.occurrences(rent, today, LocalDate.parse("2026-12-05"), LocalDate.parse("2026-12-31")).isEmpty())
    }

    @Test fun pausedEndedAndPaidOccurrencesAreNotListed() {
        assertTrue(ScheduleOutlook.occurrences(gym.copy(paused = true), today, today, LocalDate.parse("2026-12-31")).isEmpty())
        val ending = gym.copy(endDate = LocalDate.parse("2026-11-12"))
        assertEquals(2, ScheduleOutlook.occurrences(ending, today, today, LocalDate.parse("2026-12-31")).size)
        // Rent's November occurrence was paid (next index 1): only December's remains.
        val paid = ScheduleOutlook.occurrences(rent.copy(nextIndex = 1), today, today, LocalDate.parse("2026-12-31"))
        assertEquals(listOf(LocalDate.parse("2026-12-01")), paid.map { it.date })
    }

    @Test fun agendaMergesCardPaymentsByDateAndTotalsByFlow() {
        val items = ScheduleOutlook.items(listOf(salary, rent, gym, loan), listOf(cardDue("2026-11-10", 45_000)), today, today, LocalDate.parse("2026-11-30"))
        assertEquals("Rent", (items.first() as ScheduledItem.Occurrence).schedule.name)
        assertTrue(items.any { it is ScheduledItem.CardPayment })
        assertEquals(items.sortedBy { it.date }.map { it.date }, items.map { it.date })
        val totals = ScheduleOutlook.totals(items, "USD")
        assertEquals(usd(300_000), totals.income)
        // Bills: rent 1,200 + four gym visits at 25.
        assertEquals(usd(120_000 + 4 * 2_500), totals.expenses)
        // Transfers: the loan payment in full (principal 400 + interest 50) and the card's 450 due.
        assertEquals(usd(45_000 + 45_000), totals.transfers)
        // Transfers stay apart: they move money between the user's own wallets and never change the net.
        assertEquals(totals.income - totals.expenses, totals.net)
        assertEquals(1, totals.overdueCount)
        assertEquals(items.size, totals.count)
    }

    @Test fun totalsNeverMixCurrencies() {
        val euro = schedule(5, "Flat in Lisbon", TransactionType.EXPENSE, 80_000, "2026-11-10", currency = "EUR")
        val items = ScheduleOutlook.items(listOf(rent, euro), emptyList(), today, today, LocalDate.parse("2026-11-30"))
        val totals = ScheduleOutlook.totals(items, "USD")
        assertEquals(usd(120_000), totals.expenses)
        assertEquals(1, totals.count)
        assertEquals(listOf("EUR"), ScheduleOutlook.otherCurrencies(items, "USD"))
    }

    @Test fun aThreeMonthRangeCarriesOverdueAndOnlyTheCurrentCardStatement() {
        val end = YearMonth.of(2027, 1).atEndOfMonth()
        val items = ScheduleOutlook.items(listOf(salary, rent, loan), listOf(cardDue("2026-12-10", 30_000)), today, today, end)
        val totals = ScheduleOutlook.totals(items, "USD")
        // Three salaries, the overdue rent and two more, three loan payments and the card's one known statement.
        assertEquals(usd(3 * 300_000), totals.income)
        assertEquals(usd(3 * 120_000), totals.expenses)
        assertEquals(usd(3 * 45_000 + 30_000), totals.transfers)
        assertEquals(usd(3 * 300_000 - 3 * 120_000), totals.net)
        assertEquals(1, totals.overdueCount)
    }

    @Test fun nothingScheduledTotalsZero() {
        val totals = ScheduleOutlook.totals(emptyList(), "USD")
        assertTrue(totals.isEmpty && totals.net.isZero && totals.transfers.isZero)
    }
}
