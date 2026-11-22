package com.lucento.app.domain.capture

import com.lucento.app.core.money.CurrencyRules
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

/**
 * A piece of recognised text and where it sits on the image (pixels). [angle] is the line's tilt in degrees, clockwise
 * positive (ML Kit's `Text.Line.angle`); 0 when unknown.
 */
data class TextFragment(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val angle: Float = 0f) {
    val centerX: Int get() = (left + right) / 2
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
    /**
     * Amounts that could be what was paid, so the user can pick another one quickly: the suggested total first, then
     * the rest largest first. Cash handed over, change, VAT breakdowns, discounts and points are left out (tester report
     * 2026-11-21: they crowded out the real total), unless nothing else is printed.
     */
    val amounts: List<BigDecimal>,
    /** Currency named on the receipt (symbol or ISO code), or null when none or an ambiguous one (`$`, `¥`). */
    val currencyCode: String? = null,
    /** Minor-unit digits used to read amounts (from [currencyCode], else the expected currency, else 2). */
    val fractionDigits: Int = 2,
    /** Which printed line the total came from ("Amount due", "Total", "Amount paid less change"); null when unlabelled. */
    val totalLabel: String? = null,
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
     *
     * A receipt photographed at a slight tilt makes the right-hand column drift up or down a line (at 3° across a
     * 600 px slip, about 30 px), which joined "AMOUNT DUE" to the price beside the line above (tester report
     * 2026-11-21). So the centres are first straightened by the page's tilt: the median of the lines' angles.
     */
    fun mergeRows(fragments: List<TextFragment>): List<String> {
        val visible = fragments.filter { it.text.isNotBlank() }
        val tilt = pageTilt(visible)
        fun level(f: TextFragment): Double = f.centerY - f.centerX * tilt
        val rows = ArrayList<MutableList<Pair<TextFragment, Double>>>()
        visible.map { it to level(it) }.sortedBy { it.second }.forEach { (f, y) ->
            val row = rows.lastOrNull()
            val anchor = row?.let { r -> r.sumOf { it.second } / r.size }
            val tolerance = row?.let { r -> (r.maxOf { it.first.height }.coerceAtLeast(f.height)) / 2.0 } ?: 0.0
            if (row != null && anchor != null && kotlin.math.abs(y - anchor) <= tolerance) row.add(f to y) else rows.add(mutableListOf(f to y))
        }
        return rows.map { r -> r.map { it.first }.sortedBy { it.left }.joinToString("  ") { it.text.trim() } }
    }

    /** The slope (tan of the median line angle) to straighten by; 0 when nearly level, unknown, or implausibly steep. */
    internal fun pageTilt(fragments: List<TextFragment>): Double {
        val angles = fragments.map { it.angle }.filter { it.isFinite() }.sorted()
        if (angles.isEmpty()) return 0.0
        val median = angles[angles.size / 2].toDouble()
        if (kotlin.math.abs(median) < 0.3 || kotlin.math.abs(median) > 30) return 0.0
        return kotlin.math.tan(Math.toRadians(median))
    }

    /**
     * [expectedCurrency] is the currency the user will most likely record in (their default wallet's). A currency
     * printed on the receipt wins over it; amounts are then read with that currency's minor-unit digits, so
     * "1,250" is one thousand two hundred fifty yen and "12.500" is twelve dinars and five hundred fils.
     */
    fun parse(lines: List<String>, today: LocalDate, expectedCurrency: String? = null): ReceiptSuggestion {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val detected = detectCurrency(clean)
        val digits = CurrencyRules.fractionDigits(detected ?: expectedCurrency ?: "XXX").let { if (it > 3) 2 else it }
        val every = clean.flatMap { amountsIn(it, digits, allowIntegers = false) }.distinct().sortedDescending()
        val payable = clean.filterNot { notPayment.containsMatchIn(withoutTaxQualifier(it.lowercase(Locale.ROOT))) }
            .flatMap { amountsIn(it, digits, allowIntegers = false) }.distinct().sortedDescending()
        val derived = cashLessChange(clean, digits)
        val labelled = findTotal(clean, digits)
            // No total line, but the cash handed over less the change matches an amount printed on the receipt.
            ?: derived?.takeIf { d -> every.any { it.compareTo(d) == 0 } }?.let { LabelledTotal(it, "Cash less change") }
        val amounts = (listOfNotNull(derived) + payable).distinct().ifEmpty { every }
        val (total, confidence) = labelled?.let { it.amount to TotalConfidence.LABELLED }
            ?: amounts.firstOrNull()?.let { it to TotalConfidence.LARGEST_AMOUNT }
            ?: (null to TotalConfidence.NONE)
        val (date, ambiguous) = findDate(clean, today)
        return ReceiptSuggestion(
            merchant = findMerchant(clean),
            total = total,
            totalConfidence = confidence,
            date = date,
            dateAmbiguous = ambiguous,
            amounts = (listOfNotNull(total) + amounts).distinct().take(8),
            currencyCode = detected,
            fractionDigits = digits,
            totalLabel = labelled?.label,
        )
    }

