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

class PullRulesTest {

    private fun decide(exists: Boolean = true, dirty: Boolean, base: Long?, local: Long, remoteRev: Long?, remote: Long) =
        pullDecision(exists, dirty, base, local, remoteRev, remote)

    @Test
    fun `missing locally - insert`() =
        assertEquals(PullDecision.INSERT, decide(exists = false, dirty = false, base = null, local = 0, remoteRev = 1, remote = 5))

    @Test
    fun `causal successor - local kept whatever the clocks say`() {
        // Base equals the server's current revision: the user edited the latest version, so a slow clock can't lose it.
        assertEquals(PullDecision.KEEP_LOCAL, decide(dirty = true, base = 4, local = 1, remoteRev = 4, remote = 999))
        assertTrue(localEditWins(localBaseRevision = 4, localUpdatedAt = 1, remoteRevision = 4, remoteUpdatedAt = 999))
    }

    @Test
    fun `concurrent - strictly later edit wins, a tie goes to the server`() {
        assertEquals(PullDecision.KEEP_LOCAL, decide(dirty = true, base = 3, local = 11, remoteRev = 4, remote = 10))
        assertEquals(PullDecision.UPDATE, decide(dirty = true, base = 3, local = 10, remoteRev = 4, remote = 10))
        assertEquals(PullDecision.UPDATE, decide(dirty = true, base = 3, local = 9, remoteRev = 4, remote = 10))
    }

    @Test
    fun `unknown base counts as concurrent`() {
        assertEquals(PullDecision.KEEP_LOCAL, decide(dirty = true, base = null, local = 11, remoteRev = 4, remote = 10))
        assertEquals(PullDecision.UPDATE, decide(dirty = true, base = null, local = 10, remoteRev = 4, remote = 10))
    }

    @Test
    fun `clean - server wins even with an older timestamp`() =
        assertEquals(PullDecision.UPDATE, decide(dirty = false, base = 3, local = 10, remoteRev = 4, remote = 5))

    @Test
    fun `clean and identical - nothing to write`() =
        assertEquals(PullDecision.UNCHANGED, decide(dirty = false, base = 4, local = 7, remoteRev = 4, remote = 7))

    @Test
    fun `clean, same content but a newer revision - record the new base`() =
        assertEquals(PullDecision.UPDATE, decide(dirty = false, base = 3, local = 7, remoteRev = 4, remote = 7))

    @Test
    fun `cursor trails the newest row by the overlap`() =
        assertEquals(940L, nextPullCursor(sinceMillis = 0, newestSeenMillis = 1_000, oldestDeferredMillis = null, overlapMillis = 60))

    @Test
    fun `cursor never passes a deferred row`() =
        assertEquals(300L, nextPullCursor(sinceMillis = 0, newestSeenMillis = 1_000, oldestDeferredMillis = 300, overlapMillis = 60))

    @Test
    fun `cursor stays put when nothing was seen`() =
        assertEquals(500L, nextPullCursor(sinceMillis = 500, newestSeenMillis = null, oldestDeferredMillis = null, overlapMillis = 60))
}

class SyncTimeTest {

    @Test
    fun `parses the timestamptz format Postgres returns (offset, microseconds)`() {
        assertEquals(SyncTime.fromIso("2026-10-24T08:56:32.123Z"), SyncTime.fromIso("2026-10-24T08:56:32.123456+00:00"))
    }


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
