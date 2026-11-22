package com.lucento.app.domain.capture

import java.time.LocalDate

/**
 * Page geometry from the positions on-device OCR returns with each line: a photo turned sideways or upside down is put
 * upright, and receipts photographed together are told apart (docs/SCAN.md "Capture"). Pure functions on
 * [TextFragment]s, so they are tested on the JVM.
 */
object DocumentLayout {

    /**
     * Fragments turned upright when the page was photographed sideways or upside down: most lines' angles (ML Kit's
     * `Text.Line.angle`, clockwise) say by how many quarter turns, and positions are turned back by that much so rows
     * are rebuilt left to right, top to bottom. A slight tilt is left for [ReceiptParser.layoutRows] to straighten.
     */
    fun upright(fragments: List<TextFragment>): List<TextFragment> {
        val votes = fragments.filter { it.text.isNotBlank() && it.angle.isFinite() }
            .groupingBy { Math.floorMod(Math.round(it.angle / 90f), 4) }.eachCount()
        val quarter = votes.maxByOrNull { it.value }?.key ?: return fragments
        if (quarter == 0 || votes.getValue(quarter) * 2 <= votes.values.sum()) return fragments
        return fragments.map { f ->
            when (quarter) {
                // Text running down the page (turned clockwise): (x, y) → (y, −x).
                1 -> TextFragment(f.text, left = f.top, top = -f.right, right = f.bottom, bottom = -f.left, angle = f.angle - 90f)
                // Upside down: (x, y) → (−x, −y).
                2 -> TextFragment(f.text, left = -f.right, top = -f.bottom, right = -f.left, bottom = -f.top, angle = f.angle - 180f)
                // Text running up the page (turned anticlockwise): (x, y) → (−y, x).
                else -> TextFragment(f.text, left = -f.bottom, top = f.left, right = -f.top, bottom = f.right, angle = f.angle + 90f)
            }
        }
    }

    /**
     * Separate receipts photographed together: a clear vertical gutter between two blocks of text (side by side), or a
     * wide gap between two blocks (one above the other), where each block reads on its own as a receipt with a labelled
     * total. A single receipt's item column and price column never split, because neither half has a total of its own.
     * At most [MAX_DOCUMENTS] receipts, left to right then top to bottom.
     */
    fun splitReceipts(fragments: List<TextFragment>, today: LocalDate): List<List<TextFragment>> {
        val visible = fragments.filter { it.text.isNotBlank() }
        if (visible.size < MIN_FRAGMENTS * 2) return listOf(fragments)
        return split(visible, today, depth = 0).take(MAX_DOCUMENTS)
    }

    private const val MIN_FRAGMENTS = 5
    const val MAX_DOCUMENTS = 6

    private fun split(fragments: List<TextFragment>, today: LocalDate, depth: Int): List<List<TextFragment>> {
        if (depth >= 3 || fragments.size < MIN_FRAGMENTS * 2) return listOf(fragments)
        val sideBySide = bestSplit(fragments, today, vertical = true)
        val stacked = if (sideBySide == null) bestSplit(fragments, today, vertical = false) else null
        val (a, b) = sideBySide ?: stacked ?: return listOf(fragments)
        return split(a, today, depth + 1) + split(b, today, depth + 1)
    }

    /** The widest gap that leaves a complete receipt on each side, or null. [vertical]: a gutter between columns. */
    private fun bestSplit(fragments: List<TextFragment>, today: LocalDate, vertical: Boolean): Pair<List<TextFragment>, List<TextFragment>>? {
        val heights = fragments.map { it.height }.sorted()
        val lineHeight = heights[heights.size / 2].coerceAtLeast(1)
        fun start(f: TextFragment) = if (vertical) f.left else f.top
        fun end(f: TextFragment) = if (vertical) f.right else f.bottom
        val minGap = if (vertical) lineHeight * 2 else lineHeight * 3
        // Gaps in the coverage of all fragments along the axis.
        val sorted = fragments.sortedBy { start(it) }
        val gaps = ArrayList<Pair<Int, Int>>() // (gap start, gap end)
        var reach = end(sorted.first())
        sorted.drop(1).forEach { f ->
            if (start(f) - reach >= minGap) gaps += reach to start(f)
            reach = maxOf(reach, end(f))
        }
        return gaps.sortedByDescending { it.second - it.first }.firstNotNullOfOrNull { (from, to) ->
            val first = fragments.filter { end(it) <= from }
            val second = fragments.filter { start(it) >= to }
            if (first.size + second.size != fragments.size) return@firstNotNullOfOrNull null
            if (isReceipt(first, today) && isReceipt(second, today)) first to second else null
        }
    }

    /** Reads on its own as a receipt: enough lines with words, and a total whose label and amount are both on this side. */
    private fun isReceipt(fragments: List<TextFragment>, today: LocalDate): Boolean {
        if (fragments.size < MIN_FRAGMENTS) return false
        if (fragments.count { f -> f.text.count { it.isLetter() } >= 3 } * 10 < fragments.size * 3) return false
        val lines = ReceiptParser.mergeRows(fragments)
        if (lines.size < 4 || StatementParser.classify(lines) != ScanKind.RECEIPT) return false
        return ReceiptParser.parse(lines, today).totalConfidence == TotalConfidence.LABELLED
    }
}
