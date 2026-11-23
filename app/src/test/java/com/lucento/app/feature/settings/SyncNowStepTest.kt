package com.lucento.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings' Sync now: the plan comes first, then the first-sync rule (Pro-gate review 2026-11-23, PE-4). */
class SyncNowStepTest {

    @Test
    fun `without the plan nothing syncs, whatever else holds`() {
        for (never in listOf(true, false)) {
            for (signedIn in listOf(true, false)) {
                assertEquals(SyncNowStep.PLAN_LOCKED, syncNowStep(planAllows = false, neverSyncedHere = never, signedIn = signedIn))
            }
        }
    }

    @Test
    fun `with the plan, a phone that never synced with the account asks the first-sync question first`() {
        assertEquals(SyncNowStep.FIRST_SYNC_CHECK, syncNowStep(planAllows = true, neverSyncedHere = true, signedIn = true))
        assertEquals(SyncNowStep.SYNC, syncNowStep(planAllows = true, neverSyncedHere = false, signedIn = true))
        assertEquals(SyncNowStep.SYNC, syncNowStep(planAllows = true, neverSyncedHere = true, signedIn = false))
    }
}
