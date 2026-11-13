package com.lucento.app.domain.entitlement

/**
 * How readings from Google Play and from the server combine into one list (docs/MONETIZATION.md §5.1, §5.3).
 *
 * Rules, in one place so they are tested:
 * - Each row is owned by the verifier that wrote it. A Play query only ever touches [Verifier.PLAY_LOCAL] rows; the server
 *   only ever touches [Verifier.SERVER] rows — except that the server's reading of a key replaces the phone's, because it
 *   knows expiry and revocation.
 * - A *successful* Play query that no longer lists a purchase removes the phone's row for it (the subscription lapsed and
 *   Play dropped it, or another Google account is signed in to Play). A server row for the same key is untouched: it is
 *   the server's to end. A Play query that *failed* never reaches here, so unreachable Play never removes anything (L-2).
 * - When both verifiers hold the same key, the server's row stands while it is not past its expiry (plus the offline
 *   grace); a server row that has lapsed gives way to a fresh Play reading, which is how a renewal shows up before the
 *   server has heard of it.
 * - Except a purchase the server says has **ended**: revoked (refunded with revoke, charged back) or expired (Play's
 *   own server called it cancelled or expired — a Play Console refund of a Lifetime arrives this way, owner's test
 *   2026-11-13). Play's copy of that same purchase (same token) can lag behind on the phone, so it never brings it
 *   back: offline, on Play's pushes, or after signing out (owner decision 2026-11-12). Neither state comes back for the
 *   same token, since buying again after expiry is a new purchase with a new token, and that counts at once. Account
 *   hold and pause keep their token, so Play's reading still wins there.
 */
object EntitlementMerge {

    fun reconcilePlay(current: List<Entitlement>, play: List<Entitlement>, now: Long): List<Entitlement> {
        require(play.all { it.verifiedBy == Verifier.PLAY_LOCAL }) { "Play readings must be PLAY_LOCAL" }
        val serverByKey = current.filter { it.verifiedBy == Verifier.SERVER }.associateBy { it.key }
        val kept = current.filter { it.verifiedBy == Verifier.SERVER }.toMutableList()
        val replacedServerKeys = HashSet<EntitlementKey>()
        for (row in play) {
            val server = serverByKey[row.key]
            if (server == null) {
                kept += row
            } else if (!server.stillCurrent(now) && !server.ended(row)) {
                // The server's copy has lapsed and Play says the purchase is live again: trust the newer reading.
                replacedServerKeys += row.key
                kept += row
            }
        }
        return kept.filterNot { it.verifiedBy == Verifier.SERVER && it.key in replacedServerKeys }
    }

    /**
     * The server ended [play]'s own purchase for good: the same purchase token, revoked or expired. Hold, pause and a
     * lapsed date are not endings: the same token can be live again.
     */
    private fun Entitlement.ended(play: Entitlement): Boolean =
        status in ENDED && purchaseTokenHash != null && purchaseTokenHash == play.purchaseTokenHash

    private val ENDED = setOf(EntitlementStatus.REVOKED, EntitlementStatus.EXPIRED)

    fun reconcileServer(current: List<Entitlement>, server: List<Entitlement>): List<Entitlement> {
        require(server.all { it.verifiedBy == Verifier.SERVER }) { "Server readings must be SERVER" }
        val serverKeys = server.mapTo(HashSet()) { it.key }
        val playRows = current.filter { it.verifiedBy == Verifier.PLAY_LOCAL && it.key !in serverKeys }
        return server + playRows
    }

    /**
     * True while the row still allows access: a granting status, its dates with the offline grace, and a check within
     * the month. A server row the phone hasn't heard about for a month gives way to Play's fresh word.
     */
    private fun Entitlement.stillCurrent(now: Long): Boolean =
        status.grantsAccess && (expiresAtMillis == null || now < Math.addExact(expiresAtMillis, Entitlement.OFFLINE_GRACE_MILLIS)) &&
            !awaitsCheck(now)
}
