package com.lucento.app.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseInterpreterTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    private fun purchase(productId: String, pending: Boolean = false, autoRenewing: Boolean = true, boughtAt: Long = now - 2 * day) =
        PurchaseRecord(productId, purchaseToken = "tok-$productId", orderId = "GPA.1", purchaseTimeMillis = boughtAt, pending = pending, acknowledged = true, autoRenewing = autoRenewing)

    @Test fun a_subscription_grants_everything_for_the_local_horizon() {
        val rows = PurchaseInterpreter.entitlements(listOf(purchase(PlayProducts.PRO_SUBSCRIPTION)), now)
        assertEquals(1, rows.size)
        val sub = rows.single()
        assertEquals(EntitlementSource.PLAY_SUBSCRIPTION, sub.source)
        assertEquals(EntitlementStatus.ACTIVE, sub.status)
        assertEquals(Capability.entries.toSet(), sub.grants)
        assertEquals(now + PurchaseInterpreter.LOCAL_SUBSCRIPTION_HORIZON_MILLIS, sub.expiresAtMillis)
        assertEquals(Verifier.PLAY_LOCAL, sub.verifiedBy)
        assertEquals(PurchaseInterpreter.tokenHash("tok-lucento_pro"), sub.purchaseTokenHash)
        assertFalse("the token itself is never kept", sub.purchaseTokenHash!!.contains("tok"))
    }

    @Test fun a_cancelled_subscription_still_paid_up_reads_as_cancelled_active() {
        val rows = PurchaseInterpreter.entitlements(listOf(purchase(PlayProducts.PRO_SUBSCRIPTION, autoRenewing = false)), now)
        assertEquals(EntitlementStatus.CANCELLED_ACTIVE, rows.single().status)
        assertTrue(EntitlementState(rows).has(Capability.CLOUD, now))
    }

    @Test fun a_pending_purchase_grants_nothing_until_play_confirms_it() {
        val rows = PurchaseInterpreter.entitlements(listOf(purchase(PlayProducts.PRO_LIFETIME, pending = true)), now)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.status == EntitlementStatus.PENDING })
        val state = EntitlementState(rows)
        assertEquals(emptySet<Capability>(), state.capabilities(now))
        assertEquals(DisplayPlan.FREE, state.displayPlan(now))
        assertTrue(state.pending() != null)
    }

    @Test fun lifetime_is_forever_plus_twelve_months_of_services_from_the_purchase_time() {
        val bought = now - 100 * day
        val rows = PurchaseInterpreter.entitlements(listOf(purchase(PlayProducts.PRO_LIFETIME, boughtAt = bought)), now)
        val local = rows.single { it.source == EntitlementSource.PLAY_LIFETIME }
        val services = rows.single { it.source == EntitlementSource.PLAY_LIFETIME_SERVICES }
        assertEquals(null, local.expiresAtMillis)
        assertEquals(setOf(Capability.LOCAL_PRO), local.grants)
        assertEquals(bought + PurchaseInterpreter.LIFETIME_SERVICES_MILLIS, services.expiresAtMillis)
        assertEquals(setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED), services.grants)
        assertEquals(EntitlementStatus.ACTIVE, services.status)
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, EntitlementState(rows).displayPlan(now))
    }

    @Test fun a_reinstall_two_years_after_buying_lifetime_gets_local_pro_but_not_the_services() {
        val bought = now - 2 * 365 * day
        val rows = PurchaseInterpreter.entitlements(listOf(purchase(PlayProducts.PRO_LIFETIME, boughtAt = bought)), now)
        val state = EntitlementState(rows)
        assertTrue(state.has(Capability.LOCAL_PRO, now))
        assertFalse(state.has(Capability.CLOUD, now))
        assertEquals(EntitlementStatus.EXPIRED, rows.single { it.source == EntitlementSource.PLAY_LIFETIME_SERVICES }.status)
        assertEquals(DisplayPlan.LIFETIME, state.displayPlan(now))
    }

    @Test fun the_cloud_addon_grants_the_three_services_and_unknown_products_are_ignored() {
        val rows = PurchaseInterpreter.entitlements(listOf(purchase(PlayProducts.CLOUD_ADDON), purchase("someone_elses_sku")), now)
        assertEquals(1, rows.size)
        assertEquals(EntitlementSource.PLAY_CLOUD_ADDON, rows.single().source)
        assertEquals(setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED), rows.single().grants)
    }

    @Test fun token_hash_is_sha256_hex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PurchaseInterpreter.tokenHash("abc"))
    }
}
