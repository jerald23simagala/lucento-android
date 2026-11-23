package com.lucento.app.core

/**
 * Lucento's public pages, opened in the phone's browser only when the person taps them (the app itself sends nothing).
 * The Play listing link works once the app is public; until then sharing points at the website, which links to Play.
 */
object LucentoLinks {
    const val WEBSITE = "https://www.lucentoapp.com/"
    const val COMMUNITY = "https://www.facebook.com/share/g/1E4BdQJjgW/"

    /**
     * Lucento's channels, in the website's order (lucento-website `src/data/site.ts`). Each says only what is true of it
     * today: Facebook, Instagram and TikTok are @lucentoapp (the Facebook Page got its username on 2026-11-23, OT-60);
     * YouTube is linked by its channel ID, as on the website (design review 2026-11-23). Change a link here and on the
     * website together.
     */
    val FOLLOW: List<Channel> = listOf(
        Channel("Facebook", "@lucentoapp", "https://www.facebook.com/lucentoapp", com.lucento.app.R.drawable.ic_brand_facebook),
        Channel("Instagram", "@lucentoapp", "https://www.instagram.com/lucentoapp/", com.lucento.app.R.drawable.ic_brand_instagram),
        Channel("TikTok", "@lucentoapp", "https://www.tiktok.com/@lucentoapp", com.lucento.app.R.drawable.ic_brand_tiktok),
        Channel("YouTube", "Lucento · Channel", "https://www.youtube.com/channel/UCg8f6wbVTgYPnXBeMvD0FyA", com.lucento.app.R.drawable.ic_brand_youtube),
    )

    /** One of Lucento's channels: its name, what to look for there, where it opens and its logo (tinted where drawn). */
    data class Channel(val name: String, val subtitle: String, val url: String, @androidx.annotation.DrawableRes val icon: Int)
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
