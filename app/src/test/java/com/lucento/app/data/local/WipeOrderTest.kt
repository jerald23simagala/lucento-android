package com.lucento.app.data.local

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA O-1: a kill partway through the sign-out wipe must not leave an empty ledger behind a live pull cursor. */
class WipeOrderTest {
    private class FakeMarker(var value: Boolean = false) : WipeMarker {
        override fun set() = true.also { value = true }
        override fun clear() = true.also { value = false }
        override fun isSet() = value
    }

    private class Killed : RuntimeException()

    @Test fun markerFirstThenCursorBeforeTables() = runTest {
        val marker = FakeMarker()
        val steps = mutableListOf<String>()
        runWipe(
            marker,
            clearBinding = { assertTrue(marker.value); steps += "binding" },
            clearTables = { steps += "tables" },
            clearRest = { steps += "rest" },
        )
        assertEquals(listOf("binding", "tables", "rest"), steps)
        assertFalse(marker.value)
    }

    @Test fun aKillAfterTheTablesLeavesTheMarkerSoTheNextLaunchFinishes() = runTest {
        val marker = FakeMarker()
        var cursor: String? = "2026-10-29T00:00:00Z"
        var rows = 10
        runCatching {
            runWipe(marker, clearBinding = { cursor = null }, clearTables = { rows = 0; throw Killed() }, clearRest = {})
        }
        // The cursor never outlives the rows it described.
        assertEquals(null, cursor)
        assertEquals(0, rows)
        assertTrue(marker.isSet())
        // Next launch: the marker is seen and the wipe runs again to the end.
        runWipe(marker, clearBinding = { cursor = null }, clearTables = { rows = 0 }, clearRest = {})
        assertFalse(marker.isSet())
    }

    @Test fun aCallerCancelledMidWipeStillFinishesItAndClearsTheMarker() = runTest {
        // F-7: the last step sends the app to onboarding, which can end the screen (and scope) that started the wipe. A
        // marker left set would make the next launch wipe the next account's freshly restored ledger.
        val marker = FakeMarker()
        val inRest = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var restFinished = false
        val job = launch {
            runWipe(marker, clearBinding = {}, clearTables = {}, clearRest = {
                inRest.complete(Unit)
                release.await()
                restFinished = true
            })
        }
        inRest.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertTrue(restFinished)
        assertFalse(marker.isSet())
    }

    @Test fun aWipeThatCannotBeRecordedDoesNotStart() = runTest {
        val marker = object : WipeMarker {
            override fun set() = false
            override fun clear() = true
            override fun isSet() = false
        }
        var touched = false
        assertTrue(runCatching { runWipe(marker, { touched = true }, { touched = true }, { touched = true }) }.isFailure)
        assertFalse(touched)
    }
}
