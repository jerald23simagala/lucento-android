package com.lucento.app.feature.ask

import com.lucento.app.domain.ask.AskRoute
import com.lucento.app.domain.ask.AskRouter
import com.lucento.app.domain.ask.LocalAnswers
import com.lucento.app.domain.ask.ReportKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The Insights hero's typing cycle starts (and rests, in tests and with reduced motion) on the whole question. */
class AskHeroTypingTest {

    private val length = ASK_HERO_QUESTION.length

    @Test fun theCycleHoldsTheQuestionThenClearsThenTypesItOut() {
        assertEquals(length, typedLetters(0f, length))
        assertEquals(length, typedLetters(0.3f, length))
        assertEquals(0, typedLetters(0.40f, length))
        val halfway = typedLetters(0.715f, length)
        assertEquals(true, halfway in 1 until length)
        // By the end of the cycle the question is (all but) typed out again.
        assertEquals(true, typedLetters(0.9999f, length) >= length - 1)
    }

    /** The Insights hero asks what Ask's own chip asks, and it routes to the same report. */
    @Test fun theHeroAsksWhatAsksOwnChipAsks() {
        assertEquals("What are my top 5 spending categories?", ASK_HERO_QUESTION)
        assertEquals(true, ASK_HERO_QUESTION in LocalAnswers.suggestions)
        assertEquals(
            AskRoute(ReportKind.TOP_CATEGORIES, YearMonth.of(2026, 11), 5),
            AskRouter.route(ASK_HERO_QUESTION, LocalDate.of(2026, 11, 8)),
        )
    }
}
