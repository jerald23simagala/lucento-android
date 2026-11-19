package com.lucento.app.feature.pro

import com.lucento.app.domain.entitlement.ProFeature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner, 2026-11-19: the first tap on a Pro feature in a session is the dialog, later taps a short message. */
class ProWallMemoryTest {

    @Test fun `a feature's dialog is shown once a session, later taps are the short message`() {
        val memory = ProWallMemory()
        assertFalse(memory.met(ProFeature.PDF_STATEMENTS))
        memory.markMet(ProFeature.PDF_STATEMENTS)
        assertTrue(memory.met(ProFeature.PDF_STATEMENTS))
        memory.markMet(ProFeature.PDF_STATEMENTS)
        assertTrue(memory.met(ProFeature.PDF_STATEMENTS))
        // Each feature on its own: meeting one wall says nothing about another.
        assertFalse(memory.met(ProFeature.CLOUD_SYNC))
        assertFalse(memory.met(ProFeature.MONEY_WALLETS))
    }

    @Test fun `a new session starts again, since nothing is stored`() {
        ProWallMemory().markMet(ProFeature.INSIGHTS_LONG_PERIODS)
        assertFalse(ProWallMemory().met(ProFeature.INSIGHTS_LONG_PERIODS))
    }
}
