package com.lucento.app.data.sync

import com.lucento.app.data.local.WipeMarker
import com.lucento.app.data.local.runWipe
import com.lucento.app.finishInterruptedWipeBeforeUi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA R9-01 / R9-02: marker, then local session, then wipe, then revoke; and a kill anywhere recovers on launch. */
class SignOutOrderR9Test {

    private class Marker : WipeMarker {
        var set = false
        override fun set(): Boolean { set = true; return true }
        override fun clear(): Boolean { set = false; return true }
        override fun isSet() = set
    }

    private class Killed : RuntimeException()

    /** A phone: stored session, ledger rows, binding, and the durable marker. */
    private class Phone {
        val marker = Marker()
        var session: String? = "token"
        var rows = 5
        var binding: String? = "u"
        val order = mutableListOf<String>()

        suspend fun wipe() = runWipe(
            marker,
            clearBinding = { order += "binding"; binding = null },
            clearTables = { order += "tables"; rows = 0 },
            clearRest = { order += "rest" },
        )

        fun boundary(revoke: suspend () -> Unit = { order += "revoke" }, wipe: suspend () -> Unit = { this@Phone.wipe() }) =
            AccountBoundary(
                currentUserId = { "u" }, syncedUserId = { binding }, hasUnsyncedChanges = { false },
                prepare = { order += "prepare" }, wipe = wipe,
                signOut = { order += "session"; session = null },
                exclusive = { it() },
                markWipePending = { order += "marker"; marker.set() },
                revoke = revoke,
            )

        /** What the next launch does: finish the wipe before the UI. */
        fun relaunch() = finishInterruptedWipeBeforeUi(marker::isSet) { if (marker.isSet()) wipe() }
    }

    @Test fun markerAndLocalSessionComeBeforeAnyNetworkCall() = runTest {
        val phone = Phone()
        phone.boundary().signOutAndWipe()
        assertEquals(listOf("marker", "session", "prepare"), phone.order.take(3))
        assertTrue(phone.order.indexOf("session") < phone.order.indexOf("tables"))
        assertEquals("revoke", phone.order.last())
        assertTrue(phone.order.indexOf("rest") < phone.order.indexOf("revoke"))
        assertFalse(phone.marker.isSet())
        assertEquals(0, phone.rows)
    }

    @Test fun killDuringTheRevokeLeavesAFinishedWipe() = runTest {
        val phone = Phone()
        runCatching { phone.boundary(revoke = { throw Killed() }).signOutAndWipe() }
        assertNull(phone.session)
        assertEquals(0, phone.rows)
    }

    @Test fun killRightAfterConfirmIsFinishedOnTheNextLaunch() = runTest {
        val phone = Phone()
        // The confirm tap commits the marker, clears the stored session, then the process dies (150-600 ms window).
        assertTrue(phone.boundary().markWipePendingNow())
        phone.session = null
        assertEquals(5, phone.rows)

        phone.relaunch()

        assertNull(phone.session)
        assertEquals(0, phone.rows)
        assertNull(phone.binding)
        assertFalse(phone.marker.isSet())
    }

    @Test fun killMidWipeIsFinishedOnTheNextLaunch() = runTest {
        val phone = Phone()
        runCatching { phone.boundary(wipe = { phone.binding = null; throw Killed() }).signOutAndWipe() }
        assertTrue(phone.marker.isSet())
        assertEquals(5, phone.rows)
        phone.relaunch()
        assertEquals(0, phone.rows)
        assertFalse(phone.marker.isSet())
    }

    @Test fun markerIsNotClearedWhenTheTableWipeFails() = runTest {
        val marker = Marker()
        runCatching { runWipe(marker, clearBinding = {}, clearTables = { throw Killed() }, clearRest = {}) }
        assertTrue(marker.isSet())
    }

    @Test fun revokeFailureDoesNotFailSignOut() = runTest {
        val phone = Phone()
        phone.boundary(revoke = { error("offline") }).signOutAndWipe()
        assertEquals(0, phone.rows)
        assertFalse(phone.marker.isSet())
    }

    @Test fun noMarkerMeansLaunchDoesNotWipe() {
        val phone = Phone()
        phone.relaunch()
        assertEquals(5, phone.rows)
    }

    @Test fun markerThatCannotBeWrittenStopsBeforeTheSessionIsTouched() = runTest {
        val phone = Phone()
        val boundary = AccountBoundary(
            currentUserId = { "u" }, syncedUserId = { "u" }, hasUnsyncedChanges = { false },
            wipe = { phone.rows = 0 }, signOut = { phone.session = null }, exclusive = { it() },
            markWipePending = { false },
        )
        assertTrue(runCatching { boundary.signOutAndWipe() }.isFailure)
        assertEquals("token", phone.session)
        assertEquals(5, phone.rows)
    }
}
