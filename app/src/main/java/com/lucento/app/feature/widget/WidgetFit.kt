package com.lucento.app.feature.widget

import kotlin.math.ceil

/*
 * Fitting text without measuring it (docs/WIDGETS.md). A widget is drawn by the home screen from RemoteViews, so
 * Glance can't measure text: the Safe to spend widget plans its heights from line boxes measured on a device and
 * generous character widths, then draws every text with exactly the lines planned for it. Nothing runs past the
 * widget's edge at any size or text size: a figure shrinks first, then what doesn't fit is left out, least important
 * first.
 */

/** A line of widget text, per em of its size: Roboto with its font padding, measured at 1.35–1.38 on an emulator. */
const val LINE_EM = 1.4f

/** Average advance of regular and medium text per character, generous (measured ≈ 0.42 em). */
const val TEXT_EM_PER_CHAR = 0.5f

/** Bold capitals (the header's label) are wider. */
const val CAPS_EM_PER_CHAR = 0.7f

/** Roboto's bold figures are ~0.52 em; separators are narrower and a currency sign wider, so this is a safe average. */
const val AMOUNT_EM_PER_CHAR = 0.62f

/** The widget's padding: 16 dp at each side, 14 at the top and the bottom. */
const val WIDGET_PAD_H_DP = 32f
const val WIDGET_PAD_V_DP = 28f

/** Lucento's touch target, and the height of every widget button. */
const val WIDGET_TARGET_DP = 48f

/** The height of one line of [sp] text at [scale] (the phone's text size). */
fun lineDp(sp: Float, scale: Float): Float = sp * scale * LINE_EM

/** The width [text] takes on one line. */
fun textWidthDp(text: String, sp: Float, scale: Float, emPerChar: Float = TEXT_EM_PER_CHAR): Float =
    text.length * emPerChar * sp * scale

/** The lines [text] wraps to in [widthDp]. */
fun linesFor(text: String, sp: Float, scale: Float, widthDp: Float): Int =
    if (widthDp <= 0f) Int.MAX_VALUE else ceil(textWidthDp(text, sp, scale) / widthDp).toInt().coerceAtLeast(1)

/**
 * The text size (sp) that keeps [text] on one line in [availableDp]: never above [maxSp], and never below [minSp] —
 * at the floor the size wins over the fit, so an amount is never shrunk into illegibility.
 */
fun fitTextSp(text: String, availableDp: Float, fontScale: Float, maxSp: Float, minSp: Float): Float {
    if (text.isEmpty() || availableDp <= 0f) return maxSp
    val fits = availableDp / (text.length * AMOUNT_EM_PER_CHAR * fontScale.coerceAtLeast(0.5f))
    return fits.coerceIn(minSp, maxSp)
}

/** A text a layout may show: its words, its size and the most lines it may wrap to. */
data class WidgetLine(val text: String, val sp: Float, val maxLines: Int = 2)

/** Budget's footer: the caption under the bar ("72% spent") and the verdict chip beside it. */
data class WidgetFooter(val caption: String, val chip: String)

/** "+ Add" with its word: padding, the icon, the gap and "Add" (whose three letters are wide). */
fun addButtonWidthDp(scale: Float): Float = 14f + 20f + 6f + textWidthDp("Add", 15f, scale, emPerChar = 0.65f) + 18f

/** The header row: the crest, the label in capitals and, while it fits, the month. */
fun headerDp(scale: Float): Float = maxOf(18f, lineDp(12f, scale))

/** Whether the header has room for the month beside the label. */
fun headerFitsMonth(contentWidthDp: Float, scale: Float, label: String, month: String): Boolean =
    18f + 8f + textWidthDp(label, 11f, scale, CAPS_EM_PER_CHAR) + 8f + textWidthDp(month, 12f, scale) <= contentWidthDp

/**
 * How a figure state — Budget, Net flow, App lock — lays out at this size and text size.
 *
 * Full: the header, the figure beside "+ Add", text under it, then Budget's footer (the bar, its caption and the
 * verdict). Compact: the label, the figure across the width, and one status beside a round Add.
 */
