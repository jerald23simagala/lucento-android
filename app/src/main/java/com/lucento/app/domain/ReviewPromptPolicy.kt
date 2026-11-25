package com.lucento.app.domain

/**
 * When Lucento may ask Google Play to show its rating card (Play's In-App Review API; docs/HELP_AND_FEEDBACK.md).
 *
 * Owner decision 2026-11-25, after the closed test's tester report: right after a save, once the person has had Lucento
 * for a week and recorded 20 incomes and expenses, at most once every 90 days, and only once the Play listing is public.
 * The paid closed-test testers are never asked (docs/marketing/launch/TRUTH_RULES.md T25); "Rate Lucento" in Settings is
 * there for anyone who wants it. Nothing is asked before the card ("do you like Lucento?" is against Play's policy), and
 * Google Play keeps its own quota, so a due ask may still show nothing. Lucento never learns whether someone rated it.
 */
object ReviewPromptPolicy {
    const val MIN_DAYS_INSTALLED = 7
    const val MIN_RECORDED = 20
    const val DAYS_BETWEEN_ASKS = 90

    private const val DAY_MILLIS = 86_400_000L

    /**
     * Whether to ask now. [installedAtMillis] is when this app was first installed on the phone (null if unknown: never
     * ask); [recorded] counts incomes and expenses, as the awards do; [lastAskedAtMillis] is this phone's last ask. A clock
     * that moved back reads as "too soon", never as "due".
     */
    fun isDue(
        nowMillis: Long,
        installedAtMillis: Long?,
        recorded: Int,
        lastAskedAtMillis: Long?,
        listingPublic: Boolean,
    ): Boolean {
        if (!listingPublic) return false
        if (installedAtMillis == null || nowMillis - installedAtMillis < MIN_DAYS_INSTALLED * DAY_MILLIS) return false
        if (recorded < MIN_RECORDED) return false
        if (lastAskedAtMillis != null && nowMillis - lastAskedAtMillis < DAYS_BETWEEN_ASKS * DAY_MILLIS) return false
        return true
    }
}
