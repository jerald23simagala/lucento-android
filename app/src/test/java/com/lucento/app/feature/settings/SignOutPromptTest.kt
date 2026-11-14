package com.lucento.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SignOutPromptTest {

    @Test fun countAddsDirtyRowsAndPendingDeletions() {
        assertEquals(0, unsyncedChangeCount(dirtyRows = 0, pendingTombstones = 0))
        assertEquals(3, unsyncedChangeCount(dirtyRows = 2, pendingTombstones = 1))
        assertEquals(1, unsyncedChangeCount(dirtyRows = 0, pendingTombstones = 1))
    }

    @Test fun syncedJustNowRequiresAnEmptyOutbox() {
        val now = 1_700_000_000_000L
        assertEquals(
            "Synced just now",
            syncStatusText(lastSyncedAtMillis = now - 10_000, nowMillis = now, pendingChanges = 0, lastError = null),
        )
        assertEquals(
            "44 changes waiting to sync",
            syncStatusText(lastSyncedAtMillis = now - 10_000, nowMillis = now, pendingChanges = 44, lastError = null),
        )
        assertEquals(
            "1 change waiting to sync",
            syncStatusText(lastSyncedAtMillis = null, nowMillis = now, pendingChanges = 1, lastError = null),
        )
        assertEquals(
            "Not synced yet",
            syncStatusText(lastSyncedAtMillis = null, nowMillis = now, pendingChanges = 0, lastError = null),
        )
    }
}