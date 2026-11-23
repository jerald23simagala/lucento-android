package com.lucento.app.domain.capture

import com.lucento.app.core.money.CurrencyRules
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/** How the suggested total was found, so the review screen can say how much to trust it. */
enum class TotalConfidence {
    /** On a line labelled as the total (e.g. "TOTAL", "AMOUNT DUE", "SUMME", "合計"). */
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

/** Where one joined piece of a [LayoutRow] sits: its characters in the row's text and its horizontal extent on the page. */
data class LayoutCell(val start: Int, val end: Int, val left: Int, val right: Int) {
    val centerX: Int get() = (left + right) / 2
}

/** One rebuilt printed line: its text, and (when OCR or a PDF gave positions) the pieces it was joined from. */
data class LayoutRow(val text: String, val cells: List<LayoutCell> = emptyList()) {
    /** The cell holding character [index] of [text], or null. */
    fun cellAt(index: Int): LayoutCell? = cells.firstOrNull { index >= it.start && index < it.end }
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
    /** Money coming back: a refund or return receipt (its total printed negative, or labelled as a refund). */
    val refund: Boolean = false,
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
    fun mergeRows(fragments: List<TextFragment>): List<String> = layoutRows(fragments).map { it.text }

    /** [mergeRows] with each row's pieces and positions kept, for column-aware reading ([StatementParser]). */
    fun layoutRows(fragments: List<TextFragment>): List<LayoutRow> {
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
        return rows.map { r ->
            val sorted = r.map { it.first }.sortedBy { it.left }
            val text = StringBuilder()
            val cells = ArrayList<LayoutCell>()
            sorted.forEach { f ->
                if (text.isNotEmpty()) text.append("  ")
                val start = text.length
                text.append(f.text.trim())
                cells += LayoutCell(start, text.length, f.left, f.right)
            }
            LayoutRow(text.toString(), cells)
        }
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
     * [expectedCurrency] is the currency the user will most likely record in (their default wallet's, or the one the
     * document's country settles). A currency printed on the receipt wins over it; amounts are then read with that
     * currency's minor-unit digits, so "1,250" is one thousand two hundred fifty yen and "12.500" is twelve dinars and
     * five hundred fils. [dateOrder] is the document's country's way of writing dates; [phoneDayFirst] the phone's, used
     * only to pick between two readings that stay marked as unclear.
     */
    fun parse(
        lines: List<String>,
        today: LocalDate,
        expectedCurrency: String? = null,
        dateOrder: DateOrder? = null,
        phoneDayFirst: Boolean? = null,
    ): ReceiptSuggestion {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val detected = detectCurrency(clean)
        val digits = CurrencyRules.fractionDigits(detected ?: expectedCurrency ?: "XXX").let { if (it > 3) 2 else it }
        val every = clean.flatMap { amountsIn(it, digits, allowIntegers = false) }.distinct().sortedDescending()
        val payable = clean.filterNot { notPayment.containsMatchIn(withoutTaxQualifier(it.lowercase(Locale.ROOT))) }
            .flatMap { amountsIn(it, digits, allowIntegers = false) }.distinct().sortedDescending()
        val derived = cashLessChange(clean, digits)
        val labelled = findTotal(clean, digits)
            // No total line, but the cash handed over less the change matches an amount printed on the receipt.
            ?: derived?.takeIf { d -> every.any { it.compareTo(d) == 0 } }?.let { LabelledTotal(it, "Cash less change", -1) }
        val amounts = (listOfNotNull(derived) + payable).distinct().ifEmpty { every }
        val (total, confidence) = labelled?.let { it.amount to TotalConfidence.LABELLED }
            ?: amounts.firstOrNull()?.let { it to TotalConfidence.LARGEST_AMOUNT }
            ?: (null to TotalConfidence.NONE)
        val (date, ambiguous) = findDate(clean, today, dateOrder, phoneDayFirst)
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
            refund = isRefund(clean, labelled?.line),
        )
    }

    // ------------------------------------------------------------- amounts

    // A number with optional grouping (`,` `.` `'`) and an optional 1–3 digit fraction; Indian lakh grouping
    // ("1,23,456.00") first. A plain space is deliberately not a grouping separator: OCR rows like "2 150.00"
    // (quantity, then price) would otherwise read as 2,150.00. Non-breaking/thin spaces and apostrophes (Swiss style)
    // are accepted.
    internal val NUMBER = Regex(
        "(?<![\\d.,'])(\\d{1,2}(?:,\\d{2})+,\\d{3}(?:\\.\\d{1,2})?|\\d{1,3}(?:[,.'\\u00A0\\u202F]\\d{3})+(?:[.,]\\d{1,3})?|\\d+(?:[.,]\\d{1,3})?)(?![\\d]|[.,]\\d)",
    )

    /** Things that look numeric but are never a price: dates, times, percentages, phone/ID/reference numbers. */
    internal val NOT_MONEY = listOf(
        Regex("(?<!\\d)(?:19|20)\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2}(?!\\d)"),
        Regex("(?<!\\d)(?:19|20)\\d{2}\\s*[年년]\\s*\\d{1,2}\\s*[月월]\\s*\\d{1,2}\\s*[日일]?"),
        Regex("(?<!\\d)\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}(?!\\d)"),
        Regex("(?<!\\d)\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[ap]\\.?m\\.?)?", RegexOption.IGNORE_CASE),
        Regex("\\d+(?:[.,]\\d+)?\\s*%"),
        Regex("(?<![\\d.,])(?:\\+\\d{1,3}[ -]?)?\\(?0?\\d{2,4}\\)?[ -]\\d{3,4}[ -]\\d{3,4}(?![\\d.,])"),
        // A reference after its label ("ORDER #12345", "CARD 4421", "AUTH 012345") — but never an amount printed after
        // such a word ("PAYMENT TO CARD  500.00", "ZALORA ORDER  1,299.00"): a number with a decimal part is money.
        Regex("(?:\\b(?:tel|phone|mobile|fax|tin|vat\\s*reg|reg|invoice|inv|receipt|or|si|txn|trans|ref|order|table|card|acct|no|auth|appr|trace|seq|batch|terminal|tid|mid|store|op|cashier)\\b|#)\\s*[.:#]*\\s*\\d[\\d-]*(?![\\d.,'])", RegexOption.IGNORE_CASE),
        Regex("\\b\\d+\\s*(?:x|pcs?|qty|items?|kg|g|ml|l|lb|lbs|oz|gal)\\b", RegexOption.IGNORE_CASE),
        // Quantities with their unit ("GALLONS 11.462", "232 kWh", "12.5 L"), and a meter's readings ("Present Reading
        // Oct 9  12,456"): measured, never money.
        Regex("\\b(?:gallons?|gal|litres?|liters?|ltrs?|kwh|kw|cu\\.?\\s*m|m3|odometer|mileage|miles|km|units?\\s+used|consumption)\\b\\s*[:=]?\\s*\\d[\\d.,]*", RegexOption.IGNORE_CASE),
        Regex("\\d[\\d.,]*\\s*(?:kwh|kw|gallons?|gal|litres?|liters?|ltrs?|m3|cu\\.?\\s*m)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:present|previous|prior|current|last|next|actual|estimated)\\s+(?:meter\\s+)?readings?\\b.*", RegexOption.IGNORE_CASE),
        // Dates written with a month name and a year ("Nov 3, 2026", "21. November 2026"): the year is not an amount.
        Regex("(?<!\\p{L})(?:${MonthWords.pattern})\\.?\\s+\\d{1,2}(?:st|nd|rd|th)?,?\\s+\\d{4}(?!\\d)", RegexOption.IGNORE_CASE),
        Regex("(?<![\\d.,])\\d{1,2}(?:st|nd|rd|th|\\.)?\\s*(?:de\\s+)?(?:${MonthWords.pattern})\\.?,?\\s+(?:de\\s+)?\\d{4}(?!\\d)", RegexOption.IGNORE_CASE),
        // Account and policy numbers in hyphenated groups ("46-1234-5678-0012-3").
        Regex("(?<![\\d.,])\\d{2,}(?:-\\d{2,}){2,}(?:-\\d+)?(?![\\d.,])"),
    )

    /**
     * Money amounts on one line, read with [fractionDigits] minor-unit digits (0 for JPY/KRW/VND, 2 for most,
     * 3 for BHD/KWD/OMR/JOD/TND). With [allowIntegers] false, plain integers without grouping ("450") are ignored
     * because on an unlabelled line they are more often quantities or codes than prices — unless a currency is written
     * right beside them ("¥348", "₩15000", "348円"), which makes them money.
     */
    internal fun amountsIn(line: String, fractionDigits: Int = 2, allowIntegers: Boolean = false): List<BigDecimal> {
        val masked = maskNonMoney(line)
        return NUMBER.findAll(masked).mapNotNull { m ->
            val marked = !allowIntegers && currencyBeside(masked, m.range)
            interpret(m.value, fractionDigits, allowIntegers || marked)
        }.filter { it.signum() > 0 && it <= MAX_AMOUNT }.toList()
    }

    private val CURRENCY_BEFORE = Regex("(?:\\p{Sc}|(?<![A-Za-z])(?:${"PHP|USD|EUR|GBP|JPY|KRW|CNY|INR|IDR|THB|VND|Php"})(?![A-Za-z]))\\s?$")
    private val CURRENCY_AFTER = Regex("^\\s?(?:円|원|元|\\p{Sc})")

    private fun currencyBeside(masked: String, range: IntRange): Boolean {
        val before = masked.substring((range.first - 5).coerceAtLeast(0), range.first)
        val after = masked.substring(range.last + 1, (range.last + 3).coerceAtMost(masked.length))
        return CURRENCY_BEFORE.containsMatchIn(before) || CURRENCY_AFTER.containsMatchIn(after)
    }

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
                ?.let { NumberToken(m.range, it, plainInteger = m.value.none { c -> c == '.' || c == ',' || c == '\'' || c == ' ' || c == ' ' }) }
        }.toList()

