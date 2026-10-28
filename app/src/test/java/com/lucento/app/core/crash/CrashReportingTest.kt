package com.lucento.app.core.crash

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

    @Test fun installDoesNothingWhenFirebaseIsAbsent() {
        CrashReporting.install()
    }
}