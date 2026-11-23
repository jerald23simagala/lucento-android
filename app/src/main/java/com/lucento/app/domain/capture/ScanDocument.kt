package com.lucento.app.domain.capture

import java.time.LocalDate
import java.util.Locale

/** Where a page's text came from. */
enum class PageOrigin {
    /** A camera photo or a picked image, read by on-device OCR. */
    PHOTO,
    /** A page from ML Kit's Document Scanner: flattened, straightened and cleaned before OCR. */
    SCANNER,
    /** A PDF page's own text layer: exact text, nothing guessed. */
    PDF_TEXT,
    /** A PDF page with no text layer (a scanned PDF), rendered on the phone and read by OCR. */
    PDF_IMAGE,
}

/**
 * One page of a document. [fragments] are OCR's lines with their positions (empty for text without positions); [rows]
 * the page's printed lines rebuilt from them ([ReceiptParser.layoutRows]) or taken from a PDF's text.
 */
data class ScanPage(val rows: List<LayoutRow>, val origin: PageOrigin, val fragments: List<TextFragment> = emptyList()) {
    val lines: List<String> get() = rows.map { it.text }

    /** Photographed pages may overlap (the person moved the phone down a long receipt); a PDF's pages never do. */
    val mayOverlap: Boolean get() = origin == PageOrigin.PHOTO || origin == PageOrigin.SCANNER

    companion object {
        fun ofLines(lines: List<String>, origin: PageOrigin = PageOrigin.PHOTO) =
            ScanPage(lines.map { it.trim() }.filter { it.isNotEmpty() }.map { LayoutRow(it) }, origin)

        /** A page read by OCR: put upright, rows rebuilt from the positions. */
        fun ofFragments(fragments: List<TextFragment>, origin: PageOrigin): ScanPage {
            val upright = DocumentLayout.upright(fragments)
            return ScanPage(ReceiptParser.layoutRows(upright), origin, upright)
        }
    }
}

/** Everything scanned or imported at once: Document Scanner pages, several photos, or a PDF's pages. */
data class ScanDocument(val pages: List<ScanPage>) {
    companion object {
        fun ofLines(lines: List<String>) = ScanDocument(listOf(ScanPage.ofLines(lines)))
    }
}

/**
 * Pages → the documents they make up (docs/SCAN.md "Several pages"):
 * - receipts photographed together on one page are split ([DocumentLayout.splitReceipts]);
 * - a statement's consecutive pages become one statement; where photographed pages overlap, the repeated lines are
 *   dropped once (a PDF's pages never overlap and are never trimmed);
 * - a receipt without a total that continues on the next page (a long receipt in two photos) is joined to it; every
 *   complete receipt stays its own document, so five receipts scanned in one go are five payments.
 */
object DocumentAssembler {

    data class Part(
        val rows: List<LayoutRow>,
        val kind: ScanKind,
        /** How many pages it came from. */
        val pages: Int,
        /** Lines dropped because overlapping photos showed them twice. */
        val overlapRemoved: Int = 0,
    ) {
        val lines: List<String> get() = rows.map { it.text }
    }

    private class Unit(val rows: List<LayoutRow>, val origin: PageOrigin, val kind: ScanKind)

    fun assemble(document: ScanDocument, today: LocalDate): List<Part> {
        val units = document.pages.flatMap { page ->
            val kind = StatementParser.classify(page.lines)
            val pieces = if (kind == ScanKind.RECEIPT && page.fragments.isNotEmpty()) DocumentLayout.splitReceipts(page.fragments, today) else listOf(page.fragments)
            if (pieces.size <= 1) {
                listOf(Unit(page.rows, page.origin, kind))
            } else {
                pieces.map { piece -> ReceiptParser.layoutRows(piece).let { rows -> Unit(rows, page.origin, StatementParser.classify(rows.map { it.text })) } }
            }
        }.filter { it.rows.isNotEmpty() }

        val parts = ArrayList<Part>()
        var i = 0
        while (i < units.size) {
            val unit = units[i]
            var rows = unit.rows
            var pages = 1
            var removed = 0
            var j = i + 1
            // A statement's summary page (balances, minimum due, limits) heads the statement pages that follow it.
            val summaryFirst = unit.kind == ScanKind.RECEIPT && units.getOrNull(i + 1)?.kind == ScanKind.STATEMENT &&
                StatementParser.looksLikeSummaryPage(unit.rows.map { it.text })
            val kind = if (summaryFirst) ScanKind.STATEMENT else unit.kind
            if (kind == ScanKind.STATEMENT) {
                while (j < units.size && (units[j].kind == ScanKind.STATEMENT || continuesStatement(units[j], today))) {
                    val (joined, dropped) = join(rows, units[j].rows, units[j].origin != PageOrigin.PDF_TEXT)
                    rows = joined
                    removed += dropped
                    pages++
                    j++
                }
            } else {
                while (j < units.size && units[j].kind == ScanKind.RECEIPT && !complete(rows, today)) {
                    val (joined, dropped) = join(rows, units[j].rows, units[j].origin != PageOrigin.PDF_TEXT)
                    rows = joined
                    removed += dropped
                    pages++
                    j++
                }
            }
            parts += Part(rows, kind, pages, removed)
            i = j
        }
        return parts
    }