    /** Converts one numeric token to a value with exactly [fractionDigits] decimals, or null when it isn't money. */
    internal fun interpret(token: String, fractionDigits: Int, allowIntegers: Boolean): BigDecimal? {
        val raw = token.replace(" ", "").replace(" ", "").replace("'", "")
        val lastSep = raw.indexOfLast { it == '.' || it == ',' }
        val digitsAfter = if (lastSep < 0) 0 else raw.length - lastSep - 1
        val separators = raw.filter { it == '.' || it == ',' }.toSet()
        val grouped = token.any { it == ' ' || it == ' ' || it == '\'' } ||
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
        "HK\\$" to "HKD", "(?<![A-Z])A\\$" to "AUD", "AU\\$" to "AUD", "(?<![A-Z])C\\$" to "CAD", "CA\\$" to "CAD", "NZ\\$" to "NZD",
        "NT\\$" to "TWD", "MX\\$" to "MXN", "RM(?=\\s?\\d)" to "MYR", "Rp\\.?(?=\\s?\\d)" to "IDR",
        "KD(?=\\s?\\d)" to "KWD", "BD(?=\\s?\\d)" to "BHD", "JD(?=\\s?\\d)" to "JOD", "円" to "JPY", "元" to "CNY", "원" to "KRW",
        "(?<![A-Za-z])Php(?![A-Za-z])" to "PHP", "(?<![A-Za-z])Dhs?\\.?(?=\\s?\\d)" to "AED", "(?<![A-Za-z])Fr\\.(?=\\s?\\d)" to "CHF",
        // A statement that says it in words: "Montants en euros", "Beträge in Euro".
        "(?i)\\b(?:en|in)\\s+euros?\\b" to "EUR",
    ).map { (pattern, code) -> Regex(pattern) to code }

