package com.lucento.app.data.entitlement

import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.Verifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `public.entitlements` as PostgREST serialises it (the lucento-test rows of 2026-11-06) → [com.lucento.app.domain.entitlement.Entitlement]. */
class EntitlementRowsTest {
    private val grant = """{"id":"6d1c5d9a-0000-4000-8000-000000000001","user_id":"3f2b1a00-0000-4000-8000-000000000002","source":"GRANT","status":"ACTIVE","grants":["LOCAL_PRO","CLOUD","AI_PRO","SHARED"],"product_id":null,"base_plan_id":null,"purchase_token_hash":null,"starts_at":"2026-11-06T04:38:37.920967+00:00","expires_at":"2027-05-06T04:38:37.920967+00:00","verified_at":"2026-11-06T04:38:37.920967+00:00","updated_at":"2026-11-06T04:38:37.920967+00:00","note":"lucento-test account"}"""
    private val lifetime = """{"id":"x","user_id":"y","source":"PLAY_LIFETIME","status":"ACTIVE","grants":["LOCAL_PRO"],"product_id":"lucento_pro_lifetime","base_plan_id":null,"purchase_token_hash":"${"ab".repeat(32)}","starts_at":"2026-11-06T00:00:00+00:00","expires_at":null,"verified_at":"2026-11-06T00:00:00+00:00","updated_at":"2026-11-06T00:00:00+00:00","note":null}"""

    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

    @Test fun a_grant_row_maps_with_its_dates_and_server_verifier() {
        val e = EntitlementRows.parseRow(obj(grant))!!
        assertEquals(EntitlementSource.GRANT, e.source)
        assertEquals(EntitlementStatus.ACTIVE, e.status)
        assertEquals(Capability.entries.toSet(), e.grants)
        assertEquals(Verifier.SERVER, e.verifiedBy)
        assertEquals(EntitlementRows.millis("2027-05-06T04:38:37.920967+00:00"), e.expiresAtMillis)
        assertEquals(EntitlementRows.millis("2026-11-06T04:38:37.920967+00:00"), e.verifiedAtMillis)
        assertNull(e.productId)
        assertEquals(false, e.autoRenewing)
    }

    @Test fun a_lifetime_row_never_expires_and_keeps_the_token_hash() {
        val e = EntitlementRows.parseRow(obj(lifetime))!!
        assertEquals(EntitlementSource.PLAY_LIFETIME, e.source)
        assertNull(e.expiresAtMillis)
        assertEquals("lucento_pro_lifetime", e.productId)
        assertEquals("ab".repeat(32), e.purchaseTokenHash)
    }

    @Test fun rows_this_version_cannot_read_are_skipped_never_thrown() {
        val unknownSource = grant.replace("\"GRANT\"", "\"PLAY_FAMILY\"")
        val unknownCap = grant.replace("\"SHARED\"", "\"TELEPATHY\"")
        val noGrants = grant.replace("[\"LOCAL_PRO\",\"CLOUD\",\"AI_PRO\",\"SHARED\"]", "[]")
        assertNull(EntitlementRows.parseRow(obj(unknownSource)))
        assertNull(EntitlementRows.parseRow(obj(unknownCap)))
        assertNull(EntitlementRows.parseRow(obj(noGrants)))
        assertEquals(1, EntitlementRows.parse(listOf(obj(grant), obj(unknownSource))).size)
    }

    @Test fun timestamps_parse_with_and_without_fractional_seconds() {
        assertEquals(1_800_000_000_000L, EntitlementRows.millis("2027-01-15T08:00:00+00:00"))
        assertEquals(1_800_000_000_500L, EntitlementRows.millis("2027-01-15T08:00:00.5+00:00"))
        assertEquals(1_800_000_000_000L, EntitlementRows.millis("2027-01-15T16:00:00+08:00"))
    }
}
