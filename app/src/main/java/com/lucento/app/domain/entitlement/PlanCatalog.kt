package com.lucento.app.domain.entitlement

/**
 * The whole Free / Pro / Lifetime matrix as data (docs/MONETIZATION.md §3; approved limits §7). The one place a limit
 * is written in the app: gates, badges and the in-app comparison table all read it, so they can never disagree.
 * Prices are not here — they only ever come from Google Play. The AI numbers are what the server enforces
 * (`lucento_private.plan_limits`); the app shows them and the server's remaining count, it never decides.
 */
object PlanCatalog {
    const val FREE_MONEY_WALLETS = 3
    const val FREE_DEBT_ACCOUNTS = 2
    const val FREE_ACTIVE_GOALS = 2
    const val PRO_ACTIVE_GOALS = 20
    const val FREE_AI_QUESTIONS_PER_MONTH = 10
    const val PRO_AI_QUESTIONS_PER_DAY = 50
    /** A soft monthly ceiling the server applies to the Pro allowance (abuse guard, shown only when hit). */
    const val PRO_AI_MONTHLY_CEILING = 400
    const val PRO_PARTNERS = 1

    /** What [capabilities] allow for [feature]. */
    fun limit(feature: ProFeature, capabilities: Set<Capability>): Limit {
        val unlocked = feature.requires in capabilities
        return when (feature) {
            ProFeature.MONEY_WALLETS -> if (unlocked) Limit.Unlimited else Limit.Count(FREE_MONEY_WALLETS)
            ProFeature.DEBT_ACCOUNTS -> if (unlocked) Limit.Unlimited else Limit.Count(FREE_DEBT_ACCOUNTS)
            ProFeature.SAVINGS_GOALS -> Limit.Count(if (unlocked) PRO_ACTIVE_GOALS else FREE_ACTIVE_GOALS)
            ProFeature.AI_ADVISER -> if (unlocked) Limit.PerDay(PRO_AI_QUESTIONS_PER_DAY) else Limit.PerMonth(FREE_AI_QUESTIONS_PER_MONTH)
            ProFeature.SHARED_SPACE -> if (unlocked) Limit.Count(PRO_PARTNERS) else Limit.None
            ProFeature.RECEIVABLES, ProFeature.VALUED_ASSETS, ProFeature.FOREIGN_CURRENCY_WALLETS,
            ProFeature.INSIGHTS_LONG_PERIODS, ProFeature.NET_WORTH_COMPOSITION, ProFeature.PDF_STATEMENTS,
            ProFeature.EXTRA_THEMES, ProFeature.CLOUD_SYNC, ProFeature.CLOUD_RESTORE, ProFeature.SMART_READING,
            -> if (unlocked) Limit.Unlimited else Limit.None
        }
    }

    fun limit(feature: ProFeature, state: EntitlementState, now: Long): Limit = limit(feature, state.capabilities(now))

    /** The capability sets each column of the comparison table stands for. */
    val FREE_CAPABILITIES: Set<Capability> = emptySet()
    val PRO_CAPABILITIES: Set<Capability> = Capability.entries.toSet()
    val LIFETIME_CAPABILITIES: Set<Capability> = setOf(Capability.LOCAL_PRO)
    val LIFETIME_WITH_SERVICES_CAPABILITIES: Set<Capability> = Capability.entries.toSet()

    /** One row per feature, for the in-app Free / Pro / Lifetime table — generated, so it cannot drift from the gates. */
    fun comparisonRows(): List<ComparisonRow> = ProFeature.entries.map { feature ->
        ComparisonRow(
            feature = feature,
            free = limit(feature, FREE_CAPABILITIES),
            pro = limit(feature, PRO_CAPABILITIES),
            lifetime = limit(feature, LIFETIME_CAPABILITIES),
            lifetimeWithServices = limit(feature, LIFETIME_WITH_SERVICES_CAPABILITIES),
        )
    }
}

data class ComparisonRow(
    val feature: ProFeature,
    val free: Limit,
    val pro: Limit,
    /** Lifetime after its included year of services: what the person owns outright. */
    val lifetime: Limit,
    /** Lifetime during the included year, or with the Lucento Cloud add-on. */
    val lifetimeWithServices: Limit,
)
