package com.lucento.app.feature.pro.welcome

import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.Limit
import com.lucento.app.domain.entitlement.OnlineStatus
import com.lucento.app.domain.entitlement.PlanCatalog
import com.lucento.app.domain.entitlement.ProFeature
import com.lucento.app.domain.entitlement.ProGates
import com.lucento.app.domain.entitlement.ProTourStop
import com.lucento.app.domain.entitlement.ProWelcomeMode
import com.lucento.app.feature.pro.ProCopy

/**
 * Every sentence of Lucento Pro's welcome, worked out without Compose so it is unit-tested. Numbers come from
 * [PlanCatalog], names from [ProCopy]; nothing here claims an online service the server hasn't confirmed.
 */
object ProWelcomeCopy {

    /**
     * One stop's caption. [where]: where the feature lives, in words, for everyone (and for TalkBack, which can't see the
     * lit control). [changes]: what Pro changed, free plan first. [themes]: show the three Pro themes.
     */
    data class StopCopy(
        val title: String,
        val body: String,
        val where: String,
        val changes: List<Change> = emptyList(),
        val themes: Boolean = false,
    )

    /** "Money wallets: 3 → Unlimited". */
    data class Change(val label: String, val before: String, val after: String)

    /** The curtain before the tour. */
    fun curtainTitle(mode: ProWelcomeMode, restored: Boolean = false): String = when {
        mode == ProWelcomeMode.REPLAY -> "Your Pro tour"
        mode == ProWelcomeMode.RETURNING || restored -> "Welcome back to Lucento Pro"
        else -> "You’re on Lucento Pro"
    }

    fun curtainBody(stops: Int, restored: Boolean = false): String =
        if (restored) "Here’s where everything Pro opens lives, in $stops quick stops." else "Here’s what just opened up, in $stops quick stops."

    fun replayBody(stops: Int): String = "Everything Lucento Pro opens, in $stops quick stops."

    /** "Your money · 1 of 8". */
    fun progress(stop: ProTourStop, index: Int, total: Int): String = "${stop.chapter.title} · ${index + 1} of $total"