data class FigureLayout(
    val compact: Boolean,
    /** As large as fits, and never below [MIN_FIGURE_DP] on screen. */
    val figureSp: Float,
    /** Full: the header. Compact: the crest and label. */
    val showLabel: Boolean,
    /** Compact: the lines the label gets beside the crest. */
    val labelLines: Int = 1,
    /** Full: whether the header has room for the month. */
    val showMonth: Boolean = false,
    /** Full: the lines the essential text under the figure gets (Budget's reason when nothing is left). */
    val essentialLines: Int = 0,
    /** Full: the lines each extra text gets (0 = left out), in the order offered. */
    val extraLines: List<Int> = emptyList(),
    /** Full: the lines the footer's caption gets. */
    val captionLines: Int = 0,
    /** Compact: the lines the status beside Add gets (0 = left out). */
    val statusLines: Int = 0,
) {
    fun extra(index: Int): Int = extraLines.getOrElse(index) { 0 }

    companion object {
        const val MAX_FIGURE_SP = 32f

        /** The full card keeps its figure at 20 sp or more beside "+ Add"; otherwise the compact card gives it the width. */
        const val FULL_MIN_FIGURE_SP = 20f

        /** The smallest a figure is ever drawn, in dp on screen. */
        const val MIN_FIGURE_DP = 16f
        const val COMPACT_BELOW_WIDTH_DP = 240f

        /**
         * @param essential the text under the figure the full card can't do without (Budget's reason); if it can't
         *   have every line it needs, the card is compact, where it is the status.
         * @param extras optional text under the figure, most important first; each is shown whole or left out.
         * @param status the compact card's text beside Add.
         */
        fun choose(
            widthDp: Float,
            heightDp: Float,
            scale: Float,
            label: String,
            month: String?,
            figure: String,
            essential: WidgetLine? = null,
            extras: List<WidgetLine> = emptyList(),
            footer: WidgetFooter? = null,
            status: WidgetLine? = null,
        ): FigureLayout = full(widthDp, heightDp, scale, label, month, figure, essential, extras, footer)
            ?: compact(widthDp, heightDp, scale, label, figure, status)

        private fun full(
            widthDp: Float,
            heightDp: Float,
            scale: Float,
            label: String,
            month: String?,
            figure: String,
            essential: WidgetLine?,
            extras: List<WidgetLine>,
            footer: WidgetFooter?,
        ): FigureLayout? {
            if (widthDp < COMPACT_BELOW_WIDTH_DP) return null
            val width = widthDp - WIDGET_PAD_H_DP
            if (18f + 8f + textWidthDp(label, 11f, scale, CAPS_EM_PER_CHAR) > width) return null
            val beside = width - addButtonWidthDp(scale) - 12f
            if (figure.length * AMOUNT_EM_PER_CHAR * FULL_MIN_FIGURE_SP * scale > beside) return null
            val figureSp = fitTextSp(figure, beside, scale, MAX_FIGURE_SP, FULL_MIN_FIGURE_SP)
            var room = heightDp - WIDGET_PAD_V_DP - headerDp(scale) - 6f - maxOf(WIDGET_TARGET_DP, lineDp(figureSp, scale))
            var captionLines = 0
            if (footer != null) {
                val chip = textWidthDp(footer.chip, 12f, scale) + 16f
                captionLines = linesFor(footer.caption, 12f, scale, width - chip - 8f)
                if (captionLines > 2) return null
                // The bar, the gap under it, then the caption beside the chip.
                room -= 12f + maxOf(lineDp(12f, scale) + 4f, captionLines * lineDp(12f, scale))
            }
            var essentialLines = 0
            if (essential != null) {
                essentialLines = linesFor(essential.text, essential.sp, scale, width)
                if (essentialLines > essential.maxLines) return null
                room -= essentialLines * lineDp(essential.sp, scale)
            }
            if (room < 0f) return null
            val extraLines = extras.map { line ->
                val lines = linesFor(line.text, line.sp, scale, width)
                val need = lines * lineDp(line.sp, scale)
                if (lines <= line.maxLines && need <= room) {
                    room -= need
                    lines
                } else {
                    0
                }
            }
            return FigureLayout(
                compact = false,
                figureSp = figureSp,
                showLabel = true,
                showMonth = month != null && headerFitsMonth(width, scale, label, month),
                essentialLines = essentialLines,
                extraLines = extraLines,
                captionLines = captionLines,
            )
        }

        private fun compact(
            widthDp: Float,
            heightDp: Float,
            scale: Float,
            label: String,
            figure: String,
            status: WidgetLine?,
        ): FigureLayout {
            val width = widthDp - WIDGET_PAD_H_DP
            val height = heightDp - WIDGET_PAD_V_DP
            val floorSp = MIN_FIGURE_DP / scale
            val widest = fitTextSp(figure, width, scale, MAX_FIGURE_SP, floorSp)
            // The label wraps beside the crest rather than being cut; past two lines it is left out.
            val labelLines = linesFor(label, 12f, scale, width - 28f)
            val labelDp = if (labelLines <= 2) maxOf(20f, labelLines * lineDp(12f, scale)) else Float.POSITIVE_INFINITY
            val statusSp = status?.sp ?: 12f
            val statusNeed = status?.let { line ->
                linesFor(line.text, line.sp, scale, width - 8f - WIDGET_TARGET_DP).takeIf { it <= line.maxLines }
            } ?: 0
            fun row(lines: Int) = maxOf(WIDGET_TARGET_DP, lines * lineDp(statusSp, scale))
            // The figure shrinks first; then the status goes, then the label (the status comes back if that's enough).
            for ((withLabel, lines) in listOf(true to statusNeed, true to 0, false to statusNeed, false to 0)) {
                val left = height - (if (withLabel) labelDp else 0f) - row(lines)
                val sp = minOf(widest, left / (scale * LINE_EM))
                if (sp >= floorSp) {
                    return FigureLayout(compact = true, figureSp = sp, showLabel = withLabel, labelLines = labelLines, statusLines = lines)
                }
            }
            return FigureLayout(compact = true, figureSp = floorSp, showLabel = false)
        }
    }
}

