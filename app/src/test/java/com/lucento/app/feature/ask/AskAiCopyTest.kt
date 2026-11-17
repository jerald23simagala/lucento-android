package com.lucento.app.feature.ask

import com.lucento.app.domain.entitlement.PlanCatalog
import com.lucento.app.feature.legal.LegalDocuments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What AI answers say about the allowance and the first confirmation follows the plan and the Privacy Policy. */
class AskAiCopyTest {

    @Test fun `the free plan's allowance is a month's, with no Smarter reading`() {
        val free = AskAiCopy.allowance(perDay = false)
        assertTrue(free, free.contains("${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} AI questions a month"))
        assertFalse(free, free.contains("day"))
        assertFalse(free, free.contains("Smarter reading"))
    }

    @Test fun `Lucento Pro's allowance is a day's, shared with Smarter reading`() {
        val pro = AskAiCopy.allowance(perDay = true)
        assertTrue(pro, pro.contains("${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY} AI questions a day"))
        assertTrue(pro, pro.contains("shared with Smarter reading"))
    }

    @Test fun `what is left says today only for a daily allowance`() {
        assertEquals("9 left this month", AskAiCopy.remaining(9, perDay = false))
        assertEquals("49 left today", AskAiCopy.remaining(49, perDay = true))
    }

    @Test fun `signed out, the hint says where to sign in and what the free plan includes`() {
        assertTrue(AskAiCopy.signInHint(perDay = false).contains("Settings › Account & sync"))
        assertTrue(AskAiCopy.signInHint(perDay = false).contains("${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} AI questions a month"))
        assertFalse(AskAiCopy.signInHint(perDay = true).contains("free plan"))
    }

    @Test fun `the first-question card says what is sent and where`() {
        assertTrue(AskAiCopy.CONFIRM_BODY.contains("last 60 days"))
        assertTrue(AskAiCopy.CONFIRM_BODY.contains("Gemini"))
        assertTrue(AskAiCopy.DECLINED.startsWith("Nothing was sent"))
    }

    @Test fun `the Privacy Policy says AI answers send nothing until the first question is confirmed`() {
        val ai = LegalDocuments.privacy.sections.single { it.title.startsWith("AI features") }.items.joinToString(" ")
        assertTrue(ai, ai.contains("nothing is sent until you confirm your first question"))
        assertTrue(ai, ai.contains(LegalDocuments.GEMINI_DATA_USE))
        assertTrue("Smarter reading stays an opt-in", ai.contains("Read messages with Gemini (Lucento Pro) is off until you turn it on"))
    }
}
