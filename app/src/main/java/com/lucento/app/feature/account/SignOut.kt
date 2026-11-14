package com.lucento.app.feature.account

/**
 * Signing out (docs/SYNC.md "Sign-out and switching accounts"; owner decision 2026-11-12, F-2). What the confirmation
 * says and offers depends on where this phone's records stand. These are the pure parts, tested on the JVM; the order
 * of the wipe itself lives in [com.lucento.app.data.sync.AccountBoundary].
 */
enum class SignOutCase {
    /** The records belong to the account and everything has reached it: this phone's copy is removed, nothing is lost. */
    SAFE_IN_ACCOUNT,

    /** The account's records with changes still waiting to upload, and this plan can upload them: sync first. */
    UNSYNCED_CAN_SYNC,

    /**
     * The account's records with changes waiting, but this plan can't upload them now (cloud sync is part of Lucento
     * Pro). "Sync first" could never help here; a backup file, which is always free, keeps a copy.
     */
    UNSYNCED_NEEDS_BACKUP,

    /**
     * Never joined to any account and not a Shared Space: the records are this phone's own and were never uploaded. The
     * person chooses whether they stay here (only the session ends) or are removed.
     */
    PHONE_ONLY,
}

/**
 * [phoneOnly]: this phone's records were never claimed by an account or a Shared Space
 * ([com.lucento.app.data.sync.AccountBoundary.ledgerIsPhoneOnly]). [canUpload]: this plan lets sync upload right now.
 */
fun signOutCase(phoneOnly: Boolean, pendingChanges: Int, canUpload: Boolean): SignOutCase = when {
    phoneOnly -> SignOutCase.PHONE_ONLY
    pendingChanges <= 0 -> SignOutCase.SAFE_IN_ACCOUNT
    canUpload -> SignOutCase.UNSYNCED_CAN_SYNC
    else -> SignOutCase.UNSYNCED_NEEDS_BACKUP
}

/** The sign-out confirmation: [confirmLabel] is always the safe choice; [secondaryLabel], if any, removes records. */
data class SignOutDialog(
    val title: String,
    val text: String,
    val confirmLabel: String,
    val secondaryLabel: String?,
)

object SignOutCopy {
    /** The action everywhere: the profile menu, the Account & sync page and the dialog. Not "Disconnect" or "Log out". */
    const val ACTION = "Sign out"
    const val TITLE = "Sign out?"
    const val SYNC_FIRST = "Sync first"
    const val SAVE_BACKUP = "Save a backup"
    const val ANYWAY = "Sign out anyway"
    const val KEEP = "Keep them here"
    const val REMOVE = "Remove and sign out"

    /** Under the Account & sync page's Sign out button. */
    const val HINT = "Before anything leaves this phone, Lucento shows what’s safe in your account and what’s only here."

    /** After "Keep them here". */
    const val KEPT = "Signed out. Your records stay on this phone, and nothing was uploaded."

    /** "Keep them here" found the records already joined to an account (a first sync started in between). */
    const val KEEP_REFUSED = "These records now belong to your account, so signing out removes this phone’s copy. Nothing changed yet."

    fun dialog(case: SignOutCase, email: String?, pendingChanges: Int): SignOutDialog {
        val account = email?.takeIf { it.isNotBlank() }?.let { "your account ($it)" } ?: "your account"
        return when (case) {
            SignOutCase.SAFE_IN_ACCOUNT -> SignOutDialog(
                title = TITLE,
                text = "Everything is safe in $account. Signing out removes this phone’s copy, and Lucento starts " +
                    "again from setup. Sign in again any time to bring it all back.",
                confirmLabel = ACTION,
                secondaryLabel = null,
            )
            SignOutCase.UNSYNCED_CAN_SYNC -> SignOutDialog(
                title = TITLE,
                text = "${waiting(pendingChanges)} $account yet. Signing out removes this phone’s copy, so sync first " +
                    "to keep ${them(pendingChanges)}. Everything else is safe in the account.",
                confirmLabel = SYNC_FIRST,
                secondaryLabel = ANYWAY,
            )
            SignOutCase.UNSYNCED_NEEDS_BACKUP -> SignOutDialog(
                title = TITLE,
                text = "${waiting(pendingChanges)} $account, and cloud sync isn’t part of your plan right now, so " +
                    "${they(pendingChanges)} can’t be uploaded. Signing out deletes ${them(pendingChanges)} from this " +
                    "phone. A backup file keeps a copy — backups are always free.",
                confirmLabel = SAVE_BACKUP,
                secondaryLabel = ANYWAY,
            )
            SignOutCase.PHONE_ONLY -> SignOutDialog(
                title = TITLE,
                text = "Nothing on this phone has been uploaded to $account, so your records are only here. Keep " +
                    "them on this phone, or remove them as you sign out. Removing them can’t be undone.",
                confirmLabel = KEEP,
                secondaryLabel = REMOVE,
            )
        }
    }

    /** "1 change hasn’t reached" / "3 changes haven’t reached" — the account follows. */
    private fun waiting(count: Int): String =
        if (count == 1) "1 change hasn’t reached" else "$count changes haven’t reached"

    private fun they(count: Int) = if (count == 1) "it" else "they"

    private fun them(count: Int) = if (count == 1) "it" else "them"
}

/**
 * One line on the welcome screen after a sign-out (or an account deletion) took this phone back to setup. Written just
 * before the wipe, read once by onboarding; without it the person landed on the welcome with no word of what happened.
 */
enum class SignOutNotice(val key: String, val text: String) {
    /** The account's records left this phone; what synced is still in the account. */
    SIGNED_OUT(
        "signed_out",
        "You’re signed out, and this phone’s copy was removed. Choose “I already have Lucento” to bring back what’s " +
            "in your account.",
    ),

    /** "Remove and sign out" on records that were only on this phone. */
    REMOVED("removed", "You’re signed out, and the records that were only on this phone were removed."),

    ACCOUNT_DELETED("account_deleted", AccountDeletionCopy.DONE),
    ;

    companion object {
        fun fromKey(key: String?): SignOutNotice? = entries.firstOrNull { it.key == key }
    }
}
