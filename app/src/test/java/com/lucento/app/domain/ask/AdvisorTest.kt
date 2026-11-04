package com.lucento.app.domain.ask

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Goal
import com.lucento.app.domain.GoalContribution
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/**
 * Ask Lucento as an adviser (owner, 2026-11-04): projections and calculations worked out on the phone ([AskCalc]),
 * transaction patterns ([TransactionSignals]), and the goals, projections and transactions the AI may now see.
 */
class AdvisorTest {

    private fun usd(minor: Long) = Money(minor, "USD")

    private val snapshot = AskSnapshot(
        "USD", AskFixtures.today,
        listOf(
            SnapshotFigure("avg_out", "Average expenses a month", "$2,000.00", amount = usd(200_000)),
            SnapshotFigure("avg_net", "Average net flow a month", "$500.00", amount = usd(50_000)),
            SnapshotFigure("g0_left", "Goal “Trip”: still to save", "$1,250.00", amount = usd(125_000)),
            SnapshotFigure("days_left", "Days left", "16", number = BigDecimal(16)),
            SnapshotFigure("rate", "Card interest rate", "24% a year"),
            SnapshotFigure("eur", "Euro balance", "€100.00", amount = Money(10_000, "EUR")),
            SnapshotFigure("zero", "Nothing", "$0.00", amount = usd(0)),
        ),
    )

    private fun calc(expression: String) = AskCalc.evaluate(expression, snapshot)
    private fun shown(expression: String) = calc(expression)?.let { AskCalc.format(it, Locale.US) }

    // ---- calculations on the phone -----------------------------------------------------------

    @Test fun calculationsUseThePhonesOwnFigures() {
        assertEquals("$24,000.00", shown("avg_out * 12"))
        assertEquals("$200.00", shown("avg_out * 0.1"))
        assertEquals("$125.00", shown("avg_out / days_left"))
        // An amount over an amount is a number: months to a goal, a share.
        assertEquals("2.5", shown("g0_left / avg_net"))
        assertEquals("25", shown("avg_net / avg_out * 100"))
        // Precedence, brackets, unary minus.
        assertEquals("$2,500.00", shown("avg_out + avg_net"))
        assertEquals("$5,000.00", shown("(avg_out + avg_net) * 2"))
        assertEquals("-$1,500.00", shown("-avg_out + avg_net"))
        // Amounts round half-up to the currency's minor unit.
        assertEquals("$666.67", shown("avg_out / 3"))
    }

    @Test fun calculationsThatDontMakeSenseGiveNoResult() {
        assertNull(calc("avg_out + 5")) // an amount plus a plain number
        assertNull(calc("avg_out * avg_net")) // amount × amount
        assertNull(calc("avg_out + eur")) // never across currencies
        assertNull(calc("avg_out / zero")) // division by zero
        assertNull(calc("rate * 2")) // a figure that isn't a number
        assertNull(calc("made_up * 2")) // unknown id
        assertNull(calc("avg_out * 12 )")) // unbalanced
        assertNull(calc("avg_out; drop")) // anything else
        assertNull(calc("1" + " + 1".repeat(60))) // too long
    }

    @Test fun answersShowCalculationsInBoldAndStayVerified() {
        val checked = AnswerCheck.check("Trimming 10% saves about [[= avg_out * 0.1]] a month, [[=avg_out*0.1*12]] a year.", snapshot)
        assertFalse(checked.hasUnverifiedFigures)
        assertEquals(listOf("$200.00", "$2,400.00"), checked.parts.filter { it.isFigure }.map { it.text })
        val broken = AnswerCheck.check("That is [[= avg_out + eur]].", snapshot)
        assertEquals("That is —.", broken.text)
        assertTrue(broken.parts.none { it.isFigure })
    }

    // ---- transaction patterns ----------------------------------------------------------------

    private var nextId = 1L
    private fun expense(minor: Long, date: String, note: String, category: Long? = 10, wallet: Long = 1, created: Long = nextId) =
        Transaction(nextId++, TransactionType.EXPENSE, usd(minor), wallet, category, LocalDate.parse(date), note, created)

    private val today = LocalDate.parse("2026-11-15")

    @Test fun subscriptionsAndRegularBillsAreFound() {
        val history = listOf(
            expense(1_599, "2026-08-03", "Netflix", 30), expense(1_599, "2026-09-03", "NETFLIX", 30),
            expense(1_599, "2026-10-03", "Netflix", 30), expense(1_599, "2026-11-03", "Netflix", 30),
            // Two months in a row at the same price: also a subscription.
            expense(999, "2026-10-08", "Spotify", 30), expense(999, "2026-11-08", "Spotify", 30),
            // Groceries every month, but at very different amounts: not a repeat charge.
            expense(8_000, "2026-09-10", "SM Market"), expense(15_500, "2026-10-10", "SM Market"), expense(3_000, "2026-11-10", "SM Market"),
        )
        val repeats = TransactionSignals.repeating(history, "USD", today)
        assertEquals(listOf("Netflix", "Spotify"), repeats.map { it.name })
        assertEquals(4, repeats.first().months)
        assertEquals(LocalDate.parse("2026-11-03"), repeats.first().latest.date)
    }

