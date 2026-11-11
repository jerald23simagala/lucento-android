package com.lucento.app.core

/**
 * Lucento's public pages, opened in the phone's browser only when the person taps them (the app itself sends nothing).
 * The Play listing link works once the app is public; until then sharing points at the website, which links to Play.
 */
object LucentoLinks {
    const val WEBSITE = "https://www.lucentoapp.com/"
    const val COMMUNITY = "https://www.facebook.com/share/g/1E4BdQJjgW/"
    const val PLAY_STORE = "https://play.google.com/store/apps/details?id=com.lucento.app"

    /**
     * Flip to true when the Google Play listing is public (production track live): "Share Lucento" then sends the Play
     * link instead of the website. Until then the Play link would show "item not found" to anyone outside testing.
     */
    const val PLAY_LISTING_PUBLIC = false

    /** Where "Share Lucento" points people. */
    val shareLink: String get() = if (PLAY_LISTING_PUBLIC) PLAY_STORE else WEBSITE

    /** The message the share sheet starts with; the person can change it before sending. */
    fun shareMessage(link: String = shareLink): String =
        "I use Lucento to keep track of my money: budgets, wallets and goals in one calm app, and it works offline. " +
            "Financial clarity without the complexity. $link"
}