    // ------------------------------------------------------------- amounts

    // A number with optional grouping (`,` `.` `'`) and an optional 1–3 digit fraction.
    // A plain space is deliberately not a grouping separator: OCR rows like "2 150.00" (quantity, then price)
    // would otherwise read as 2,150.00. Non-breaking/thin spaces and apostrophes (Swiss style) are accepted.
    internal val NUMBER = Regex("(?<![\\d.,'])(\\d{1,3}(?:[,.'\\u00A0\\u202F]\\d{3})+(?:[.,]\\d{1,3})?|\\d+(?:[.,]\\d{1,3})?)(?![\\d]|[.,]\\d)")

    /** Things that look numeric but are never a price: dates, times, percentages, phone/ID/reference numbers. */
    internal val NOT_MONEY = listOf(
        Regex("(?<!\\d)(?:19|20)\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2}(?!\\d)"),
        Regex("(?<!\\d)\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}(?!\\d)"),
        Regex("(?<!\\d)\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[ap]\\.?m\\.?)?", RegexOption.IGNORE_CASE),
        Regex("\\d+(?:[.,]\\d+)?\\s*%"),
        Regex("(?<![\\d.,])(?:\\+\\d{1,3}[ -]?)?\\(?0?\\d{2,4}\\)?[ -]\\d{3,4}[ -]\\d{3,4}(?![\\d.,])"),
        Regex("(?:\\b(?:tel|phone|mobile|fax|tin|vat\\s*reg|reg|invoice|inv|receipt|or|si|txn|trans|ref|order|table|card|acct|no)\\b|#)\\s*[.:#]*\\s*\\d[\\d-]*", RegexOption.IGNORE_CASE),
        Regex("\\b\\d+\\s*(?:x|pcs?|qty|items?|kg|g|ml|l)\\b", RegexOption.IGNORE_CASE),
    )

    /**
     * Money amounts on one line, read with [fractionDigits] minor-unit digits (0 for JPY/KRW/VND, 2 for most,
     * 3 for BHD/KWD/OMR/JOD/TND). With [allowIntegers] false, plain integers without grouping ("450") are ignored
     * because on an unlabelled line they are more often quantities or codes than prices.
     */
    internal fun amountsIn(line: String, fractionDigits: Int = 2, allowIntegers: Boolean = false): List<BigDecimal> =
        NUMBER.findAll(maskNonMoney(line)).mapNotNull { m -> interpret(m.value, fractionDigits, allowIntegers) }
            .filter { it.signum() > 0 && it <= MAX_AMOUNT }
            .toList()

    /** [line] with dates, times, percentages, phone and reference numbers blanked out (same length, so positions hold). */
    internal fun maskNonMoney(line: String): String {
        var masked = line
        NOT_MONEY.forEach { re -> masked = re.replace(masked) { m -> " ".repeat(m.value.length) } }
        return masked
    }

    /** A number in a line that can be money: where it is, its value, and whether it was written as a bare integer. */
    internal data class NumberToken(val range: IntRange, val value: BigDecimal, val plainInteger: Boolean)

    /** Every number in [masked] (see [maskNonMoney]) that can be an amount, bare integers included, left to right. */
    internal fun numberTokens(masked: String, fractionDigits: Int): List<NumberToken> =
        NUMBER.findAll(masked).mapNotNull { m ->
            interpret(m.value, fractionDigits, allowIntegers = true)
                ?.takeIf { it.signum() > 0 && it <= MAX_AMOUNT }
                ?.let { NumberToken(m.range, it, plainInteger = m.value.none { c -> c == '.' || c == ',' || c == '\'' || c == '\u00A0' || c == '\u202F' }) }
        }.toList()