    /** ISO codes Lucento recognises on receipts; a curated list so words like "ALL" or "TOP" don't count. */
    internal val ISO_CODES = setOf(
        "PHP", "USD", "EUR", "GBP", "JPY", "CNY", "KRW", "INR", "IDR", "MYR", "SGD", "THB", "VND", "HKD", "TWD",
        "AUD", "NZD", "CAD", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "TRY", "ILS", "AED", "SAR", "QAR",
        "KWD", "BHD", "OMR", "JOD", "TND", "EGP", "ZAR", "NGN", "KES", "GHS", "BRL", "MXN", "ARS", "CLP", "COP", "PEN",
        "RMB",
    )
    private val ISO_TOKEN = Regex("(?<![A-Za-z])([A-Z]{3})(?![A-Za-z])")

    /** The currencies one line names (symbols and ISO codes). */
    internal fun detectCurrencies(line: String): Set<String> {
        val found = LinkedHashSet<String>()
        CURRENCY_MARKERS.forEach { (re, code) -> if (re.containsMatchIn(line)) found += code }
        ISO_TOKEN.findAll(line).forEach { m ->
            val code = m.groupValues[1]
            if (code in ISO_CODES) found += if (code == "RMB") "CNY" else code
        }
        return found
    }

    /** The single currency the receipt names, or null when it names none or more than one. */
    internal fun detectCurrency(lines: List<String>): String? = lines.flatMap { detectCurrencies(it) }.toSet().singleOrNull()

    // ------------------------------------------------------------- total labels, many languages