    @Test fun unusualChargesAndPossibleDuplicatesAreFound() {
        val dining = (1..6).map { expense(2_000 + it * 100L, "2026-09-${10 + it}", "Lunch $it", category = 12) }
        val big = expense(15_000, "2026-11-12", "Anniversary dinner", category = 12)
        val twice = listOf(expense(4_500, "2026-11-14", "Grab", category = 13, created = 900), expense(4_500, "2026-11-14", "GRAB", category = 13, created = 901))
        val history = dining + big + expense(2_400, "2026-11-13", "Lunch again", category = 12) + twice
        val unusual = TransactionSignals.unusual(history, "USD", today)
        assertEquals(listOf(big.id), unusual.map { it.transaction.id })
        assertEquals(usd(2_400), unusual.single().typical)
        val duplicates = TransactionSignals.duplicates(history, "USD", today)
        assertEquals(1, duplicates.size)
        assertEquals(setOf(twice[0].id, twice[1].id), setOf(duplicates.single().first.id, duplicates.single().second.id))
    }

    @Test fun topPayeesGroupTheSameWordsTogether() {
        val history = listOf(
            expense(4_500, "2026-11-01", "Grab"), expense(3_000, "2026-11-05", "GRAB!"), expense(9_000, "2026-11-06", "Rent"),
            expense(500, "2026-11-07", ""), // no words, no payee
            expense(1_000, "2026-07-01", "Grab"), // older than the window
        )
        val top = TransactionSignals.topPayees(history, "USD", today.minusDays(89), today)
        assertEquals(listOf("Rent", "GRAB!"), top.map { it.name })
        assertEquals(usd(7_500), top[1].total)
        assertEquals(2, top[1].count)
    }

    // ---- what the adviser sees ---------------------------------------------------------------

    @Test fun theSnapshotCarriesProjectionsGoalsAndRecentTransactions() {
        val trip = Goal(1, "Trip to Japan", usd(300_000), LocalDate.parse("2027-06-30"), linkedWalletId = 1, createdAtEpochMillis = 0)
        val inputs = AskFixtures.inputs().copy(
            goals = listOf(trip),
            contributions = listOf(GoalContribution(1, 1, usd(60_000), LocalDate.parse("2026-09-01"), "")),
        )
        val snapshot = AskSnapshotBuilder.build(inputs, "USD")
        val byId = snapshot.byId
        // Projections and averages, with their numbers kept for calculations.
        listOf("pace_day", "proj_out", "days_left", "avg_in", "avg_out", "avg_net", "on_hand").forEach { assertTrue(it, byId.containsKey(it)) }
        assertEquals(BigDecimal(16), byId.getValue("days_left").number)
        // October is the only full month on record: its income and expenses are the averages.
        assertEquals(usd(300_000), byId.getValue("avg_in").amount)
        assertEquals(usd(25_000), byId.getValue("avg_out").amount)
        // Goals.
        assertEquals(usd(240_000), byId.getValue("g0_left").amount)
        assertTrue(byId.getValue("g0_need").label.contains("Trip to Japan"))
        // Debts in depth: the card's monthly interest at 24% a year on 450.00 is 9.00; the loan's instalment.
        val cardIndex = snapshot.figures.first { it.id.endsWith("_owed") && it.label.startsWith("Travel card") }.id.removeSuffix("_owed")
        assertEquals(usd(900), byId.getValue("${cardIndex}_int").amount)
        assertTrue(snapshot.figures.any { it.label == "Car loan monthly instalment" && it.amount == usd(45_000) })
        // Recent transactions, newest first; a transfer appears once, from one account to the other.
        val listed = snapshot.figures.filter { Regex("t\\d+").matches(it.id) }
        // The newest is the loan payment on Nov 8 (the Nov 9 balance adjustment isn't a transaction to talk about).
        assertEquals("2026-11-08", listed.first().label.take(10))
        assertEquals(1, listed.count { it.label.contains("transfer · from Everyday bank to Car loan") })
        assertTrue(listed.none { it.label.contains("Lisbon") })
        assertTrue(listed.any { it.label.contains(AskFixtures.PRIVATE_NOTE) })
        assertTrue(snapshot.figures.size <= AskSnapshotBuilder.MAX_FIGURES)
        assertEquals(snapshot.figures.size, snapshot.figures.map { it.id }.toSet().size)
    }
}