    /** Converts one numeric token to a value with exactly [fractionDigits] decimals, or null when it isn't money. */
    internal fun interpret(token: String, fractionDigits: Int, allowIntegers: Boolean): BigDecimal? {
        val raw = token.replace("\u00A0", "").replace("\u202F", "").replace("'", "")
        val lastSep = raw.indexOfLast { it == '.' || it == ',' }
        val digitsAfter = if (lastSep < 0) 0 else raw.length - lastSep - 1
        val separators = raw.filter { it == '.' || it == ',' }.toSet()
        val grouped = token.any { it == '\u00A0' || it == '\u202F' || it == '\'' } ||
            raw.count { it == '.' || it == ',' } > 1

        val (whole, fraction) = when {
            lastSep < 0 -> raw to ""
            // Two different separators ("1,234.50", "1.234,50"): the last one is the decimal mark.
            separators.size == 2 -> raw.substring(0, lastSep).filter { it.isDigit() } to raw.substring(lastSep + 1)
            digitsAfter == 3 && fractionDigits == 3 && raw[lastSep] == '.' && !grouped -> raw.substring(0, lastSep) to raw.substring(lastSep + 1)
            // "0,500" is never 500 (P0-3): a thousands group can't follow a leading zero.
            digitsAfter == 3 && !grouped && raw.substring(0, lastSep).startsWith("0") -> raw.substring(0, lastSep) to raw.substring(lastSep + 1)
            // "1,250" / "12.500": a group of three is a thousands separator unless the currency uses 3 decimals.
            digitsAfter == 3 -> raw.filter { it.isDigit() } to ""
            else -> raw.substring(0, lastSep).filter { it.isDigit() } to raw.substring(lastSep + 1)
        }
        if (whole.isEmpty() || whole.length > 13) return null
        val isPlainInteger = fraction.isEmpty() && lastSep < 0
        if (isPlainInteger && !allowIntegers) return null
        val value = when {
            fraction.isEmpty() -> BigDecimal(whole)
            fraction.length == fractionDigits -> BigDecimal("$whole.$fraction")
            // "1.25" for a 3-decimal currency is 1.250; "1250.00" for a 0-decimal currency is 1250.
            fraction.length < fractionDigits -> BigDecimal("$whole.$fraction")
            fraction.trimEnd('0').length <= fractionDigits -> BigDecimal("$whole.$fraction")
            else -> return null // more precision than the currency has: a weight, rate or code, not a price
        }
        // One-digit fractions ("1.5") are usually weights or quantities, not prices.
        if (fraction.length == 1 && fractionDigits >= 2) return null
        return value.setScale(fractionDigits, java.math.RoundingMode.UNNECESSARY)
    }

    private val MAX_AMOUNT = BigDecimal("1000000000")

    // ------------------------------------------------------------- currency

    /** Unambiguous symbols and abbreviations. `$`, `¥`, `kr` and `R` are deliberately absent: they name several currencies. */
    private val CURRENCY_MARKERS: List<Pair<Regex, String>> = listOf(
        "₱" to "PHP", "€" to "EUR", "£" to "GBP", "₩" to "KRW", "₹" to "INR", "฿" to "THB", "₫" to "VND",
        "₦" to "NGN", "₺" to "TRY", "₪" to "ILS", "zł" to "PLN", "(?<![A-Z])R\\$" to "BRL", "US\\$" to "USD", "(?<![A-Z])S\\$" to "SGD",
        "HK\\$" to "HKD", "(?<![A-Z])A\\$" to "AUD", "(?<![A-Z])C\\$" to "CAD", "NZ\\$" to "NZD", "RM(?=\\s?\\d)" to "MYR", "Rp\\.?(?=\\s?\\d)" to "IDR",
        "KD(?=\\s?\\d)" to "KWD", "BD(?=\\s?\\d)" to "BHD", "JD(?=\\s?\\d)" to "JOD", "円" to "JPY", "元" to "CNY",
    ).map { (pattern, code) -> Regex(pattern) to code }

    /** ISO codes Lucento recognises on receipts; a curated list so words like "ALL" or "TOP" don't count. */
    internal val ISO_CODES = setOf(
        "PHP", "USD", "EUR", "GBP", "JPY", "CNY", "KRW", "INR", "IDR", "MYR", "SGD", "THB", "VND", "HKD", "TWD",
        "AUD", "NZD", "CAD", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "TRY", "ILS", "AED", "SAR", "QAR",
        "KWD", "BHD", "OMR", "JOD", "TND", "EGP", "ZAR", "NGN", "KES", "GHS", "BRL", "MXN", "ARS", "CLP", "COP", "PEN",
    )
    private val ISO_TOKEN = Regex("(?<![A-Za-z])([A-Z]{3})(?![A-Za-z])")

