package com.lucento.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which navigation a window gets (owner, 2026-11-26): phones keep the dock; wide windows get the rail and two panes. */
class NavLayoutTest {

    @Test
    fun phonesKeepTheDockUpright() {
        assertEquals(NavLayout.DOCK, navLayoutFor(widthDp = 360, heightDp = 640))
        assertEquals(NavLayout.DOCK, navLayoutFor(widthDp = 411, heightDp = 914))
        // Pixel 9 Pro Fold, folded (cover screen).
        assertEquals(NavLayout.DOCK, navLayoutFor(widthDp = 443, heightDp = 994))
    }

    @Test
    fun aPhoneOnItsSideKeepsTheDock() {
        // Wide enough to split, but too short: phones never change layout.
        assertEquals(NavLayout.DOCK, navLayoutFor(widthDp = 914, heightDp = 411))
        assertEquals(NavLayout.DOCK, navLayoutFor(widthDp = 640, heightDp = 360))
    }

    @Test
    fun anUnfoldedFoldableSplits() {
        // Pixel 9 Pro Fold, unfolded: upright and on its side.
        assertEquals(NavLayout.SPLIT, navLayoutFor(widthDp = 851, heightDp = 883))
        assertEquals(NavLayout.SPLIT, navLayoutFor(widthDp = 883, heightDp = 851))
        // A tablet on its side.
        assertEquals(NavLayout.SPLIT, navLayoutFor(widthDp = 1280, heightDp = 800))
    }

    @Test
    fun mediumWidthGetsTheRailWithOnePane() {
        assertEquals(NavLayout.RAIL, navLayoutFor(widthDp = 600, heightDp = 960))
        // A tablet held upright keeps one pane.
        assertEquals(NavLayout.RAIL, navLayoutFor(widthDp = 800, heightDp = 1280))
        assertEquals(NavLayout.RAIL, navLayoutFor(widthDp = 803, heightDp = 900))
    }

    @Test
    fun aGalaxyFoldSplitsSidewaysAndKeepsOnePaneUpright() {
        // Owner, 2026-11-26. Galaxy Z Fold5 (1812 × 2176 px) and Fold6 (1856 × 2160 px) inner screens at 420dpi; Samsung
        // lists both orientations as medium width, under Material's 840dp.
        assertEquals(NavLayout.SPLIT, navLayoutFor(widthDp = 829, heightDp = 690))
        assertEquals(NavLayout.SPLIT, navLayoutFor(widthDp = 823, heightDp = 707))
        assertEquals(NavLayout.RAIL, navLayoutFor(widthDp = 690, heightDp = 829))
        assertEquals(NavLayout.RAIL, navLayoutFor(widthDp = 707, heightDp = 823))
        // Chrome DevTools' device list (2026-11-27): Galaxy Z Fold 5 690 × 829, Galaxy Z Fold 6 744 × 860 unfolded.
        assertEquals(NavLayout.SPLIT, navLayoutFor(widthDp = 860, heightDp = 744))
        assertEquals(NavLayout.RAIL, navLayoutFor(widthDp = 744, heightDp = 860))
    }

    @Test
    fun realGalaxyFoldReadingsSplitInLandscapeAndKeepOnePaneUpright() {
        // Read on real phones, Samsung Remote Test Lab, 2026-11-27, default screen zoom (KNOWN_ISSUES WW-5).
        // Galaxy Z Fold7 (Android 16): 832 × 750 in landscape, 750 × 832 upright; Lucento itself split there.
        assertEquals(NavLayout.SPLIT, navLayoutFor(widthDp = 832, heightDp = 750))
        assertEquals(NavLayout.RAIL, navLayoutFor(widthDp = 750, heightDp = 832))
        // Galaxy Z Fold8 (Android 17): 933 × 704 in its natural landscape orientation, 704 × 933 upright.
        assertEquals(NavLayout.SPLIT, navLayoutFor(widthDp = 933, heightDp = 704))
        assertEquals(NavLayout.RAIL, navLayoutFor(widthDp = 704, heightDp = 933))
    }

    @Test
    fun theSplitStartsWhereBothPanesGetASmallPhonesWidth() {
        // The 84dp rail and two 360dp panes; at the threshold the list keeps 360dp and the detail pane the rest.
        assertEquals(SPLIT_MIN_WIDTH_DP, RailWidth.value.toInt() + 2 * LIST_PANE_MIN_DP)
        assertEquals(LIST_PANE_MIN_DP, listPaneWidthDp(SPLIT_MIN_WIDTH_DP - RailWidth.value.toInt()))
    }

    @Test
    fun breakpointsAreInclusiveAtTheirLowerEdge() {
        assertEquals(NavLayout.DOCK, navLayoutFor(widthDp = 599, heightDp = 900))
        assertEquals(NavLayout.RAIL, navLayoutFor(widthDp = 600, heightDp = 480))
        assertEquals(NavLayout.DOCK, navLayoutFor(widthDp = 900, heightDp = 479))
        assertEquals(NavLayout.SPLIT, navLayoutFor(widthDp = 804, heightDp = 480))
        assertEquals(NavLayout.SPLIT, navLayoutFor(widthDp = 840, heightDp = 480))
    }

    @Test
    fun theListPaneTakesHalfWithinItsBounds() {
        // The unfolded Fold (851dp) beside the 84dp rail: two equal panes.
        assertEquals(383, listPaneWidthDp(767))
        // Just enough room: the list keeps a small phone's width and so does the detail pane.
        assertEquals(LIST_PANE_MIN_DP, listPaneWidthDp(720))
        // Not enough for two 360dp panes (side insets): equal halves, so the detail pane isn't the one squeezed.
        assertEquals(350, listPaneWidthDp(700))
        // Big tablets don't stretch the list past a readable column.
        assertEquals(LIST_PANE_MAX_DP, listPaneWidthDp(1200))
    }

    @Test
    fun everyTabHasADefaultPane() {
        // Owner, 2026-11-26: Insights beside Home.
        assertEquals(DefaultPane.INSIGHTS, defaultPaneFor("home", insightsOn = true, scheduledOn = true))
        assertEquals(DefaultPane.NEWEST_TRANSACTION, defaultPaneFor("transactions", insightsOn = true, scheduledOn = true))
        assertEquals(DefaultPane.DEFAULT_WALLET, defaultPaneFor("wallets", insightsOn = true, scheduledOn = true))
        assertEquals(DefaultPane.SCHEDULED, defaultPaneFor("budget", insightsOn = true, scheduledOn = true))
    }

    @Test
    fun aSwitchedOffModuleFallsBackRatherThanShowingATurnedOffPage() {
        assertEquals(DefaultPane.SCHEDULED, defaultPaneFor("home", insightsOn = false, scheduledOn = true))
        assertEquals(DefaultPane.INSIGHTS, defaultPaneFor("budget", insightsOn = true, scheduledOn = false))
        assertEquals(DefaultPane.NOTHING_OPEN, defaultPaneFor("home", insightsOn = false, scheduledOn = false))
        assertEquals(DefaultPane.NOTHING_OPEN, defaultPaneFor("budget", insightsOn = false, scheduledOn = false))
        // Lists always have their own default (or say there's nothing in them yet).
        assertEquals(DefaultPane.NEWEST_TRANSACTION, defaultPaneFor("transactions", insightsOn = false, scheduledOn = false))
        assertEquals(DefaultPane.DEFAULT_WALLET, defaultPaneFor("wallets", insightsOn = false, scheduledOn = false))
    }
}
