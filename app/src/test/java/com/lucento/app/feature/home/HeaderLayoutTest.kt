package com.lucento.app.feature.home

import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.Spacing
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA R6-09: at 360dp the avatar wrapped onto a second header line. The row now always keeps the mark in view. */
class HeaderLayoutTest {
    private val row360 = 360.dp - Spacing.gutter * 2

    @Test fun mark_and_gap_fit_beside_chip_bell_and_avatar_at_360dp() {
        val room = HeaderLayout.brandRoom(row360, hasBell = true, gap = Spacing.sm)
        assertTrue("brand room $room", room >= HeaderLayout.MARK + Spacing.sm)
    }

    @Test fun without_the_bell_the_wordmark_gets_more_room() {
        assertTrue(HeaderLayout.brandRoom(row360, hasBell = false, gap = Spacing.sm) > HeaderLayout.brandRoom(row360, hasBell = true, gap = Spacing.sm))
    }

    @Test fun chip_cap_leaves_the_avatar_its_full_touch_target() {
        assertTrue(HeaderLayout.ACTION >= 48.dp)
        assertTrue(HeaderLayout.CHIP_MAX < row360 / 2)
    }
}
