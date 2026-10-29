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

    private class SessionStorage(var token: String?) {
        fun relaunch(): String? = token
    }
}
