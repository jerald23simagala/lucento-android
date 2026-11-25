package com.lucento.app.domain

import java.util.concurrent.TimeUnit

/**
 * Anonymous usage counts. The twelve names are the whole allowlist, the same twelve as the server table's own check
 * (`usage_events_event_allowlist`). Props are only [PROP_KEYS]. Nothing here reads the server table back. Opting out
 * drops the queue and the install id.
 */
object UsageCounts {
    const val ONBOARDING_STARTED = "onboarding_started"
    const val ONBOARDING_STEP_VIEWED = "onboarding_step_viewed"
    const val FIRST_ENTRY_SAVED = "first_entry_saved"
    const val D1_RETURN = "d1_return"
    const val D7_RETURN = "d7_return"
    const val FIRST_WEEK_CARD_STEP_DONE = "first_week_card_step_done"
    const val BUDGET_SUGGESTION_SHOWN = "budget_suggestion_shown"
    const val BUDGET_SUGGESTION_ACCEPTED = "budget_suggestion_accepted"

    // The way to Lucento Pro (docs/growth/CRO_PLAN.md, CRO-2). Server side since 20261125090000.
    const val PRO_DIALOG_SHOWN = "pro_dialog_shown"
    const val PLAN_PAGE_VIEWED = "plan_page_viewed"
    const val PURCHASE_STARTED = "purchase_started"
    const val PURCHASE_COMPLETED = "purchase_completed"

    val EVENTS: List<String> = listOf(
        ONBOARDING_STARTED,
        ONBOARDING_STEP_VIEWED,
        FIRST_ENTRY_SAVED,
        D1_RETURN,
        D7_RETURN,
        FIRST_WEEK_CARD_STEP_DONE,
        BUDGET_SUGGESTION_SHOWN,
        BUDGET_SUGGESTION_ACCEPTED,
        PRO_DIALOG_SHOWN,
        PLAN_PAGE_VIEWED,
        PURCHASE_STARTED,
        PURCHASE_COMPLETED,
    )

    /** `feature`, `from` and `plan` carry only the fixed values [ConversionCounts] makes, never text the person typed. */
    val PROP_KEYS: Set<String> = setOf("step", "ms", "day_index", "count", "feature", "from", "plan")

    const val MAX_QUEUE = 40
    const val MAX_PROP_LENGTH = 32
    const val MAX_APP_VERSION = 32

    fun allows(name: String): Boolean = name in EVENTS

    /** Keeps only allowlisted keys and short values. Drops anything else, including free text. */
    fun props(raw: Map<String, String>): Map<String, String> =
        raw.filterKeys { it in PROP_KEYS }
            .mapValues { (_, value) -> value.trim().take(MAX_PROP_LENGTH) }
            .filterValues { it.isNotEmpty() }

    /**
     * Whether the server's answer [sqlState] means it will never take this count: a data exception (class 22, such as
     * the reserved install id) or a constraint it breaks (class 23, such as a name outside the allowlist). Anything
     * else (no network, a timeout, a server that's down) is worth trying again later.
     */
    fun isRefusal(sqlState: String?): Boolean =
        sqlState != null && sqlState.length == 5 && (sqlState.startsWith("22") || sqlState.startsWith("23"))
}

/**
 * The props of the conversion counts (docs/growth/CRO_PLAN.md, CRO-2). Every value is one of a fixed few, so a count
 * says which door was used and which plan was chosen, and nothing about the person's money.
 */
object ConversionCounts {
    /** Where the plan page was opened from, when it wasn't through a Pro wall. */
    const val FROM_SETTINGS = "settings"
    const val FROM_PROFILE = "profile"
    const val FROM_PLAN_ENDED = "plan_ended"
    const val FROM_ONBOARDING = "onboarding"
    /** Through a Pro wall: its dialog, the short message's Unlock, or a lock note naming a feature. */
    const val FROM_WALL = "wall"
    const val FROM_OTHER = "other"

    private val NAMED_DOORS = setOf(FROM_SETTINGS, FROM_PROFILE, FROM_PLAN_ENDED, FROM_ONBOARDING)

