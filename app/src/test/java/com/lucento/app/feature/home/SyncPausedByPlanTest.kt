package com.lucento.app.feature.home

import com.lucento.app.data.sync.backgroundSyncAllowed
import com.lucento.app.data.sync.shouldSyncOnLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lucento Pro, Phase 2: sync pauses (never errors, never deletes) when the plan doesn't include it (docs/MONETIZATION.md §6.4). */
class SyncPausedByPlanTest {
    private fun status(planAllows: Boolean, enabled: Boolean = true, signedIn: Boolean = true, online: Boolean = true) =
        CloudStatus.of(configured = true, signedIn = signedIn, enabled = enabled, hasError = false, everSynced = true, pendingChanges = 2, online = online, planAllows = planAllows)

    @Test fun a_plan_without_cloud_shows_paused_not_an_error() {
        assertEquals(CloudStatus.PAUSED, status(planAllows = false))
        assertEquals(CloudStatus.PENDING, status(planAllows = true))
        // Off, signed out or unconfigured stays "On device": the plan only matters once sync is on.
        assertEquals(CloudStatus.LOCAL, status(planAllows = false, enabled = false))
        assertEquals(CloudStatus.LOCAL, status(planAllows = false, signedIn = false))
        // Paused wins over offline: the reason the phone isn't syncing is the plan, not the connection.
        assertEquals(CloudStatus.PAUSED, status(planAllows = false, online = false))
    }

    @Test fun the_chip_and_the_sheet_say_why_and_what_is_safe() {
        val details = SyncDetails(email = "a@b.c", lastSyncedAtMillis = 1L, pendingChanges = 2)
        assertEquals("Sync paused", syncChipLabel(CloudStatus.PAUSED, details, 1_000L))
        assertTrue(syncChipDescription(CloudStatus.PAUSED, details, 1_000L).contains("Sync paused"))
        val copy = syncSheetCopy(CloudStatus.PAUSED, details, 1_000L)
        assertEquals("Cloud sync is part of Lucento Pro", copy.headline)
        assertTrue(copy.body.contains("Nothing in your account is deleted"))
        assertTrue(copy.body.contains("backup file"))
        assertEquals("Open Account & sync", copy.action)
        assertTrue(copy.actionOpensSettings)
    }

    @Test fun background_and_launch_sync_wait_for_the_plan() {
        assertTrue(backgroundSyncAllowed(syncEnabled = true, onboardingComplete = true, planAllows = true))
        assertFalse(backgroundSyncAllowed(syncEnabled = true, onboardingComplete = true, planAllows = false))
        assertTrue("the old two-argument form still means allowed", backgroundSyncAllowed(syncEnabled = true, onboardingComplete = true))
        assertTrue(shouldSyncOnLaunch(true, "user-1", planAllows = true))
        assertFalse(shouldSyncOnLaunch(true, "user-1", planAllows = false))
    }
}
