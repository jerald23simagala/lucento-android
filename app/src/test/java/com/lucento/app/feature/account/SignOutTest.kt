package com.lucento.app.feature.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sign out (owner decision 2026-11-12, F-2): the dialog never promises what it can't do, and never hides a loss. */
class SignOutTest {

    @Test fun recordsNoAccountEverClaimedArePhoneOnlyWhateverIsPending() {
        // Every row starts unsynced, so a never-synced phone always has "changes"; they aren't the account's.
        assertEquals(SignOutCase.PHONE_ONLY, signOutCase(phoneOnly = true, pendingChanges = 214, canUpload = false))
        assertEquals(SignOutCase.PHONE_ONLY, signOutCase(phoneOnly = true, pendingChanges = 0, canUpload = true))
    }

    @Test fun anAccountsLedgerWithNothingWaitingIsSafe() {
        assertEquals(SignOutCase.SAFE_IN_ACCOUNT, signOutCase(phoneOnly = false, pendingChanges = 0, canUpload = false))
        assertEquals(SignOutCase.SAFE_IN_ACCOUNT, signOutCase(phoneOnly = false, pendingChanges = -1, canUpload = true))
    }

    @Test fun waitingChangesOfferSyncOnlyWhenThePlanCanUpload() {
        assertEquals(SignOutCase.UNSYNCED_CAN_SYNC, signOutCase(phoneOnly = false, pendingChanges = 3, canUpload = true))
        // A plan without cloud sync: "Sync first" could never upload, so the dialog offers a backup file instead.
        assertEquals(SignOutCase.UNSYNCED_NEEDS_BACKUP, signOutCase(phoneOnly = false, pendingChanges = 3, canUpload = false))
    }

    @Test fun safeCaseHasOneActionAndNamesTheAccount() {
        val d = SignOutCopy.dialog(SignOutCase.SAFE_IN_ACCOUNT, "ada@example.com", 0)
        assertEquals("Sign out?", d.title)
        assertEquals("Sign out", d.confirmLabel)
        assertNull(d.secondaryLabel)
        assertTrue(d.text.contains("ada@example.com"))
        assertTrue(d.text.contains("bring it all back"))
    }

    @Test fun unsyncedCasesCountTheChangesWithTheRightVerb() {
        val one = SignOutCopy.dialog(SignOutCase.UNSYNCED_CAN_SYNC, "ada@example.com", 1)
        assertTrue(one.text.startsWith("1 change hasn’t reached your account (ada@example.com) yet."))
        assertTrue(one.text.contains("sync first to keep it."))
        assertEquals("Sync first", one.confirmLabel)
        assertEquals("Sign out anyway", one.secondaryLabel)

        val many = SignOutCopy.dialog(SignOutCase.UNSYNCED_NEEDS_BACKUP, "ada@example.com", 3)
        assertTrue(many.text.startsWith("3 changes haven’t reached your account (ada@example.com),"))
        assertEquals("Save a backup", many.confirmLabel)
        assertEquals("Sign out anyway", many.secondaryLabel)
        assertTrue(many.text.contains("so they can’t be uploaded. Signing out deletes them from this phone."))

        // One change is "it", never "they" or "them".
        val single = SignOutCopy.dialog(SignOutCase.UNSYNCED_NEEDS_BACKUP, "ada@example.com", 1)
        assertTrue(single.text.contains("so it can’t be uploaded. Signing out deletes it from this phone."))
    }

    @Test fun phoneOnlyNeverPromisesADownloadAndOffersKeepOrRemove() {
        val d = SignOutCopy.dialog(SignOutCase.PHONE_ONLY, "ada@example.com", 214)
        // F-2: the old text said "Sign back in to download them again", untrue for records never uploaded.
        assertFalse(d.text.contains("download"))
        assertFalse(d.text.contains("bring"))
        assertTrue(d.text.contains("only here"))
        assertTrue(d.text.contains("can’t be undone"))
        assertEquals("Keep them here", d.confirmLabel)
        assertEquals("Remove and sign out", d.secondaryLabel)
        // The count of "changes" means nothing here (every row of a never-synced phone counts), so it isn't shown.
        assertFalse(d.text.contains("214"))
    }

    @Test fun aMissingEmailStillReadsAsASentence() {
        val d = SignOutCopy.dialog(SignOutCase.SAFE_IN_ACCOUNT, "", 0)
        assertTrue(d.text.startsWith("Everything is safe in your account. "))
        assertTrue(SignOutCopy.dialog(SignOutCase.PHONE_ONLY, null, 0).text.contains("uploaded to your account, so"))
    }

    @Test fun theConfirmButtonIsNeverTheOneThatRemovesRecords() {
        SignOutCase.entries.forEach { case ->
            val d = SignOutCopy.dialog(case, "ada@example.com", 2)
            assertFalse(case.name, d.confirmLabel == SignOutCopy.ANYWAY || d.confirmLabel == SignOutCopy.REMOVE)
        }
    }

    @Test fun noticesRoundTripThroughTheirStoredKey() {
        SignOutNotice.entries.forEach { assertEquals(it, SignOutNotice.fromKey(it.key)) }
        assertNull(SignOutNotice.fromKey(null))
        assertNull(SignOutNotice.fromKey("something-else"))
        assertEquals(AccountDeletionCopy.DONE, SignOutNotice.ACCOUNT_DELETED.text)
        // Only the account's copy can be brought back; records that were only on the phone can't.
        assertTrue(SignOutNotice.SIGNED_OUT.text.contains("bring back"))
        assertFalse(SignOutNotice.REMOVED.text.contains("bring back"))
    }
}
