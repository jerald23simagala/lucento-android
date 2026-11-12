package com.lucento.app.domain.entitlement

/**
 * Lucento Pro's welcome (owner, 2026-11-12): right after Google Play confirms a plan, a deck of cards, one per thing
 * Pro opened, turned over one at a time; the collection of them all; then the Lucento Pro medal, which flies to the
 * profile picture. (It replaced a tour of the real screens, whose buttons failed on real phones: KNOWN_ISSUES PW-1.)
 * These are the rules behind it: pure, so every decision is a JVM test.
 *
 * - It plays once per new plan: a renewal, a trial turning paid, or a re-read keep the same purchase and stay quiet.
 * - It never plays for a pending payment (a pending purchase grants nothing).
 * - The medal is presented once per person and kept for good. The Pro ring on the profile picture shows only while
 *   Pro is active.
 */
object ProWelcome {

    /**
     * The plans that make this person Pro right now, one key per purchase (or grant). A renewal keeps the purchase token,
     * so it keeps the key; a new purchase (Lifetime after a subscription, a return after a lapse) brings a new one. Only
     * rows granting [Capability.LOCAL_PRO] count: the Lucento Cloud add-on alone isn't a welcome to Pro.
     */
    fun planKeys(state: EntitlementState, now: Long): Set<String> =
        state.entitlements
            .filter { it.covers(Capability.LOCAL_PRO, now) }
            .mapTo(sortedSetOf()) { e -> "${e.source.name}:${e.purchaseTokenHash ?: "${e.productId.orEmpty()}@${e.startsAtMillis}"}" }

    /**
     * What to play now, if anything. [welcomed]: plan keys already welcomed on this phone. [memberSince]: when the medal
     * was presented, null before. [presentedThisProcess]: the medal was already presented since the app started (its
     * stored date may not have landed yet), so it is never presented twice in a row.
     */
    fun toStart(
        state: EntitlementState,
        now: Long,
        welcomed: Set<String>,
        memberSince: Long?,
        presentedThisProcess: Boolean = false,
    ): ProWelcomeRun? {
        val keys = planKeys(state, now)
        if (keys.isEmpty()) return null
        val firstMedal = memberSince == null && !presentedThisProcess
        val fresh = keys - welcomed
        // When Pro really began (the earliest plan that grants it): the award's date, and whether this is a plan the
        // person has had for a while (a restore on a new phone, a reinstall), which is a welcome back, not news.
        val since = state.entitlements.filter { it.covers(Capability.LOCAL_PRO, now) }.minOfOrNull { it.startsAtMillis }
        val restored = since != null && now - since > RESTORED_AFTER_MILLIS
        return when {
            fresh.isNotEmpty() && firstMedal -> ProWelcomeRun(ProWelcomeMode.FIRST, fresh, since, restored)
            fresh.isNotEmpty() -> ProWelcomeRun(ProWelcomeMode.RETURNING, fresh, since, restored)
            // Welcomed, but the sequence stopped before the medal (the app was closed mid-tour): the medal alone.
            firstMedal -> ProWelcomeRun(ProWelcomeMode.MEDAL_ONLY, emptySet(), since, restored)
            else -> null
        }
    }

    /**
     * The cards for this plan and these modules, in order. A card for something this plan doesn't hold is left out, and so
     * is one this phone can't act on (a Partner's phone doesn't add wallets).
     */
    fun stops(context: ProTourContext): List<ProTourStop> {
        val local = Capability.LOCAL_PRO in context.capabilities
        val wallets = local && context.walletsEnabled && context.managesStructure
        return buildList {
            if (wallets) {
                add(ProTourStop.WALLETS)
                add(ProTourStop.NET_WORTH)
            }
            // Gemini reads what is typed or said in the composer: it needs a Lucento account (a build with sync).
            if (Capability.AI_PRO in context.capabilities && context.syncAvailable) add(ProTourStop.SMART_READING)
            if (local && context.goalsEnabled) add(ProTourStop.GOALS)
            if (local && context.insightsEnabled) {
                add(ProTourStop.INSIGHTS_PERIODS)
                add(ProTourStop.STATEMENTS)
            }
            if (Capability.AI_PRO in context.capabilities && context.insightsEnabled && context.syncAvailable) add(ProTourStop.AI_ANSWERS)
            if (Capability.CLOUD in context.capabilities && context.syncAvailable) add(ProTourStop.SYNC)
            // Inviting a Partner is the Owner's (a Partner's phone is already in someone's Shared Space).
            if (Capability.SHARED in context.capabilities && context.syncAvailable && context.managesStructure) add(ProTourStop.SHARED)
            if (local) add(ProTourStop.THEMES)
        }
    }

    /**
     * Whether the online services are confirmed by Lucento's server for this plan: they are server-enforced (the AI
     * allowance, sync, Lucento Shared), so the summary never claims them before the server knows.
     */
    fun onlineStatus(state: EntitlementState, now: Long, signedIn: Boolean, syncAvailable: Boolean = true): OnlineStatus {
        val online = state.entitlements.filter { e -> OnlineCapabilities.any { e.covers(it, now) } }
        return when {
            online.isEmpty() -> OnlineStatus.NOT_IN_PLAN
            !syncAvailable -> OnlineStatus.NOT_IN_THIS_BUILD
            online.any { it.verifiedBy == Verifier.SERVER } -> OnlineStatus.CONFIRMED
            !signedIn -> OnlineStatus.SIGN_IN_NEEDED
            else -> OnlineStatus.CONFIRMING
        }
    }

