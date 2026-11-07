package com.lucento.app.data.billing

import com.lucento.app.data.entitlement.EntitlementManager
import com.lucento.app.data.entitlement.EntitlementRemote
import com.lucento.app.data.entitlement.HmacSealer
import com.lucento.app.data.entitlement.InMemorySnapshotStore
import com.lucento.app.data.entitlement.SealedEntitlementCache
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.DisplayPlan
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.PurchaseInterpreter
import com.lucento.app.domain.entitlement.Verifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The test-build plan simulator: each scenario is what a real Play purchase would produce, and it persists. */
class PlanSimulatorTest {
    private val now = 1_800_000_000_000L

    private fun plan(sim: SimulatedPlan) = EntitlementState(PurchaseInterpreter.entitlements(sim.purchases(now), now))

    @Test fun each_scenario_reads_as_its_plan() {
        assertEquals(DisplayPlan.FREE, plan(SimulatedPlan.FREE).displayPlan(now))
        assertEquals(DisplayPlan.PRO, plan(SimulatedPlan.PRO_MONTHLY).displayPlan(now))
        assertEquals(DisplayPlan.PRO, plan(SimulatedPlan.PRO_CANCELLED).displayPlan(now))
        assertEquals(EntitlementStatus.CANCELLED_ACTIVE, plan(SimulatedPlan.PRO_CANCELLED).entitlements.single().status)
        assertEquals(DisplayPlan.FREE, plan(SimulatedPlan.PRO_PENDING).displayPlan(now))
        assertNotNull(plan(SimulatedPlan.PRO_PENDING).pending())
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, plan(SimulatedPlan.LIFETIME).displayPlan(now))
        assertEquals(DisplayPlan.LIFETIME, plan(SimulatedPlan.LIFETIME_YEAR_ENDED).displayPlan(now))
        assertFalse(plan(SimulatedPlan.LIFETIME_YEAR_ENDED).has(Capability.CLOUD, now))
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, plan(SimulatedPlan.LIFETIME_WITH_CLOUD).displayPlan(now))
    }

    @Test fun a_choice_persists_and_ignores_the_server_until_cleared() {
        val store = InMemorySimulatorStore()
        val purchases = FakePurchaseSource()
        val sim = DebugPlanSimulator(store, purchases) { now }
        assertNull(sim.current.value)
        assertFalse(sim.ignoresServer)
        sim.select(SimulatedPlan.LIFETIME)
        assertEquals("LIFETIME", store.value)
        assertTrue(sim.ignoresServer)
        // A restart reads it back and reports the same purchases.
        val again = DebugPlanSimulator(store, FakePurchaseSource()) { now }
        assertEquals(SimulatedPlan.LIFETIME, again.current.value)
        sim.select(null)
        assertNull(store.value)
        assertFalse(sim.ignoresServer)
    }

    @Test fun simulating_free_hides_a_server_grant_and_clearing_brings_it_back() = runTest {
        val grant = Entitlement(
            EntitlementSource.GRANT, EntitlementStatus.ACTIVE, Capability.entries.toSet(), now - 1, now + 1_000_000,
            productId = null, verifiedAtMillis = now, verifiedBy = Verifier.SERVER,
        )
        val purchases = FakePurchaseSource()
        val sim = DebugPlanSimulator(InMemorySimulatorStore(), purchases) { now }
        // The same wrapper AppContainer builds around the server.
        val remote = EntitlementRemote { if (sim.ignoresServer) emptyList() else listOf(grant) }
        val manager = EntitlementManager(SealedEntitlementCache(InMemorySnapshotStore(), HmacSealer(ByteArray(32))), purchases, remote) { now }
        manager.refresh()
        assertEquals(DisplayPlan.PRO, manager.state.value.displayPlan(now))
        sim.select(SimulatedPlan.FREE)
        manager.refresh()
        assertEquals(DisplayPlan.FREE, manager.state.value.displayPlan(now))
        sim.select(SimulatedPlan.LIFETIME_YEAR_ENDED)
        manager.refresh()
        assertEquals(DisplayPlan.LIFETIME, manager.state.value.displayPlan(now))
        sim.select(null)
        manager.refresh()
        assertEquals(DisplayPlan.PRO, manager.state.value.displayPlan(now))
        assertTrue(manager.ready.value)
    }
}