    fun stop(
        stop: ProTourStop,
        capabilities: Set<Capability>,
        signedIn: Boolean,
        aiAnswersOn: Boolean,
        online: OnlineStatus = OnlineStatus.CONFIRMED,
        hasWallets: Boolean = true,
        hasGoals: Boolean = true,
        smartReadingOn: Boolean = false,
    ): StopCopy = when (stop) {
        ProTourStop.WALLETS -> StopCopy(
            title = "Add every account you use",
            body = "Cash, banks, e-wallets, savings, cards and loans: as many as you have.",
            where = if (hasWallets) "Add is at the top right of Wallets." else "Add wallet is in the middle of Wallets.",
            changes = listOf(
                Change(ProCopy.featureName(ProFeature.MONEY_WALLETS), "${PlanCatalog.FREE_MONEY_WALLETS}", NO_LIMIT),
                Change(ProCopy.featureName(ProFeature.DEBT_ACCOUNTS), "${PlanCatalog.FREE_DEBT_ACCOUNTS}", NO_LIMIT),
            ),
        )
        ProTourStop.NET_WORTH -> StopCopy(
            title = "Count everything you have",
            body = "Add money owed to you, things you own at a value you set, and wallets in other currencies. " +
                "Your net worth counts them all.",
            where = "You add them with Add, at the top right of Wallets.",
        )
        ProTourStop.SMART_READING -> StopCopy(
            title = "Smarter reading, with Gemini",
            body = when {
                online == OnlineStatus.SIGN_IN_NEEDED ->
                    "Gemini reads what you type or say here and fills in the details. It uses your Lucento account: sign in " +
                        "from Settings › Account & sync, then turn it on in Settings › Privacy & security."
                online == OnlineStatus.CONFIRMING ->
                    "Gemini reads what you type or say here and fills in the details. It starts as soon as your Lucento " +
                        "account confirms Pro."
                smartReadingOn -> "Smarter reading is on: Gemini reads what you type or say here."
                else -> "Gemini reads what you type or say here and fills in the details. Turn it on in Settings › " +
                    "Privacy & security when you want it; nothing is sent until you do."
            },
            where = "The composer opens from + in the middle of the bar.",
        )
        ProTourStop.GOALS -> StopCopy(
            title = "Save for more at once",
            body = "Keep several savings goals going side by side.",
            where = if (hasGoals) "Add is at the top right of Goals." else "Create a goal is in the middle of Goals.",
            changes = listOf(Change(ProCopy.featureName(ProFeature.SAVINGS_GOALS), "${PlanCatalog.FREE_ACTIVE_GOALS}", "${PlanCatalog.PRO_ACTIVE_GOALS}")),
        )
        ProTourStop.INSIGHTS_PERIODS -> StopCopy(
            title = "Look further back",
            body = "See your trends over 6 and 12 months, as well as your last 3 months.",
            where = "6 and 12 months are above your charts in Insights.",
            changes = listOf(Change("Trends", "${ProGates.FREE_INSIGHTS_MONTHS} months", "12 months")),
        )
        ProTourStop.STATEMENTS -> StopCopy(
            title = "Keep your statements",
            body = "Save or share a report like this one as a PDF.",
            where = "Reports are in Insights; save or share one from inside it.",
        )
        ProTourStop.AI_ANSWERS -> StopCopy(
            title = "AI answers, with Gemini",
            body = when {
                online == OnlineStatus.SIGN_IN_NEEDED ->
                    "Gemini answers your questions about your money. It uses your Lucento account: sign in from Settings › " +
                        "Account & sync, then turn AI answers on here."
                online == OnlineStatus.CONFIRMING ->
                    "Gemini answers your questions about your money. Your daily allowance starts as soon as your Lucento " +
                        "account confirms Pro."
                aiAnswersOn -> "AI answers from Gemini are on. Smarter reading shares the same daily allowance."
                else -> "Gemini answers your questions about your money. Turn AI answers on here when you want them; " +
                    "nothing is sent until you do."
            },
            where = "Ask Lucento is at the top of Insights.",
            changes = if (online == OnlineStatus.CONFIRMED) {
                listOf(
                    Change(
                        ProCopy.featureName(ProFeature.AI_ADVISER),
                        limitText(ProFeature.AI_ADVISER, free(ProFeature.AI_ADVISER)),
                        limitText(ProFeature.AI_ADVISER, PlanCatalog.limit(ProFeature.AI_ADVISER, capabilities)),
                    ),
                )
            } else {
                emptyList()
            },
        )
        ProTourStop.SYNC -> StopCopy(
            title = "Your money on every phone",
            body = if (signedIn) "Sync between phones and restore from your account." else "Sign in here to sync between phones and restore from your account.",
            where = "In Settings › Account & sync.",
        )
        ProTourStop.SHARED -> StopCopy(
            title = "Share with a Partner",
            body = if (signedIn) {
                "Invite one Partner to see and add to your money with you, on their own phone."
            } else {
                "Invite one Partner to see and add to your money with you, on their own phone. It uses your Lucento " +
                    "account: sign in from Settings › Account & sync first."
            },
            where = "Lucento Shared is in your profile and in Settings.",
        )
        ProTourStop.THEMES -> StopCopy(
            title = "Three new themes",
            body = "Grove, Mono and Aurora are yours.",
            where = "Themes are in your profile, behind your picture at the top right.",
            themes = true,
        )
    }

    // ------------------------------------------------------------------------------------------------ the summary

    fun summaryTitle(mode: ProWelcomeMode, restored: Boolean = false): String = when {
        mode == ProWelcomeMode.REPLAY -> "What Pro gives you"
        mode == ProWelcomeMode.RETURNING || restored -> "Welcome back to Pro"
        else -> "Congratulations, you’re Pro"
    }

    /** The tour's way out, on every stop but the last (where the next step is the summary anyway). */
    const val END_TOUR = "End tour"

    /** The Lucento Pro page's word for a limit that is gone (its comparison table says the same). */
    const val NO_LIMIT = "No limit"

    /**
     * What the summary's sections don't list but the plan includes (for anyone who ended the tour early), and that the
     * tour can be watched again. Null for a plan without Pro's phone features.
     */
    fun alsoYours(capabilities: Set<Capability>, replay: Boolean): String? {
        if (Capability.LOCAL_PRO !in capabilities) return null
        val also = "Also yours: statements as PDF, three more themes, and money owed to you, things you own and wallets " +
            "in other currencies."
        return if (replay) also else "$also Watch this tour again from Lucento Pro in your profile."
    }