    /** The single currency the receipt names, or null when it names none or more than one. */
    internal fun detectCurrency(lines: List<String>): String? {
        val found = LinkedHashSet<String>()
        lines.forEach { line ->
            CURRENCY_MARKERS.forEach { (re, code) -> if (re.containsMatchIn(line)) found += code }
            ISO_TOKEN.findAll(line).forEach { m -> if (m.groupValues[1] in ISO_CODES) found += m.groupValues[1] }
        }
        return found.singleOrNull()
    }

    /**
     * Total labels, best first. What the customer owes ("Amount due") outranks a plain "Total"; "Amount paid" ranks last,
     * because on a cash receipt it is the money handed over, and the change comes back out of it.
     */
    private val totalLabels = listOf(
        Regex("grand\\s*total") to 0,
        // Thermal printers abbreviate: "AMT DUE", "TOTAL AMT", "TTL", "AMT PAID".
        Regex(
            "total\\s*(amount\\s*|amt\\.?\\s*)?due|(amount|amt\\.?)\\s*due|balance\\s*due|(amount|amt\\.?)\\s*payable|" +
                "(total|net)\\s*payable|amount\\s*to\\s*pay",
        ) to 1,
        Regex("total\\s*(amount|amt)|net\\s*total|total\\s*sales?\\b|net\\s*amount|\\btotal\\b|\\bttl\\b") to 2,
        Regex("(amount|amt\\.?)\\s*paid|total\\s*paid|you\\s*paid|paid\\s*amount|payment\\s*amount") to 3,
    )
    private val notTotal = Regex(
        "sub\\s*-?\\s*total|total\\s*(items?|qty|quantity|savings|discount|vat|tax)|\\bvat\\b|\\btax\\b|change|tender|cash\\s*paid|discount|points|" +
            "min(imum)?\\.?\\s*(amount\\s*)?due",
    )
    private val changeLine = Regex("\\bchange\\b")

    /**
     * "TOTAL (VAT INCLUSIVE)", "Amount due incl. VAT": the tax words qualify the total; they don't make it a tax line.
     * Removed before a line is tested against [notTotal].
     */
    private val taxQualifier = Regex(
        "\\(?\\s*(vat|tax)[\\s-]*(incl(usive|\\.|uded)?|inc\\.?)\\s*\\)?|\\(?\\s*incl(usive|\\.|uding)?\\s*(of\\s*)?(vat|tax(es)?)\\s*\\)?",
    )

    internal fun withoutTaxQualifier(lower: String): String = taxQualifier.replace(lower, " ")

    /** Lines whose amounts are never what was paid: money handed over, change, tax breakdowns, savings, counts. */
    private val notPayment = Regex(
        "\\bchange\\b|tender|\\bcash\\b(?!\\s*back)|\\bvat|\\btax\\b|exempt|zero[-\\s]*rated|discount|savings|points|rewards?|" +
            "total\\s*(items?|qty|quantity)|min(imum)?\\.?\\s*(amount\\s*)?due",
    )

    /** Money handed over: "CASH 1,000.00", "AMOUNT TENDERED 500.00". */
    private val tenderedLine = Regex("\\bcash\\b(?!\\s*back)|tender")

    /** The cash handed over less the change given back, when both are printed; null otherwise. */
    internal fun cashLessChange(lines: List<String>, fractionDigits: Int): BigDecimal? {
        // The first such line that carries an amount: "PAYMENT TYPE: CASH" alone names the method, not the money.
        fun firstAmount(matches: (String) -> Boolean): BigDecimal? = lines.firstNotNullOfOrNull { line ->
            line.takeIf { matches(it.lowercase(Locale.ROOT)) }?.let { amountsIn(it, fractionDigits, allowIntegers = true).lastOrNull() }
        }
        val tendered = firstAmount { tenderedLine.containsMatchIn(it) && !changeLine.containsMatchIn(it) } ?: return null
        val change = firstAmount { changeLine.containsMatchIn(it) } ?: return null
        return (tendered - change).takeIf { it.signum() > 0 }
    }

    /** A labelled total and the words that labelled it. */
    private data class LabelledTotal(val amount: BigDecimal, val label: String)

