package com.lucento.app.domain.ask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Ask Lucento's safeguards: local routing, what the AI may see and the check on every answer. */
class AskTest {

    private val today = AskFixtures.today
    private val november = YearMonth.of(2026, 11)

    // ---- routing on the phone ----------------------------------------------------------------

    @Test fun presetQuestionsRouteToTheirReports() {
        assertEquals(AskRoute(ReportKind.TOP_CATEGORIES, november, 5), AskRouter.route("What’s my top 5 spending categories?", today))
        assertEquals(AskRoute(ReportKind.TOP_CATEGORIES, november.minusMonths(1), 3), AskRouter.route("Top 3 categories last month", today))
        assertEquals(ReportKind.NET_WORTH, AskRouter.route("What's my net worth?", today)?.kind)
        assertEquals(ReportKind.BUDGET_VS_ACTUAL, AskRouter.route("Am I on budget this month?", today)?.kind)
        assertEquals(ReportKind.DEBTS, AskRouter.route("How are my debts doing?", today)?.kind)
        assertEquals(ReportKind.CASH_FLOW, AskRouter.route("How much did I spend in October?", today)?.kind)
        LocalAnswers.suggestions.forEach { assertTrue(it, AskRouter.route(it, today) != null) }
    }

    @Test fun monthsNamedInTheQuestionAreThePastOnes() {
        assertEquals(YearMonth.of(2026, 10), AskRouter.route("cash flow for October", today)?.month)
        // December is still ahead this year, so it means last December.
        assertEquals(YearMonth.of(2025, 12), AskRouter.route("budget in December", today)?.month)
        // "May I…" isn't the month of May.
        assertEquals(november, AskRouter.route("May I see my budget?", today)?.month)
        assertEquals(YearMonth.of(2026, 5), AskRouter.route("budget for May", today)?.month)
    }

    @Test fun otherQuestionsNeedTheAi() {
        assertNull(AskRouter.route("Tell me a joke", today))
        assertNull(AskRouter.route("Should I buy a new phone?", today))
    }

    // ---- what the AI may see -----------------------------------------------------------------

    @Test fun theSnapshotHoldsTheMainCurrencyOnly() {
        val snapshot = AskSnapshotBuilder.build(AskFixtures.inputs(), "USD")
        val text = snapshot.figures.joinToString("\n") { "${it.id} ${it.label} ${it.value}" }
        // Owner, 2026-11-04: transactions (with their notes) are sent under the v2 consent; other currencies never are.
        assertTrue(text, AskFixtures.PRIVATE_NOTE in text)
        assertFalse(text, "Lisbon" in text)
        assertFalse(text, "€" in text)
        assertEquals(snapshot.figures.size, snapshot.figures.map { it.id }.toSet().size)
        assertTrue(snapshot.figures.size <= AskSnapshotBuilder.MAX_FIGURES)
        assertTrue(snapshot.byId.containsKey("nw"))
        assertTrue(snapshot.byId.containsKey("in_202611"))
        assertTrue(snapshot.byId.getValue("cat0_0").label.contains("Rent"))
        assertTrue(snapshot.byId.containsKey("bud_total"))
        assertTrue(snapshot.byId.containsKey("sch_tr"))
    }

    // ---- the check on every answer -----------------------------------------------------------

    private val snapshot = AskSnapshot("USD", today, listOf(SnapshotFigure("nw", "Net worth today", "$1,000.00")))

    @Test fun figureIdsAreFilledInFromThePhone() {
        val checked = AnswerCheck.check("Your net worth is [[nw]].", snapshot)
        assertEquals("Your net worth is $1,000.00.", checked.text)
        assertTrue(checked.parts.single { it.isFigure }.text == "$1,000.00")
        assertFalse(checked.hasUnverifiedFigures)
    }

    @Test fun unknownIdsShowADashNotAGuess() {
        val checked = AnswerCheck.check("That was [[made_up]].", snapshot)
        assertEquals("That was —.", checked.text)
        assertTrue(checked.parts.none { it.isFigure })
    }

    @Test fun amountsTheModelWroteItselfAreFlagged() {
        assertTrue(AnswerCheck.check("You spent $45.20 on coffee.", snapshot).hasUnverifiedFigures)
        assertTrue(AnswerCheck.check("About 1,250 went to rent.", snapshot).hasUnverifiedFigures)
        assertTrue(AnswerCheck.check("Around USD 300 a month.", snapshot).hasUnverifiedFigures)
        // Plain counts, percentages and dates are fine.
        assertFalse(AnswerCheck.check("Spending rose 12% across 3 months since November 3, 2026.", snapshot).hasUnverifiedFigures)
    }

    @Test fun linksAndMarkdownAreRemovedAndLengthCapped() {
        val checked = AnswerCheck.check("**Look** at https://evil.example/x now " + "a".repeat(3_000), snapshot)
        assertFalse(checked.text.contains("http"))
        assertFalse(checked.text.contains("**"))
        assertTrue(checked.text.length <= AnswerCheck.MAX_ANSWER)
    }

    @Test fun followUpsAreShortPlainAndAtMostThree() {
        val out = AnswerCheck.followUps(listOf("What about October?", "", "x".repeat(200), "Visit www.example.com", "Net worth [[nw]]?", "Budget?", "Debts?"))
        assertEquals(3, out.size)
        assertTrue(out.none { "[[" in it || "www." in it })
    }
}
