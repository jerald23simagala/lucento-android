package com.lucento.app.domain

import com.lucento.app.core.money.Money
import com.lucento.app.domain.fx.FxTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The progress awards (owner, 2026-11-23): Steady Month, Goal Reached and Loan Cleared, and how an update adds them. */
class ProgressAwardsTest {
    private val today = LocalDate.parse("2026-11-15")
    private fun php(minor: Long) = Money(minor, "PHP")
    private val food = Category(4, "Food", CategoryKind.EXPENSE, "groceries")
    private val budgets = listOf(Budget(1, 4, php(10_000), createdOn = LocalDate.parse("2026-08-15")))
    private val bank = Wallet(1, "Bank", WalletType.BANK, php(0), 0)
    private fun spend(minor: Long, date: String) =
        Transaction(minor, TransactionType.EXPENSE, php(minor), 1, 4, LocalDate.parse(date), "", 0)

    private fun steady(txs: List<Transaction>, rows: List<Budget> = budgets) =
        Achievements.steadyMonth(listOf(food), rows, FxTranslation.translate("PHP", listOf(bank), txs), "PHP", today)

    @Test fun `steady month needs a finished month within a budget that covered all of it`() {
        assertTrue("September: ₱90 of ₱100", steady(listOf(spend(9_000, "2026-09-10"))))
        assertFalse("October over, and November isn't finished", steady(listOf(spend(10_001, "2026-10-10"), spend(1, "2026-11-02"))))
        // August's budget began mid-month, so August isn't judged.
        assertFalse(steady(listOf(spend(1_000, "2026-08-20"))))
        assertFalse("no budget at all", steady(listOf(spend(1_000, "2026-09-10")), rows = emptyList()))
    }

    private val trip = Goal(1, "Trip", php(20_000), null, null, 0)
    private fun put(id: Long, minor: Long, date: String) = GoalContribution(id, 1, php(minor), LocalDate.parse(date), "")

    @Test fun `a goal reached stays reached when money is taken back later`() {
        assertTrue(Achievements.everReached(trip, listOf(put(1, 15_000, "2026-09-01"), put(2, 5_000, "2026-10-01"), put(3, -8_000, "2026-11-01"))))
        assertFalse(Achievements.everReached(trip, listOf(put(1, 15_000, "2026-09-01"), put(2, 4_999, "2026-10-01"))))
        // Order is by date: a withdrawal first doesn't count against a later top-up.
        assertTrue(Achievements.everReached(trip, listOf(put(2, 25_000, "2026-10-01"), put(1, -5_000, "2026-09-01"))))
    }

    @Test fun `a loan paid down to nothing, with a payment recorded, is cleared, but a card paid in full is not`() {
        val paidLoan = Wallet(2, "Car loan", WalletType.LOAN, php(0), 0, openingBalance = php(100_000))
        val newLoan = Wallet(3, "New loan", WalletType.LOAN, php(0), 0)
        val card = Wallet(4, "Card", WalletType.CREDIT_CARD, php(0), 0, openingBalance = php(5_000))
        val payments = listOf(
            Transaction(10, TransactionType.TRANSFER_IN, php(100_000), 2, null, LocalDate.parse("2026-10-01"), "", 0, transferGroup = "a"),
            Transaction(11, TransactionType.TRANSFER_IN, php(5_000), 4, null, LocalDate.parse("2026-10-01"), "", 0, transferGroup = "b"),
        )
        val facts = Achievements.facts(payments, listOf(bank, paidLoan, newLoan, card), listOf(food), budgets, emptyList(), today, visitedInsights = false)
        assertEquals(1, facts.loansCleared)
        assertTrue(Achievement.LOAN_CLEARED in Achievements.earned(facts))
    }

    @Test fun `the progress awards come from the same facts as their progress`() {
        val facts = ProgressFacts(0, 0, 0, 0, 0, visitedInsights = false, steadyMonth = true, goalsReached = 1, loansCleared = 0)
        val earned = Achievements.earned(facts)
        assertTrue(Achievement.STEADY_MONTH in earned)
        assertTrue(Achievement.GOAL_REACHED in earned)
        assertFalse(Achievement.LOAN_CLEARED in earned)
        Achievement.entries.forEach { a -> assertEquals(a.name, a in earned, Achievements.progress(a, facts).earned) }
    }

    @Test fun `an update's new awards are recorded quietly, never presented for the past`() {
        // A phone from before the progress awards knew the first seven.
        assertEquals(setOf(Achievement.STEADY_MONTH, Achievement.GOAL_REACHED, Achievement.LOAN_CLEARED), Achievements.newToThisPhone(null))
        assertTrue(Achievements.newToThisPhone(Achievement.entries.map { it.key }.toSet()).isEmpty())
        // A key from a newer build is ignored, not a crash.
        assertEquals(
            setOf(Achievement.LOAN_CLEARED),
            Achievements.newToThisPhone((Achievement.entries - Achievement.LOAN_CLEARED).map { it.key }.toSet() + "from_the_future"),
        )
        val earned = setOf(Achievement.MONEY_KEEPER, Achievement.STEADY_MONTH)
        val newHere = Achievements.newToThisPhone(null)
        // Steady Month waits for the quiet record; a newly earned old award is still presented.
        assertEquals(listOf(Achievement.MONEY_KEEPER), Achievements.toAcknowledgeAll(earned, emptySet(), newHere))
        assertNull(Achievements.toAcknowledge(earned, setOf(Achievement.MONEY_KEEPER), newHere))
        // Once recorded, nothing is left to present.
        assertNull(Achievements.toAcknowledge(earned, earned, emptySet()))
    }

    @Test fun `the cabinet never takes an award away`() {
        val shown = Achievements.shownEarned(earned = setOf(Achievement.FIRST_STEP), acknowledged = setOf(Achievement.GOAL_SETTER))
        assertEquals(setOf(Achievement.FIRST_STEP, Achievement.GOAL_SETTER), shown)
        assertEquals(setOf(Achievement.FIRST_STEP), Achievements.shownEarned(setOf(Achievement.FIRST_STEP), acknowledged = null))
    }
}
