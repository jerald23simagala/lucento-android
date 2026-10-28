package com.lucento.app.core.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportsFlagTest {

    @Test fun theFlagDefaultsToOffUntilItIsSet() {
        val store = MemoryStore()
        assertFalse(CrashReportsFlag.isEnabled(store))
        CrashReportsFlag.set(store, true)
        assertTrue(CrashReportsFlag.isEnabled(store))
        CrashReportsFlag.set(store, false)
        assertFalse(CrashReportsFlag.isEnabled(store))
    }

    @Test fun theFirstStartupCopiesDataStoreOnceAndALaterValueCannotReplaceIt() {
        val store = MemoryStore()
        var reads = 0
        assertTrue(CrashReportsFlag.enabledForStartup(store) { reads += 1; true })
        assertEquals(1, reads)
        assertTrue(CrashReportsFlag.isEnabled(store))

        assertTrue(CrashReportsFlag.migrate(store, dataStoreValue = false))
        assertTrue(CrashReportsFlag.isEnabled(store))
        assertTrue(CrashReportsFlag.enabledForStartup(store) { reads += 1; false })
        assertEquals(1, reads)
    }

    @Test fun aSavedOptOutIsWhatTheNextLaunchUses() {
        val store = MemoryStore()
        CrashReportsFlag.set(store, false)
        var reads = 0
        assertFalse(CrashReportsFlag.enabledForStartup(store) { reads += 1; true })
        assertEquals(0, reads)
    }

    private class MemoryStore : CrashReportsFlag.Store {
        private val values = mutableMapOf<String, Boolean>()

        override fun getBoolean(key: String, default: Boolean): Boolean = values[key] ?: default

        override fun commit(edits: Map<String, Boolean>) {
            values.putAll(edits)
        }
    }
}
