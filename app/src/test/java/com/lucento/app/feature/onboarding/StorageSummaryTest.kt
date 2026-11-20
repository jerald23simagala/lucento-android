package com.lucento.app.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The onboarding summary never says "Synced" for an account whose plan doesn't sync (launch-film discovery issue 6). */
class StorageSummaryTest {

    @Test fun signedOutKeepsEverythingOnThePhone() {
        val s = storageSummary(signedInEmail = null, syncIncluded = null)
        assertEquals("Kept on this phone", s.title)
        assertEquals("Export a backup any time in Settings", s.detail)
        assertFalse(s.cloud)
    }

    @Test fun aPlanWithSyncSaysSynced() {
        val s = storageSummary("ana@example.com", syncIncluded = true)
        assertEquals("Synced with your account", s.title)
        assertEquals("ana@example.com", s.detail)
        assertTrue(s.cloud)
    }

    @Test fun aFreeAccountIsSignedInButKeptOnThePhone() {
        val s = storageSummary("ana@example.com", syncIncluded = false)
        assertEquals("Kept on this phone", s.title)
        assertEquals("Signed in as ana@example.com. Cloud sync is part of Lucento Pro.", s.detail)
        assertFalse(s.cloud)
    }

    @Test fun anAccountWithoutAnAddressStillReadsWell() {
        assertEquals("Signed in. Cloud sync is part of Lucento Pro.", storageSummary("", syncIncluded = false).detail)
        assertEquals("Connected", storageSummary("", syncIncluded = true).detail)
    }

    @Test fun whileThePlanIsUnknownItClaimsNothing() {
        val s = storageSummary("ana@example.com", syncIncluded = null)
        assertEquals("Signed in", s.title)
        assertEquals("ana@example.com", s.detail)
        assertFalse(s.cloud)
    }
}
