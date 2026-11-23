package com.lucento.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Owner, 2026-11-23: people should see that they are spending their AI questions. A composer reply Gemini read says how
 * many of the questions Smarter reading shares with Ask Lucento are left ("Read by Gemini · …").
 */
class GeminiReplyLabelTest {

    @Test fun `a Gemini reply says how many AI questions are left this month`() {
        assertEquals("Gemini · 12 AI questions left this month", HybridTransactionInterpreter.gemini(12).label)
        assertEquals("Gemini · 1 AI question left this month", HybridTransactionInterpreter.gemini(1).label)
        assertEquals("Gemini · 0 AI questions left this month", HybridTransactionInterpreter.gemini(0).label)
        assertFalse(HybridTransactionInterpreter.gemini(12).onDevice)
    }

    @Test fun `without the server's count it still says Gemini`() {
        assertEquals(HybridTransactionInterpreter.GEMINI, HybridTransactionInterpreter.gemini(null))
    }
}
