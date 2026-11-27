package com.lucento.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Safe to spend's per-day line keeps "for 23 days" whole when a narrow pane wraps it (tablet screenshots, 2026-11-26). */
class PerDayCaptionTest {
    private val nbsp = '\u00A0'

    @Test fun keepsForTheCountAndTheUnitTogether() {
        assertEquals("$9.87 a day for${nbsp}23${nbsp}days", perDayCaption("$9.87", 23))
        assertEquals("₱98.70 a day for${nbsp}1${nbsp}day", perDayCaption("₱98.70", 1))
    }

    @Test fun theOnlyPlaceItMayBreakIsBeforeFor() {
        val caption = perDayCaption("$9.87", 23)
        assertEquals(listOf("$9.87", "a", "day", "for${nbsp}23${nbsp}days"), caption.split(' '))
        assertFalse(daysKeptWhole(23).contains(' '))
    }

    @Test fun singularAndPluralDays() {
        assertEquals("1${nbsp}day", daysKeptWhole(1))
        assertEquals("0${nbsp}days", daysKeptWhole(0))
        assertEquals("30${nbsp}days", daysKeptWhole(30))
    }
}
