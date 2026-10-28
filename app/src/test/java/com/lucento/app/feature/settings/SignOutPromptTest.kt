package com.lucento.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignOutPromptTest {

    @Test fun countAddsDirtyRowsAndPendingDeletions() {
        assertEquals(0, unsyncedChangeCount(dirtyRows = 0, pendingTombstones = 0))
        assertEquals(3, unsyncedChangeCount(dirtyRows = 2, pendingTombstones = 1))
        assertEquals(1, unsyncedChangeCount(dirtyRows = 0, pendingTombstones = 1))
    }

    @Test fun theDialogNamesHowManyChangesWouldBeDeleted() {
        assertNull(signOutUnsyncedMessage(0))
        assertEquals(
            "1 change hasn’t synced and will be deleted from this phone.",
            signOutUnsyncedMessage(1),
        )
        assertEquals(
            "3 changes haven’t synced and will be deleted from this phone.",
            signOutUnsyncedMessage(3),
        )
    }
}