    /**
     * Total labels, best first. What the customer owes ("Amount due") outranks a plain "Total"; "Amount paid" ranks last,
     * because on a cash receipt it is the money handed over, and the change comes back out of it. The words are those
     * printed in the languages of Lucento's markets (English, Filipino, Spanish, Portuguese, French, German, Italian,
     * Dutch, Indonesian/Malay, the Nordic languages, Polish, Turkish, Vietnamese, Japanese, Chinese and Korean).
     */
    private val totalLabels = listOf(
        Regex("grand\\s*total|gesamtsumme|totale\\s*complessivo|总计|総合計|총\\s*합계|jumlah\\s*keseluruhan") to 0,
        // Thermal printers abbreviate: "AMT DUE", "TOTAL AMT", "TTL", "AMT PAID".
        Regex(
            "total\\s*(amount\\s*|amt\\.?\\s*)?due|(amount|amt\\.?)\\s*due|balance\\s*due|bal\\.?\\s*due|(amount|amt\\.?)\\s*payable|" +
                "(total|net)\\s*payable|amount\\s*to\\s*pay|zu\\s*zahlen|zahlbetrag|net\\s*[àa]\\s*payer|total\\s*[àa]\\s*payer|[àa]\\s*payer|" +
                "montant\\s*d[ûu]|total\\s*a\\s*pagar|importe\\s*a\\s*pagar|a\\s*pagar|valor\\s*a\\s*pagar|da\\s*pagare|totale\\s*da\\s*pagare|" +
                "te\\s*betalen|total\\s*bayar|jumlah\\s*bayar|harus\\s*dibayar|ご請求額|お支払(い)?金額|应付(金额)?|결제\\s*금액|받을\\s*금액|" +
                "att\\s*betala|at\\s*betale|[åa]\\s*betale|do\\s*zap[łl]aty|t[ổo]ng\\s*thanh\\s*to[áa]n|" +
                // Payment confirmations: what was sent.
                "(total\\s*)?amount\\s*sent|transfer\\s*amount|amount\\s*transferred|send\\s*amount|refund\\s*amount|amount\\s*refunded",
        ) to 1,
        Regex(
            "total\\s*(amount|amt)|net\\s*total|total\\s*sales?\\b|net\\s*amount|\\btotal\\b|\\bttl\\b|\\bsumme\\b|\\bgesamt(betrag)?\\b|endbetrag|" +
                "betrag\\s*(eur|chf)|montant\\s*(total|ttc)|total\\s*ttc|importe\\s*total|\\bimporte\\b|\\btotale\\b|\\bimporto\\b|valor\\s*total|" +
                "\\btotaal(bedrag)?\\b|\\bjumlah\\b|\\bkabuuan\\b|总额|合計|合计|総額|합계|총액|\\bsumma\\b|\\btotalt\\b|\\bi\\s*alt\\b|\\brazem\\b|" +
                "\\bsuma\\b|\\btoplam\\b|t[ổo]ng\\s*c[ộo]ng|th[àa]nh\\s*ti[ềe]n|\\bcelkem\\b|[öo]sszesen|" +
                // A fuel pump's or terminal's sale line, and a confirmation's headline word on its own line ("Paid").
                "fuel\\s*sale|sale\\s*amount|purchase\\s*amount|amount\\s*charged|total\\s*charged|^\\s*(paid|sent|received|refunded)\\s*:?\\s*$",
        ) to 2,
        Regex(
            "(amount|amt\\.?)\\s*paid|total\\s*paid|you\\s*paid|you\\s*sent|paid\\s*amount|payment\\s*amount|montant\\s*pay[ée]|importe\\s*pagado|" +
                "\\bbezahlt\\b|\\bpagato\\b|\\bbetaald\\b|\\bdibayar\\b|お支払|^\\s*amount\\b",
        ) to 3,
    )

    /** "GST INCLUDED IN TOTAL $2.37", "VAT included in the total": a tax figure, though it says "total". */
    private val taxInTotal = Regex("(gst|vat|tax|iva|tva|mwst|btw|moms)\\s+(is\\s+)?(included|incl\\.?)\\s+in\\s+(the\\s+)?total|in\\s+total\\s+(gst|vat|tax)\\s+incl")

    private val notTotal = Regex(
        "sub\\s*-?\\s*total|total\\s*(items?|qty|quantity|savings|discount|vat|tax|tip)|\\bvat\\b|\\btax\\b|change|tender|cash\\s*paid|discount|points|" +
            "min(imum)?\\.?\\s*(amount\\s*)?due|zwischensumme|sous[-\\s]*total|subtotale|subtotaal|小計|小计|소계|\\bmwst\\b|\\bust\\b|\\btva\\b|" +
            "\\biva\\b|\\bbtw\\b|\\bmoms\\b|\\bmva\\b|\\bgst\\b|\\bhst\\b|\\bpst\\b|\\bqst\\b|\\b[cis]gst\\b|消費税|부가세|r[üu]ckgeld|wechselgeld|" +
            "\\brendu\\b|\\bmonnaie\\b|\\bcambio\\b|\\btroco\\b|wisselgeld|kembali(an)?|お釣|釣銭|找零|거스름|suggested|tip\\s*guide|gratuity\\s*guide|" +
            "items?\\s*sold|number\\s*of\\s*items|item\\s*count|you\\s*saved|your\\s*savings|total\\s*saved|\\bsavings\\b|before\\s*tip|" +
            "previous\\s*balance|balance\\s*forward|total\\s*(checking|savings|rewards|points)|total\\s*(deposits|withdrawals|credits|debits|purchases)",
    )
    private val changeLine = Regex("\\bchange\\b|\\bcambio\\b|\\brendu\\b|\\bmonnaie\\b|r[üu]ckgeld|wechselgeld|\\btroco\\b|wisselgeld|kembali(an)?|お釣|釣銭|找零|거스름")

    /**
     * "TOTAL (VAT INCLUSIVE)", "Amount due incl. VAT", "TOTAL TTC", "Summe inkl. MwSt": the tax words qualify the total;
     * they don't make it a tax line. Removed before a line is tested against [notTotal].
     */
    private val taxQualifier = Regex(
        "\\(?\\s*(vat|tax|gst|iva|tva|mwst|btw|moms)[\\s-]*(incl(usive|\\.|uded|uido|uso|use)?|inc\\.?|inkl\\.?|enthalten|incluido|incluso|inclusa)\\s*\\)?|" +
            "\\(?\\s*(incl|inkl|inc)(usive|\\.|uding)?\\s*(of\\s*)?(vat|tax(es)?|gst|mwst|btw|moms|tva|iva)\\s*\\)?|\\(?\\s*tax\\s*incl\\w*\\s*\\)?|税込",
    )

