package com.lucento.app.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Inserts one anonymous event. Implementations must not read the table back. */
fun interface UsageEventSender {
    suspend fun insert(installId: String, event: UsageEvent, appVersion: String)
}

/** Calls delete_usage_events. Must not select the table. */
fun interface UsageEventDeleter {
    suspend fun delete(installId: String)
}

/**
 * Queues events on the device and uploads them only while the person has opted in.
 * A failed upload leaves the event queued. Opting out clears the queue and does not upload it.
 */
class UsageCountsService(
    private val load: suspend () -> UsageLedger,
    private val save: suspend (UsageLedger) -> Unit,
    private val send: UsageEventSender,
    private val deleteRemote: UsageEventDeleter,
    private val newInstallId: () -> String,
    private val now: () -> Long,
    private val appVersion: String,
    /** Runs the server delete off the opt-out call, so a slow network cannot block saving "off". */
    private val launchInBackground: (suspend () -> Unit) -> Unit,
) {
    private val gate = Mutex()

    suspend fun setOptIn(enabled: Boolean) {
        val followUp = gate.withLock {
            if (enabled) {
                retryPendingLocked()
                val next = UsageLedgerLogic.optIn(load(), now(), newInstallId())
                save(next)
                upload(next)
                null
            } else {
                optOutLocally()
            }
        }
        if (followUp != null) launchInBackground(followUp)
    }

    /**
     * Persist opted-out and an empty queue before any network call. The install id moves to the pending
     * slot so a kill during the background delete can be retried next launch or when the network returns.
     * Returns the background delete, or null when there is nothing on the server to remove.
     */
    private suspend fun optOutLocally(): (suspend () -> Unit)? {
        val current = load()
        val id = UsageLedgerLogic.idToDelete(current)
        save(UsageLedgerLogic.afterOptOut(current, deleted = false))
        if (id == null) return null
        return { finishDeletion(id) }
    }

    private suspend fun finishDeletion(id: String) {
        val deleted = try {
            deleteRemote.delete(id)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!deleted) return
        gate.withLock {
            val latest = load()
            if (latest.pendingDeletionInstallId == id) {
                save(UsageLedgerLogic.afterDeletionRetry(latest, deleted = true))
            }
        }
    }

    /** Next launch, or the network coming back: delete rows for an install id whose opt-out did not finish. */
    suspend fun retryPendingDeletion() = gate.withLock { retryPendingLocked() }

    private suspend fun retryPendingLocked() {
        val id = load().pendingDeletionInstallId ?: return
        val deleted = try {
            deleteRemote.delete(id)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (deleted) save(UsageLedgerLogic.afterDeletionRetry(load(), deleted = true))
    }

    suspend fun noteWelcome() = gate.withLock {
        save(UsageLedgerLogic.noteWelcome(load(), now()))
    }

    suspend fun stepViewed(step: Int) = gate.withLock {
        val next = UsageLedgerLogic.stepViewed(load(), step, now())
        save(next)
        upload(next)
    }

    suspend fun record(name: String, props: Map<String, String> = emptyMap()) = gate.withLock {
        val next = UsageLedgerLogic.enqueue(load(), name, props)
        save(next)
        upload(next)
    }

    /** Day-1 and day-7 opens, plus the one-shot home events derived from what Home is showing. */
    suspend fun onHome(
        realEntries: Int,
        suggestionDays: Int?,
        doneSteps: Set<String>,
        budgetedCategories: Int,
        acceptSuggestion: Boolean,
        markAdjust: Boolean,
    ) = gate.withLock {
        val loaded = load()
        var ledger = loaded
        if (ledger.installedAtMillis == null) ledger = ledger.copy(installedAtMillis = now())
        val installed = ledger.installedAtMillis ?: now()
        // Milestones reached while counts are off are marked sent on purpose. A later opt-in must not
        // upload d1, d7, or the first entry: those happened before the person agreed to share counts.
        for (event in UsageLedgerLogic.returnsDue(installed, now(), ledger.sent)) {
            ledger = UsageLedgerLogic.enqueue(ledger, event.name, event.props)
                .copy(sent = ledger.sent + event.name)
        }
        if (realEntries >= 1 && UsageCounts.FIRST_ENTRY_SAVED !in ledger.sent) {
            val start = ledger.onboardingStartedAtMillis ?: installed
            ledger = UsageLedgerLogic.enqueue(
                ledger,
                UsageCounts.FIRST_ENTRY_SAVED,
                mapOf("ms" to (now() - start).coerceAtLeast(0).toString()),
            ).copy(sent = ledger.sent + UsageCounts.FIRST_ENTRY_SAVED)
        }
        if (suggestionDays != null && UsageCounts.BUDGET_SUGGESTION_SHOWN !in ledger.sent) {
            ledger = UsageLedgerLogic.enqueue(
                ledger,
                UsageCounts.BUDGET_SUGGESTION_SHOWN,
                mapOf("count" to suggestionDays.toString()),
            ).copy(sent = ledger.sent + UsageCounts.BUDGET_SUGGESTION_SHOWN)
        }
        doneSteps.forEach { step ->
            val key = "week:$step"
            if (key !in ledger.sent) {
                ledger = UsageLedgerLogic.enqueue(
                    ledger,
                    UsageCounts.FIRST_WEEK_CARD_STEP_DONE,
                    mapOf("step" to step),
                ).copy(sent = ledger.sent + key)
            }
        }
        if (markAdjust) ledger = ledger.copy(adjustPending = true)
        if (acceptSuggestion || (ledger.adjustPending && budgetedCategories > 0)) {
            if (UsageCounts.BUDGET_SUGGESTION_ACCEPTED !in ledger.sent) {
                val count = if (acceptSuggestion) budgetedCategories else budgetedCategories
                ledger = UsageLedgerLogic.enqueue(
                    ledger,
                    UsageCounts.BUDGET_SUGGESTION_ACCEPTED,
                    mapOf("count" to count.coerceAtLeast(1).toString()),
                ).copy(sent = ledger.sent + UsageCounts.BUDGET_SUGGESTION_ACCEPTED, adjustPending = false)
            }
        }
        if (ledger != loaded) save(ledger)
        upload(if (ledger != loaded) ledger else loaded)
    }

    private suspend fun upload(ledger: UsageLedger) {
        val installId = ledger.installId ?: return
        val waiting = UsageLedgerLogic.drain(ledger)
        if (waiting.isEmpty()) return
        var sent = 0
        for (event in waiting) {
            try {
                send.insert(installId, event, appVersion.take(UsageCounts.MAX_APP_VERSION))
                sent++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                break
            }
        }
        if (sent > 0) save(UsageLedgerLogic.acknowledge(load(), sent))
    }
}