    /**
     * The amount on (or right after) the best-ranked total line. When the only label is "Amount paid" and a change line
     * is printed, the total is what was paid less the change.
     */
    private fun findTotal(lines: List<String>, fractionDigits: Int): LabelledTotal? {
        var best: Triple<Int, Int, BigDecimal>? = null // rank, line index, amount
        var bestLabel = ""
        lines.forEachIndexed { index, line ->
            val lower = withoutTaxQualifier(line.lowercase(Locale.ROOT))
            if (notTotal.containsMatchIn(lower)) return@forEachIndexed
            val (re, rank) = totalLabels.firstOrNull { (re, _) -> re.containsMatchIn(lower) } ?: return@forEachIndexed
            // On a line labelled as the total, a plain integer ("TOTAL 250") is a legitimate amount.
            val amount = amountsIn(line, fractionDigits, allowIntegers = true).lastOrNull()
                ?: lines.getOrNull(index + 1)?.let { amountsIn(it, fractionDigits, allowIntegers = true).lastOrNull() }
                ?: return@forEachIndexed
            val current = best
            // Better rank wins; for equal rank the later line wins (totals sit near the bottom).
            if (current == null || rank < current.first || (rank == current.first && index > current.second)) {
                best = Triple(rank, index, amount)
                bestLabel = re.find(lower)?.value.orEmpty()
            }
        }
        val found = best ?: return null
        val label = bestLabel.replace(Regex("\\s+"), " ").trim().replaceFirstChar { it.titlecase(Locale.ROOT) }
        if (found.first == PAID_RANK) {
            val change = lines.firstOrNull { changeLine.containsMatchIn(it.lowercase(Locale.ROOT)) }
                ?.let { amountsIn(it, fractionDigits, allowIntegers = true).lastOrNull() }
            if (change != null && change.signum() > 0 && change < found.third) return LabelledTotal(found.third - change, "$label less change")
        }
        return LabelledTotal(found.third, label)
    }

    private const val PAID_RANK = 3

    // ------------------------------------------------------------- items vs total

    enum class ItemsCheck { ADDS_UP, DOES_NOT_ADD_UP, UNKNOWN }

    private val subtotalLabel = Regex("sub\\s*-?\\s*total")
    private val taxLine = Regex("\\b(vat|tax|service\\s*charge)\\b")
    private val notAddedTax = Regex("vatable|vat[-\\s]*exempt|zero[-\\s]*rated|incl|inclusive|reg|tin")
    private val discountLine = Regex("discount|\\bless\\b|promo|voucher")

    /**
     * Whether the receipt's own numbers support [total]: a printed subtotal (plus added tax, less discounts), or else the
     * priced item lines above the total. Deliberately cautious: [ItemsCheck.DOES_NOT_ADD_UP] is reported only with two or
     * more priced items and a mismatch, and [ItemsCheck.UNKNOWN] whenever the layout can't be read with confidence.
     */
    fun checkItems(lines: List<String>, total: BigDecimal?, fractionDigits: Int = 2): ItemsCheck {
        if (total == null) return ItemsCheck.UNKNOWN
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val totalIndex = clean.indexOfFirst { line ->
            val lower = withoutTaxQualifier(line.lowercase(Locale.ROOT))
            !notTotal.containsMatchIn(lower) && totalLabels.any { (re, _) -> re.containsMatchIn(lower) }
        }
        if (totalIndex <= 0) return ItemsCheck.UNKNOWN
        val above = clean.subList(0, totalIndex)
        fun last(line: String) = amountsIn(line, fractionDigits, allowIntegers = false).lastOrNull()
        var tax = BigDecimal.ZERO
        var discount = BigDecimal.ZERO
        var subtotal: BigDecimal? = null
        var items = BigDecimal.ZERO
        var itemCount = 0
        above.forEach { line ->
            val lower = line.lowercase(Locale.ROOT)
            val amount = last(line) ?: return@forEach
            when {
                subtotalLabel.containsMatchIn(lower) -> subtotal = amount
                taxLine.containsMatchIn(lower) && !notAddedTax.containsMatchIn(lower) -> tax += amount
                discountLine.containsMatchIn(lower) -> discount += amount
                notTotal.containsMatchIn(lower) || notAddedTax.containsMatchIn(lower) -> Unit
                else -> {
                    items += amount
                    itemCount++
                }
            }
        }
        fun matches(base: BigDecimal) = listOf(base, base + tax, base - discount, base + tax - discount).any { it.compareTo(total) == 0 }
        subtotal?.let { return if (matches(it)) ItemsCheck.ADDS_UP else ItemsCheck.DOES_NOT_ADD_UP }
        if (itemCount < 2) return ItemsCheck.UNKNOWN
        return if (matches(items)) ItemsCheck.ADDS_UP else ItemsCheck.DOES_NOT_ADD_UP
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
