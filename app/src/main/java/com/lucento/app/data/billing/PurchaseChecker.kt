package com.lucento.app.data.billing

import com.lucento.app.data.ai.postWithoutAccount
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.PurchaseRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What Lucento's server said about one Google Play purchase (docs/monetization/SERVER_CONTRACT.md §3b). */
sealed interface PurchaseVerdict {
    /** Google's current state of the purchase, as the server read it. REVOKED and EXPIRED end it for good. */
    data class Known(val status: EntitlementStatus) : PurchaseVerdict

    /** No connection, the server or Google didn't answer, or this purchase can't be checked. Changes nothing. */
    data object Unavailable : PurchaseVerdict
}

/**
 * The purchase check (owner decision 2026-11-13, docs/MONETIZATION.md §5.3 L-6). It asks Lucento's server whether a
 * purchase Google Play lists on this phone is still live, whether or not anyone is signed in. Play's copy on the phone
 * can lag behind a refund by days.
 */
fun interface PurchaseChecker {
    suspend fun check(purchase: PurchaseRecord): PurchaseVerdict
}

/**
 * `POST /api/billing/check` on Lucento's own site, without an account. Sent: Google Play's signed purchase data and its
 * signature, exactly as Play gave them: what was bought, its order number, status, dates and purchase token, never
 * anything about the person's money. The site checks the signature with Play's public key, asks Google and answers with
 * the purchase's state.
 */
class ProxyPurchaseChecker(private val endpoint: String) : PurchaseChecker {
    override suspend fun check(purchase: PurchaseRecord): PurchaseVerdict = withContext(Dispatchers.IO) {
        val data = purchase.signedData
        val signature = purchase.signature
        if (endpoint.isBlank() || data.isNullOrBlank() || signature.isNullOrBlank()) return@withContext PurchaseVerdict.Unavailable
        val body = json.encodeToString(CheckRequest.serializer(), CheckRequest(purchaseData = data, signature = signature))
        val reply = runCatching { postWithoutAccount(endpoint, body, READ_TIMEOUT_MS) }
            .getOrElse { return@withContext PurchaseVerdict.Unavailable }
        verdict(reply.code, reply.body)
    }

    internal companion object {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private const val READ_TIMEOUT_MS = 15_000

        /** The server's answer as a [PurchaseVerdict]. Pure; the contract's status codes are pinned by a test. */
        fun verdict(code: Int, body: String?): PurchaseVerdict {
            if (code != 200) return PurchaseVerdict.Unavailable
            val status = runCatching { json.decodeFromString(CheckReply.serializer(), body.orEmpty()).status }.getOrNull()
            val known = EntitlementStatus.entries.firstOrNull { it.name == status } ?: return PurchaseVerdict.Unavailable
            return PurchaseVerdict.Known(known)
        }
    }
}

@Serializable
internal data class CheckRequest(val purchaseData: String, val signature: String)

@Serializable
internal data class CheckReply(val status: String? = null)
