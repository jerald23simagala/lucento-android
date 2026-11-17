package com.lucento.app.feature.settings

import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.data.sync.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** P2-4: the Settings hub rows say what is set on each page. */
class SettingsHubTest {

    private val now = 1_800_000_000_000L
    private val prefs = UserPreferences(onboardingComplete = true, themeMode = ThemeMode.SYSTEM, primaryCurrency = "USD")

    private fun sync(configured: Boolean = true, email: String? = "ada@example.com", last: Long? = now - 5 * 60_000, error: String? = null, pending: Int = 0) =
        CloudSyncUiState(
            configured = configured,
            googleSignInConfigured = true,
            signedInEmail = email,
            status = SyncStatus(enabled = email != null, lastSyncedAtMillis = last, lastError = error),
            pendingChanges = pending,
        )

    @Test fun `account row says who is signed in and how sync stands`() {
        assertEquals("ada@example.com · Synced 5 min ago", accountSummary(sync(), now))
        assertEquals("ada@example.com · Last sync failed: offline", accountSummary(sync(error = "offline"), now))
        assertEquals("Off · your data stays on this phone", accountSummary(sync(email = null), now))
        assertEquals("Not available in this version", accountSummary(sync(configured = false), now))
    }

    @Test fun `privacy row counts the sharing switches, none by default`() {
        assertEquals("App lock off · nothing shared", privacySummary(prefs))
        assertEquals("App lock on · 1 sharing option on", privacySummary(prefs.copy(appLock = true, crashReports = true)))
        assertEquals("App lock off · 2 sharing options on", privacySummary(prefs.copy(usageCounts = true, smartReading = true)))
    }

    @Test fun `AI answers count as sharing only once confirmed, since nothing is sent before`() {
        // On by default for a signed-in account (owner, 2026-11-17), but not yet confirmed: nothing is shared.
        assertEquals("App lock off · nothing shared", privacySummary(prefs.copy(askLucentoAi = true, askAiConfirmed = false)))
        assertEquals("App lock off · 1 sharing option on", privacySummary(prefs.copy(askLucentoAi = true, askAiConfirmed = true)))
        assertEquals("App lock off · nothing shared", privacySummary(prefs.copy(askLucentoAi = false, askAiConfirmed = false)))
        // Where AI answers aren't offered (signed out, or no proxy in this build), they never count.
        assertEquals("App lock off · nothing shared", privacySummary(prefs.copy(askAiConfirmed = true), smartReadingOffered = false))
    }

    @Test fun `pages round-trip through their route key and unknown keys open the hub`() {
        SettingsPage.entries.forEach { assertEquals(it, SettingsPage.fromKey(it.key)) }
        assertNull(SettingsPage.fromKey(""))
        assertNull(SettingsPage.fromKey("nope"))
    }
}
