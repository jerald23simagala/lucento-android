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
        val free = AskAiCopy.allowance(pro = false)
        assertEquals("The free plan includes 5 AI questions a month.", free)
        assertFalse(free, free.contains("day"))
        assertFalse(free, free.contains("Smarter reading"))
    }

    @Test fun `Lucento Pro's allowance is a month's too, shared with Smarter reading`() {
        // Owner, 2026-11-23: 45 a month replaced 50 a day.
        val pro = AskAiCopy.allowance(pro = true)
        assertEquals("Your plan includes 45 AI questions a month, shared with Smarter reading.", pro)
        assertFalse(pro, pro.contains("day"))
    }

    @Test fun `what is left is always this month's`() {
        assertEquals("4 left this month", AskAiCopy.remaining(4))
        assertEquals("44 left this month", AskAiCopy.remaining(44))
    }

    @Test fun `signed out, the hint says where to sign in and what the free plan includes`() {
        assertTrue(AskAiCopy.signInHint(pro = false).contains("Settings › Account & sync"))
        assertTrue(AskAiCopy.signInHint(pro = false).contains("${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} AI questions a month"))
        assertFalse(AskAiCopy.signInHint(pro = true).contains("free plan"))
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
