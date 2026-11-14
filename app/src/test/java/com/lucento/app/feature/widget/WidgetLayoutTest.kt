package com.lucento.app.feature.widget

import com.lucento.app.feature.capture.ComposerStart
import com.lucento.app.feature.widget.SafeToSpendWidgetModel.Companion.BILLS_REASON
import com.lucento.app.feature.widget.SafeToSpendWidgetModel.Companion.BUDGETS_OFF_NOTE
import com.lucento.app.feature.widget.SafeToSpendWidgetModel.Companion.OVER_BUDGET_REASON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/** How the widgets arrange themselves, and how a tap finds its way into Lucento (docs/WIDGETS.md). */
class WidgetLayoutTest {

    @Test fun quickAddKeepsTheFullLabelOnlyWhenItFits() {
        assertEquals(QuickAddLayout.FULL, QuickAddLayout.choose(widthDp = 358f, fontScale = 1f))
        // A 360 dp phone's 4×1 cell, and text from 115 %: the short label.
        assertEquals(QuickAddLayout.SHORT, QuickAddLayout.choose(widthDp = 328f, fontScale = 1f))
        assertEquals(QuickAddLayout.SHORT, QuickAddLayout.choose(widthDp = 358f, fontScale = 1.15f))
        // From 150 %, and in a 2×1 cell: one Add.
        assertEquals(QuickAddLayout.SINGLE, QuickAddLayout.choose(widthDp = 358f, fontScale = 1.5f))
        assertEquals(QuickAddLayout.SINGLE, QuickAddLayout.choose(widthDp = 172f, fontScale = 1f))
    }

    @Test fun theFullCardWhileItFitsAndTheCompactOneOtherwise() {
        val full = budget(widthDp = 358f, heightDp = 196f, scale = 1f)
        assertFalse(full.compact)
        assertEquals(listOf(1, 1), full.extraLines)
        assertTrue(full.showMonth)
        // A narrow cell: compact.
        assertTrue(budget(widthDp = 200f, heightDp = 196f, scale = 1f).compact)
        // At 200 % the full card stays while what it must show fits whole, leaving out the lines that don't…
        val large = budget(widthDp = 358f, heightDp = 196f, scale = 2f)
        assertFalse(large.compact)
        assertEquals(listOf(0, 0), large.extraLines)
        // …and once the figure no longer fits beside "+ Add", the compact card gives it the width.
        assertTrue(budget(widthDp = 308f, heightDp = 196f, scale = 2f).compact)
        // 200 % in a tall cell: the full card, keeping only what fits whole (the per-day line would wrap).
        val tall = budget(widthDp = 358f, heightDp = 300f, scale = 2f)
        assertFalse(tall.compact)
        assertEquals(listOf(1, 0), tall.extraLines)
    }

    @Test fun whenTheCellIsShortThePerDayLineGoesFirstAndTheBillsLineStays() {
        val tight = budget(widthDp = 358f, heightDp = 160f, scale = 1f)
        assertFalse(tight.compact)
        assertEquals(listOf(1, 0), tight.extraLines)
        val tighter = budget(widthDp = 358f, heightDp = 145f, scale = 1f)
        assertFalse(tighter.compact)
        assertEquals(listOf(0, 0), tighter.extraLines)
    }

    @Test fun theCompactCardShrinksItsFigureThenLeavesOutTheStatusThenTheLabel() {
        // This emulator's 4×2 cell at 200 %: the label, a smaller figure, and both lines of the reason.
        val roomy = nothingLeft(widthDp = 308f, heightDp = 196f, scale = 2f)
        assertTrue(roomy.compact)
        assertTrue(roomy.showLabel)
        assertEquals(2, roomy.statusLines)
        assertTrue(roomy.figureSp < FigureLayout.MAX_FIGURE_SP)
        // The smallest cell at 200 %: the figure and Add, nothing cut.
        val smallest = nothingLeft(widthDp = 308f, heightDp = 110f, scale = 2f)
        assertTrue(smallest.compact)
        assertFalse(smallest.showLabel)
        assertEquals(0, smallest.statusLines)
        assertTrue(smallest.figureSp * 2f >= FigureLayout.MIN_FIGURE_DP)
    }

