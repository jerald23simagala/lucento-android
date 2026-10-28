package com.lucento.app.core.crash

import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportingTest {

    @Test fun reportsKeepTheStackAndDropAmountsAndUserText() {
        val original = IllegalStateException("spent 12.50 on rent at the cafe")
        original.initCause(IllegalArgumentException("note: birthday gift 40"))
        val safe = CrashReporting.sanitize(original)
        val rendered = buildString {
            append(safe)
            append(safe.cause)
        }
        assertFalse(rendered.contains("12.50"))
        assertFalse(rendered.contains("rent"))
        assertFalse(rendered.contains("birthday"))
        assertTrue(safe.message!!.contains("IllegalStateException"))
        assertTrue(safe.cause!!.message!!.contains("IllegalArgumentException"))
        assertEquals(original.stackTrace.toList(), safe.stackTrace.toList())
    }

    @Test fun thePreferenceDefaultsToOff() {
        assertFalse(UserPreferences(onboardingComplete = false, themeMode = ThemeMode.SYSTEM, primaryCurrency = "USD").crashReports)
    }

    @Test fun collectionStartsDisabledAndTheToggleChangesItImmediately() {
        val beforeInstall = CrashReporting.unsentReportsDeleted
        CrashReporting.install()
        assertEquals(false, CrashReporting.collectionEnabled)
        assertEquals(beforeInstall + 1, CrashReporting.unsentReportsDeleted)
        CrashReporting.setCollectionEnabled(true)
        assertEquals(true, CrashReporting.collectionEnabled)
        val deletedBefore = CrashReporting.unsentReportsDeleted
        CrashReporting.setCollectionEnabled(false)
        assertEquals(false, CrashReporting.collectionEnabled)
        assertEquals(deletedBefore, CrashReporting.unsentReportsDeleted)
        CrashReporting.deleteUnsentReports()
        assertEquals(deletedBefore + 1, CrashReporting.unsentReportsDeleted)
    }

    @Test fun installUsesTheStoredOptInAndDoesNotDropReportsWhenOn() {
        val deletedBefore = CrashReporting.unsentReportsDeleted
        CrashReporting.install(enabled = true)
        assertEquals(true, CrashReporting.collectionEnabled)
        assertEquals(deletedBefore, CrashReporting.unsentReportsDeleted)
    }
}