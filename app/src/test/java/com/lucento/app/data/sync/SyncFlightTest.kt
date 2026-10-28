package com.lucento.app.data.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Sync now" and a background cycle must not run at the same time. */
class SyncFlightTest {

    @Test fun exclusiveBlocksOverlapAndReleasesAfterEachCall() = runTest {
        var inside = 0
        var peak = 0
        val jobs = List(6) {
            launch {
                SyncFlight.exclusive {
                    inside += 1
                    peak = maxOf(peak, inside)
                    delay(20)
                    inside -= 1
                }
            }
        }
        jobs.forEach { it.join() }
        assertEquals(1, peak)
        assertEquals(0, inside)
    }
}
