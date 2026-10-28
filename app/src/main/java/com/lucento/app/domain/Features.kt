package com.lucento.app.domain

/**
 * Optional Lucento modules the user can switch on and off. A switch expresses preference only: turning a
 * module off hides it from navigation, shortcuts and dashboards but never deletes its data, and turning
 * it back on restores everything. Core infrastructure (transactions, categories, the default account) is
 * not optional because the financial model depends on it.
 *
 * [requires] lists modules that must also be on for this one to be usable; the UI explains this.
 */
enum class Feature(
    val key: String,
    val title: String,
    val description: String,
    val requires: Set<Feature> = emptySet(),
) {
    MULTIPLE_WALLETS(
        "multiple_wallets",
        "Multiple wallets",
        "Track several accounts and move money between them with transfers.",
    ),
    BUDGETS("budgets", "Budgets", "Monthly amounts per category; your total budget is their sum."),
    SAVINGS_GOALS("savings_goals", "Savings goals", "Targets, contributions and projected completion dates."),
    DEBTS_LOANS(
        "debts_loans",
        "Debts & loans",
        "Credit cards and loans as accounts, with payments recorded as transfers.",
        requires = setOf(MULTIPLE_WALLETS),
    ),
    RECURRING("recurring", "Recurring payments", "Bills, subscriptions and salary you confirm when they happen."),
    INSIGHTS("insights", "Insights & reports", "Spending breakdowns, monthly comparisons and net flow."),
    RECEIPT_SCANNING("receipt_scanning", "Receipt scanning", "Read totals from receipt photos on this device."),
    VOICE_ENTRY("voice_entry", "Voice entry", "Speak transactions; your phone’s speech service turns them into text for you to review.");

    companion object {
        fun fromKey(key: String): Feature? = entries.firstOrNull { it.key == key }
    }
}

/** The user's module choices with dependency resolution in one place. */
data class FeatureSet(val enabled: Set<Feature>) {

    /** Switched on by the user (regardless of dependencies). */
    fun isSwitchedOn(feature: Feature): Boolean = feature in enabled

    /** Usable: switched on and every required module is usable too. */
    fun isEnabled(feature: Feature): Boolean =
        feature in enabled && feature.requires.all { isEnabled(it) }

    /** Required modules that are currently off and block [feature]. */
    fun missingRequirements(feature: Feature): List<Feature> = feature.requires.filterNot { isEnabled(it) }

    fun with(feature: Feature, on: Boolean): FeatureSet = FeatureSet(if (on) enabled + feature else enabled - feature)

    companion object {
        val ALL = FeatureSet(Feature.entries.toSet())
    }
}

/** What the user told onboarding they care about. Each focus maps to modules switched on initially. */
enum class FinancialFocus(val title: String, val description: String, val features: Set<Feature>) {
    EVERYDAY_SPENDING("Track everyday spending", "Essential for everyone", emptySet()),
    BUDGETS("Manage budgets", "Plan and control spending", setOf(Feature.BUDGETS)),
    SAVINGS("Build savings goals", "Reach financial milestones", setOf(Feature.SAVINGS_GOALS)),
    DEBTS("Track debts and loans", "Manage what you owe", setOf(Feature.DEBTS_LOANS, Feature.MULTIPLE_WALLETS)),
    RECURRING("Manage recurring payments", "Never miss a bill", setOf(Feature.RECURRING)),
    TRENDS("Understand financial trends", "See where your money goes", setOf(Feature.INSIGHTS));

    companion object {
        /**
         * Modules a new install starts with. Goals, recurring payments and debts are on
         * ([FirstRunDefaults.DEBTS_ON_BY_DEFAULT] is true). A chosen focus can still add its modules (used when an
         * older in-progress setup is resumed). Everything remains switchable in Settings.
         */
        fun initialFeatures(focuses: Set<FinancialFocus>): Set<Feature> =
            FirstRunDefaults.features() + focuses.flatMap { it.features }
    }
}
