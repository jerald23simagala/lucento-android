package com.lucento.app.data.sync

/**
 * Keeps one account's ledger from being uploaded into another.
 *
 * The first account to sync is remembered. Signing out removes this phone's financial data and that binding.
 * Signing in to a different account does the same before anything is pushed: immediately when nothing is waiting
 * to sync, and only after the user confirms when unsynced changes would be lost.
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
) {
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
    }

    /**
     * Clears the stored session, then removes this phone's ledger.
     * [signOut] finishes first — and still deletes the session when the logout request fails offline — so a kill
     * during [wipe] cannot leave a token the next launch would use to download the account.
     */
    suspend fun signOutAndWipe() {
        exclusive {
            signOut()
            prepare()
            wipe()
        }
    }
}
