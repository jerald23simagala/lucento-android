package com.lucento.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** P2-5 (N-21): modules that are off stay findable on Home and turn on with what they need. */
class ModuleRediscoveryTest {

    @Test fun `only quick-link modules that are off are offered, in their Home order`() {
        val set = FeatureSet(setOf(Feature.BUDGETS, Feature.MULTIPLE_WALLETS, Feature.INSIGHTS))
        assertEquals(listOf(Feature.SAVINGS_GOALS, Feature.RECURRING, Feature.DEBTS_LOANS), set.rediscoverable())
        assertEquals(emptyList<Feature>(), FeatureSet.ALL.rediscoverable())
    }

    @Test fun `a module whose requirement is off is offered, and turning it on brings the requirement`() {
        // Debts is switched on but unusable without Multiple wallets: it is offered, and only the missing piece is added.
        val debtsOnly = FeatureSet(setOf(Feature.DEBTS_LOANS))
        assertEquals(true, Feature.DEBTS_LOANS in debtsOnly.rediscoverable())
        assertEquals(setOf(Feature.MULTIPLE_WALLETS), debtsOnly.toTurnOn(Feature.DEBTS_LOANS))
        assertEquals(setOf(Feature.DEBTS_LOANS, Feature.MULTIPLE_WALLETS), FeatureSet(emptySet()).toTurnOn(Feature.DEBTS_LOANS))
        assertEquals(setOf(Feature.SAVINGS_GOALS), FeatureSet(emptySet()).toTurnOn(Feature.SAVINGS_GOALS))
        assertEquals(emptySet<Feature>(), FeatureSet.ALL.toTurnOn(Feature.INSIGHTS))
    }

    @Test fun `after turning on what toTurnOn lists, the module is usable`() {
        Feature.entries.forEach { f ->
            val empty = FeatureSet(emptySet())
            val after = FeatureSet(empty.enabled + empty.toTurnOn(f))
            assertEquals(f.name, true, after.isEnabled(f))
        }
    }
}