    /** [door] is what the route says opened the page; [featureKey] the feature that led there, if any. */
    fun from(door: String?, featureKey: String?): String = when {
        door in NAMED_DOORS -> door!!
        !featureKey.isNullOrBlank() -> FROM_WALL
        else -> FROM_OTHER
    }

    /** A feature as a count names it: the enum's name in lower case ("money_wallets"). */
    fun feature(enumName: String): String = enumName.lowercase()

    /** The props of [UsageCounts.PLAN_PAGE_VIEWED]. */
    fun planPageProps(door: String?, featureEnumName: String?): Map<String, String> = buildMap {
        put("from", from(door, featureEnumName))
        if (!featureEnumName.isNullOrBlank()) put("feature", feature(featureEnumName))
    }

    const val PLAN_MONTHLY = "monthly"
    const val PLAN_ANNUAL = "annual"
    const val PLAN_ANNUAL_TRIAL = "annual_trial"
    const val PLAN_LIFETIME = "lifetime"
    const val PLAN_CLOUD_MONTHLY = "cloud_monthly"
    const val PLAN_CLOUD_ANNUAL = "cloud_annual"
    const val PLAN_OTHER = "other"

    /**
     * The plan a purchase is for, from Google Play's product and base plan: [billingPeriodIso] P1M or P1Y for a
     * subscription, null for Lifetime; [withTrial] when the offer starts with a free trial.
     */
    fun plan(productId: String, billingPeriodIso: String?, withTrial: Boolean): String = when (productId) {
        com.lucento.app.domain.entitlement.PlayProducts.PRO_LIFETIME -> PLAN_LIFETIME
        com.lucento.app.domain.entitlement.PlayProducts.PRO_SUBSCRIPTION -> when (billingPeriodIso) {
            "P1M" -> PLAN_MONTHLY
            "P1Y" -> if (withTrial) PLAN_ANNUAL_TRIAL else PLAN_ANNUAL
            else -> PLAN_OTHER
        }
        com.lucento.app.domain.entitlement.PlayProducts.CLOUD_ADDON -> when (billingPeriodIso) {
            "P1M" -> PLAN_CLOUD_MONTHLY
            "P1Y" -> PLAN_CLOUD_ANNUAL
            else -> PLAN_OTHER
        }
        else -> PLAN_OTHER
    }
}

data class UsageEvent(val name: String, val props: Map<String, String> = emptyMap())

/**
 * Local opt-in state. [queue] is waiting to send. [sent] remembers one-shot events.
 * [onboardingSeen] is set when welcome is shown, so opting in on that screen can send [UsageCounts.ONBOARDING_STARTED].
 */
data class UsageLedger(
    val optedIn: Boolean = false,
    val installId: String? = null,
    val installedAtMillis: Long? = null,
    val onboardingStartedAtMillis: Long? = null,
    val onboardingSeen: Boolean = false,
    val queue: List<UsageEvent> = emptyList(),
    val sent: Set<String> = emptySet(),
    val adjustPending: Boolean = false,
    /**
     * Install id whose server rows still need deleting. Set when opt-out's delete call fails.
     * The next launch retries with this id. Cleared only after the delete succeeds.
     */
    val pendingDeletionInstallId: String? = null,
)

object UsageLedgerCodec {
    fun encode(queue: List<UsageEvent>): String =
        queue.joinToString("\n") { event ->
            val body = event.props.entries.joinToString(",") { (key, value) -> "$key=$value" }
            "${event.name}|$body"
        }

    fun decode(raw: String): List<UsageEvent> =
        raw.lineSequence().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val name = line.substringBefore('|')
            if (!UsageCounts.allows(name)) return@mapNotNull null
            val props = line.substringAfter('|', "")
                .split(',')
                .mapNotNull { pair ->
                    val key = pair.substringBefore('=', "")
                    val value = pair.substringAfter('=', "")
                    if (key.isEmpty()) null else key to value
                }
                .toMap()
            UsageEvent(name, UsageCounts.props(props))
        }.toList()
}

