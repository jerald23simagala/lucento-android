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
 * - What the phone already knew about the same purchase (same key, same token) stays with Play's new reading (L-6). That
 *   covers an ending the purchase check reported, which Play's copy can't undo, and Lucento's last confirmation, taken
 *   from the account's own row for that purchase when it still grants. It also covers when the check last answered.
 * - The server's answer replaces the phone's rows for the keys it holds, except a **different purchase** of the same
 *   product while the server's row no longer counts. That's the product bought again after a refund or an expiry, which
 *   the server hasn't recorded yet (`/api/billing/verify` failed, or refused a test purchase). Play's reading of it stays,
 *   so the purchase check and the next verification still see it (2026-11-13, MONETIZATION §5.3 L-7). Without the rule,
 *   a buyer saw the free plan right after paying (KNOWN_ISSUES PB-9).
 */
object EntitlementMerge {

    fun reconcilePlay(current: List<Entitlement>, play: List<Entitlement>, now: Long): List<Entitlement> {
        require(play.all { it.verifiedBy == Verifier.PLAY_LOCAL }) { "Play readings must be PLAY_LOCAL" }
        val serverByKey = current.filter { it.verifiedBy == Verifier.SERVER }.associateBy { it.key }
        val before = current.filter { it.verifiedBy == Verifier.PLAY_LOCAL && it.purchaseTokenHash != null }
            .associateBy { it.key to it.purchaseTokenHash }
        val kept = current.filter { it.verifiedBy == Verifier.SERVER }.toMutableList()
        val replacedServerKeys = HashSet<EntitlementKey>()
        for (reading in play) {
            val row = reading.carriedFrom(before[reading.key to reading.purchaseTokenHash], serverByKey[reading.key])
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
        status in Entitlement.ENDED && purchaseTokenHash != null && purchaseTokenHash == play.purchaseTokenHash

    /**
     * Play's new reading of a purchase, keeping what this phone already knew about the same purchase ([before], the same
     * key and token): an ending the purchase check reported, and Lucento's latest confirmation. That's the check's,
     * or the account's own [server] row for the same token while it grants. It also keeps when the check last answered.
     * A reading without a token can't be matched and stays as it is.
     */
    private fun Entitlement.carriedFrom(before: Entitlement?, server: Entitlement?): Entitlement {
        if (purchaseTokenHash == null) return this
        val account = server?.takeIf { it.purchaseTokenHash == purchaseTokenHash && it.status.grantsAccess }
        val confirmed = listOfNotNull(confirmedAtMillis, before?.confirmedAtMillis, account?.checkedAtMillis).maxOrNull()
        return copy(
            status = if (before != null && before.status in Entitlement.ENDED) before.status else status,
            confirmedAtMillis = confirmed,
            lucentoCheckedAtMillis = listOfNotNull(lucentoCheckedAtMillis, before?.lucentoCheckedAtMillis).maxOrNull(),
        )
    }

    fun reconcileServer(current: List<Entitlement>, server: List<Entitlement>, now: Long): List<Entitlement> {
        require(server.all { it.verifiedBy == Verifier.SERVER }) { "Server readings must be SERVER" }
        val serverByKey = server.groupBy { it.key }
        val playRows = current.filter { row ->
            row.verifiedBy == Verifier.PLAY_LOCAL && serverByKey[row.key].orEmpty().all { it.earlierPurchaseThan(row, now) }
        }
        return server + playRows
    }

    /**
     * This server row is about an earlier purchase of the same product that no longer counts, and [play] is a different
     * purchase: a new token the server hasn't recorded yet. Rows without a token can't be told apart and stay the server's.
     */
    private fun Entitlement.earlierPurchaseThan(play: Entitlement, now: Long): Boolean =
        purchaseTokenHash != null && play.purchaseTokenHash != null && purchaseTokenHash != play.purchaseTokenHash &&
            !stillCurrent(now)

    /**
     * True while the row still allows access: a granting status, its dates with the offline grace, and a check within
     * the month. A server row the phone hasn't heard about for a month gives way to Play's fresh word.
     */
    private fun Entitlement.stillCurrent(now: Long): Boolean =
        status.grantsAccess && (expiresAtMillis == null || now < Math.addExact(expiresAtMillis, Entitlement.OFFLINE_GRACE_MILLIS)) &&
            !awaitsCheck(now)
}