    /** A statement's last page can hold a single line and the closing balance: it reads like a receipt on its own. */
    private fun continuesStatement(unit: Unit, today: LocalDate): Boolean {
        val lines = unit.rows.map { it.text }
        val reading = StatementParser.parse(lines, today)
        return reading.lines.any { it.date != null } || reading.summary.closing != null
    }

    private fun complete(rows: List<LayoutRow>, today: LocalDate): Boolean =
        ReceiptParser.parse(rows.map { it.text }, today).totalConfidence == TotalConfidence.LABELLED

    /**
     * [next] appended to [rows]. With [mayOverlap], the longest run of lines that ends [rows] and starts [next] (skipping
     * a few header lines at the top of [next] and footer lines at the bottom of [rows]) is kept once: at least two lines,
     * or one line carrying a running balance, so two genuinely equal purchases are never merged on a guess.
     */
    internal fun join(rows: List<LayoutRow>, next: List<LayoutRow>, mayOverlap: Boolean): Pair<List<LayoutRow>, Int> {
        if (!mayOverlap || rows.isEmpty() || next.isEmpty()) return (rows + next) to 0
        val a = rows.map { Line(it.text) }
        val b = next.map { Line(it.text) }
        var best = 0
        var bestLead = 0
        for (trail in 0..minOf(4, a.size - 1)) {
            for (lead in 0..minOf(6, b.size - 1)) {
                val aEnd = a.size - trail
                val max = minOf(12, aEnd, b.size - lead)
                for (k in max downTo 1) {
                    if (k <= best) break
                    if ((0 until k).all { t -> a[aEnd - k + t].sameAs(b[lead + t]) }) {
                        val block = rows.subList(aEnd - k, aEnd)
                        val money = block.count { ReceiptParser.amountsIn(it.text, 2).isNotEmpty() }
                        val safe = money >= 1 && (k >= 2 || ReceiptParser.amountsIn(block.single().text, 2).size >= 2)
                        if (safe) {
                            best = k
                            bestLead = lead
                        }
                        break
                    }
                }
            }
        }
        if (best == 0) return (rows + next) to 0
        // Keep the earlier page's copy (and its footer lines), drop the later page's header lines and repeated run.
        val kept = rows + next.drop(bestLead + best)
        return kept to best
    }

    /**
     * A line compared across photos, where OCR reads the same print slightly differently each time: the same amounts,
     * and words that match once case, spacing, stray marks and look-alike characters (O/0, l/1, S/5) are set aside —
     * allowing one or two misread letters on a long line.
     */
    private class Line(text: String) {
        val amounts: List<java.math.BigDecimal> = ReceiptParser.amountsIn(text, 2)
        val key: String = text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
            .replace('o', '0').replace('l', '1').replace('i', '1').replace('s', '5').replace('b', '8')

        fun sameAs(other: Line): Boolean {
            if (key == other.key) return true
            if (amounts.isEmpty() || amounts != other.amounts) return false
            val longest = maxOf(key.length, other.key.length)
            return longest >= 12 && editDistance(key, other.key) <= (if (longest >= 24) 2 else 1)
        }
    }

    private fun editDistance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            previous = current
        }
        return previous[b.length]
    }
}
