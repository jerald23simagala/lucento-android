package com.lucento.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one conflict rule the whole sync engine relies on (see docs/SYNC.md "Conflict resolution"): a local
 * edit newer than an incoming remote delete is never silently discarded.
 */
class SyncConflictTest {

    @Test
    fun `a dirty local row edited after the remote delete wins — the delete is skipped`() {
        assertTrue(localWinsOverRemoteDelete(localUpdatedAtMillis = 2000, localDirty = true, remoteDeletedAtMillis = 1000))
    }

    @Test
    fun `a dirty local row edited before the remote delete loses — the delete proceeds`() {
        assertFalse(localWinsOverRemoteDelete(localUpdatedAtMillis = 500, localDirty = true, remoteDeletedAtMillis = 1000))
    }

    @Test
    fun `a clean (already-synced) local row never wins, even if its timestamp is newer`() {
        // dirty = false means this device has nothing of its own to protect; the remote delete always wins.
        assertFalse(localWinsOverRemoteDelete(localUpdatedAtMillis = 5000, localDirty = false, remoteDeletedAtMillis = 1000))
    }

    @Test
    fun `an exact tie goes to the remote delete, never to the local row`() {
        assertFalse(localWinsOverRemoteDelete(localUpdatedAtMillis = 1000, localDirty = true, remoteDeletedAtMillis = 1000))
    }
}

class SyncWireFormatTest {

    @Test
    fun `a pushed live row always sends deleted_at null, so a newer local edit can undo a remote delete`() {
        val row = WalletRow(
            id = "w", userId = "u", name = "Cash", type = "CASH", currencyCode = "USD", openingBalanceMinor = 0,
            sortOrder = 0, archived = false, createdAt = SyncTime.toIso(0), updatedAt = SyncTime.toIso(1),
        )
        // Default Json omits default values; @EncodeDefault must override that for deleted_at.
        val json = kotlinx.serialization.json.Json.encodeToString(WalletRow.serializer(), row)
        assertTrue(json, json.contains("\"deleted_at\":null"))
    }
}

class SyncTimeTest {

    @Test
    fun `epoch millis survive an ISO round trip exactly`() {
        val millis = 1_732_000_123_456L
        assertEquals(millis, SyncTime.fromIso(SyncTime.toIso(millis)))
    }

    @Test
    fun `epoch zero round trips too`() {
        assertEquals(0L, SyncTime.fromIso(SyncTime.toIso(0L)))
    }
}