    internal fun withoutTaxQualifier(lower: String): String = taxQualifier.replace(lower, " ")

    /** Lines whose amounts are never what was paid: money handed over, change, tax breakdowns, savings, counts, tips. */
    private val notPayment = Regex(
        "\\bchange\\b|tender|\\bcash\\b(?!\\s*back)|\\bvat|\\btax\\b|exempt|zero[-\\s]*rated|discount|savings|points|rewards?|" +
            "total\\s*(items?|qty|quantity)|min(imum)?\\.?\\s*(amount\\s*)?due|\\bgst\\b|\\bhst\\b|\\bpst\\b|\\b[cis]gst\\b|\\bmwst\\b|\\btva\\b|" +
            "\\biva\\b|\\bbtw\\b|\\bmoms\\b|\\bmva\\b|消費税|부가세|r[üu]ckgeld|wechselgeld|\\bbar\\s*gegeben|\\bgegeben\\b|esp[èe]ces|\\befectivo\\b|" +
            "contanti|dinheiro|\\bcontant\\b|\\btunai\\b|お預|預り|받은\\s*금액|現金|现金|\\bcambio\\b|\\brendu\\b|\\btroco\\b|kembali|お釣|找零|" +
            "suggested|tip\\s*guide|items?\\s*sold|you\\s*saved",
    )

    /** Money handed over: "CASH 1,000.00", "AMOUNT TENDERED 500.00", "BAR 50,00", "EFECTIVO 20.00". */
    private val tenderedLine = Regex(
        "\\bcash\\b(?!\\s*back)|tender|^bar\\b|\\bbar\\s*gegeben|\\bgegeben\\b|esp[èe]ces|\\befectivo\\b|contanti|dinheiro|\\bcontant\\b|\\btunai\\b|お預|預り|現金|现金|받은\\s*금액",
    )

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

    /** A labelled total, the words that labelled it, its line (-1 when derived) and its label's rank. */
    private data class LabelledTotal(val amount: BigDecimal, val label: String, val line: Int, val rank: Int = PAID_RANK)

    /**
     * Whether [lines] print a receipt's or a bill's total ("TOTAL", "AMOUNT DUE", "Total amount due", "SUMME") — not a
     * statement's section total, which [notTotal] leaves out, and not an app history's "You paid …" lines. Tells a bill
     * with a few dated lines from a statement.
     */
    internal fun hasReceiptTotal(lines: List<String>): Boolean =
        findTotal(lines.map { it.trim() }.filter { it.isNotEmpty() }, 2)?.let { it.rank < PAID_RANK } == true

    /** The heading of a tax breakdown ("BTW  OVER  EUR", "VAT%  NET  VAT", "MwSt  Netto  Steuer"): its "TOTAAL" row sums tax. */
    private val taxTable = Regex(
        "\\b(btw|vat|mwst|ust|tva|iva|gst|tax|moms|mva)\\b.*\\b(over|net|netto|base|basis|excl|gross|brutto|grundlage|bedrag|amount|steuer|ht)\\b",
    )

    /** Only an amount (and perhaps a currency) on the line: the value of a label printed on the line above. */
    private val AMOUNT_ONLY = Regex("^[\\s\\p{Sc}A-Z+(\\-−]{0,5}[\\d.,'\\s\\u00A0]+[\\s\\p{Sc}A-Z)\\-]{0,5}$")

    /**
     * The amount on (or right after) the best-ranked total line. When the only label is "Amount paid" and a change line
     * is printed, the total is what was paid less the change. The line after a label counts only when it holds nothing
     * but the amount, so "TOTAL: ______" over a suggested-tip line never takes the tip.
     */
    private fun findTotal(lines: List<String>, fractionDigits: Int): LabelledTotal? {
        var best: Triple<Int, Int, BigDecimal>? = null // rank, line index, amount
        var bestLabel = ""
        lines.forEachIndexed { index, line ->
            if (taxInTotal.containsMatchIn(line.lowercase(Locale.ROOT))) return@forEachIndexed
            // The total row of a tax table just above it sums the tax, not the purchase.
            if ((maxOf(0, index - 4) until index).any { taxTable.containsMatchIn(lines[it].lowercase(Locale.ROOT)) && amountsIn(lines[it], fractionDigits).isEmpty() }) {
                return@forEachIndexed
            }
            val lower = withoutTaxQualifier(line.lowercase(Locale.ROOT))
            if (notTotal.containsMatchIn(lower)) return@forEachIndexed
            val (re, rank) = totalLabels.firstOrNull { (re, _) -> re.containsMatchIn(lower) } ?: return@forEachIndexed
            // On a line labelled as the total, a plain integer ("TOTAL 250") is a legitimate amount.
            val amount = amountsIn(line, fractionDigits, allowIntegers = true).lastOrNull()
                ?: lines.getOrNull(index + 1)?.takeIf { AMOUNT_ONLY.matches(it.trim()) }?.let { amountsIn(it, fractionDigits, allowIntegers = true).lastOrNull() }
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
            if (change != null && change.signum() > 0 && change < found.third) return LabelledTotal(found.third - change, "$label less change", found.second, found.first)
        }
        return LabelledTotal(found.third, label, found.second, found.first)
    }

