package com.lucento.app.domain.entitlement

import java.security.MessageDigest

/** The products in Google Play Console (docs/MONETIZATION.md §4.1). Ids are configuration, prices never are. */
object PlayProducts {
    const val PRO_SUBSCRIPTION = "lucento_pro"
    const val PRO_LIFETIME = "lucento_pro_lifetime"
    const val CLOUD_ADDON = "lucento_cloud"

    val SUBSCRIPTIONS: Set<String> = setOf(PRO_SUBSCRIPTION, CLOUD_ADDON)
    val ONE_TIME: Set<String> = setOf(PRO_LIFETIME)
    val ALL: Set<String> = SUBSCRIPTIONS + ONE_TIME
}

/** A purchase as Google Play reports it on this phone, stripped to what the entitlement needs. Billing-library free. */
data class PurchaseRecord(
    val productId: String,
    val purchaseToken: String,
    val orderId: String?,
    val purchaseTimeMillis: Long,
    /** Play has not confirmed payment yet (cash at a store). */
    val pending: Boolean,
    val acknowledged: Boolean,
    val autoRenewing: Boolean,
)

/**
 * Turns the phone's Play reading into [Entitlement]s (docs/MONETIZATION.md §5.1 input 2). Pure, so every product and
 * state is a JVM test.
 *
 * A Play `Purchase` carries no expiry, so a subscription read here is trusted for [LOCAL_SUBSCRIPTION_HORIZON_MILLIS]
 * from the moment it was read: Play only lists subscriptions that are live, and every successful re-query pushes the
 * horizon out again. The server's reading (`subscriptionsv2`) carries the real expiry and replaces this one.
 */
object PurchaseInterpreter {
    /** How long a Play-local subscription reading stands before the phone must see it again (the longest base plan + slack). */
    const val LOCAL_SUBSCRIPTION_HORIZON_MILLIS: Long = 35L * 24 * 60 * 60 * 1000
    /** Lifetime's included online services, from the purchase time Play reports (so a reinstall can't restart them). */
    const val LIFETIME_SERVICES_MILLIS: Long = 365L * 24 * 60 * 60 * 1000

    fun entitlements(purchases: List<PurchaseRecord>, now: Long): List<Entitlement> = purchases.flatMap { p ->
        val status = if (p.pending) EntitlementStatus.PENDING else EntitlementStatus.ACTIVE
        val hash = tokenHash(p.purchaseToken)
        when (p.productId) {
            PlayProducts.PRO_SUBSCRIPTION -> listOf(
                Entitlement(
                    source = EntitlementSource.PLAY_SUBSCRIPTION,
                    status = if (!p.pending && !p.autoRenewing) EntitlementStatus.CANCELLED_ACTIVE else status,
                    grants = Capability.entries.toSet(),
                    startsAtMillis = p.purchaseTimeMillis,
                    expiresAtMillis = Math.addExact(now, LOCAL_SUBSCRIPTION_HORIZON_MILLIS),
                    productId = p.productId,
                    purchaseTokenHash = hash,
                    verifiedAtMillis = now,
                    verifiedBy = Verifier.PLAY_LOCAL,
                    autoRenewing = p.autoRenewing,
                ),
            )
            PlayProducts.CLOUD_ADDON -> listOf(
                Entitlement(
                    source = EntitlementSource.PLAY_CLOUD_ADDON,
                    status = if (!p.pending && !p.autoRenewing) EntitlementStatus.CANCELLED_ACTIVE else status,
                    grants = setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED),
                    startsAtMillis = p.purchaseTimeMillis,
                    expiresAtMillis = Math.addExact(now, LOCAL_SUBSCRIPTION_HORIZON_MILLIS),
                    productId = p.productId,
                    purchaseTokenHash = hash,
                    verifiedAtMillis = now,
                    verifiedBy = Verifier.PLAY_LOCAL,
                    autoRenewing = p.autoRenewing,
                ),
            )
            PlayProducts.PRO_LIFETIME -> {
                val servicesEnd = Math.addExact(p.purchaseTimeMillis, LIFETIME_SERVICES_MILLIS)
                listOf(
                    Entitlement(
                        source = EntitlementSource.PLAY_LIFETIME,
                        status = status,
                        grants = setOf(Capability.LOCAL_PRO),
                        startsAtMillis = p.purchaseTimeMillis,
                        expiresAtMillis = null,
                        productId = p.productId,
                        purchaseTokenHash = hash,
                        verifiedAtMillis = now,
                        verifiedBy = Verifier.PLAY_LOCAL,
                    ),
                    Entitlement(
                        source = EntitlementSource.PLAY_LIFETIME_SERVICES,
                        status = if (p.pending) EntitlementStatus.PENDING else if (now < servicesEnd) EntitlementStatus.ACTIVE else EntitlementStatus.EXPIRED,
                        grants = setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED),
                        startsAtMillis = p.purchaseTimeMillis,
                        expiresAtMillis = servicesEnd,
                        productId = p.productId,
                        purchaseTokenHash = hash,
                        verifiedAtMillis = now,
                        verifiedBy = Verifier.PLAY_LOCAL,
                    ),
                )
            }
            else -> emptyList() // not one of ours; ignored
        }
    }

    /** SHA-256, lower-case hex. The token itself is never stored. */
    fun tokenHash(purchaseToken: String): String =
        MessageDigest.getInstance("SHA-256").digest(purchaseToken.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
