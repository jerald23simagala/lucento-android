package com.lucento.app.feature.profile

import com.lucento.app.data.preferences.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Owner, 2026-11-19: a Pro theme can be tried on while the profile sheet is open; nothing is saved. */
class ThemeTrialTest {

    @Test fun `a theme tried on shows until it ends`() {
        val trial = ThemeTrial()
        assertNull(trial.trying.value)
        trial.start(ThemeId.GROVE)
        assertEquals(ThemeId.GROVE, trial.trying.value)
        // Another Pro theme replaces it; there is only ever one.
        trial.start(ThemeId.AURORA)
        assertEquals(ThemeId.AURORA, trial.trying.value)
        trial.end()
        assertNull(trial.trying.value)
    }
}
