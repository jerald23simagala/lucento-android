package com.lucento.app.data.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lucento Pro, Phase 4: Play's answers in plain terms, and the server's verify contract (SERVER_CONTRACT.md §3). */
class BillingProblemTest {
    @Test fun the_repeated_codes_match_the_billing_library() {
        assertEquals(BillingResponseCode.OK, BillingProblem.CODE_OK)
        assertEquals(BillingResponseCode.USER_CANCELED, BillingProblem.CODE_USER_CANCELED)
        assertEquals(BillingResponseCode.SERVICE_TIMEOUT, BillingProblem.CODE_SERVICE_TIMEOUT)
        assertEquals(BillingResponseCode.FEATURE_NOT_SUPPORTED, BillingProblem.CODE_FEATURE_NOT_SUPPORTED)
        assertEquals(BillingResponseCode.SERVICE_DISCONNECTED, BillingProblem.CODE_SERVICE_DISCONNECTED)
        assertEquals(BillingResponseCode.SERVICE_UNAVAILABLE, BillingProblem.CODE_SERVICE_UNAVAILABLE)
        assertEquals(BillingResponseCode.BILLING_UNAVAILABLE, BillingProblem.CODE_BILLING_UNAVAILABLE)
        assertEquals(BillingResponseCode.ITEM_UNAVAILABLE, BillingProblem.CODE_ITEM_UNAVAILABLE)
        assertEquals(BillingResponseCode.DEVELOPER_ERROR, BillingProblem.CODE_DEVELOPER_ERROR)
        assertEquals(BillingResponseCode.ITEM_ALREADY_OWNED, BillingProblem.CODE_ITEM_ALREADY_OWNED)
        assertEquals(BillingResponseCode.ITEM_NOT_OWNED, BillingProblem.CODE_ITEM_NOT_OWNED)
        assertEquals(BillingResponseCode.NETWORK_ERROR, BillingProblem.CODE_NETWORK_ERROR)
    }

    @Test fun each_play_answer_maps_to_one_plain_problem() {
        assertEquals(BillingProblem.UNAVAILABLE, BillingProblem.fromResponseCode(BillingResponseCode.BILLING_UNAVAILABLE))
        assertEquals(BillingProblem.NETWORK, BillingProblem.fromResponseCode(BillingResponseCode.NETWORK_ERROR))
        assertEquals(BillingProblem.NETWORK, BillingProblem.fromResponseCode(BillingResponseCode.SERVICE_DISCONNECTED))
        assertEquals(BillingProblem.ALREADY_OWNED, BillingProblem.fromResponseCode(BillingResponseCode.ITEM_ALREADY_OWNED))
        assertEquals(BillingProblem.ITEM_UNAVAILABLE, BillingProblem.fromResponseCode(BillingResponseCode.ITEM_UNAVAILABLE))
        assertEquals(BillingProblem.DEVELOPER, BillingProblem.fromResponseCode(BillingResponseCode.DEVELOPER_ERROR))
        assertEquals(BillingProblem.OTHER, BillingProblem.fromResponseCode(BillingResponseCode.ERROR))
        assertEquals(BillingProblem.OTHER, BillingProblem.fromResponseCode(99))
    }

    @Test fun the_verify_endpoints_answers_follow_the_server_contract() {
        assertEquals(VerifyOutcome.VERIFIED, ProxyPurchaseVerifier.outcome(200, """{"result":"ok"}"""))
        assertEquals(VerifyOutcome.SIGNED_OUT, ProxyPurchaseVerifier.outcome(401, """{"error":"sign_in_required"}"""))
        assertEquals(VerifyOutcome.LINKED_ELSEWHERE, ProxyPurchaseVerifier.outcome(409, """{"error":"linked_elsewhere"}"""))
        assertEquals(VerifyOutcome.REVIEW_REQUIRED, ProxyPurchaseVerifier.outcome(409, """{"error":"review_required"}"""))
        assertEquals("an unreadable 409 is still a refusal", VerifyOutcome.LINKED_ELSEWHERE, ProxyPurchaseVerifier.outcome(409, null))
        assertEquals(VerifyOutcome.UNAVAILABLE, ProxyPurchaseVerifier.outcome(502, """{"error":"play_unavailable"}"""))
        assertEquals(VerifyOutcome.UNAVAILABLE, ProxyPurchaseVerifier.outcome(404, """{"error":"purchase_not_found"}"""))
        assertEquals(VerifyOutcome.UNAVAILABLE, ProxyPurchaseVerifier.outcome(503, null))
    }
}
