package com.lucento.app.navigation

/**
 * How the app's navigation is laid out for a window (owner, 2026-11-26: side rail and two panes when unfolded).
 *
 * - [DOCK]: phones, and anything short (a phone turned sideways keeps the dock): the bottom navigation dock.
 * - [RAIL]: a medium-width window (a small tablet, a foldable's inner screen in a narrow split): the side rail, one pane.
 * - [SPLIT]: a wide window (an unfolded foldable, a tablet): the side rail and two panes, the current tab on the left
 *   and what was opened from it on the right.
 */
enum class NavLayout { DOCK, RAIL, SPLIT }

/** Material's medium-width breakpoint: below it, the dock. */
const val RAIL_MIN_WIDTH_DP = 600

/** Material's expanded-width breakpoint: two panes of at least 360dp each, beside the rail. */
const val SPLIT_MIN_WIDTH_DP = 840

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
