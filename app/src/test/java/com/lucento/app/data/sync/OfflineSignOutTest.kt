package com.lucento.app.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline sign-out must delete the stored session. supabase-kt's signOut only does that after the
 * network call returns, so a failed logout used to leave the tokens for the next launch.
 */
class OfflineSignOutTest {

    @Test fun offlineSignOutLeavesNoSessionAfterRelaunch() = runTest {
        val storage = SessionStorage(token = "access-token")
        endLocalSession(
            signOutLocal = { error("offline") },
            clearSession = { storage.token = null },
        )
        assertNull(storage.relaunch())
    }

    @Test fun successfulSignOutStillClearsTheStoredSession() = runTest {
        val storage = SessionStorage(token = "access-token")
        var loggedOut = false
        endLocalSession(
            signOutLocal = {
                loggedOut = true
                storage.token = null
            },
            clearSession = { storage.token = null },
        )
        assertTrue(loggedOut)
        assertNull(storage.relaunch())
    }

    @Test fun aSecondClearAfterASuccessfulLogoutStaysSignedOut() = runTest {
        val storage = SessionStorage(token = "access-token")
        var clears = 0
        endLocalSession(
            signOutLocal = { storage.token = null },
            clearSession = { clears += 1; storage.token = null },
        )
        assertEquals(1, clears)
        assertNull(storage.token)
    }

    @Test fun appKilledMidSignOutLeavesNoUsableSessionOnNextLaunch() = runTest {
        val storage = SessionStorage(token = "access-token")
        val order = mutableListOf<String>()
        val boundary = AccountBoundary(
            currentUserId = { storage.token?.let { "user-1" } },
            syncedUserId = { "user-1" },
            hasUnsyncedChanges = { false },
            prepare = {},
            signOut = {
                endLocalSession(
                    signOutLocal = { error("offline") },
                    clearSession = {
                        order += "clear"
                        storage.token = null
                    },
                )
            },
            wipe = {
                order += "wipe"
                error("killed")
            },
            exclusive = { block -> block() },
        )
        val killed = runCatching { boundary.signOutAndWipe() }
        assertTrue(killed.exceptionOrNull() is IllegalStateException)
        // The session is cleared before the wipe. Dying mid-wipe must not leave a token for the next launch.
        assertEquals(listOf("clear", "wipe"), order)
        assertNull(storage.relaunch())
    }

    private class SessionStorage(var token: String?) {
        fun relaunch(): String? = token
    }
}
