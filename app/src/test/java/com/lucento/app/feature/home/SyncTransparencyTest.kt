package com.lucento.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P2-2 sync status chip and details sheet, P2-6 "changed on another device" notice. */
class SyncTransparencyTest {

    private val now = 1_800_000_000_000L

    private fun of(error: Boolean = false, ever: Boolean = true, pending: Int = 0, online: Boolean = true, running: Boolean = false, enabled: Boolean = true) =
        CloudStatus.of(configured = true, signedIn = true, enabled = enabled, hasError = error, everSynced = ever, pendingChanges = pending, online = online, running = running)

    @Test fun `a running sync shows as Syncing, also as the retry after a failure, but not while offline or sync is off`() {
        assertEquals(CloudStatus.SYNCING, of(running = true))
        assertEquals(CloudStatus.SYNCING, of(running = true, error = true, pending = 2))
        assertEquals(CloudStatus.OFFLINE, of(running = true, online = false))
        assertEquals(CloudStatus.LOCAL, of(running = true, enabled = false))
    }

    @Test fun `the chip says Synced, Syncing or Needs attention in words`() {
        assertEquals("Synced", CloudStatus.SYNCED.label)
        assertEquals("Syncing", CloudStatus.SYNCING.label)
        assertEquals("Needs attention", CloudStatus.ERROR.label)
    }

    @Test fun `a failure offers Try again and says nothing was lost`() {
        val copy = syncSheetCopy(CloudStatus.ERROR, SyncDetails(email = "a@b.c", lastSyncedAtMillis = now - 3_600_000, lastError = "Couldn't reach the server"), now)
        assertEquals("Try again", copy.action)
        assertFalse(copy.actionOpensSettings)
        assertTrue(copy.body.contains("Couldn't reach the server"))
        assertTrue(copy.body.contains("Nothing on this phone was lost"))
    }

    @Test fun `the first download says so and offers no button`() {
        val copy = syncSheetCopy(CloudStatus.SYNCING, SyncDetails(email = "a@b.c"), now)
        assertNull(copy.action)
        assertTrue(copy.body.startsWith("Downloading your account for the first time"))
        assertTrue(syncSheetCopy(CloudStatus.SYNCING, SyncDetails(lastSyncedAtMillis = now), now).body.startsWith("Sending and receiving"))
    }

    @Test fun `offline has nothing to press and counts what is waiting`() {
        val copy = syncSheetCopy(CloudStatus.OFFLINE, SyncDetails(lastSyncedAtMillis = now, pendingChanges = 2), now)
        assertNull(copy.action)
        assertTrue(copy.body.endsWith("2 changes are waiting."))
    }

    @Test fun `a phone that never synced is sent to Settings, which asks before combining data`() {
        val copy = syncSheetCopy(CloudStatus.PENDING, SyncDetails(email = "a@b.c", lastSyncedAtMillis = null, pendingChanges = 4), now)
        assertTrue(copy.actionOpensSettings)
        assertTrue(syncSheetCopy(CloudStatus.LOCAL, SyncDetails(), now).actionOpensSettings)
        assertFalse(syncSheetCopy(CloudStatus.SYNCED, SyncDetails(lastSyncedAtMillis = now - 120_000), now).actionOpensSettings)
        assertTrue(syncSheetCopy(CloudStatus.SYNCED, SyncDetails(lastSyncedAtMillis = now - 120_000), now).body.startsWith("Synced 2 min ago"))
    }

    @Test fun `an account mismatch is not offered as a retry but sent to Account and sync`() {
        val copy = syncSheetCopy(CloudStatus.ERROR, SyncDetails(lastError = com.lucento.app.data.sync.SyncRepository.ACCOUNT_MISMATCH_MESSAGE), now)
        assertTrue(copy.actionOpensSettings)
        assertEquals("Open Account & sync", copy.action)
        assertFalse("never claims retrying fixes it", copy.body.contains("tries again"))
        assertFalse(syncSheetCopy(CloudStatus.ERROR, SyncDetails(lastError = "timeout"), now).body.contains("keeps retrying"))
    }

    @Test fun `a build without cloud sync offers nothing to set up`() {
        val copy = syncSheetCopy(CloudStatus.LOCAL, SyncDetails(configured = false), now)
        assertNull(copy.action)
        assertTrue(copy.headline.contains("isn’t available"))
    }

    @Test fun `the replaced-change notice appears only when something of this phone's was replaced`() {
        assertNull(overriddenNotice(0))
        assertTrue(overriddenNotice(1)!!.startsWith("A change you made on this phone was replaced"))
        assertTrue(overriddenNotice(3)!!.startsWith("3 changes you made"))
    }
}