    fun summaryButton(mode: ProWelcomeMode): String = if (mode.medal) "Collect your award" else "Done"

    /** The last stop's button, on to the summary. */
    const val LAST_STOP_BUTTON = "See what changed"

    const val LIMITS_TITLE = "Higher limits"
    const val ONLINE_TITLE = "Online services"

    /** A limit in words for the summary: "3", "Unlimited", "10 a month", "50 a day", "1 Partner", "Included". */
    fun limitText(feature: ProFeature, limit: Limit): String = when (feature) {
        ProFeature.INSIGHTS_LONG_PERIODS -> if (limit == Limit.None) "${ProGates.FREE_INSIGHTS_MONTHS} months" else "12 months"
        ProFeature.SHARED_SPACE -> when (limit) {
            is Limit.Count -> if (limit.max == 1) "1 Partner" else "${limit.max} Partners"
            Limit.Unlimited -> "Included"
            else -> "Not included"
        }
        else -> when (limit) {
            Limit.Unlimited -> if (feature.counted) NO_LIMIT else "Included"
            Limit.None -> "Not included"
            is Limit.Count -> "${limit.max}"
            is Limit.PerMonth -> "${limit.max} a month"
            is Limit.PerDay -> "${limit.max} a day"
        }
    }

    /**
     * An online service's value in the summary: this plan's allowance only once the server has confirmed the plan;
     * otherwise what is still needed. [confirmed] tells the row how to show it (the plan's value is emphasised).
     */
    fun onlineValue(feature: ProFeature, limit: Limit, status: OnlineStatus): String = when (status) {
        OnlineStatus.CONFIRMED, OnlineStatus.NOT_IN_PLAN -> limitText(feature, limit)
        OnlineStatus.CONFIRMING -> "Starting soon"
        OnlineStatus.SIGN_IN_NEEDED -> "Needs sign-in"
        OnlineStatus.NOT_IN_THIS_BUILD -> "Not in this version"
    }

    /** What TalkBack says for a summary row: the free plan's allowance, then what this plan gives (or still needs). */
    fun spokenRow(name: String, before: String, after: String, confirmed: Boolean): String =
        if (confirmed) "$name: $before on the free plan, $after now" else "$name: $before on the free plan; $after"

    /** A row's name in the summary: the plan catalog's, except Trends, named as on its tour stop. */
    fun rowName(feature: ProFeature): String =
        if (feature == ProFeature.INSIGHTS_LONG_PERIODS) "Trends" else ProCopy.featureName(feature)

    /** The free plan's value as shown: "—" where the free plan has none of it (said "Not included" to TalkBack). */
    fun freeShown(feature: ProFeature, limit: Limit): String =
        if (limit == Limit.None && feature != ProFeature.INSIGHTS_LONG_PERIODS) "—" else limitText(feature, limit)

    /** A second line under a row whose name needs explaining. */
    fun rowDetail(feature: ProFeature): String? = when (feature) {
        ProFeature.SMART_READING -> "Gemini reads what you type or say"
        ProFeature.AI_ADVISER -> "Answers from Ask Lucento, by Gemini"
        else -> null
    }

    /** Where the online services stand, in words; null when this plan has none. */
    fun onlineStatus(status: OnlineStatus): String? = when (status) {
        OnlineStatus.CONFIRMED -> "Confirmed with your Lucento account."
        OnlineStatus.CONFIRMING -> "Confirming with your Lucento account. These switch on as soon as it’s done."
        OnlineStatus.SIGN_IN_NEEDED -> "These need your Lucento account. Sign in from Settings › Account & sync."
        OnlineStatus.NOT_IN_PLAN -> null
        OnlineStatus.NOT_IN_THIS_BUILD -> "This version of Lucento can’t connect to a Lucento account yet, so these aren’t available here."
    }

    private fun free(feature: ProFeature): Limit = PlanCatalog.limit(feature, PlanCatalog.FREE_CAPABILITIES)

    /** Features measured as a count of things (wallets, goals), where "unlimited" is the word; the rest are included or not. */
    private val ProFeature.counted: Boolean
        get() = this == ProFeature.MONEY_WALLETS || this == ProFeature.DEBT_ACCOUNTS || this == ProFeature.SAVINGS_GOALS
}