    private const val PAID_RANK = 3

    // ------------------------------------------------------------- refunds

    private val REFUND_WORDS = Regex(
        "\\brefund(ed|s)?\\b|\\breturn(ed)?\\b|credit\\s*(memo|note)|\\breembolso\\b|devoluci[óo]n|rembours|r[üu]ckerstattung|\\berstattung\\b|" +
            "\\brimborso\\b|\\bestorno\\b|terugbetaling|pengembalian|返金|退款|환불",
        RegexOption.IGNORE_CASE,
    )
    /**
     * The total's own number is negative: "-19.99", "- $19.99", "($19.99)", "19.99-". Only the last number on the line is
     * looked at, so an account number's dashes ("46-1234-5678") never make a bill a refund.
     */
    private fun negativeTotal(line: String): Boolean {
        val masked = maskNonMoney(line)
        val last = NUMBER.findAll(masked).lastOrNull() ?: return false
        val before = masked.substring((last.range.first - 4).coerceAtLeast(0), last.range.first)
        val after = masked.substring(last.range.last + 1, (last.range.last + 3).coerceAtMost(masked.length))
        return Regex("(?<![\\p{L}\\p{N}])[-−–]\\s*\\p{Sc}?\\s*$|\\(\\s*\\p{Sc}?\\s*$").containsMatchIn(before) || Regex("^\\s?[-)]").containsMatchIn(after)
    }

    /**
     * A refund or return: the total printed negative, or refund words on the total line, a title line, or a line
     * with an amount ("Refund amount: $19.99"). A "returns accepted within 30 days" footer has no amount and doesn't count.
     */
    private fun isRefund(lines: List<String>, totalLine: Int?): Boolean {
        val total = totalLine?.takeIf { it >= 0 }?.let { lines.getOrNull(it) }
        if (total != null && (negativeTotal(total) || REFUND_WORDS.containsMatchIn(total))) return true
        return lines.withIndex().any { (i, line) ->
            REFUND_WORDS.containsMatchIn(line) && !Regex("polic|within|days|accepted|exchange", RegexOption.IGNORE_CASE).containsMatchIn(line) &&
                (i < 4 || amountsIn(line, 2, allowIntegers = false).isNotEmpty() && Regex("amount|total|refund", RegexOption.IGNORE_CASE).containsMatchIn(line))
        }
    }

    // ------------------------------------------------------------- items vs total

    enum class ItemsCheck { ADDS_UP, DOES_NOT_ADD_UP, UNKNOWN }

    private val subtotalLabel = Regex("sub\\s*-?\\s*total|zwischensumme|sous[-\\s]*total|subtotale|subtotaal|小計|小计|소계")
    private val taxLine = Regex("\\b(vat|tax|service\\s*charge|gst|hst|pst|qst|[cis]gst|mwst|ust|tva|iva|btw|moms|mva|ppn|sst)\\b|消費税|부가세")
    private val tipLine = Regex("\\b(tip|gratuity|propina|pourboire|trinkgeld|mancia|fooi)\\b")
    private val notAddedTax = Regex("vatable|vat[-\\s]*exempt|zero[-\\s]*rated|incl|inkl|inclusive|reg|tin|enthalten|incluido")
    private val discountLine = Regex("discount|\\bless\\b|promo|voucher|coupon|rabatt|remise|descuento|sconto|korting|diskon")

    /**
     * Whether the receipt's own numbers support [total]: a printed subtotal (plus added tax and tip, less discounts), or
     * else the priced item lines above the total. Deliberately cautious: [ItemsCheck.DOES_NOT_ADD_UP] is reported only with
     * two or more priced items and a mismatch, and [ItemsCheck.UNKNOWN] whenever the layout can't be read with confidence.
     */
    fun checkItems(lines: List<String>, total: BigDecimal?, fractionDigits: Int = 2): ItemsCheck {
        if (total == null) return ItemsCheck.UNKNOWN
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val totalIndex = clean.indexOfFirst { line ->
            val lower = withoutTaxQualifier(line.lowercase(Locale.ROOT))
            !taxInTotal.containsMatchIn(line.lowercase(Locale.ROOT)) && !notTotal.containsMatchIn(lower) && totalLabels.any { (re, _) -> re.containsMatchIn(lower) }
        }
        if (totalIndex <= 0) return ItemsCheck.UNKNOWN
        val above = clean.subList(0, totalIndex)
        fun last(line: String) = amountsIn(line, fractionDigits, allowIntegers = false).lastOrNull()
        var tax = BigDecimal.ZERO
        var tip = BigDecimal.ZERO
        var discount = BigDecimal.ZERO
        var subtotal: BigDecimal? = null
        var items = BigDecimal.ZERO
        var itemCount = 0
        above.forEach { line ->
            val lower = line.lowercase(Locale.ROOT)
            val amount = last(line) ?: return@forEach
            when {
                subtotalLabel.containsMatchIn(lower) -> subtotal = amount
                tipLine.containsMatchIn(lower) -> if (!lower.contains("suggest")) tip += amount
                taxLine.containsMatchIn(lower) && !notAddedTax.containsMatchIn(lower) -> tax += amount
                discountLine.containsMatchIn(lower) -> discount += amount
                notTotal.containsMatchIn(lower) || notAddedTax.containsMatchIn(lower) -> Unit
                else -> {
                    items += amount
                    itemCount++
                }
            }
        }
        fun matches(base: BigDecimal) = listOf(base, base + tax, base - discount, base + tax - discount)
            .flatMap { listOf(it, it + tip) }
            .any { it.compareTo(total) == 0 }
        subtotal?.let { return if (matches(it)) ItemsCheck.ADDS_UP else ItemsCheck.DOES_NOT_ADD_UP }
        if (itemCount < 2) return ItemsCheck.UNKNOWN
        return if (matches(items)) ItemsCheck.ADDS_UP else ItemsCheck.DOES_NOT_ADD_UP
    }

