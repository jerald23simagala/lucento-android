package com.lucento.app.feature.onboarding

import com.lucento.app.data.sync.AccountProbe

/** How onboarding continues once an account is connected (P0-2). */
enum class SetupRoute {
    /** No account connected (or sync unavailable in this build): an ordinary setup on this phone. */
    LOCAL,
    /** The account has nothing a person made: set up as new; it syncs when setup finishes. */
    NEW_SETUP,
    /** The account already holds the user's data: bring it to this phone instead of starting over. */
    RESTORE,
    /** The account couldn't be checked. Nothing is set up and nothing is assumed: try again (or continue offline). */
    RETRY,
}

/**
 * The single rule for what a connected account means for onboarding. A failed check is never read as "new": that is
 * how a returning user's account would get a second set of wallets and categories. [confirmedNewFor] is the account
 * the user already chose to set up as new after a restore found nothing to bring back, so it isn't offered again.
 */
internal fun setupRoute(probe: AccountProbe, confirmedNewFor: String?): SetupRoute = when (probe) {
    AccountProbe.NotConfigured, AccountProbe.NotSignedIn -> SetupRoute.LOCAL
    is AccountProbe.Empty -> SetupRoute.NEW_SETUP
    is AccountProbe.HasData -> if (probe.userId == confirmedNewFor) SetupRoute.NEW_SETUP else SetupRoute.RESTORE
    is AccountProbe.Unavailable -> SetupRoute.RETRY
}

/** What the sync step shows while a connected account is checked. */
sealed interface AccountCheck {
    data object Idle : AccountCheck
    data object Checking : AccountCheck
    /** The check failed. Try again, or carry on without the account (see `OnboardingViewModel.leaveAccount`). */
    data object Unavailable : AccountCheck
}
