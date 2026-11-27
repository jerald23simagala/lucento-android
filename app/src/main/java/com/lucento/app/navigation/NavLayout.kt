package com.lucento.app.navigation

/**
 * How the app's navigation is laid out for a window (owner, 2026-11-26: side rail and two panes when unfolded).
 *
 * - [DOCK]: phones, and anything short (a phone turned sideways keeps the dock): the bottom navigation dock.
 * - [RAIL]: a medium-width window (a tablet or a Galaxy Z Fold held upright, a foldable's inner screen in a narrow split):
 *   the side rail, one pane.
 * - [SPLIT]: a wide window (an unfolded foldable, a Galaxy Z Fold held in landscape, a tablet on its side): the side rail
 *   and two panes, the current tab on the left and what was opened from it on the right.
 */
enum class NavLayout { DOCK, RAIL, SPLIT }

/** Material's medium-width breakpoint: below it, the dock. */
const val RAIL_MIN_WIDTH_DP = 600

/**
 * Two panes of at least [LIST_PANE_MIN_DP] each beside the 84dp rail (`RailWidth`): 804dp. That is a little under
 * Material's expanded breakpoint (840dp) on purpose (owner, 2026-11-26): a Galaxy Z Fold held in landscape is about
 * 829–933dp wide (read on real phones 2026-11-27: Fold7 832, Fold8 933; Chrome DevTools' list: Fold5 829, Fold6 860;
 * Samsung lists it as medium), and it splits too. Upright (about 690–750dp) it keeps one pane, as does a tablet held
 * upright (800dp).
 */
const val SPLIT_MIN_WIDTH_DP = 804

/** Below this height the window is a phone on its side: the dock stays, so phones never change layout. */
const val WIDE_MIN_HEIGHT_DP = 480

/**
 * The layout for a window of [widthDp] × [heightDp], as the window's Configuration reports it. Android 15 and later count
 * the system bars in; earlier versions leave them out, so the same window reads a little smaller there. The thresholds
 * have room for that either way: a phone on its side is about 360–430dp tall, an unfolded foldable over 800dp.
 */
fun navLayoutFor(widthDp: Int, heightDp: Int): NavLayout = when {
    heightDp < WIDE_MIN_HEIGHT_DP || widthDp < RAIL_MIN_WIDTH_DP -> NavLayout.DOCK
    widthDp < SPLIT_MIN_WIDTH_DP -> NavLayout.RAIL
    else -> NavLayout.SPLIT
}

/**
 * The list pane's width beside the rail, out of [contentWidthDp]: half, held between a small phone's width and a readable
 * column. When the width can't give both panes [LIST_PANE_MIN_DP] (side insets on a narrow split) they share it equally,
 * so the detail pane is never the one squeezed.
 */
fun listPaneWidthDp(contentWidthDp: Int): Int =
    if (contentWidthDp < 2 * LIST_PANE_MIN_DP) contentWidthDp / 2 else (contentWidthDp / 2).coerceIn(LIST_PANE_MIN_DP, LIST_PANE_MAX_DP)

const val LIST_PANE_MIN_DP = 360
const val LIST_PANE_MAX_DP = 480

/** What a tab's right-hand pane shows while nothing has been opened from it: it is never left empty. */
enum class DefaultPane { INSIGHTS, SCHEDULED, NEWEST_TRANSACTION, DEFAULT_WALLET, NOTHING_OPEN }

/**
 * The default right-hand pane for tab [tabKey] (owner, 2026-11-26: Insights beside Home). Home falls back to Scheduled,
 * and Budget (whose natural partner is the bills still to come) to Insights, when a module is switched off.
 */
fun defaultPaneFor(tabKey: String, insightsOn: Boolean, scheduledOn: Boolean): DefaultPane = when (tabKey) {
    "transactions" -> DefaultPane.NEWEST_TRANSACTION
    "wallets" -> DefaultPane.DEFAULT_WALLET
    "budget" -> when {
        scheduledOn -> DefaultPane.SCHEDULED
        insightsOn -> DefaultPane.INSIGHTS
        else -> DefaultPane.NOTHING_OPEN
    }
    else -> when {
        insightsOn -> DefaultPane.INSIGHTS
        scheduledOn -> DefaultPane.SCHEDULED
        else -> DefaultPane.NOTHING_OPEN
    }
}
