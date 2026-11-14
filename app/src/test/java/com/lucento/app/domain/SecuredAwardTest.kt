package com.lucento.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Secured award (owner test 2026-11-09): earned by turning on App lock, kept once earned. */
class SecuredAwardTest {
    private fun facts(secured: Boolean) = ProgressFacts(
        assetWallets = 0, budgetedCategories = 0, recordedTransactions = 0, activeDays = 0, goals = 0,
        visitedInsights = false, secured = secured,
    )

    @Test
    fun `turning on app lock earns Secured, and nothing else does`() {
        assertEquals(setOf(Achievement.SECURED), Achievements.earned(facts(secured = true)))
        assertFalse(Achievement.SECURED in Achievements.earned(facts(secured = false)))
    }

    @Test
    fun `Secured is a new award to acknowledge for someone who already has awards`() {
        val acknowledged = setOf(Achievement.MONEY_KEEPER)
        assertEquals(listOf(Achievement.SECURED), Achievements.toAcknowledgeAll(setOf(Achievement.MONEY_KEEPER, Achievement.SECURED), acknowledged))
    }

    @Test
    fun `its key is stable for stored acknowledgements`() {
        assertEquals(Achievement.SECURED, Achievement.fromKey("secured"))
        assertTrue(Achievement.SECURED.howTo.contains("Lock Lucento"))
    }
}
