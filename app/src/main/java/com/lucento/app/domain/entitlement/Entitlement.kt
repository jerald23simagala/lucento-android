package com.lucento.app.domain.entitlement

/**
 * What a plan can unlock, in four groups (docs/MONETIZATION.md §0, §5.1). Pro Lifetime owns [LOCAL_PRO] forever; the
 * other three run on servers and are sold as a service (a subscription, Lifetime's included year, or the Cloud add-on).
 */
enum class Capability {
    /** Every Pro feature that runs on the phone: unlimited wallets, 20 goals, 12-month Insights, other currencies, PDF, themes. */
    LOCAL_PRO,
    /** Cloud sync and restoring from the account (Supabase). */
    CLOUD,
    /** The AI adviser's Pro allowance and Smarter reading (Gemini through the proxy). */
    AI_PRO,
    /** Lucento Shared: inviting a Partner. Needs [CLOUD] in practice; granted together with it. */
    SHARED,
}

/** Where an [Entitlement] comes from. Each source row is keyed by (source, productId); a person can hold several. */
enum class EntitlementSource {
    /** `lucento_pro`: everything, while it renews. */
    PLAY_SUBSCRIPTION,
    /** `lucento_pro_lifetime`: [Capability.LOCAL_PRO], never expires. */
    PLAY_LIFETIME,
    /** The 12 months of online services that come with Lifetime; derived from the purchase time, so a reinstall can't restart them. */
    PLAY_LIFETIME_SERVICES,
    /** `lucento_cloud`: the online services for a Lifetime owner after the included year. */
    PLAY_CLOUD_ADDON,
    /** Written by the owner on the server: founders, testers, goodwill. Always server-verified, always with an expiry. */
    GRANT,
}

/** Google Play's subscription lifecycle plus the one-time states, as the server and the phone both read them. */
enum class EntitlementStatus {
    ACTIVE,
    TRIAL,
    /** Payment failed; Play keeps access during its grace period. */
    GRACE,
    /** Cancelled but paid up to [Entitlement.expiresAtMillis]. */
    CANCELLED_ACTIVE,
    /** Payment failed and the grace period ended; Play recommends revoking access. */
    ON_HOLD,
    PAUSED,
    /** A purchase Play has not confirmed yet (cash at a store). Grants nothing until it completes. */
    PENDING,
    EXPIRED,
    /** Refunded with revoke, charged back, or removed by the owner. */
    REVOKED,
    ;

    /** Whether this status, on its own, lets the entitlement grant anything. */
    val grantsAccess: Boolean get() = this == ACTIVE || this == TRIAL || this == GRACE || this == CANCELLED_ACTIVE
}

/** Who last confirmed an [Entitlement]. The server's reading replaces the phone's for the same key (docs §5.1). */
enum class Verifier {
    /** Read from Google Play on this phone (`queryPurchasesAsync`); works offline from Play's own cache. */
    PLAY_LOCAL,
    /** Read from `public.entitlements`, which the Vercel endpoints keep current with the Play Developer API. */
    SERVER,
}

/**
 * One verified reason this person has some [Capability]s. Never a Boolean: a Lifetime owner after month 12 holds
 * [Capability.LOCAL_PRO] with no expiry and nothing else; a subscriber in grace holds everything until a date.
 */
