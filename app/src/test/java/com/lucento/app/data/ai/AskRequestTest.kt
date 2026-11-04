package com.lucento.app.data.ai

import com.lucento.app.domain.ask.AskSnapshot
import com.lucento.app.domain.ask.SnapshotFigure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** What Ask Lucento sends to the proxy: the question capped, the last turns only, and the snapshot's figures as they are. */
class AskRequestTest {

    private val snapshot = AskSnapshot("PHP", LocalDate.parse("2026-11-15"), listOf(SnapshotFigure("nw", "Net worth today", "₱10,000.00")))

    @Test fun questionAndHistoryAreCapped() {
        val history = (1..10).map { AskLucentoClient.Turn(fromUser = it % 2 == 1, text = "turn $it " + "x".repeat(2_000)) }
        val request = AskRequest.from("q".repeat(900), history, snapshot)
        assertEquals(AskRequest.MAX_QUESTION, request.question.length)
        assertEquals(AskRequest.MAX_TURNS, request.history.size)
        assertEquals("lucento", request.history.last().role)
        assertEquals(AskRequest.MAX_TURN_TEXT, request.history.first().text.length)
    }

    @Test fun theSnapshotGoesAsLabelledFigures() {
        val request = AskRequest.from("What's my net worth?", emptyList(), snapshot)
        assertEquals("PHP", request.currency)
        assertEquals("2026-11-15", request.today)
        assertEquals(listOf(AskFigure("nw", "Net worth today", "₱10,000.00")), request.figures)
    }

    @Test fun theBodyTellsTheProxyThisAppIsVersion2() {
        // Without it the proxy answers with the summaries-only prompt (no advice, no calculations).
        val body = AskLucentoClient.body(AskRequest.from("How can I save more?", emptyList(), snapshot))
        assertTrue(body, body.contains("\"version\":2"))
    }
}
