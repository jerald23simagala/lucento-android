package com.lucento.app.feature.account

import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.feature.legal.LegalDocuments

/**
 * Deleting a Lucento account from inside the app (Google Play's account-deletion requirement; docs/ACCOUNT_DELETION.md).
 * The server deletes the account and everything stored under it (`public.delete_my_account`); the phone is then wiped
 * exactly like a sign-out. These are the pure parts: what the confirmation says, and what each server answer means.
 */

/** What the person holds through Google Play, because deleting the account changes neither. */
data class DeletionPlan(val subscribed: Boolean = false, val lifetime: Boolean = false) {
    companion object {
        fun of(plan: EntitlementState?, now: Long): DeletionPlan {
            val rows = plan?.entitlements.orEmpty()
            fun live(vararg sources: EntitlementSource) =
                rows.any { row -> row.source in sources && Capability.entries.any { row.covers(it, now) } }
            return DeletionPlan(
                subscribed = live(EntitlementSource.PLAY_SUBSCRIPTION, EntitlementSource.PLAY_CLOUD_ADDON),
                lifetime = live(EntitlementSource.PLAY_LIFETIME),
            )
        }
    }
}

/** The confirmation dialog's state. [error] is the last attempt's failure, shown in the dialog. */
data class DeleteAccountPrompt(
    val plan: DeletionPlan,
    val deleting: Boolean = false,
    val error: String? = null,
)

object AccountDeletionCopy {
    const val ENTRY = "Delete account"
    const val ENTRY_HINT = "Permanently deletes your account and its cloud data."
    const val TITLE = "Delete your Lucento account?"
    const val ACKNOWLEDGE = "I understand this can’t be undone"
    const val CONFIRM = "Delete account"
    const val DELETING = "Deleting…"
    const val MANAGE_SUBSCRIPTION = "Manage subscription in Google Play"
    const val DONE = "Your Lucento account and its cloud data have been deleted."

    fun paragraphs(plan: DeletionPlan): List<String> = buildList {
        add(
            "This permanently deletes your account and everything stored with it in Lucento’s cloud: wallets, " +
                "transactions, budgets, goals, debts, scheduled payments and settings.",
        )
        add(
            "Lucento Shared: if you own a Shared Space, the people in it lose access. If you joined someone’s, you " +
                "leave it, and what you added stays with its owner.",
        )
        add(
            "This phone’s records are erased too, and Lucento starts again from setup. To keep a copy, save a backup " +
                "file first in Settings › Your data.",
        )
        if (plan.subscribed) {
            add(
                "Deleting your account doesn’t cancel your Lucento subscription. Cancel it in Google Play first, or " +
                    "Google Play keeps charging you.",
            )
        }
        if (plan.lifetime) {
            add(
                "Your Lifetime purchase stays with your Google Play account. You can restore it later on a new Lucento " +
                    "account from the same Google account.",
            )
        }
    }
}

/** What the server said, as the app acts on it. */
sealed interface DeleteAccountResult {
    /** The account is gone (or was already gone): wipe the phone. */
    data object Deleted : DeleteAccountResult

    /** Nothing changed on the server. [message] says why, in plain words. */
    data class Failed(val message: String) : DeleteAccountResult
}

object DeleteAccountFailures {
    const val NOT_CONFIGURED = "Accounts aren’t available in this version of Lucento, so there is nothing to delete."
    const val OFFLINE = "You’re offline. Connect to the internet to delete your account. Nothing was changed."
    const val SIGNED_OUT = "Your sign-in has ended. Sign in again, then delete your account. Nothing was changed."
    /** A timeout: the deletion may or may not have happened. Trying again is safe either way. */
    const val NO_ANSWER = "Lucento didn’t hear back from the server. Try again — if your account was already deleted, " +
        "Lucento finishes on this phone."
    val UNAVAILABLE = "Couldn’t delete your account right now. Nothing was changed. Try again later, or email " +
        "${LegalDocuments.CONTACT_EMAIL} from your account’s address to ask for deletion."

    /**
     * Maps a refused `delete_my_account` call. [dbCode] is the database's SQLSTATE (or PostgREST's code), [status] the
     * HTTP status. "Already deleted" (P0002) counts as done: a second phone, or a retry after a lost answer.
     */
    fun fromServer(status: Int?, dbCode: String?): DeleteAccountResult = when {
        dbCode == "P0002" -> DeleteAccountResult.Deleted
        dbCode == "28000" || status == 401 -> DeleteAccountResult.Failed(SIGNED_OUT)
        else -> DeleteAccountResult.Failed(UNAVAILABLE)
    }
}