    @Test fun noFigureCardIsEverPlannedTallerThanItsCell() {
        val detail = "In \$3,000.00 · Out \$1,750.00"
        val cards = listOf(
            Card(
                "Safe to spend", "\$458.00", null,
                listOf(WidgetLine("After \$20.00 bills due", 13f, 1), WidgetLine("\$38.16 a day · 12 days left", 13f, 1)),
                WidgetFooter("72% spent", "On track"), "On track",
            ),
            Card("Safe to spend", "\$0.00", WidgetLine(BILLS_REASON, 13f), emptyList(), WidgetFooter("99% spent", "Ahead of pace"), BILLS_REASON),
            Card("Overspent", "₱1,234,567.89", WidgetLine(OVER_BUDGET_REASON, 13f), emptyList(), WidgetFooter("112% spent", "Over budget"), OVER_BUDGET_REASON),
            Card("Net flow", "+\$1,250.00", WidgetLine(detail, 13f), listOf(WidgetLine(BUDGETS_OFF_NOTE, 12f)), null, detail),
            Card(
                "Safe to spend", "₱••••", WidgetLine("Hidden while App lock is on", 13f),
                listOf(WidgetLine("Open Lucento and unlock to see your month", 12f)), null, "Hidden while App lock is on",
            ),
        )
        for (card in cards) for (width in WIDTHS) for (height in HEIGHTS) for (scale in SCALES) {
            val layout = FigureLayout.choose(
                width, height, scale, card.label, "September", card.figure, card.essential, card.extras, card.footer,
                WidgetLine(card.status, 12f),
            )
            val planned = plannedHeight(layout, scale, card)
            val there = height - WIDGET_PAD_V_DP
            assertTrue("${card.figure} at $width×$height, ${scale}x: $planned dp planned, $there dp there", planned <= there + 0.01f)
            assertTrue(layout.figureSp * scale >= FigureLayout.MIN_FIGURE_DP - 0.01f)
            if (!layout.compact) {
                // Everything the full card shows is whole: the essential line and each extra it keeps.
                val text = width - WIDGET_PAD_H_DP
                card.essential?.let { assertEquals(linesFor(it.text, it.sp, scale, text), layout.essentialLines) }
                card.extras.forEachIndexed { i, line ->
                    if (layout.extra(i) > 0) assertEquals(linesFor(line.text, line.sp, scale, text), layout.extra(i))
                }
            }
        }
    }

    @Test fun noMessageCardIsEverPlannedTallerThanItsCell() {
        val cards = listOf(
            Triple(WidgetLine("No budget for September yet", 15f), WidgetLine("Give your categories an amount to see your pace.", 13f), "Set amounts" to true),
            Triple(WidgetLine("Choose the categories you spend on — your budget is built from them.", 13f, 4), null, "Choose categories" to false),
            Triple(WidgetLine("Finish setting up Lucento", 15f), WidgetLine("This widget fills in once your wallets are ready.", 13f), "Open Lucento" to false),
        )
        for ((message, detail, action) in cards) for (width in WIDTHS) for (height in HEIGHTS) for (scale in SCALES) {
            val labelDp = headerDp(scale) + 8f
            val layout = MessageLayout.choose(width, height, scale, labelDp, "Safe to spend", "September", message, detail, action.first, action.second)
            val planned = (if (layout.showLabel) labelDp else 0f) + layout.messageLines * lineDp(message.sp, scale) +
                layout.detailLines * lineDp(detail?.sp ?: 13f, scale) + MessageLayout.actionRowDp(layout.actionLines, scale)
            assertTrue("${action.first} at $width×$height, ${scale}x: $planned dp planned", planned <= height - WIDGET_PAD_V_DP + 0.01f)
            assertTrue(layout.actionLines in 1..2)
        }
    }

    @Test fun anActionWrapsRatherThanBeingCutWhenThereIsRoom() {
        val layout = MessageLayout.choose(
            widthDp = 308f, heightDp = 196f, scale = 2f, labelDp = headerDp(2f) + 8f, label = "Safe to spend", month = "November",
            message = WidgetLine("Choose the categories you spend on — your budget is built from them.", 13f, 4), detail = null,
            action = "Choose categories", withAdd = false,
        )
        assertEquals(2, layout.actionLines)
        assertTrue(layout.messageLines >= 1)
    }

