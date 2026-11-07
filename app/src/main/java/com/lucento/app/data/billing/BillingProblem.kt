package com.lucento.app.data.billing

/**
 * Why Google Play couldn't do what was asked (docs/MONETIZATION.md §9 "Errors"). The Pro page words each one plainly
 * (`ProCopy.billingProblem`); none of them changes the plan this phone holds.
 */
enum class BillingProblem {
    /** No Play Store, an account Play can't bill, or Play turned billing off on this phone. */
    UNAVAILABLE,
    /** No connection, or Play didn't answer in time. Worth retrying. */
    NETWORK,
    /** This Google account already owns it (Restore purchases brings it back). */
    ALREADY_OWNED,
    /** The plan isn't on sale in this country, or Play doesn't list it right now. */
    ITEM_UNAVAILABLE,
    /** Lucento asked Play for something it can't do. A bug, never the person's fault. */
    DEVELOPER,
    /** Anything else Play reports. Worth retrying. */
    OTHER,
    ;

    companion object {
        /**
         * Maps a Billing Library response code (`BillingClient.BillingResponseCode`, plain ints) to a problem. OK and
         * USER_CANCELED are not problems; callers handle them before asking.
         */
        fun fromResponseCode(code: Int): BillingProblem = when (code) {
            CODE_BILLING_UNAVAILABLE, CODE_FEATURE_NOT_SUPPORTED -> UNAVAILABLE
            CODE_SERVICE_TIMEOUT, CODE_SERVICE_DISCONNECTED, CODE_SERVICE_UNAVAILABLE, CODE_NETWORK_ERROR -> NETWORK
            CODE_ITEM_ALREADY_OWNED -> ALREADY_OWNED
            CODE_ITEM_UNAVAILABLE -> ITEM_UNAVAILABLE
            CODE_DEVELOPER_ERROR, CODE_ITEM_NOT_OWNED -> DEVELOPER
            else -> OTHER
        }

        // The Billing Library's own values (BillingClient.BillingResponseCode), repeated so this file stays free of the
        // library. BillingProblemTest pins them to the library's constants.
        internal const val CODE_OK = 0
        internal const val CODE_USER_CANCELED = 1
        internal const val CODE_SERVICE_TIMEOUT = -3
        internal const val CODE_FEATURE_NOT_SUPPORTED = -2
        internal const val CODE_SERVICE_DISCONNECTED = -1
        internal const val CODE_SERVICE_UNAVAILABLE = 2
        internal const val CODE_BILLING_UNAVAILABLE = 3
        internal const val CODE_ITEM_UNAVAILABLE = 4
        internal const val CODE_DEVELOPER_ERROR = 5
        internal const val CODE_ITEM_ALREADY_OWNED = 7
        internal const val CODE_ITEM_NOT_OWNED = 8
        internal const val CODE_NETWORK_ERROR = 12
    }
}

/** How a purchase Lucento started ended, as Play reported it. The plan itself arrives through [PurchaseSource.updates]. */
sealed interface PurchaseResult {
    /** Play confirmed the payment. */
    data object Completed : PurchaseResult
    /** Play accepted the order but the payment isn't confirmed yet (cash at a store, customer copy D-9). */
    data object Pending : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Failed(val problem: BillingProblem) : PurchaseResult
}