    private val OnlineCapabilities = setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED)

    /** A plan older than this when first seen on this phone was restored, not just bought. */
    const val RESTORED_AFTER_MILLIS: Long = 24L * 60 * 60 * 1000
}

/**
 * What the welcome plays. [planKeys]: the plans it welcomes, stored as welcomed when it starts. [since]: when Pro began
 * (the award's date). [restored]: a plan the person has had for a while (a new phone, a reinstall): a welcome back.
 */
data class ProWelcomeRun(
    val mode: ProWelcomeMode,
    val planKeys: Set<String>,
    val since: Long? = null,
    val restored: Boolean = false,
)

enum class ProWelcomeMode(
    /** The deck of cards and the collection. */
    val reveal: Boolean,
    /** The Lucento Pro medal's ceremony. */
    val medal: Boolean,
    /** The badge flies to the profile picture, where the Pro ring appears. */
    val flight: Boolean,
) {
    /** A first plan: cards, collection, medal, flight. */
    FIRST(reveal = true, medal = true, flight = true),

    /** A new plan for someone who already holds the medal (back after a lapse, or Lifetime after a subscription). */
    RETURNING(reveal = true, medal = false, flight = true),

    /** The sequence stopped before the medal: the medal and its flight, nothing else. */
    MEDAL_ONLY(reveal = false, medal = true, flight = true),

    /** "See your Pro cards" on the Lucento Pro page: the cards and the collection again, without the medal (owner). */
    REPLAY(reveal = true, medal = false, flight = false),
}

/** The cards' four chapters, in order. */
enum class ProTourChapter(val title: String) {
    YOUR_MONEY("Your money"),
    YOUR_GOALS("Your goals"),
    YOUR_INSIGHTS("Your insights"),
    MAKE_IT_YOURS("Make it yours"),
}

/** One card of the welcome: one thing Pro opened, and where it lives in the app. */
enum class ProTourStop(val chapter: ProTourChapter) {
    /** Wallets › Add: as many wallets, cards and loans as you use. */
    WALLETS(ProTourChapter.YOUR_MONEY),

    /** Wallets › net worth: money owed to you, things you own, other currencies. */
    NET_WORTH(ProTourChapter.YOUR_MONEY),

    /** The composer (the + button): Smarter reading, Gemini reading what is typed or said (still the person's choice). */
    SMART_READING(ProTourChapter.YOUR_MONEY),

    /** Goals › Add: up to 20 active goals. */
    GOALS(ProTourChapter.YOUR_GOALS),

    /** Insights › the period: 6 and 12 months. */
    INSIGHTS_PERIODS(ProTourChapter.YOUR_INSIGHTS),

    /** Insights › Reports: statements as PDF. */
    STATEMENTS(ProTourChapter.YOUR_INSIGHTS),

    /** Ask Lucento › where answers come from: the AI allowance (still the person's choice to turn on). */
    AI_ANSWERS(ProTourChapter.YOUR_INSIGHTS),

    /** Settings › Account & sync: sync between phones and restore from your account. */
    SYNC(ProTourChapter.MAKE_IT_YOURS),

    /** Settings › Lucento Shared: inviting one Partner. */
    SHARED(ProTourChapter.MAKE_IT_YOURS),

    /** The profile picture: Grove, Mono and Aurora, chosen from the profile. */
    THEMES(ProTourChapter.MAKE_IT_YOURS),
}

/** What decides the cards: the plan, the modules switched on, and whether this phone may change the wallets. */
data class ProTourContext(
    val capabilities: Set<Capability>,
    /** Several wallets ([com.lucento.app.domain.Feature.MULTIPLE_WALLETS]): adding wallets is possible at all. */
    val walletsEnabled: Boolean,
    val goalsEnabled: Boolean,
    val insightsEnabled: Boolean,
    /** False on a Partner's phone in Lucento Shared: the Owner manages the wallets. */
    val managesStructure: Boolean,
    /** False in a build without cloud sync (no Lucento account possible): the online services' cards are left out. */
    val syncAvailable: Boolean = true,
    /** Whether any wallet exists: an empty Wallets page has no Add pill, so its card says where Add wallet is instead. */
    val hasWallets: Boolean = true,
    /** Whether any goal exists: an empty Goals page has no Add pill, only "Create a goal". */
    val hasGoals: Boolean = true,
)

/** Where the online services stand with Lucento's server (they are enforced there). */
enum class OnlineStatus {
    /** The server has this plan: the AI allowance, sync and Lucento Shared are on. */
    CONFIRMED,

    /** Signed in, and the server hasn't confirmed the plan yet; the phone keeps asking. */
    CONFIRMING,

    /** Online services need a Lucento account on this phone. */
    SIGN_IN_NEEDED,

    /** This plan has no online services (Pro Lifetime after its included year). */
    NOT_IN_PLAN,

    /** A build without cloud sync (test builds): no Lucento account, so no online services here. */
    NOT_IN_THIS_BUILD,
}
