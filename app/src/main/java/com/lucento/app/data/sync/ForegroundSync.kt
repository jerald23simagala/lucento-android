package com.lucento.app.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Syncs while Lucento is open (owner, 2026-11-04: "is it real time, or is something pending?"). When the app opens or
 * comes back to the front, a moment after each saved change, and on Sync now, the sync runs here straight away instead
 * of queueing in WorkManager, which Android and many phones' battery savers may hold back for a long time. In the
 * background, WorkManager still syncs about every 30 minutes and after changes.
 *
 * Requests that arrive while a sync runs collapse into one more run after it, so a change saved mid-sync is never left
 * behind and taps never pile up. A plain request (opening the app) is skipped when a sync finished moments ago with
 * nothing waiting; a [request] with `forced` (a change to send, the user's tap) always runs.
 */
internal class ForegroundSync(
    scope: CoroutineScope,
    /** Sync is on, onboarding finished (the same rule as background sync, [backgroundSyncAllowed]). */
    private val allowed: suspend () -> Boolean,
    /** A sync finished moments ago and nothing is waiting to upload. */
    private val upToDate: suspend () -> Boolean,
    private val sync: suspend () -> Unit,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val forcedPending = AtomicBoolean(false)

    init {
        scope.launch {
            for (request in requests) {
                val forced = forcedPending.getAndSet(false)
                try {
                    if (allowed() && (forced || !upToDate())) sync()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A failed sync is already stored and shown by SyncRepository; the next request tries again.
                }
            }
        }
    }

    fun request(forced: Boolean) {
        if (forced) forcedPending.set(true)
        requests.trySend(Unit)
    }

    companion object {
        /** Opening the app again within this long after a complete sync doesn't sync again. */
        const val FRESH_MILLIS = 60_000L
    }
}