    // ------------------------------------------------------------- date

    private val ISO = Regex("(?<!\\d)(20\\d{2})\\s*[-/.年년]\\s*(\\d{1,2})\\s*[-/.月월]\\s*(\\d{1,2})(?!\\d)")
    private val NUMERIC = Regex("(?<!\\d)(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})(?!\\d)")
    private val MONTH = "(?<!\\p{L})(${MonthWords.pattern})(?!\\p{L})\\.?"
    private val TEXTUAL_DMY = Regex("(?<!\\d)(\\d{1,2})(?:st|nd|rd|th|\\.)?\\s*(?:de\\s+)?$MONTH,?\\s*(?:de\\s+)?('?\\d{2,4})(?!\\d)", RegexOption.IGNORE_CASE)
    private val TEXTUAL_MDY = Regex("$MONTH\\s*(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{2,4})(?!\\d)", RegexOption.IGNORE_CASE)

    /** Labels that say which date a cell holds: the transaction's (preferred) or one that isn't (a due date, a period). */
    private val GOOD_DATE = Regex(
        "\\b(bill(ing)?\\s*date|statement\\s*date|invoice\\s*date|date\\s*(of\\s*)?(issue|invoice|purchase|payment|transaction)|transaction\\s*date|" +
            "txn\\s*date|trans(action)?\\s*date|payment\\s*date|date\\s*paid|paid\\s*on|order\\s*date|receipt\\s*date|date\\s*issued|issued\\s*on|" +
            "fecha|datum|data|date)\\b|日付|날짜|日期",
        RegexOption.IGNORE_CASE,
    )
    private val NOT_THE_DATE = Regex(
        "\\b(due|pay\\s*by|period|from|through|thru|until|till|coverage|covered|cycle|next\\s*(reading|bill)|meter\\s*reading|reading\\s*date|" +
            "valid|expir\\w*|exp|member\\s*since|since|birth|dob|f[äa]llig|vencimiento|[ée]ch[ée]ance|scadenza|vervaldatum|jatuh\\s*tempo|periodo|p[ée]riode|zeitraum)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Between two dates in one cell, these make a period ("Oct 15 - Nov 14, 2026"), not a transaction date. */
    private val CONNECTOR = Regex("^\\s*(?:-|–|—|~|to|thru|through|until|bis|au|al|hasta|at[ée]|sampai|s/d)\\s*$", RegexOption.IGNORE_CASE)
    private val CELLS = Regex("\\s{2,}")

    private data class DateHit(val date: LocalDate, val ambiguous: Boolean, val start: Int, val end: Int)

    /**
     * The receipt's date. Cells labelled as the bill, invoice or transaction date win; due dates, billing periods and
     * expiry dates are passed over (so a bill gives its bill date, not the end of its period). A numeric date that
     * reads two ways ("03/04/2026") is settled by another date on the receipt with a day above 12, else by the country's
     * order ([order]); otherwise the phone's order ([phoneDayFirst]) or the reading closest to today is offered and it is
     * marked as unclear. A month written as a word in another language than English needs a four-digit year.
     */
    internal fun findDate(lines: List<String>, today: LocalDate, order: DateOrder? = null, phoneDayFirst: Boolean? = null): Pair<LocalDate?, Boolean> {
        val numericPairs = lines.flatMap { line -> NUMERIC.findAll(line).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toList() }
        val forcedDayFirst = numericPairs.any { (a, b) -> a > 12 && b <= 12 }
        val forcedMonthFirst = numericPairs.any { (a, b) -> b > 12 && a <= 12 }
        val documentDayFirst: Boolean? = when {
            forcedDayFirst && !forcedMonthFirst -> true
            forcedMonthFirst && !forcedDayFirst -> false
            order == DateOrder.MONTH_FIRST -> false
            order == DateOrder.DAY_FIRST -> true
            else -> null
        }

        fun overlaps(out: List<DateHit>, range: IntRange) = out.any { range.first <= it.end && it.start <= range.last }

        fun hits(lower: String): List<DateHit> {
            val out = ArrayList<DateHit>()
            ISO.findAll(lower).forEach { m ->
                date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), today)?.let { out += DateHit(it, false, m.range.first, m.range.last) }
            }
            TEXTUAL_DMY.findAll(lower).forEach { m ->
                val word = m.groupValues[2]
                val month = MonthWords.month(word) ?: return@forEach
                if (!MonthWords.isEnglish(word) && m.groupValues[3].trim('\'').length < 4) return@forEach
                if (overlaps(out, m.range)) return@forEach
                date(year(m.groupValues[3]), month, m.groupValues[1].toInt(), today)?.let { out += DateHit(it, false, m.range.first, m.range.last) }
            }
            TEXTUAL_MDY.findAll(lower).forEach { m ->
                val word = m.groupValues[1]
                val month = MonthWords.month(word) ?: return@forEach
                if (!MonthWords.isEnglish(word) && m.groupValues[3].length < 4) return@forEach
                if (overlaps(out, m.range)) return@forEach
                date(year(m.groupValues[3]), month, m.groupValues[2].toInt(), today)?.let { out += DateHit(it, false, m.range.first, m.range.last) }
            }
            NUMERIC.findAll(lower).forEach { m ->
                if (overlaps(out, m.range)) return@forEach
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                val y = year(m.groupValues[3])
                val dayFirst = date(y, b, a, today)
                val monthFirst = date(y, a, b, today)
                val hit = when {
                    dayFirst != null && monthFirst != null && dayFirst != monthFirst -> when (documentDayFirst) {
                        true -> dayFirst to false
                        false -> monthFirst to false
                        // Ambiguous (e.g. 03/04): the phone's order when known, else the reading closest to today; marked.
                        null -> (
                            phoneDayFirst?.let { if (it) dayFirst else monthFirst }
                                ?: listOf(dayFirst, monthFirst).minBy { kotlin.math.abs(it.toEpochDay() - today.toEpochDay()) }
                            ) to true
                    }
                    dayFirst != null -> dayFirst to false
                    monthFirst != null -> monthFirst to false
                    else -> null
                }
                hit?.let { (d, amb) -> out += DateHit(d, amb, m.range.first, m.range.last) }
            }
            return out.sortedBy { it.start }
        }

