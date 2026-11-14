package com.lucento.app.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sign-out and account switching never upload one account's ledger into another. */
class AccountBoundaryTest {

    private class Harness(
        var current: String? = "account-a",
        var owner: String? = "account-a",
        var unsynced: Boolean = false,
        var spaceOwner: String? = null,
    ) {
        var wiped = 0
        var signedOut = 0
        var prepared = 0
        var forgotten = 0
        var revoked = 0
        var marked = 0
        /** Runs inside the sync lock, before the block: a first sync claiming the ledger at the last moment. */
        var underLock: () -> Unit = {}

        val boundary = AccountBoundary(
            currentUserId = { current },
            syncedUserId = { owner },
            hasUnsyncedChanges = { unsynced },
            prepare = { prepared++ },
            wipe = { wiped++; owner = null },
            signOut = { signedOut++; current = null },
            exclusive = { block ->
                underLock()
                block()
            },
            markWipePending = { marked++; true },
            revoke = { revoked++ },
            sharedSpaceOwner = { spaceOwner },
            forgetAccount = { forgotten++ },
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

    // ---- F-2 (owner decision 2026-11-12): records no account ever claimed may stay on the phone.

    @Test fun onlyRecordsNoAccountOrSharedSpaceClaimedArePhoneOnly() = runTest {
        assertTrue(Harness(owner = null).boundary.ledgerIsPhoneOnly())
        assertFalse(Harness(owner = "account-a").boundary.ledgerIsPhoneOnly())
        // A Partner's phone before its first pull: no synced account yet, but the ledger is the Owner's.
        assertFalse(Harness(owner = null, spaceOwner = "owner-x").boundary.ledgerIsPhoneOnly())
    }

    @Test fun keepingThePhonesOwnRecordsEndsTheSessionAndWipesNothing() = runTest {
        val h = Harness(owner = null, unsynced = true)
        assertTrue(h.boundary.signOutKeepingLedger())
        assertEquals(0, h.wiped)
        assertEquals(0, h.marked)
        assertEquals(1, h.signedOut)
        assertEquals(null, h.current)
        assertEquals(1, h.prepared)
        assertEquals(1, h.forgotten)
        assertEquals(1, h.revoked)
    }

    @Test fun anAccountsLedgerIsNeverKeptOnSignOut() = runTest {
        val h = Harness(owner = "account-a")
        assertFalse(h.boundary.signOutKeepingLedger())
        assertEquals(0, h.signedOut)
        assertEquals("account-a", h.current)
        assertEquals(0, h.prepared)
        assertEquals(0, h.forgotten)
        assertEquals(0, h.revoked)
    }

    @Test fun aSharedSpaceIsNeverKeptOnSignOut() = runTest {
        val h = Harness(owner = null, spaceOwner = "owner-x")
        assertFalse(h.boundary.signOutKeepingLedger())
        assertEquals(0, h.signedOut)
        assertEquals(0, h.forgotten)
    }

    @Test fun aFirstSyncThatClaimsTheLedgerJustBeforeTheLockWins() = runTest {
        val h = Harness(owner = null)
        // The dialog was opened while the records were the phone's own; a sync bound them before "Keep" ran.
        h.underLock = { h.owner = "account-a" }
        assertFalse(h.boundary.signOutKeepingLedger())
        assertEquals(0, h.signedOut)
        assertEquals("account-a", h.current)
    }
}
