package com.lucento.app.data.billing

import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.PurchaseRecord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The purchase check's contract (docs/monetization/SERVER_CONTRACT.md §3b), as the app reads it. */
class ProxyPurchaseCheckerTest {

    @Test fun a_known_state_is_an_answer() {
        for (status in EntitlementStatus.entries) {
            assertEquals(PurchaseVerdict.Known(status), ProxyPurchaseChecker.verdict(200, """{"status":"${status.name}"}"""))
        }
    }

    @Test fun anything_else_changes_nothing() {
        assertEquals(PurchaseVerdict.Unavailable, ProxyPurchaseChecker.verdict(200, """{"status":"FOREVER"}"""))
        assertEquals(PurchaseVerdict.Unavailable, ProxyPurchaseChecker.verdict(200, """{}"""))
        assertEquals(PurchaseVerdict.Unavailable, ProxyPurchaseChecker.verdict(200, "not json"))
        assertEquals(PurchaseVerdict.Unavailable, ProxyPurchaseChecker.verdict(200, null))
        // bad_request / bad_signature, purchase_not_found, rate limited, Play or the database down, not configured.
        for (code in listOf(400, 404, 429, 502, 503, 500)) {
            assertEquals("HTTP $code", PurchaseVerdict.Unavailable, ProxyPurchaseChecker.verdict(code, """{"status":"REVOKED"}"""))
        }
    }

    @Test fun a_purchase_without_googles_signed_data_is_never_sent() = runTest {
        // The test store's purchases carry none; nothing is sent, so the endpoint below is never reached.
        val record = PurchaseRecord(PlayProducts.PRO_LIFETIME, "tok", "GPA.1", 1L, pending = false, acknowledged = true, autoRenewing = false)
        assertEquals(PurchaseVerdict.Unavailable, ProxyPurchaseChecker("https://invalid.example/api/billing/check").check(record))
        assertEquals(PurchaseVerdict.Unavailable, ProxyPurchaseChecker("").check(record.copy(signedData = "{}", signature = "sig")))
    }
}
