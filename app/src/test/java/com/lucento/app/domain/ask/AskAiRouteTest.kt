package com.lucento.app.domain.ask

import org.junit.Assert.assertEquals
import org.junit.Test

/** AI answers are on by default and confirmed once (owner, 2026-11-17): nothing is sent before the person confirms. */
class AskAiRouteTest {

    @Test fun `on by default but not yet confirmed waits for the person and sends nothing`() {
        assertEquals(AskAiRoute.CONFIRM_FIRST, AskAiRoute.of(available = true, on = true, confirmed = false, offTopic = false))
    }

    @Test fun `once confirmed, a question goes to Gemini`() {
        assertEquals(AskAiRoute.GEMINI, AskAiRoute.of(available = true, on = true, confirmed = true, offTopic = false))
    }

    @Test fun `turned off, the phone answers, even after a confirmation`() {
        assertEquals(AskAiRoute.PHONE, AskAiRoute.of(available = true, on = false, confirmed = false, offTopic = false))
        assertEquals(AskAiRoute.PHONE, AskAiRoute.of(available = true, on = false, confirmed = true, offTopic = false))
    }

    @Test fun `signed out or a build without the proxy never asks and never sends`() {
        listOf(true, false).forEach { confirmed ->
            assertEquals(AskAiRoute.PHONE, AskAiRoute.of(available = false, on = true, confirmed = confirmed, offTopic = false))
        }
    }

    @Test fun `a question plainly not about money stays on the phone, without a card`() {
        listOf(true, false).forEach { confirmed ->
            assertEquals(AskAiRoute.PHONE, AskAiRoute.of(available = true, on = true, confirmed = confirmed, offTopic = true))
        }
    }
}
