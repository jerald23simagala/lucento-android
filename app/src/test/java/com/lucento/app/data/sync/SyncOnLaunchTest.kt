package com.lucento.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA R6-11: a cold launch while signed in with sync on pulls right away. */
class SyncOnLaunchTest {
    @Test fun signed_in_with_sync_on_syncs_on_launch() = assertTrue(shouldSyncOnLaunch(true, "user-1"))
    @Test fun sync_off_does_not() = assertFalse(shouldSyncOnLaunch(false, "user-1"))
    @Test fun signed_out_does_not() {
        assertFalse(shouldSyncOnLaunch(true, null))
        assertFalse(shouldSyncOnLaunch(true, ""))
    }
}