data class Entitlement(
    val source: EntitlementSource,
    val status: EntitlementStatus,
    val grants: Set<Capability>,
    val startsAtMillis: Long,
    /** Null: never expires (Lifetime's [Capability.LOCAL_PRO]). */
    val expiresAtMillis: Long?,
    val productId: String?,
    val basePlanId: String? = null,
    /** SHA-256 of the Play purchase token, so the token itself is never stored on the phone or the server. */
    val purchaseTokenHash: String? = null,
    val verifiedAtMillis: Long,
    val verifiedBy: Verifier,
    val autoRenewing: Boolean = false,
    /**
     * When this phone last heard about this row from its [verifiedBy]: Google Play's reading for a [Verifier.PLAY_LOCAL]
     * row (the same moment as [verifiedAtMillis]), the latest successful server fetch for a [Verifier.SERVER] row.
     */
    val checkedAtMillis: Long = verifiedAtMillis,
    /**
     * [Verifier.PLAY_LOCAL] rows only: when Lucento's server last said this purchase is live. That's the purchase check,
     * which needs no account, or the account's own row for the purchase. Null means not yet on this install. Google Play's
     * word alone carries a purchase for [FIRST_CONFIRMATION_DAYS] days from its purchase time, then
     * [CONFIRMATION_CAP_DAYS] days from Lucento's last confirmation ([awaitsLucento], owner decision 2026-11-13, docs §5.3
     * L-6). It defaults to the reading time, so a row this phone cached before the check existed counts as confirmed then.
     * A build without the purchase check (debug, the plan simulator) keeps that default: there, Play's word is the
     * confirmation.
     */
    val confirmedAtMillis: Long? = verifiedAtMillis,
    /** [Verifier.PLAY_LOCAL] rows only: when this phone last had an answer from the purchase check, to space the checks out. */
    val lucentoCheckedAtMillis: Long? = null,
) {
    /** Rows that describe the same thing replace each other: (source, product). */
    val key: EntitlementKey get() = EntitlementKey(source, productId)

    /**
     * Whether this row grants [capability] at [now]. A row with an expiry is honoured for [offlineGraceMillis] past it,
     * so a phone that could not re-check for a few days keeps working (docs §5.3 L-1). Anything bought through Google
     * Play also needs a fresh check at least every [RECHECK_DAYS] days ([awaitsCheck], L-2), a Lifetime included.
     */
    fun covers(capability: Capability, now: Long, offlineGraceMillis: Long = OFFLINE_GRACE_MILLIS): Boolean =
        capability in grants && status.grantsAccess &&
            (expiresAtMillis == null || now < Math.addExact(expiresAtMillis, offlineGraceMillis)) &&
            !awaitsCheck(now)

    /**
     * A row bought through Google Play that nobody has confirmed for [RECHECK_DAYS] days (owner decisions 2026-11-12, docs
     * §5.3 L-2). A refund reaches the phone through Google Play or Lucento's server. A phone that stops letting Lucento
     * hear from both can't keep a refunded plan, a Lifetime or a year's subscription, past the month. A buyer keeps it as
     * long as either source answers once a month. Grants are the owner's, server-only, and keep their own dates.
     */
    fun awaitsCheck(now: Long): Boolean =
        source in PLAY_SOURCES && (now >= Math.addExact(checkedAtMillis, RECHECK_MILLIS) || awaitsLucento(now))

    /**
     * A Google Play reading that Lucento's server hasn't confirmed lately (owner decision 2026-11-13, docs §5.3 L-6).
     * Play's copy on the phone can lag behind a refund for days, so it carries a purchase only so far:
     * - [FIRST_CONFIRMATION_DAYS] days from the purchase time when the server hasn't confirmed it on this install. A data
     *   clear, a reinstall or switching Play profiles can't restart that, because Google signs the purchase time.
     * - [CONFIRMATION_CAP_DAYS] days from the server's last confirmation otherwise, so a phone kept fully offline after a
     *   refund loses it in the end too.
     * Paused, never ended: one answer from the server brings it back. A server row is the server's own word.
     */
    fun awaitsLucento(now: Long): Boolean {
        if (verifiedBy != Verifier.PLAY_LOCAL || source !in PLAY_SOURCES) return false
        val confirmed = confirmedAtMillis
        return if (confirmed == null) {
            now >= Math.addExact(startsAtMillis, FIRST_CONFIRMATION_MILLIS)
        } else {
            now >= Math.addExact(confirmed, CONFIRMATION_CAP_MILLIS)
        }
    }

    companion object {
        const val OFFLINE_GRACE_MILLIS: Long = 3L * 24 * 60 * 60 * 1000
        const val RECHECK_DAYS: Int = 30
        const val RECHECK_MILLIS: Long = RECHECK_DAYS * 24L * 60 * 60 * 1000
        /** L-6: how long Google Play's word alone carries a purchase Lucento's server hasn't confirmed on this install. */
        const val FIRST_CONFIRMATION_DAYS: Int = 3
        const val FIRST_CONFIRMATION_MILLIS: Long = FIRST_CONFIRMATION_DAYS * 24L * 60 * 60 * 1000
        /** L-6: how long Google Play's word alone carries a purchase after Lucento's server last confirmed it. */
        const val CONFIRMATION_CAP_DAYS: Int = 90
        const val CONFIRMATION_CAP_MILLIS: Long = CONFIRMATION_CAP_DAYS * 24L * 60 * 60 * 1000
        /** Statuses that end a purchase for good: the same token never comes back from them (EntitlementMerge). */
        val ENDED: Set<EntitlementStatus> = setOf(EntitlementStatus.REVOKED, EntitlementStatus.EXPIRED)
        private val PLAY_SOURCES = setOf(
            EntitlementSource.PLAY_SUBSCRIPTION,
            EntitlementSource.PLAY_LIFETIME,
            EntitlementSource.PLAY_LIFETIME_SERVICES,
            EntitlementSource.PLAY_CLOUD_ADDON,
        )
    }
}

