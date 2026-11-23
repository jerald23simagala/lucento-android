package com.lucento.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When opening Lucento again syncs: [ForegroundSync.isUpToDate]. */
class ForegroundSyncUpToDateTest {

    private val now = 1_000_000_000L
    private val justNow = now - 1_000

    @Test
    fun `a sync moments ago with nothing waiting is up to date`() {
        assertTrue(ForegroundSync.isUpToDate(justNow, now, pendingChanges = 0, settingsWaiting = false, settingsCanUpload = false))
    }

    @Test
    fun `never synced, synced a minute ago, or a record waiting means sync again`() {
        assertFalse(ForegroundSync.isUpToDate(null, now, pendingChanges = 0, settingsWaiting = false, settingsCanUpload = false))
        assertFalse(ForegroundSync.isUpToDate(now - ForegroundSync.FRESH_MILLIS, now, 0, settingsWaiting = false, settingsCanUpload = false))
        assertFalse(ForegroundSync.isUpToDate(justNow, now, pendingChanges = 1, settingsWaiting = false, settingsCanUpload = false))
    }

    @Test
    fun `a waiting settings change counts only where the plan can save it`() {
        // Pro-gate review 2026-11-23 (F3): a Partner without a cloud plan of their own can't save settings, so theirs wait
        // for good. Counting them would sync again on every return to the app.
        assertFalse(ForegroundSync.isUpToDate(justNow, now, 0, settingsWaiting = true, settingsCanUpload = true))
        assertTrue(ForegroundSync.isUpToDate(justNow, now, 0, settingsWaiting = true, settingsCanUpload = false))
    }
}
