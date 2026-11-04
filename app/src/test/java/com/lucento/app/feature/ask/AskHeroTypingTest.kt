package com.lucento.app.feature.ask

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
