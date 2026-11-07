package com.lucento.app.data.entitlement

import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.Verifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementCacheTest {
    private val now = 1_800_000_000_000L
    private val key = ByteArray(32) { it.toByte() }
    private val lifetime = Entitlement(
        source = EntitlementSource.PLAY_LIFETIME, status = EntitlementStatus.ACTIVE, grants = setOf(Capability.LOCAL_PRO),
        startsAtMillis = now - 1, expiresAtMillis = null, productId = "lucento_pro_lifetime", basePlanId = null,
        purchaseTokenHash = "ab".repeat(32), verifiedAtMillis = now, verifiedBy = Verifier.PLAY_LOCAL,
    )
    private val sub = Entitlement(
        source = EntitlementSource.PLAY_SUBSCRIPTION, status = EntitlementStatus.CANCELLED_ACTIVE, grants = Capability.entries.toSet(),
        startsAtMillis = now - 5, expiresAtMillis = now + 5, productId = "lucento_pro", basePlanId = "annual",
        purchaseTokenHash = null, verifiedAtMillis = now, verifiedBy = Verifier.SERVER, autoRenewing = false,
    )

    @Test fun round_trip_keeps_every_field() = runTest {
        val cache = SealedEntitlementCache(InMemorySnapshotStore(), HmacSealer(key))
        cache.write(listOf(lifetime, sub))
        assertEquals(listOf(lifetime, sub), cache.read())
    }

    @Test fun nothing_stored_reads_as_unknown() = runTest {
        assertNull(SealedEntitlementCache(InMemorySnapshotStore(), HmacSealer(key)).read())
    }

    @Test fun a_tampered_payload_is_ignored() = runTest {
        val store = InMemorySnapshotStore()
        val cache = SealedEntitlementCache(store, HmacSealer(key))
        cache.write(listOf(sub))
        val sealed = store.snapshot!!
        store.snapshot = sealed.copy(payload = sealed.payload.replace("\"CANCELLED_ACTIVE\"", "\"ACTIVE\"").replace(""""expiresAt":${now + 5}""", """"expiresAt":null"""))
        assertNull("edited snapshot fails verification", cache.read())
    }

    @Test fun a_snapshot_copied_from_another_phone_is_ignored() = runTest {
        val phoneA = InMemorySnapshotStore()
        SealedEntitlementCache(phoneA, HmacSealer(key)).write(listOf(lifetime))
        val phoneB = InMemorySnapshotStore(phoneA.snapshot)
        assertNull(SealedEntitlementCache(phoneB, HmacSealer(ByteArray(32) { 7 })).read())
        // The rightful phone still reads it.
        assertEquals(listOf(lifetime), SealedEntitlementCache(phoneA, HmacSealer(key)).read())
    }

    @Test fun unreadable_or_newer_payloads_read_as_unknown_never_crash() {
        assertNull(EntitlementSnapshotCodec.decode("not json"))
        assertNull(EntitlementSnapshotCodec.decode("""{"version":2,"rows":[]}"""))
        assertNull(EntitlementSnapshotCodec.decode("""{"version":1,"rows":[{"source":"FUTURE_SOURCE","status":"ACTIVE","grants":[],"startsAt":1,"expiresAt":null,"productId":null,"basePlanId":null,"tokenHash":null,"verifiedAt":1,"verifiedBy":"SERVER","autoRenewing":false}]}"""))
        assertEquals(emptyList<Entitlement>(), EntitlementSnapshotCodec.decode("""{"version":1,"rows":[]}"""))
    }

    @Test fun the_payload_never_contains_a_purchase_token_only_its_hash() {
        val json = EntitlementSnapshotCodec.encode(listOf(lifetime))
        assertTrue(json.contains("ab".repeat(32)))
        assertTrue(!json.contains("purchaseToken\""))
    }

    @Test fun seals_compare_in_constant_time_shape() {
        val sealer = HmacSealer(key)
        val seal = sealer.seal("payload")
        assertTrue(sealer.verify("payload", seal))
        assertTrue(!sealer.verify("payload", seal.dropLast(1) + (if (seal.last() == '0') '1' else '0')))
        assertTrue(!sealer.verify("payload", seal.dropLast(1)))
        assertTrue(!constantTimeEquals("abc", "abd"))
        assertTrue(constantTimeEquals("abc", "abc"))
    }
}
