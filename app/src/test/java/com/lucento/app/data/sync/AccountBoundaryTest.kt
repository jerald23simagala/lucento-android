package com.lucento.app.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sign-out and account switching never upload one account's ledger into another. */
class AccountBoundaryTest {

    private class Harness(
        var current: String? = "account-a",
        var owner: String? = "account-a",
        var unsynced: Boolean = false,
    ) {
        var wiped = 0
        var signedOut = 0
        var prepared = 0

        val boundary = AccountBoundary(
            currentUserId = { current },
            syncedUserId = { owner },
            hasUnsyncedChanges = { unsynced },
            prepare = { prepared++ },
            wipe = { wiped++; owner = null },
            signOut = { signedOut++; current = null },
            exclusive = { block -> block() },
        )
    }

    @Test fun sameAccountSyncsWithoutWiping() = runTest {
        val h = Harness()
        assertEquals(AccountBoundary.AfterSignIn.Ready, h.boundary.afterSignIn())
        assertEquals(0, h.wiped)
        assertEquals(0, h.signedOut)
    }

    @Test fun firstAccountOnThisPhoneIsNotASwitch() = runTest {
        val h = Harness(owner = null)
        assertEquals(AccountBoundary.AfterSignIn.Ready, h.boundary.afterSignIn())
        assertEquals(0, h.wiped)
    }

    @Test fun differentAccountWithNothingPendingWipesBeforeAnyPush() = runTest {
        val h = Harness(current = "account-b", unsynced = false)
        assertEquals(AccountBoundary.AfterSignIn.Wiped, h.boundary.afterSignIn())
        assertEquals(1, h.wiped)
        assertEquals(1, h.prepared)
        assertEquals(null, h.owner)
        assertEquals(0, h.signedOut)
    }

    @Test fun differentAccountWithUnsyncedChangesWaitsForConfirmation() = runTest {
        val h = Harness(current = "account-b", unsynced = true)
        val check = h.boundary.afterSignIn()
        assertTrue(check is AccountBoundary.AfterSignIn.ConfirmSwitch)
        assertEquals(0, h.wiped)
        assertEquals("account-a", h.owner)

        h.boundary.confirmSwitch()
        assertEquals(1, h.wiped)
        assertEquals(null, h.owner)
        assertEquals(0, h.signedOut)
    }

    @Test fun cancellingASwitchSignsOutAndKeepsThePreviousLedger() = runTest {
        val h = Harness(current = "account-b", unsynced = true)
        h.boundary.afterSignIn()
        h.boundary.cancelSwitch()
        assertEquals(0, h.wiped)
        assertEquals("account-a", h.owner)
        assertEquals(1, h.signedOut)
        assertEquals(null, h.current)
    }

    @Test fun signOutRemovesTheSessionAndTheLedger() = runTest {
        val h = Harness(unsynced = true)
        h.boundary.signOutAndWipe()
        assertEquals(1, h.signedOut)
        assertEquals(1, h.wiped)
        assertEquals(null, h.owner)
        assertEquals(null, h.current)
    }
}
