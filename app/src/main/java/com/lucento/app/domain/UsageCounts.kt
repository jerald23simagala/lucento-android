package com.lucento.app.domain

import java.util.concurrent.TimeUnit

/**
 * Anonymous usage counts. The eight names are the whole allowlist. Props are only [PROP_KEYS].
 * Nothing here reads the server table back. Opting out drops the queue and the install id.
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

    val EVENTS: List<String> = listOf(
        ONBOARDING_STARTED,
        ONBOARDING_STEP_VIEWED,
        FIRST_ENTRY_SAVED,
        D1_RETURN,
        D7_RETURN,
        FIRST_WEEK_CARD_STEP_DONE,
        BUDGET_SUGGESTION_SHOWN,
        BUDGET_SUGGESTION_ACCEPTED,
    )

    val PROP_KEYS: Set<String> = setOf("step", "ms", "day_index", "count")

    const val MAX_QUEUE = 40
    const val MAX_PROP_LENGTH = 32
    const val MAX_APP_VERSION = 32

    fun allows(name: String): Boolean = name in EVENTS

    /** Keeps only allowlisted keys and short values. Drops anything else, including free text. */
    fun props(raw: Map<String, String>): Map<String, String> =
        raw.filterKeys { it in PROP_KEYS }
            .mapValues { (_, value) -> value.trim().take(MAX_PROP_LENGTH) }
            .filterValues { it.isNotEmpty() }
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