/**
 * Pure transitions. Sending is a separate step: [drain] is empty unless the person has opted in.
 */
object UsageLedgerLogic {
    fun noteWelcome(ledger: UsageLedger, now: Long): UsageLedger =
        ledger.copy(
            onboardingSeen = true,
            installedAtMillis = ledger.installedAtMillis ?: now,
        )

    fun optIn(ledger: UsageLedger, now: Long, installId: String): UsageLedger {
        val withId = ledger.copy(
            optedIn = true,
            installId = ledger.installId ?: installId,
            installedAtMillis = ledger.installedAtMillis ?: now,
        )
        if (!withId.onboardingSeen || UsageCounts.ONBOARDING_STARTED in withId.sent) return withId
        return enqueue(
            withId.copy(onboardingStartedAtMillis = withId.onboardingStartedAtMillis ?: now),
            UsageCounts.ONBOARDING_STARTED,
            emptyMap(),
        ).copy(sent = withId.sent + UsageCounts.ONBOARDING_STARTED)
    }

    /**
     * Local half of opt-out, applied only after [idToDelete] has been sent to delete_usage_events.
     * [deleted] true forgets the id. False keeps it in [UsageLedger.pendingDeletionInstallId] for the next launch.
     * The active install id is cleared either way so a later opt-in mints a new one.
     */
    fun idToDelete(ledger: UsageLedger): String? = ledger.installId ?: ledger.pendingDeletionInstallId

    fun afterOptOut(ledger: UsageLedger, deleted: Boolean): UsageLedger {
        val id = idToDelete(ledger)
        return ledger.copy(
            optedIn = false,
            queue = emptyList(),
            adjustPending = false,
            installId = null,
            pendingDeletionInstallId = if (id != null && !deleted) id else null,
        )
    }

    fun afterDeletionRetry(ledger: UsageLedger, deleted: Boolean): UsageLedger =
        if (deleted) ledger.copy(pendingDeletionInstallId = null) else ledger

    fun enqueue(ledger: UsageLedger, name: String, props: Map<String, String>): UsageLedger {
        if (!ledger.optedIn || !UsageCounts.allows(name)) return ledger
        val event = UsageEvent(name, UsageCounts.props(props))
        val queue = (ledger.queue + event).takeLast(UsageCounts.MAX_QUEUE)
        return ledger.copy(queue = queue)
    }

    /** Events that may leave the device. Empty when opted out, even if a queue was left behind. */
    fun drain(ledger: UsageLedger): List<UsageEvent> =
        if (ledger.optedIn && ledger.installId != null) ledger.queue else emptyList()

    fun acknowledge(ledger: UsageLedger, sent: Int): UsageLedger =
        ledger.copy(queue = ledger.queue.drop(sent.coerceIn(0, ledger.queue.size)))

    fun stepViewed(ledger: UsageLedger, step: Int, now: Long): UsageLedger {
        val start = ledger.onboardingStartedAtMillis ?: ledger.installedAtMillis ?: now
        return enqueue(
            ledger,
            UsageCounts.ONBOARDING_STEP_VIEWED,
            mapOf("step" to step.toString(), "ms" to (now - start).coerceAtLeast(0).toString()),
        )
    }

    fun returnsDue(installedAtMillis: Long, now: Long, sent: Set<String>): List<UsageEvent> {
        val elapsedMs = now - installedAtMillis
        if (elapsedMs < 0) return emptyList()
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedMs)
        val days = TimeUnit.MILLISECONDS.toDays(elapsedMs)
        val due = mutableListOf<UsageEvent>()
        if (hours in 18..48 && UsageCounts.D1_RETURN !in sent) {
            due += UsageEvent(UsageCounts.D1_RETURN, mapOf("day_index" to "1"))
        }
        if (days in 6L..8L && UsageCounts.D7_RETURN !in sent) {
            due += UsageEvent(UsageCounts.D7_RETURN, mapOf("day_index" to "7"))
        }
        return due
    }
}