data class EntitlementKey(val source: EntitlementSource, val productId: String?)

/** What the person's plan looks like in words: for badges and the plan row only, never for access decisions. */
enum class DisplayPlan { FREE, PRO, LIFETIME, LIFETIME_WITH_SERVICES }

/**
 * Everything verified about this person's plans. The single observable truth for the app; [FREE] when nothing is known.
 */
data class EntitlementState(val entitlements: List<Entitlement>) {

    fun has(capability: Capability, now: Long): Boolean = entitlements.any { it.covers(capability, now) }

    fun capabilities(now: Long): Set<Capability> = Capability.entries.filterTo(LinkedHashSet()) { has(it, now) }

    /** The rows that grant something right now, newest verification first. */
    fun active(now: Long): List<Entitlement> =
        entitlements.filter { e -> Capability.entries.any { e.covers(it, now) } }.sortedByDescending { it.verifiedAtMillis }

    fun displayPlan(now: Long): DisplayPlan {
        val lifetime = entitlements.any { it.source == EntitlementSource.PLAY_LIFETIME && it.covers(Capability.LOCAL_PRO, now) }
        return when {
            lifetime && has(Capability.CLOUD, now) -> DisplayPlan.LIFETIME_WITH_SERVICES
            lifetime -> DisplayPlan.LIFETIME
            has(Capability.LOCAL_PRO, now) -> DisplayPlan.PRO
            else -> DisplayPlan.FREE
        }
    }

    /** A pending purchase waiting for Google Play to confirm it. */
    fun pending(): Entitlement? = entitlements.firstOrNull { it.status == EntitlementStatus.PENDING }

    /**
     * Pro stopped counting only because the purchase is waiting for a check ([Entitlement.awaitsCheck]): nobody has
     * confirmed it for a month, or Lucento's server hasn't confirmed Google Play's reading lately (L-6). Paused, not
     * ended: an answer brings it back. A Play reading's end date is only the date it must be read again by, so it doesn't
     * count as the plan ending; a server row past its real end does.
     */
    fun awaitingCheck(now: Long): Boolean =
        !has(Capability.LOCAL_PRO, now) && entitlements.any {
            Capability.LOCAL_PRO in it.grants && it.status.grantsAccess && it.awaitsCheck(now) &&
                (it.verifiedBy == Verifier.PLAY_LOCAL || it.expiresAtMillis == null ||
                    now < Math.addExact(it.expiresAtMillis, Entitlement.OFFLINE_GRACE_MILLIS))
        }

    companion object {
        val FREE = EntitlementState(emptyList())
    }
}