        fun labelScore(text: String): Int? = when {
            NOT_THE_DATE.containsMatchIn(text) -> -3
            GOOD_DATE.containsMatchIn(text) -> 2
            else -> null
        }

        data class Found(val date: LocalDate, val ambiguous: Boolean, val score: Int, val line: Int)
        val found = ArrayList<Found>()
        lines.forEachIndexed { index, line ->
            // Cells are scored on their own ("Statement Date: Nov 15  Payment Due Date: Dec 5"); a label alone in a cell
            // labels the date in the next one.
            var carried: Int? = null
            line.split(CELLS).forEach { cell ->
                val lower = cell.lowercase(Locale.ROOT)
                val dates = hits(lower)
                val words = StringBuilder(lower)
                dates.forEach { h -> for (k in h.start..h.end.coerceAtMost(words.length - 1)) words.setCharAt(k, ' ') }
                val own = labelScore(maskTimes(words.toString()))
                if (dates.isEmpty()) {
                    carried = own ?: carried
                    return@forEach
                }
                val ranged = dates.zipWithNext().any { (a, b) -> b.start > a.end + 1 && CONNECTOR.matches(lower.substring(a.end + 1, b.start)) }
                val score = if (ranged) -3 else own ?: carried ?: 0
                dates.forEach { found += Found(it.date, it.ambiguous, score, index) }
                carried = null
            }
        }
        val best = found.filter { it.score > -3 }.maxWithOrNull(compareBy<Found> { it.score }.thenByDescending { it.line })
            ?: return null to false
        return best.date to best.ambiguous
    }

    private val TIMES = Regex("\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[ap]\\.?m\\.?)?")
    private fun maskTimes(text: String) = TIMES.replace(text, " ")

    private fun year(text: String): Int = text.trim('\'').toInt().let { if (it < 100) 2000 + it else it }

    /** A plausible receipt date: valid, not in the future, and within the last five years. */
    private fun date(year: Int, month: Int, day: Int, today: LocalDate): LocalDate? {
        val d = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        if (d.isAfter(today) || d.isBefore(today.minusYears(5))) return null
        return d
    }

    // ------------------------------------------------------------- merchant

    private val notMerchant = Regex(
        "receipt|invoice|\\btin\\b|vat\\s*reg|tel|phone|www\\.|http|@|cashier|terminal|\\bpos\\b|date|time|order|table|welcome|thank|" +
            "payment\\s*successful|successful|transaction\\s*(details|receipt|history)|express\\s*send|send\\s*money|bills?\\s*payment|pay\\s*bills|" +
            "you\\s*(paid|sent)|confirmation|approved|statement|official|sales\\s*invoice|copy|reprint|duplicate",
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
