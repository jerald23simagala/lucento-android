package com.lucento.app.data.billing

import com.lucento.app.data.ai.postToProxy
import com.lucento.app.domain.entitlement.PurchaseRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the server said about one purchase (docs/monetization/SERVER_CONTRACT.md §3). */
enum class VerifyOutcome {
    /** Checked with Google Play and recorded on this Lucento account. */
    VERIFIED,
    /** The purchase is recorded on a different Lucento account; this one can't take it over (§5.4 linking rule). */
    LINKED_ELSEWHERE,
    /** The purchase moved between accounts too often; support has to look at it. */
    REVIEW_REQUIRED,
    /** No Lucento account is signed in, so there's nothing to record it on. Not a problem: Play's reading still counts. */
    SIGNED_OUT,
    /** No connection, the server or Play didn't answer, or this build has no server. Tried again on the next refresh. */
    UNAVAILABLE,
    ;

    /** An answer that won't change by asking again in this process. */
    val settled: Boolean get() = this == VERIFIED || this == LINKED_ELSEWHERE || this == REVIEW_REQUIRED
}

/** Tells the server about a purchase so it can check it with Play and keep the account's copy (`public.entitlements`). */
fun interface PurchaseVerifier {
    suspend fun verify(purchase: PurchaseRecord): VerifyOutcome
}

/**
 * `POST /api/billing/verify` on Lucento's own site (lucento-website), with the signed-in person's Supabase token. The
 * site holds the Play Developer API credentials and the service-role key; the app holds neither (CLAUDE.md). Sent: the
 * purchase token, the product id and the package name — nothing about the person's money.
 */
class ProxyPurchaseVerifier(
    private val endpoint: String,
    private val packageName: String,
    private val accessToken: suspend (refresh: Boolean) -> String?,
) : PurchaseVerifier {
    override suspend fun verify(purchase: PurchaseRecord): VerifyOutcome = withContext(Dispatchers.IO) {
        if (endpoint.isBlank()) return@withContext VerifyOutcome.UNAVAILABLE
        val body = json.encodeToString(VerifyRequest.serializer(), VerifyRequest(purchase.purchaseToken, purchase.productId, packageName))
        val reply = runCatching { postToProxy(endpoint, body, READ_TIMEOUT_MS, errorBodies = true, accessToken = accessToken) }
            .getOrElse { return@withContext VerifyOutcome.UNAVAILABLE }
            ?: return@withContext VerifyOutcome.SIGNED_OUT
        outcome(reply.code, reply.body)
    }

    internal companion object {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private const val READ_TIMEOUT_MS = 20_000

        /** The server's answer in [VerifyOutcome] terms. Pure; the contract's status codes are pinned by a test. */
        fun outcome(code: Int, body: String?): VerifyOutcome = when (code) {
            200 -> VerifyOutcome.VERIFIED
            401 -> VerifyOutcome.SIGNED_OUT
            409 -> when (runCatching { json.decodeFromString(VerifyError.serializer(), body.orEmpty()).error }.getOrNull()) {
                "review_required" -> VerifyOutcome.REVIEW_REQUIRED
                else -> VerifyOutcome.LINKED_ELSEWHERE
            }
            else -> VerifyOutcome.UNAVAILABLE
        }
    }
}

@Serializable
internal data class VerifyRequest(val purchaseToken: String, val productId: String, val packageName: String)

@Serializable
internal data class VerifyError(val error: String? = null)