/**
 * A message state — no budget yet, choose categories, finish setting up: a label, the message, an optional detail
 * and one action, with Add beside it where the state has one. The action keeps its words on one line while they fit
 * and otherwise takes the row and wraps to two. When room runs out the detail goes first, then the label, then the
 * message's lines; at the very smallest only the action is left, which always fits.
 */
data class MessageLayout(
    val showLabel: Boolean,
    val showMonth: Boolean,
    /** 0: left out. */
    val messageLines: Int,
    val detailLines: Int,
    /** 1: the action at its own width beside Add (cut short only at the very smallest). 2: it takes the row and wraps. */
    val actionLines: Int,
    /** Whether Add has room for its word beside the action; otherwise it is the round icon. */
    val addWithWord: Boolean,
) {
    companion object {
        /**
         * @param labelDp the label's height with the gap under it: the header, or the crest when setting up.
         * @param month the month for the header, shown while it fits; null for none.
         */
        fun choose(
            widthDp: Float,
            heightDp: Float,
            scale: Float,
            labelDp: Float,
            label: String,
            month: String?,
            message: WidgetLine,
            detail: WidgetLine?,
            action: String,
            withAdd: Boolean,
        ): MessageLayout {
            val width = widthDp - WIDGET_PAD_H_DP
            val height = heightDp - WIDGET_PAD_V_DP
            val actionWidth = textWidthDp(action, 15f, scale) + 32f
            val addWithWord = withAdd && actionWidth + 8f + addButtonWidthDp(scale) <= width
            val addWidth = when {
                !withAdd -> 0f
                addWithWord -> 8f + addButtonWidthDp(scale)
                else -> 8f + WIDGET_TARGET_DP
            }
            val actionRoom = width - addWidth
            val actionNeed = if (actionWidth <= actionRoom) 1 else linesFor(action, 15f, scale, actionRoom - 32f).coerceAtMost(2)
            val messageNeed = linesFor(message.text, message.sp, scale, width).coerceAtMost(message.maxLines)
            val detailNeed = detail?.let { line -> linesFor(line.text, line.sp, scale, width).takeIf { it <= line.maxLines } } ?: 0
            val detailSp = detail?.sp ?: 13f
            // Most to least: everything; without the detail; without the label; the message shorter; the action alone.
            val candidates = listOf(Candidate(true, messageNeed, detailNeed, actionNeed), Candidate(true, messageNeed, 0, actionNeed)) +
                (messageNeed downTo 1).map { Candidate(false, it, 0, actionNeed) } +
                listOf(Candidate(false, 0, 0, actionNeed), Candidate(false, 0, 0, 1))
            val chosen = candidates.firstOrNull { candidate ->
                (if (candidate.label) labelDp else 0f) + candidate.message * lineDp(message.sp, scale) +
                    candidate.detail * lineDp(detailSp, scale) + actionRowDp(candidate.action, scale) <= height
            } ?: candidates.last()
            return MessageLayout(
                showLabel = chosen.label,
                showMonth = month != null && headerFitsMonth(width, scale, label, month),
                messageLines = chosen.message,
                detailLines = chosen.detail,
                actionLines = chosen.action,
                addWithWord = addWithWord,
            )
        }

        /** The action row's height: a 48 dp target, taller when its words wrap. */
        fun actionRowDp(lines: Int, scale: Float): Float = maxOf(WIDGET_TARGET_DP, lines * lineDp(15f, scale) + 8f)

        private data class Candidate(val label: Boolean, val message: Int, val detail: Int, val action: Int)
    }
}
