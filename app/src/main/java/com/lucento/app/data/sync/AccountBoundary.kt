package com.lucento.app.data.sync

/**
 * Keeps one account's ledger from being uploaded into another.
 *
 * The first account to sync is remembered. Signing out removes this phone's financial data and that binding.
 * Signing in to a different account does the same before anything is pushed: immediately when nothing is waiting
 * to sync, and only after the user confirms when unsynced changes would be lost.
 *
 * The one exception (owner decision 2026-11-12, F-2): records no account ever claimed are this phone's own, so a
 * sign-out may keep them ([signOutKeepingLedger]) instead of wiping them.
 */
class AccountBoundary(
    private val currentUserId: suspend () -> String?,
    private val syncedUserId: suspend () -> String?,
    private val hasUnsyncedChanges: suspend () -> Boolean,
    /** Cancel background sync and turn the preference off. Must not wipe. */
    private val prepare: suspend () -> Unit = {},
    private val wipe: suspend () -> Unit,
    private val signOut: suspend () -> Unit,
    private val exclusive: suspend (suspend () -> Unit) -> Unit = { block -> SyncFlight.exclusive { block() } },
    /** Durably records (commit(), synchronous) that a wipe must happen. Must not touch the network. */
    private val markWipePending: () -> Boolean = { true },
    /** Best-effort server-side revoke of the ended session. Runs last, after the local wipe has committed. */
    private val revoke: suspend () -> Unit = {},
    /** Lucento Shared: the Owner whose Shared Space this phone holds, or null. That ledger is never this phone's own. */
    private val sharedSpaceOwner: suspend () -> String? = { null },
    /**
     * What else this phone remembers about the signed-in account (Lucento Shared's cached space and invitations, the AI
     * consents given while signed in). Must leave the ledger alone.
     */
    private val forgetAccount: suspend () -> Unit = {},
) {
    /**
     * QA R9-01: call on the confirm tap, before any coroutine or network call. A kill at any later point leaves the
     * marker behind, so the next launch finishes the wipe ([com.lucento.app.data.local.LocalLedgerReset.resumeIfInterrupted]).
     */
    fun markWipePendingNow(): Boolean = runCatching { markWipePending() }.getOrDefault(false)

    sealed interface AfterSignIn {
        /** Same account, or this phone has never synced. Nothing was removed. */
        data object Ready : AfterSignIn

        /** A different account, and the previous ledger was already removed. The caller can sync. */
        data object Wiped : AfterSignIn

        /** A different account, with changes that have not synced. Nothing is removed until [confirmSwitch]. */
        data class ConfirmSwitch(val unsynced: Boolean) : AfterSignIn
    }

    suspend fun afterSignIn(): AfterSignIn {
        val current = currentUserId() ?: return AfterSignIn.Ready
        val owner = syncedUserId() ?: return AfterSignIn.Ready
        if (owner == current) return AfterSignIn.Ready
        if (hasUnsyncedChanges()) {
            exclusive { prepare() }
            return AfterSignIn.ConfirmSwitch(unsynced = true)
        }
        exclusive {
            prepare()
            wipe()
        }
        return AfterSignIn.Wiped
    }

    /** The user agreed to drop unsynced changes and start the signed-in account clean. */
    suspend fun confirmSwitch() {
        exclusive {
            prepare()
            wipe()
        }
    }

    /** Stay with the previous ledger. The new session is dropped; nothing local is removed. */
    suspend fun cancelSwitch() {
        exclusive { signOut() }
        runCatching { revoke() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    /**
     * Order (QA R9-01): 1) durable wipe marker, 2) local session cleared (no network), 3) sync stopped and the ledger
     * wiped, 4) only then the server revoke, if online. A kill after step 1 relaunches into a finished wipe; a kill
     * before it leaves everything (session and data) as it was. The revoke can no longer delay the marker.
     */
    suspend fun signOutAndWipe() {
        check(markWipePendingNow()) { "Could not record the wipe" }
        exclusive {
            signOut()
            prepare()
            wipe()
        }
        runCatching { revoke() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    /**
     * True when no account ever claimed this phone's records: no first sync bound them ([syncedUserId] is claimed before
     * anything is pulled or pushed) and they aren't a Shared Space. They are the phone's own, never uploaded.
     */
    suspend fun ledgerIsPhoneOnly(): Boolean = syncedUserId() == null && sharedSpaceOwner() == null

    /**
     * Signs out and keeps this phone's own records (owner decision 2026-11-12, F-2). Only for a ledger that
     * [ledgerIsPhoneOnly], checked again under the sync lock so a first sync can't claim it in between. The session ends
     * as in [signOutAndWipe] (recorded for the server revoke, then deleted), sync stops and the account's cached state is
     * forgotten; nothing in the ledger changes and no wipe marker is set. Returns false, with nothing changed, when the
     * records belong to an account after all: those leave this phone only through [signOutAndWipe].
     */
    suspend fun signOutKeepingLedger(): Boolean {
        var signedOut = false
        exclusive {
            if (ledgerIsPhoneOnly()) {
                signOut()
                prepare()
                forgetAccount()
                signedOut = true
            }
        }
        if (signedOut) runCatching { revoke() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        return signedOut
    }
}
