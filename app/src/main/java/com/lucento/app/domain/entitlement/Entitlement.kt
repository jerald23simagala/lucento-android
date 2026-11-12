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
) {
    /** Rows that describe the same thing replace each other: (source, product). */
    val key: EntitlementKey get() = EntitlementKey(source, productId)

    /**
     * Whether this row grants [capability] at [now]. A row with an expiry is honoured for [offlineGraceMillis] past it,
     * so a phone that could not re-check for a few days keeps working (docs §5.3 L-1). A Lifetime has no expiry, but it
     * needs a fresh check at least every [LIFETIME_RECHECK_DAYS] days ([awaitsCheck], L-2).
     */
    fun covers(capability: Capability, now: Long, offlineGraceMillis: Long = OFFLINE_GRACE_MILLIS): Boolean =
        capability in grants && status.grantsAccess &&
            (expiresAtMillis == null || now < Math.addExact(expiresAtMillis, offlineGraceMillis)) &&
            !awaitsCheck(now)

    /**
     * A Lifetime row nobody has confirmed for [LIFETIME_RECHECK_DAYS] days (owner decision 2026-11-12, docs §5.3 L-2). A
     * refund reaches the phone through Google Play or Lucento's server. A phone that stops letting Lucento hear from
     * both can't keep a refunded Lifetime for good. An owner keeps it as long as either source answers once a month.
     */
    fun awaitsCheck(now: Long): Boolean =
        source in LIFETIME_SOURCES && now >= Math.addExact(checkedAtMillis, LIFETIME_RECHECK_MILLIS)

    companion object {
        const val OFFLINE_GRACE_MILLIS: Long = 3L * 24 * 60 * 60 * 1000
        const val LIFETIME_RECHECK_DAYS: Int = 30
        const val LIFETIME_RECHECK_MILLIS: Long = LIFETIME_RECHECK_DAYS * 24L * 60 * 60 * 1000
        private val LIFETIME_SOURCES = setOf(EntitlementSource.PLAY_LIFETIME, EntitlementSource.PLAY_LIFETIME_SERVICES)
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
     * A Pro Lifetime that stopped counting only because nobody has confirmed it for a month ([Entitlement.awaitsCheck]):
     * paused, not refunded. Google Play or the server answering once brings it back.
     */
    fun lifetimeAwaitingCheck(now: Long): Boolean =
        entitlements.none { it.source == EntitlementSource.PLAY_LIFETIME && it.covers(Capability.LOCAL_PRO, now) } &&
            entitlements.any {
                it.source == EntitlementSource.PLAY_LIFETIME && Capability.LOCAL_PRO in it.grants && it.status.grantsAccess &&
                    it.awaitsCheck(now)
            }

    companion object {
        val FREE = EntitlementState(emptyList())
    }
}
