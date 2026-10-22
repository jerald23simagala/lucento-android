package com.lucento.app.domain.capture

import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.util.Locale

/** How the suggested total was found, so the review screen can say how much to trust it. */
enum class TotalConfidence {
    /** On a line labelled as the total (e.g. "TOTAL", "AMOUNT DUE"). */
    LABELLED,
    /** No labelled total; the largest amount on the receipt was used. Needs checking. */
    LARGEST_AMOUNT,
    NONE,
}

/** A piece of recognised text and where it sits on the image (pixels). */
data class TextFragment(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/** Suggestions read from receipt text. All of them are editable and none is saved without review. */
data class ReceiptSuggestion(
    val merchant: String?,
    val total: BigDecimal?,
    val totalConfidence: TotalConfidence,
    val date: LocalDate?,
    /** True when the date's day/month order could not be determined and the reading may be wrong. */
    val dateAmbiguous: Boolean,
    /** Every money amount seen, largest first, so the user can pick another one quickly. */
    val amounts: List<BigDecimal>,
)

/**
 * Heuristic, offline extraction of merchant, total and date from OCR'd receipt lines. Deliberately
 * conservative: a missing value is better than a confident wrong one.
 */
object ReceiptParser {

    /**
     * Receipts are laid out in columns ("TOTAL" on the left, "274.40" on the right) and OCR returns them
     * as separate fragments. This rebuilds visual rows: fragments whose vertical centres fall within
     * half a line of each other are joined left-to-right, rows ordered top-to-bottom.
     */
    fun mergeRows(fragments: List<TextFragment>): List<String> {
        val rows = ArrayList<MutableList<TextFragment>>()
        fragments.filter { it.text.isNotBlank() }.sortedBy { it.centerY }.forEach { f ->
            val row = rows.lastOrNull()
            val anchor = row?.let { r -> r.sumOf { it.centerY } / r.size }
            val tolerance = row?.let { r -> (r.maxOf { it.height }.coerceAtLeast(f.height)) / 2 } ?: 0
            if (row != null && anchor != null && kotlin.math.abs(f.centerY - anchor) <= tolerance) row.add(f) else rows.add(mutableListOf(f))
        }
        return rows.map { r -> r.sortedBy { it.left }.joinToString("  ") { it.text.trim() } }
    }

    fun parse(lines: List<String>, today: LocalDate): ReceiptSuggestion {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val amounts = clean.flatMap { amountsIn(it) }.distinct().sortedDescending()
        val (total, confidence) = findTotal(clean) ?: (amounts.firstOrNull()?.let { it to TotalConfidence.LARGEST_AMOUNT }
            ?: (null to TotalConfidence.NONE))
        val (date, ambiguous) = findDate(clean, today)
        return ReceiptSuggestion(
            merchant = findMerchant(clean),
            total = total,
            totalConfidence = confidence,
            date = date,
            dateAmbiguous = ambiguous,
            amounts = amounts.take(8),
        )
    }

    // ------------------------------------------------------------- amounts

    /** Money-looking numbers with exactly two decimals, e.g. "1,234.50", "1.234,50", "89.00". */
    private val MONEY = Regex("(?<![\\d.,])(\\d{1,3}(?:[,. ]\\d{3})*[.,]\\d{2}|\\d+[.,]\\d{2})(?![\\d])")

    internal fun amountsIn(line: String): List<BigDecimal> =
        MONEY.findAll(line).mapNotNull { m ->
            val raw = m.groupValues[1].replace(" ", "")
            QuickEntryParser.normalizeNumber(raw)?.let { runCatching { BigDecimal(it) }.getOrNull() }
        }.filter { it.signum() > 0 }.toList()

    private val totalLabels = listOf(
        Regex("grand\\s*total") to 0,
        Regex("total\\s*(amount\\s*)?due|amount\\s*due|balance\\s*due|amount\\s*payable|total\\s*payable") to 1,
        Regex("total\\s*amount|net\\s*total|total\\s*sales|\\btotal\\b") to 2,
    )
    private val notTotal = Regex("sub\\s*-?\\s*total|total\\s*(items?|qty|quantity|savings|discount|vat|tax)|\\bvat\\b|\\btax\\b|change|tender|cash\\s*paid|discount|points")

    /** The amount on (or right after) the best-ranked total line. */
    private fun findTotal(lines: List<String>): Pair<BigDecimal, TotalConfidence>? {
        var best: Triple<Int, Int, BigDecimal>? = null // rank, line index, amount
        lines.forEachIndexed { index, line ->
            val lower = line.lowercase(Locale.ROOT)
            if (notTotal.containsMatchIn(lower)) return@forEachIndexed
            val rank = totalLabels.firstOrNull { (re, _) -> re.containsMatchIn(lower) }?.second ?: return@forEachIndexed
            val amount = amountsIn(line).lastOrNull() ?: lines.getOrNull(index + 1)?.let { amountsIn(it).lastOrNull() }
                ?: return@forEachIndexed
            val current = best
            // Better rank wins; for equal rank the later line wins (totals sit near the bottom).
            if (current == null || rank < current.first || (rank == current.first && index > current.second)) {
                best = Triple(rank, index, amount)
            }
        }
        return best?.let { it.third to TotalConfidence.LABELLED }
    }

    // ------------------------------------------------------------- date

    private val ISO = Regex("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})")
    private val NUMERIC = Regex("(?<!\\d)(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})(?!\\d)")
    private val TEXTUAL_DMY = Regex("(?<!\\d)(\\d{1,2})\\s+([a-z]{3,9})\\.?,?\\s+(\\d{2,4})")
    private val TEXTUAL_MDY = Regex("([a-z]{3,9})\\.?\\s+(\\d{1,2}),?\\s+(\\d{2,4})")

    internal fun findDate(lines: List<String>, today: LocalDate): Pair<LocalDate?, Boolean> {
        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            ISO.find(lower)?.let { m ->
                date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), today)?.let { return it to false }
            }
            TEXTUAL_DMY.find(lower)?.let { m ->
                val month = month(m.groupValues[2])
                if (month != null) date(year(m.groupValues[3]), month, m.groupValues[1].toInt(), today)?.let { return it to false }
            }
            TEXTUAL_MDY.find(lower)?.let { m ->
                val month = month(m.groupValues[1])
                if (month != null) date(year(m.groupValues[3]), month, m.groupValues[2].toInt(), today)?.let { return it to false }
            }
            NUMERIC.find(lower)?.let { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                val y = year(m.groupValues[3])
                val dayFirst = date(y, b, a, today)
                val monthFirst = date(y, a, b, today)
                when {
                    dayFirst != null && monthFirst != null && dayFirst != monthFirst -> {
                        // Ambiguous (e.g. 03/04): prefer the reading closest to today, and say so.
                        val pick = listOf(dayFirst, monthFirst).minByOrNull { kotlin.math.abs(it.toEpochDay() - today.toEpochDay()) }
                        return pick to true
                    }
                    dayFirst != null -> return dayFirst to false
                    monthFirst != null -> return monthFirst to false
                }
            }
        }
        return null to false
    }

    private fun year(text: String): Int = text.toInt().let { if (it < 100) 2000 + it else it }

    private fun month(word: String): Int? {
        val w = word.take(3)
        return Month.entries.firstOrNull { it.name.lowercase(Locale.ROOT).startsWith(w) }?.value
    }

    /** A plausible receipt date: valid, not in the future, and within the last five years. */
    private fun date(year: Int, month: Int, day: Int, today: LocalDate): LocalDate? {
        val d = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        if (d.isAfter(today) || d.isBefore(today.minusYears(5))) return null
        return d
    }

    // ------------------------------------------------------------- merchant

    private val notMerchant = Regex(
        "receipt|invoice|\\btin\\b|vat\\s*reg|tel|phone|www\\.|http|@|cashier|terminal|\\bpos\\b|date|time|order|table|welcome|thank",
    )

    /** The first line near the top that reads like a name: mostly letters, not a label or address. */
    internal fun findMerchant(lines: List<String>): String? =
        lines.take(6).firstOrNull { line ->
            val lower = line.lowercase(Locale.ROOT)
            val letters = line.count { it.isLetter() }
            val digits = line.count { it.isDigit() }
            letters >= 3 && digits <= 2 && letters >= line.length / 2 && !notMerchant.containsMatchIn(lower)
        }?.let { name ->
            name.trim().take(40).split(' ').joinToString(" ") { word ->
                if (word.length > 1 && word.all { !it.isLetter() || it.isUpperCase() }) {
                    word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                } else {
                    word
                }
            }
        }
}
