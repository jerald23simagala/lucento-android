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
     * One card. [chapter]: the group it belongs to ("Your money"). [where]: where the feature lives, in words (the card
     * can't point at it). [changes]: what Pro changed, free plan first. [term]: how long an online service is included,
     * on the online services' cards. [themes]: show the three Pro themes. [short] and [gain]: the card as the collection
     * lists it ("Every account", "No limit").
     */
    data class StopCopy(
        val title: String,
        val body: String,
        val where: String,
        val changes: List<Change> = emptyList(),
        val themes: Boolean = false,
        val chapter: String = "",
        val short: String = "",
        val gain: String? = null,
        val term: String? = null,
    )

    /** "Money wallets: 3 → No limit". */
    data class Change(val label: String, val before: String, val after: String)

    // ------------------------------------------------------------------------------------------------ the deck

    fun revealTitle(mode: ProWelcomeMode, restored: Boolean = false): String = when {
        mode == ProWelcomeMode.REPLAY -> "Your Pro cards"
        mode == ProWelcomeMode.RETURNING || restored -> "Welcome back to Lucento Pro"
        else -> "You’re on Lucento Pro"
    }

    fun revealBody(count: Int): String = "Your Pro deck holds ${perks(count)}."

    /** The gold band around the deck: "Lucento Pro · 10 perks", breaking (at large text) only after the dot. */
    fun band(count: Int): String = "Lucento\u00A0Pro\u00A0· ${perks(count)}"

    /** "3 of 10". */
    fun progress(index: Int, total: Int): String = "${index + 1} of $total"

    const val OPEN_DECK = "Open your Pro deck"

    /** The deck without its band (a welcome back, a replay): the first card waits face down. */
    const val REVEAL_FIRST = "Reveal your first perk"
    const val NEXT = "Next"

    /** The card before; not "Back", which the system key uses to leave the deck. */
    const val PREVIOUS = "Previous"
    const val TO_COLLECTION = "See your collection"
    const val SKIP = "Skip"
    const val SKIP_SPOKEN = "Skip to your collection"

    /** What TalkBack says for a row of what changed: the free plan's allowance, then this plan's. */
    fun spokenChange(change: Change): String = "${change.label}: ${change.before} on the free plan, ${change.after} now"

    /** "6 perks", the number bound to its noun so a line never ends on it (critic round 3: "holds 6 / perks."). */
    private fun perks(count: Int): String = if (count == 1) "1\u00A0perk" else "$count\u00A0perks"

    // ------------------------------------------------------------------------------------------------ the cards

    fun stop(
        stop: ProTourStop,
        capabilities: Set<Capability>,
        signedIn: Boolean,
        aiAnswersOn: Boolean,
        online: OnlineStatus = OnlineStatus.CONFIRMED,
        hasWallets: Boolean = true,
        hasGoals: Boolean = true,
        smartReadingOn: Boolean = false,
        /** On what terms the online services are included ([ProCopy.serviceTerm]); said on their cards only. */
        term: ProCopy.ServiceTerm? = null,
    ): StopCopy = when (stop) {
        ProTourStop.WALLETS -> StopCopy(
            title = "Add every account you use",
            body = "Cash, banks, e-wallets, savings, cards and loans: as many as you have.",
            where = if (hasWallets) "Tap Add at the top right of Wallets." else "Tap Add wallet in the middle of Wallets.",
            changes = listOf(
                Change(ProCopy.featureName(ProFeature.MONEY_WALLETS), "${PlanCatalog.FREE_MONEY_WALLETS}", NO_LIMIT),
                Change(ProCopy.featureName(ProFeature.DEBT_ACCOUNTS), "${PlanCatalog.FREE_DEBT_ACCOUNTS}", NO_LIMIT),
            ),
            short = "Every account",
            gain = NO_LIMIT,
        )
        ProTourStop.NET_WORTH -> StopCopy(
            title = "Count everything you have",
            body = "Money owed to you, things you own at a value you set, and wallets in other currencies all count in " +
                "your net worth.",
            where = if (hasWallets) "In Wallets, under Add at the top right." else "In Wallets, under Add wallet in the middle.",
            short = "Everything you own",
        )
        ProTourStop.SMART_READING -> StopCopy(
            title = "Smarter reading, with Gemini",
            body = when {
                online == OnlineStatus.SIGN_IN_NEEDED ->
                    "Gemini fills in the details from what you type or say in the composer. It uses your Lucento account: " +
                        "sign in from Settings › Account & sync, then turn it on in Settings › Privacy & security."
                online == OnlineStatus.CONFIRMING ->
                    "Gemini fills in the details from what you type or say in the composer. It starts as soon as your " +
                        "Lucento account confirms Pro."
                smartReadingOn -> "Smarter reading is on: Gemini fills in the details from what you type or say in the composer."
                else -> "Gemini fills in the details from what you type or say in the composer. Turn it on in Settings › " +
                    "Privacy & security; nothing is sent until you do."
            },
            where = "The composer opens from + in the middle of the bar.",
            short = "Smarter reading",
            term = term?.let(::serviceTermText),
        )
        ProTourStop.GOALS -> StopCopy(
            title = "Save for more at once",
            body = "Keep several savings goals going side by side.",
            where = if (hasGoals) "Tap Add at the top right of Goals." else "Tap Create a goal in the middle of Goals.",
            changes = listOf(Change(ProCopy.featureName(ProFeature.SAVINGS_GOALS), "${PlanCatalog.FREE_ACTIVE_GOALS}", "${PlanCatalog.PRO_ACTIVE_GOALS}")),
            short = "More savings goals",
            gain = "${PlanCatalog.PRO_ACTIVE_GOALS} goals",
        )
        ProTourStop.INSIGHTS_PERIODS -> StopCopy(
            title = "Look further back",
            body = "See 6 and 12 months of income against expenses, where your money went and how your net worth moved.",
            where = "Choose 6 or 12 months above the charts in Insights.",
            changes = listOf(Change("Trends", "${ProGates.FREE_INSIGHTS_MONTHS} months", "12 months")),
            short = "Longer trends",
            gain = "12 months",
        )
        ProTourStop.STATEMENTS -> StopCopy(
            title = "Keep your statements",
            body = "Save or share any report as a PDF.",
            where = "In Insights, under Reports.",
            short = "PDF statements",
        )
        ProTourStop.AI_ANSWERS -> {
            // The allowance is the server's to give: said only once the server has confirmed the plan.
            val confirmed = online == OnlineStatus.CONFIRMED
            val allowance = limitText(ProFeature.AI_ADVISER, PlanCatalog.limit(ProFeature.AI_ADVISER, capabilities))
            StopCopy(
                title = "AI answers, with Gemini",
                body = when {
                    online == OnlineStatus.SIGN_IN_NEEDED ->
                        "Gemini answers your questions about your money. It uses your Lucento account: sign in from " +
                            "Settings › Account & sync, then turn AI answers on in Ask Lucento."
                    online == OnlineStatus.CONFIRMING ->
                        "Gemini answers your questions about your money. Your daily allowance starts as soon as your " +
                            "Lucento account confirms Pro."
                    aiAnswersOn -> "AI answers from Gemini are on. Smarter reading shares the same daily allowance."
                    else -> "Gemini answers your questions about your money. Turn it on in Ask Lucento; nothing is sent until you do."
                },
                where = "Ask Lucento is at the top of Insights.",
                changes = if (confirmed) {
                    listOf(Change(ProCopy.featureName(ProFeature.AI_ADVISER), limitText(ProFeature.AI_ADVISER, free(ProFeature.AI_ADVISER)), allowance))
                } else {
                    emptyList()
                },
                short = "AI answers",
                gain = if (confirmed) allowance else null,
                term = term?.let(::aiTermText),
            )
        }
        ProTourStop.SYNC -> StopCopy(
            title = "Your money on every phone",
            body = if (signedIn) {
                "Sync between phones and restore from your account."
            } else {
                "Sign in from Settings › Account & sync to sync between phones and restore from your account."
            },
            where = "In Settings › Account & sync.",
            short = "Sync and restore",
            term = term?.let(::serviceTermText),
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
            short = "Share with a Partner",
            term = term?.let(::serviceTermText),
        )
        ProTourStop.THEMES -> StopCopy(
            title = "Three new themes",
            body = "Each one recolours the whole app, in light and dark.",
            where = "Themes are in your profile, behind your picture at the top right.",
            themes = true,
            short = "Three new themes",
        )
    }.copy(chapter = stop.chapter.title)

    /** The online services' term on their cards: "Included until Nov 12, 2027", "While you subscribe". */
    fun serviceTermText(term: ProCopy.ServiceTerm): String = when (term) {
        ProCopy.ServiceTerm.Subscription -> "While you subscribe"
        ProCopy.ServiceTerm.Cloud -> "With Lucento Cloud"
        is ProCopy.ServiceTerm.Until -> "Included until ${term.date}"
        ProCopy.ServiceTerm.Included -> "Included"
    }

    /**
     * The AI adviser's term: after a Lifetime's included year the allowance falls back to the free plan's, it doesn't
     * stop ("50 a day until Nov 12, 2027, then 10 a month", as the Lucento Pro page's comparison says it).
     */
    fun aiTermText(term: ProCopy.ServiceTerm): String = when (term) {
        is ProCopy.ServiceTerm.Until ->
            "${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY} a day until ${term.date}, then ${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} a month"
        else -> serviceTermText(term)
    }

    /**
     * What the collection shows beside a card's name: its gain ("No limit", "50 a day"), or for an online service with an
     * end date, that date ("Until Nov 12, 2027"). The AI adviser shows its allowance, never a date (it carries on after).
     */
    fun collectionTag(stop: ProTourStop, copy: StopCopy, term: ProCopy.ServiceTerm?): String? = when {
        copy.gain != null -> copy.gain
        stop == ProTourStop.AI_ANSWERS -> null
        stop in ServiceStops && term is ProCopy.ServiceTerm.Until -> "Until ${term.date}"
        else -> null
    }

    private val ServiceStops = setOf(ProTourStop.SMART_READING, ProTourStop.AI_ANSWERS, ProTourStop.SYNC, ProTourStop.SHARED)

    // ------------------------------------------------------------------------------------------------ the collection

    /** [compact]: at large text, a title short enough that no word has to break across lines. */
    fun collectionTitle(mode: ProWelcomeMode, restored: Boolean = false, compact: Boolean = false): String = when {
        mode == ProWelcomeMode.REPLAY -> "What Pro gives you"
        mode == ProWelcomeMode.RETURNING || restored -> "Welcome back to Pro"
        compact -> "You’re Pro"
        else -> "Congratulations, you’re Pro"
    }

    /** "Your Pro collection · 10 perks". */
    fun collectionEyebrow(count: Int): String = "Your Pro collection\u00A0· ${perks(count)}"

    fun collectionButton(mode: ProWelcomeMode): String = if (mode.medal) "Collect your award" else "Done"

    /** The Lucento Pro page's word for a limit that is gone (its comparison table says the same). */
    const val NO_LIMIT = "No limit"

    /** A limit in words: "3", "No limit", "10 a month", "50 a day", "1 Partner", "Included". */
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

    /** Where the online services stand, in words; null when this plan has none or the server has confirmed them. */
    fun onlineStatus(status: OnlineStatus): String? = when (status) {
        OnlineStatus.CONFIRMED -> null
        OnlineStatus.CONFIRMING -> "Confirming with your Lucento account. Online services switch on as soon as it’s done."
        OnlineStatus.SIGN_IN_NEEDED -> "Online services need your Lucento account. Sign in from Settings › Account & sync."
        OnlineStatus.NOT_IN_PLAN -> null
        OnlineStatus.NOT_IN_THIS_BUILD -> "This version of Lucento can’t connect to a Lucento account yet, so online services aren’t available here."
    }

    private fun free(feature: ProFeature): Limit = PlanCatalog.limit(feature, PlanCatalog.FREE_CAPABILITIES)

    /** Features measured as a count of things (wallets, goals), where "no limit" is the word; the rest are included or not. */
    private val ProFeature.counted: Boolean
        get() = this == ProFeature.MONEY_WALLETS || this == ProFeature.DEBT_ACCOUNTS || this == ProFeature.SAVINGS_GOALS
}