    @Test fun amountsShrinkToFitButNeverBelowTheFloor() {
        assertEquals(32f, fitTextSp("$458.00", availableDp = 300f, fontScale = 1f, maxSp = 32f, minSp = 20f), 0.001f)
        val long = fitTextSp("₱1,234,567.89", availableDp = 192f, fontScale = 1f, maxSp = 32f, minSp = 20f)
        assertTrue(long < 32f && long >= 20f)
        assertEquals(20f, fitTextSp("₱1,234,567,890.12", availableDp = 100f, fontScale = 2f, maxSp = 32f, minSp = 20f), 0.001f)
    }

    @Test fun everyTargetRoundTripsThroughItsKey() {
        val all = ComposerStart.entries.map { WidgetLaunch.Composer(it) } +
            listOf(WidgetLaunch.Budget, WidgetLaunch.Categories, WidgetLaunch.Home)
        all.forEach { assertEquals(it, WidgetLaunch.fromKey(it.key)) }
    }

    @Test fun unknownKeysOpenLucentoAsItIs() {
        assertNull(WidgetLaunch.fromKey(null))
        assertNull(WidgetLaunch.fromKey("wallets"))
        assertNull(WidgetLaunch.fromKey("composer:DANCE"))
    }

    @Test fun aNewerTapIsNotTakenByAnOlderOne() {
        WidgetLaunches.offer(WidgetLaunch.Home)
        WidgetLaunches.offer(WidgetLaunch.Budget)
        assertFalse(WidgetLaunches.consume(WidgetLaunch.Home))
        assertTrue(WidgetLaunches.consume(WidgetLaunch.Budget))
        assertNull(WidgetLaunches.pending.value)
    }

    @Test fun theMidnightRedrawRunsJustAfterTheDateTurns() {
        val now = ZonedDateTime.of(2026, 11, 12, 23, 30, 0, 0, ZoneId.of("Asia/Manila"))
        assertEquals(Duration.ofMinutes(31), delayToNextWidgetDay(now))
    }

    private data class Card(
        val label: String,
        val figure: String,
        val essential: WidgetLine?,
        val extras: List<WidgetLine>,
        val footer: WidgetFooter?,
        val status: String,
    )

    private fun budget(widthDp: Float, heightDp: Float, scale: Float) = FigureLayout.choose(
        widthDp, heightDp, scale, label = "Safe to spend", month = "November", figure = "\$458.00",
        extras = listOf(WidgetLine("After \$20.00 bills due", 13f, 1), WidgetLine("\$38.16 a day · 12 days left", 13f, 1)),
        footer = WidgetFooter("72% spent", "On track"), status = WidgetLine("On track", 12f),
    )

    private fun nothingLeft(widthDp: Float, heightDp: Float, scale: Float) = FigureLayout.choose(
        widthDp, heightDp, scale, label = "Safe to spend", month = "November", figure = "\$0.00",
        essential = WidgetLine(BILLS_REASON, 13f), footer = WidgetFooter("99% spent", "Ahead of pace"),
        status = WidgetLine(BILLS_REASON, 12f),
    )

    /** The height the widget's rows take for [layout], row by row, as SafeToSpendWidget lays them out. */
    private fun plannedHeight(layout: FigureLayout, scale: Float, card: Card): Float = if (layout.compact) {
        (if (layout.showLabel) maxOf(20f, layout.labelLines * lineDp(12f, scale)) else 0f) + lineDp(layout.figureSp, scale) +
            maxOf(WIDGET_TARGET_DP, layout.statusLines * lineDp(12f, scale))
    } else {
        headerDp(scale) + 6f + maxOf(WIDGET_TARGET_DP, lineDp(layout.figureSp, scale)) +
            (card.essential?.let { layout.essentialLines * lineDp(it.sp, scale) } ?: 0f) +
            card.extras.indices.map { layout.extra(it) * lineDp(card.extras[it].sp, scale) }.sum() +
            (if (card.footer != null) 12f + maxOf(lineDp(12f, scale) + 4f, layout.captionLines * lineDp(12f, scale)) else 0f)
    }

    private companion object {
        val WIDTHS = listOf(180f, 240f, 276f, 308f, 358f, 412f)
        val HEIGHTS = listOf(110f, 130f, 150f, 170f, 196f, 230f, 300f)
        val SCALES = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.75f, 2f)
    }
}
