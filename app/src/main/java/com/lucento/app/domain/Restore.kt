package com.lucento.app.domain

/**
 * Restoring an existing account on a new phone (onboarding's "I already have an account"). After the first sync has
 * pulled the account's rows, these rules decide whether there was anything to restore and fill in the preferences
 * that live only on the device (main currency, enabled modules), so the user lands on Home instead of the story.
 */
object RestorePlan {

    /**
     * True when the account brought something back: a wallet, or an expense category (a fresh install only seeds
     * income categories, so those alone mean an empty account). An empty account continues with the normal setup.
     */
    fun hasData(wallets: List<Wallet>, categories: List<Category>): Boolean =
        wallets.isNotEmpty() || categories.any { it.kind == CategoryKind.EXPENSE && !it.archived }

    /** The account has spending categories, so the category picker can be skipped too. */
    fun hasSpendingCategories(categories: List<Category>): Boolean =
        categories.any { it.kind == CategoryKind.EXPENSE && !it.archived }

    /**
     * The main currency is a device preference, so it is not in the account: take the currency most of the restored
     * wallets use. On a tie, [fallback] (the phone's locale currency) wins if it is among the tied ones, else the
     * alphabetically first, so the choice never depends on row order. No wallets: [fallback].
     */
    fun primaryCurrency(wallets: List<Wallet>, fallback: String): String {
        if (wallets.isEmpty()) return fallback
        val counts = wallets.groupingBy { it.currencyCode }.eachCount()
        val top = counts.values.max()
        val tied = counts.filterValues { it == top }.keys
        return if (fallback in tied) fallback else tied.min()
    }

    /** Every module on: a restored account may use any of them, and switching one off later never deletes data. */
    val features: Set<Feature> get() = Feature.entries.toSet()
}
