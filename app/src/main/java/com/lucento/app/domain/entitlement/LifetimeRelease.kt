package com.lucento.app.domain.entitlement

/**
 * Which Pro Lifetime purchases Google Play still lists on this account after Lucento ended them. Releasing them lets the
 * person buy Lifetime again (docs/MONETIZATION.md §5.3 L-7, owner's test 2026-11-13).
 *
 * A Play Console refund leaves the purchase with the buyer unless **Remove entitlement** is ticked, and Google offers no way
 * to remove it afterwards. Lucento ends a refunded Lifetime all the same: Play's server calls it cancelled, which is stored
 * as EXPIRED (§5.1). Play then refuses a new Lifetime with "You already own this item" while Lucento shows the free plan.
 * Consuming the purchase is how Google Play lets an item be bought again.
 *
 * Releasing can't be undone, so only Lucento's server's word counts: the Lifetime row of that purchase (same token) is
 * REVOKED or EXPIRED, on a server row or as the purchase check's answer. These keep a purchase:
 * - its services row ending with the year, which isn't the Lifetime ending;
 * - a row of the same purchase that still grants anything;
 * - a pending payment;
 * - a subscription, which Play can't consume.
 *
 * Pure, so every case is a JVM test.
 */
object LifetimeRelease {

    fun releasable(rows: List<Entitlement>, owned: List<PurchaseRecord>): List<PurchaseRecord> {
        val ended = rows
            .filter { it.source == EntitlementSource.PLAY_LIFETIME && it.status in Entitlement.ENDED && it.endedByServer() }
            .mapNotNullTo(HashSet()) { it.purchaseTokenHash }
        if (ended.isEmpty()) return emptyList()
        val stillGranting = rows.filter { it.status.grantsAccess }.mapNotNullTo(HashSet()) { it.purchaseTokenHash }
        return owned.filter { purchase ->
            val hash = PurchaseInterpreter.tokenHash(purchase.purchaseToken)
            purchase.productId in PlayProducts.ONE_TIME && !purchase.pending && hash in ended && hash !in stillGranting
        }
    }

    /**
     * The server's row, or a Play reading that the purchase check answered. Those are the only ways a Lifetime row ends.
     * The phone never ends one on its own reasoning.
     */
    private fun Entitlement.endedByServer(): Boolean = verifiedBy == Verifier.SERVER || lucentoCheckedAtMillis != null
}
