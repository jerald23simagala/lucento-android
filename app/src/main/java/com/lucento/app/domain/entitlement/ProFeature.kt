package com.lucento.app.domain.entitlement

/**
 * Every place the plan matters (docs/MONETIZATION.md §3, §5.2). Each needs one [Capability]. Not to be confused with
 * [com.lucento.app.domain.Feature], the person's own module toggles: a module can be on and locked.
 */
enum class ProFeature(val requires: Capability) {
    /** Cash, bank, e-wallet and savings wallets (Free: a count). */
    MONEY_WALLETS(Capability.LOCAL_PRO),
    /** Credit cards and loans (Free: a count). */
    DEBT_ACCOUNTS(Capability.LOCAL_PRO),
    /** Money someone owes the person. */
    RECEIVABLES(Capability.LOCAL_PRO),
    /** A home, a car, investments at a value the person sets. */
    VALUED_ASSETS(Capability.LOCAL_PRO),
    /** A wallet in a currency other than the main one. */
    FOREIGN_CURRENCY_WALLETS(Capability.LOCAL_PRO),
    /** Active savings goals (a count on every plan). */
    SAVINGS_GOALS(Capability.LOCAL_PRO),
    /** Insights over 6 and 12 months (3 months is free). */
    INSIGHTS_LONG_PERIODS(Capability.LOCAL_PRO),
    /** Insights: what the net worth is made of. */
    NET_WORTH_COMPOSITION(Capability.LOCAL_PRO),
    /** Ask Lucento's statements saved or shared as PDF (on screen they are free). */
    PDF_STATEMENTS(Capability.LOCAL_PRO),
    /** Grove, Mono and Aurora (Sunrise and Midnight are free). */
    EXTRA_THEMES(Capability.LOCAL_PRO),
    CLOUD_SYNC(Capability.CLOUD),
    CLOUD_RESTORE(Capability.CLOUD),
    /** Questions to the AI adviser (a count per month on Free, per day with the capability; the server decides). */
    AI_ADVISER(Capability.AI_PRO),
    SMART_READING(Capability.AI_PRO),
    /** Partners the person may have in their Shared Space (the server decides). */
    SHARED_SPACE(Capability.SHARED),
}

/** How much of a [ProFeature] a plan allows. The number is always "what the person may have", never "what they lose". */
sealed interface Limit {
    data object Unlimited : Limit
    /** Not available on this plan. */
    data object None : Limit
    /** At most [max] active items. */
    data class Count(val max: Int) : Limit
    /** At most [max] uses per calendar month (server-counted). */
    data class PerMonth(val max: Int) : Limit
    /** At most [max] uses per day (server-counted). */
    data class PerDay(val max: Int) : Limit

    /** Whether one more is allowed when [used] are already in use (active items, or uses in the period). */
    fun allows(used: Int): Boolean = when (this) {
        Unlimited -> true
        None -> false
        is Count -> used < max
        is PerMonth -> used < max
        is PerDay -> used < max
    }

    /** The bare number, for copy ("the most on the free plan is 3"); null for [Unlimited] and [None]. */
    val maxOrNull: Int?
        get() = when (this) {
            Unlimited, None -> null
            is Count -> max
            is PerMonth -> max
            is PerDay -> max
        }
}
