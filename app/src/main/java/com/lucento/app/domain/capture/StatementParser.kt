package com.lucento.app.domain.capture

import com.lucento.app.core.money.CurrencyRules
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What a scanned image holds. */
enum class ScanKind {
    /** One payment (a receipt, an invoice, a payment confirmation): one transaction for its total. */
    RECEIPT,
    /** A list of transactions (a bank or card statement, an app's history): one transaction per line. */
    STATEMENT,
}

/** Which way a statement line moved money, seen from the account: OUT leaves it, IN arrives in it. */
enum class StatementFlow { OUT, IN, UNKNOWN }

/** What decided a line's [StatementFlow], strongest first. */
enum class FlowEvidence {
    /** The printed running balance went down (OUT) or up (IN) by the amount. */
    BALANCE,
    /** A sign or mark next to the amount: −, +, ( ), CR, DR. */
    MARKER,
    /** The words on the line ("deposit", "withdrawal", "payment — thank you"). */
    WORDS,
    /** A card statement's lines are charges unless something says otherwise. */
    CARD_DEFAULT,
    NONE,
}

/** One transaction read from a statement. Every value is a suggestion the user reviews. */
data class StatementLine(
    val date: LocalDate?,
    /** The day/month order couldn't be settled ("03/04"). */
    val dateAmbiguous: Boolean,
    val description: String,
    val amount: BigDecimal,
    val flow: StatementFlow,
    val evidence: FlowEvidence,
    /** The running balance printed on the line, when there is one. */
    val balance: BigDecimal?,
    /** The scanned row(s) this line came from. */
    val source: String,
)

data class StatementReading(
    val lines: List<StatementLine>,
    /** Currency printed on the statement (symbol or ISO code); null when none or several. */
    val currencyCode: String?,
    val fractionDigits: Int,
    /** True when a printed running balance confirmed the direction of every line that carries one. */
    val balanceChecked: Boolean,
    /** Lines past [StatementParser.MAX_LINES] that were left out. */
    val leftOut: Int,
    /** It reads like a credit card statement (charges, "minimum due", posting dates). */
    val looksLikeCard: Boolean,
)

/**
 * Reads statements and transaction histories from on-device OCR rows ([ReceiptParser.mergeRows]), and tells them apart
 * from receipts. Deterministic rules, never presented as AI (docs/CAPTURE.md "Scanning").
 *
 * - **Receipt or statement?** ([classify]) A receipt is one payment: many priced lines, one date, a total. A statement
 *   lists many payments: several lines that each carry a date and an amount (or dates heading groups of amounts, as in
 *   an e-wallet's history), running balances, money-in and money-out marks.
 * - **Each line** has a date (its own, the date heading its group, or the date printed under it), a description (what's
 *   left of the line), an amount, and a direction. The direction comes from the running balance when one is printed
 *   (the balance went down by the amount: money out), else a sign or mark next to the amount, else the words; a card
 *   statement's lines are charges unless they say payment or credit. Anything still unclear is marked for the user.
 * - Summary lines (opening and closing balances, totals, minimum due, limits) are never transactions; an opening balance
 *   anchors the running-balance check.
 */
object StatementParser {

    const val MAX_LINES = 60

    // ----------------------------------------------------------------- dates

    private const val MONTH = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)(?![a-z])\\.?"
    private val ISO_DATE = Regex("(?<!\\d)(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)")
    /** Not followed by more of a number ("1,250.00"): a comma or dot may follow only when no digit comes after it. */
    private const val END = "(?!\\d|[.,:]\\d)"
    private val NUMERIC_DATE = Regex("(?<![\\d.,])(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4}|\\d{2})$END")
    private val TEXT_DMY = Regex("(?<![\\d.,])(\\d{1,2})(?:st|nd|rd|th)?[\\s-]?$MONTH(?:[\\s,-]+('?\\d{2}$END|\\d{4}$END))?", RegexOption.IGNORE_CASE)
    private val TEXT_MDY = Regex("(?<![a-z])$MONTH\\s*(\\d{1,2})(?:st|nd|rd|th)?$END(?:,?\\s+(\\d{4})$END)?", RegexOption.IGNORE_CASE)
    private val SHORT_DATE = Regex("(?<![\\d.,/-])(\\d{1,2})[/-](\\d{1,2})(?![\\d/-])")
    private val DOTTED_SHORT_DATE = Regex("(?<![\\d.,])(\\d{1,2})\\.(\\d{1,2})\\.(?!\\d)")
    private val RELATIVE = Regex("\\b(today|yesterday)\\b", RegexOption.IGNORE_CASE)
    private val TIME = Regex("(?<!\\d)\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[ap]\\.?m\\.?)?", RegexOption.IGNORE_CASE)

    private sealed interface RawDate {
        /** Month known (written as a word, or ISO); [year] null when not printed. */
        data class Known(val year: Int?, val month: Int, val day: Int) : RawDate
        /** Two numbers whose day/month order depends on the document. */
        data class Numeric(val year: Int?, val first: Int, val second: Int) : RawDate
        data class Relative(val daysAgo: Long) : RawDate
    }

    private fun monthOf(word: String): Int? {
        val w = word.lowercase(Locale.ROOT).trimEnd('.').take(3)
        return listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(w).takeIf { it >= 0 }?.plus(1)
    }

    private fun yearOf(text: String?): Int? = text?.trim('\'')?.toIntOrNull()?.let { if (it < 100) 2000 + it else it }

    // ----------------------------------------------------------------- amounts and marks

    /**
     * A currency written beside an amount: an ISO code Lucento knows ("PHP", "Php"), a symbol ("₱", "$"), or a lone "P",
     * which is how on-device OCR often reads the peso sign.
     */
    private val CURRENCY = "(?:(?<![A-Za-z])(?:${ReceiptParser.ISO_CODES.joinToString("|")}|Php|Rp|RM|P)(?![A-Za-z])|\\p{Sc})"
    /** "-500", "-₱500", "- PHP 500", "+500", "(500" — a spaced dash with no currency is punctuation, not a sign. */
    private val SIGN_BEFORE = Regex("([-−–+])(?:\\s*$CURRENCY\\s*)?$|\\(\\s*(?:$CURRENCY\\s*)?$")
    private val CURRENCY_BEFORE = Regex("$CURRENCY\\s*$")
    private val MARK_AFTER = Regex("^(?:\\s*(cr|dr)\\b|(-)(?!\\d)|\\s*(\\)))", RegexOption.IGNORE_CASE)

    private data class Amount(val value: BigDecimal, val flow: StatementFlow, val start: Int, val end: Int, val trailingMinus: Boolean)

    // ----------------------------------------------------------------- rows

    private enum class Kind { HEADER, SUMMARY, TRANSACTION, DATE_ONLY, TEXT, NOISE }

    private class Row(
        val text: String,
        val dates: List<RawDate>,
        val hasTime: Boolean,
        val amounts: List<Amount>,
        val description: String,
        val kind: Kind,
        val opening: Boolean,
    )

    private val COLUMN_WORDS = Regex(
        "\\b(date|description|particulars|details|debit|credit|amount|balance|withdrawals?|deposits?|reference|ref|transaction|posting|post|sale|trans|narration|remarks|check)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val OPENING = Regex("\\b(beginning|opening|previous|starting)\\s+balance\\b|balance\\s+(brought\\s+)?forward|\\bbrought\\s+forward\\b|\\bb/f\\b", RegexOption.IGNORE_CASE)
    private val SUMMARY = Regex(
        "\\b(ending|closing|current|available|outstanding|ledger|statement|new)\\s+balance\\b|\\bcarried\\s+forward\\b|\\bc/f\\b|" +
            "\\bsub\\s*-?\\s*total\\b|\\btotals?\\b|\\bminimum\\b|\\bamount\\s+due\\b|\\bcredit\\s+limit\\b|\\bavailable\\s+credit\\b|" +
            "\\bpayment\\s+due\\b|\\bdue\\s+date\\b|\\bpage\\s+\\d|\\bstatement\\s+(date|period)\\b|\\bperiod\\s+covered\\b",
        RegexOption.IGNORE_CASE,
    )

    private val STATEMENT_WORDS = Regex(
        "\\b(statement|transaction\\s+history|transactions|posting\\s+date|post\\s+date|sale\\s+date|running\\s+balance|beginning\\s+balance|" +
            "ending\\s+balance|available\\s+balance|withdrawals?|deposits?|debits?|credits?|account\\s+(no|number)|period)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val RECEIPT_WORDS = Regex(
        "\\b(subtotal|sub\\s+total|change|tendered|vat|vatable|qty|cashier|official\\s+receipt|sales\\s+invoice|invoice|" +
            "thank\\s+you\\s+for|items?|table|server|guest|served)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val CARD_WORDS = Regex(
        "credit\\s+card|card\\s+(no|number|ending)|minimum\\s+(amount\\s+)?due|credit\\s+limit|statement\\s+balance|post(ing)?\\s+date|sale\\s+date|" +
            "finance\\s+charge|cardholder|card\\s+holder",
        RegexOption.IGNORE_CASE,
    )

    private val IN_WORDS = Regex(
        "\\b(deposit(ed)?|cash\\s*-?\\s*in|salary|payroll|credited|received|receive|refund(ed)?|reversal|reversed|interest\\s+(earned|credit|income)|" +
            "cash\\s*back|cashback|rebate|incoming|(received|transfer(red)?|money)\\s+from|payment\\s*(received|-?\\s*thank)|thank\\s+you|top\\s*-?\\s*up\\s+from)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val OUT_WORDS = Regex(
        "\\b(withdraw(al|n)?|atm|purchase[sd]?|pos|payment\\s+to|bills?\\s*pay(ment)?|paid|transfer(red)?\\s+to|send\\s+money|sent|debited|" +
            "fees?|service\\s+charge|buy|bought|load|outgoing|online\\s+shopping)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val CARD_IN_WORDS = Regex("\\b(payment|thank\\s+you|credit|refund|reversal|rebate|cash\\s*back|adjustment\\s+credit)\\b", RegexOption.IGNORE_CASE)

    /** Whether the rows read as one receipt or as a list of transactions. */
    fun classify(lines: List<String>): ScanKind {
        val rows = analyze(lines, fractionDigitsFor(lines, null))
        return if (looksLikeStatement(rows)) ScanKind.STATEMENT else ScanKind.RECEIPT
    }

    private fun looksLikeStatement(rows: List<Row>): Boolean {
        val transactions = rows.filter { it.kind == Kind.TRANSACTION }
        if (transactions.size < 2) return false
        val withOwnDate = transactions.count { it.dates.isNotEmpty() }
        val dateRows = rows.count { it.kind == Kind.DATE_ONLY }
        val signed = transactions.count { r -> r.amounts.any { it.flow != StatementFlow.UNKNOWN } }
        val text = rows.joinToString("\n") { it.text }
        val statementCues = STATEMENT_WORDS.findAll(text).count()
        val receiptCues = RECEIPT_WORDS.findAll(text).count()
        val distinctDates = rows.flatMap { it.dates }.distinct().size
        return when {
            // Several lines that each carry a date and an amount: a statement, whatever else is printed.
            withOwnDate >= 2 -> true
            // Dates heading (or under) groups of amounts, as in an app's history.
            dateRows >= 1 && distinctDates >= 1 && (signed >= 2 || statementCues >= 1) && receiptCues <= statementCues -> true
            // Money-in and money-out marks on several lines.
            signed >= 3 && receiptCues == 0 -> true
            else -> false
        }
    }

    /**
     * Reads a statement. [accountIsCard] when it was scanned into a credit card (its lines are charges unless marked);
     * [expectedCurrency] is the account's, used when the statement prints none; [dayFirst] is the phone's date order,
     * used only when the statement's own dates can't settle it.
     */
    fun parse(
        lines: List<String>,
        today: LocalDate,
        expectedCurrency: String? = null,
        accountIsCard: Boolean = false,
        dayFirst: Boolean? = null,
    ): StatementReading {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val detected = ReceiptParser.detectCurrency(clean)
        val digits = fractionDigitsFor(clean, expectedCurrency)
        val rows = analyze(clean, digits)
        val text = clean.joinToString("\n")
        val card = accountIsCard || CARD_WORDS.containsMatchIn(text)

        // Dates: the document's own day/month order, and a year for dates printed without one.
        val resolver = DateResolver(rows.flatMap { it.dates }, today, dayFirst)

        // Running balances: a header naming "balance", or most amount lines carrying two amounts.
        val transactionRows = rows.withIndex().filter { it.value.kind == Kind.TRANSACTION }
        val headerHasBalance = rows.any { it.kind == Kind.HEADER && Regex("balance", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
        val twoAmountRows = transactionRows.count { it.value.amounts.size >= 2 }
        val balanceColumn = headerHasBalance || (twoAmountRows >= 2 && twoAmountRows * 10 >= transactionRows.size * 6)

        // Stacked layouts (an app's history): a date row heads each group, or sits under each transaction.
        val firstUndated = transactionRows.firstOrNull { it.value.dates.isEmpty() }?.index
        val datesUnder = firstUndated != null &&
            rows.getOrNull(firstUndated - 1)?.kind != Kind.DATE_ONLY &&
            rows.getOrNull(firstUndated + 1)?.kind == Kind.DATE_ONLY

        data class Draft(val row: Int, val date: RawDate?, val description: String, val amount: Amount, val balance: BigDecimal?, val source: String)

        val drafts = ArrayList<Draft>()
        var heading: RawDate? = null
        var opening: BigDecimal? = null
        rows.forEachIndexed { i, row ->
            when (row.kind) {
                Kind.DATE_ONLY -> if (!datesUnder) heading = row.dates.first()
                Kind.SUMMARY -> if (row.opening && opening == null && drafts.isEmpty()) opening = row.amounts.lastOrNull()?.value
                Kind.TRANSACTION -> {
                    val below = rows.getOrNull(i + 1)
                    val above = rows.getOrNull(i - 1)
                    val date = row.dates.firstOrNull()
                        ?: (if (datesUnder && below?.kind == Kind.DATE_ONLY) below.dates.first() else null)
                        ?: heading
                    if (row.dates.isNotEmpty()) heading = row.dates.first()
                    val amount = if (balanceColumn && row.amounts.size >= 2) row.amounts.first() else row.amounts.last()
                    val balance = if (balanceColumn && row.amounts.size >= 2) row.amounts.last().value else null
                    // A line with no words of its own takes them from the line above (an app's title row) or below.
                    val description = row.description.ifBlank {
                        listOf(above, below).firstOrNull { it?.kind == Kind.TEXT }?.description.orEmpty()
                    }
                    drafts += Draft(i, date, description, amount, balance, row.text)
                }
                Kind.TEXT -> {
                    // A wrapped description under a dated line on a printed statement ("TO JUAN DELA CRUZ").
                    val last = drafts.lastOrNull()
                    if (last != null && last.row == i - 1 && rows[last.row].dates.isNotEmpty() && row.description.length <= 40) {
                        drafts[drafts.lastIndex] = last.copy(description = (last.description + " " + row.description).trim().take(80), source = last.source + "\n" + row.text)
                    }
                }
                else -> Unit
            }
        }

        // The running balance decides direction where it can: forward (oldest first) or backward (newest first).
        val flows = arrayOfNulls<StatementFlow>(drafts.size)
        fun check(order: List<Int>, start: BigDecimal?): Int {
            var previous = start
            var matched = 0
            order.forEach { k ->
                val d = drafts[k]
                val balance = d.balance
                if (previous != null && balance != null) {
                    when {
                        (previous - d.amount.value).compareTo(balance) == 0 -> matched++
                        (previous + d.amount.value).compareTo(balance) == 0 -> matched++
                    }
                }
                previous = balance
            }
            return matched
        }
        fun apply(order: List<Int>, start: BigDecimal?) {
            var previous = start
            order.forEach { k ->
                val d = drafts[k]
                val balance = d.balance
                if (previous != null && balance != null) {
                    when {
                        (previous - d.amount.value).compareTo(balance) == 0 -> flows[k] = StatementFlow.OUT
                        (previous + d.amount.value).compareTo(balance) == 0 -> flows[k] = StatementFlow.IN
                    }
                }
                previous = balance
            }
        }
        var balanceChecked = false
        if (balanceColumn) {
            val forward = drafts.indices.toList()
            val forwardHits = check(forward, opening)
            // Newest first: each balance is the one below it plus or minus its amount.
            val backwardHits = check(forward.reversed(), null)
            if (forwardHits > 0 || backwardHits > 0) {
                if (forwardHits >= backwardHits) apply(forward, opening) else apply(forward.reversed(), null)
                val withBalance = drafts.indices.filter { drafts[it].balance != null }
                // Every line with a balance agrees, except the first (nothing above it to compare with).
                balanceChecked = withBalance.count { flows[it] != null } >= (withBalance.size - 1).coerceAtLeast(1)
            }
        }

        val read = drafts.mapIndexed { k, d ->
            val words = d.description + " " + d.source
            val (flow, evidence) = when {
                flows[k] != null -> flows[k]!! to FlowEvidence.BALANCE
                // A trailing minus is a credit on a card statement and a debit on a bank's.
                d.amount.trailingMinus -> (if (card) StatementFlow.IN else StatementFlow.OUT) to FlowEvidence.MARKER
                d.amount.flow != StatementFlow.UNKNOWN -> d.amount.flow to FlowEvidence.MARKER
                card -> (if (CARD_IN_WORDS.containsMatchIn(words)) StatementFlow.IN else StatementFlow.OUT) to
                    (if (CARD_IN_WORDS.containsMatchIn(words)) FlowEvidence.WORDS else FlowEvidence.CARD_DEFAULT)
                else -> {
                    val inWords = IN_WORDS.containsMatchIn(words)
                    val outWords = OUT_WORDS.containsMatchIn(words)
                    when {
                        inWords && !outWords -> StatementFlow.IN to FlowEvidence.WORDS
                        outWords && !inWords -> StatementFlow.OUT to FlowEvidence.WORDS
                        else -> StatementFlow.UNKNOWN to FlowEvidence.NONE
                    }
                }
            }
            val (date, ambiguous) = d.date?.let { resolver.resolve(it) } ?: (null to false)
            StatementLine(
                date = date,
                dateAmbiguous = ambiguous,
                description = tidy(d.description),
                amount = d.amount.value,
                flow = flow,
                evidence = evidence,
                balance = d.balance,
                source = d.source,
            )
        }
        return StatementReading(
            lines = read.take(MAX_LINES),
            currencyCode = detected,
            fractionDigits = digits,
            balanceChecked = balanceChecked,
            leftOut = (read.size - MAX_LINES).coerceAtLeast(0),
            looksLikeCard = card,
        )
    }

    private fun fractionDigitsFor(lines: List<String>, expectedCurrency: String?): Int =
        CurrencyRules.fractionDigits(ReceiptParser.detectCurrency(lines) ?: expectedCurrency ?: "XXX").let { if (it > 3) 2 else it }

    // ----------------------------------------------------------------- one row

    private fun analyze(lines: List<String>, digits: Int): List<Row> = lines.map { analyzeRow(it, digits) }

    private fun analyzeRow(text: String, digits: Int): Row {
        val chars = text.toCharArray()
        fun blank(range: IntRange) = range.forEach { if (it in chars.indices) chars[it] = ' ' }
        fun current() = String(chars)

        val found = ArrayList<Pair<Int, RawDate>>()
        ISO_DATE.findAll(current()).forEach { m ->
            found += m.range.first to RawDate.Known(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            blank(m.range)
        }
        NUMERIC_DATE.findAll(current()).forEach { m ->
            found += m.range.first to RawDate.Numeric(yearOf(m.groupValues[3]), m.groupValues[1].toInt(), m.groupValues[2].toInt())
            blank(m.range)
        }
        TEXT_DMY.findAll(current()).forEach { m ->
            val month = monthOf(m.groupValues[2]) ?: return@forEach
            found += m.range.first to RawDate.Known(yearOf(m.groupValues[3].ifBlank { null }), month, m.groupValues[1].toInt())
            blank(m.range)
        }
        TEXT_MDY.findAll(current()).forEach { m ->
            val month = monthOf(m.groupValues[1]) ?: return@forEach
            found += m.range.first to RawDate.Known(yearOf(m.groupValues[3].ifBlank { null }), month, m.groupValues[2].toInt())
            blank(m.range)
        }
        TIME.findAll(current()).forEach { blank(it.range) }
        val hasTime = TIME.containsMatchIn(text)
        SHORT_DATE.findAll(current()).forEach { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            if (a in 1..31 && b in 1..31 && (a <= 12 || b <= 12)) {
                found += m.range.first to RawDate.Numeric(null, a, b)
                blank(m.range)
            }
        }
        DOTTED_SHORT_DATE.findAll(current()).forEach { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            if (a in 1..31 && b in 1..12) {
                found += m.range.first to RawDate.Known(null, b, a)
                blank(m.range)
            }
        }
        RELATIVE.findAll(current()).forEach { m ->
            found += m.range.first to RawDate.Relative(if (m.value.equals("today", ignoreCase = true)) 0 else 1)
            blank(m.range)
        }
        val dates = found.sortedBy { it.first }.map { it.second }

        // Amounts, with any sign or mark around them.
        val masked = ReceiptParser.maskNonMoney(current())
        val amounts = ArrayList<Amount>()
        ReceiptParser.numberTokens(masked, digits).forEach { token ->
            val before = masked.substring((token.range.first - 8).coerceAtLeast(0), token.range.first)
            val after = masked.substring(token.range.last + 1, (token.range.last + 6).coerceAtMost(masked.length))
            val sign = SIGN_BEFORE.find(before)
            val mark = MARK_AFTER.find(after)
            val currency = CURRENCY_BEFORE.find(before)
            // A bare integer is money only with a currency or a sign beside it ("₱500", "+500"); otherwise a code or count.
            if (token.plainInteger && sign == null && currency == null) return@forEach
            var flow = StatementFlow.UNKNOWN
            var trailingMinus = false
            var start = token.range.first
            var end = token.range.last
            if (sign != null) {
                start = token.range.first - (before.length - sign.range.first)
                val symbol = sign.groupValues[1]
                flow = when {
                    symbol == "+" -> StatementFlow.IN
                    symbol.isNotEmpty() -> StatementFlow.OUT
                    // "(1,234.00)": an accounting negative, when the bracket closes after it.
                    mark?.groupValues?.get(3) == ")" -> StatementFlow.OUT
                    else -> StatementFlow.UNKNOWN
                }
            }
            if (mark != null) {
                end = token.range.last + mark.range.last + 1
                when {
                    mark.groupValues[1].equals("cr", ignoreCase = true) -> flow = StatementFlow.IN
                    mark.groupValues[1].equals("dr", ignoreCase = true) -> flow = StatementFlow.OUT
                    mark.groupValues[2] == "-" && flow == StatementFlow.UNKNOWN -> trailingMinus = true
                }
            }
            // The currency written before the amount belongs to it, not to the description.
            if (sign == null && currency != null) start = token.range.first - (before.length - currency.range.first)
            amounts += Amount(token.value, flow, start.coerceAtLeast(0), end.coerceAtMost(masked.lastIndex), trailingMinus)
        }
        amounts.forEach { blank(it.start..it.end) }

        val description = describe(String(chars))
        val opening = OPENING.containsMatchIn(text)
        val kind = when {
            amounts.isEmpty() && COLUMN_WORDS.findAll(text).count() >= 2 && dates.isEmpty() -> Kind.HEADER
            opening || (amounts.isNotEmpty() && SUMMARY.containsMatchIn(text)) -> Kind.SUMMARY
            amounts.isNotEmpty() -> Kind.TRANSACTION
            dates.isNotEmpty() -> Kind.DATE_ONLY
            description.count { it.isLetter() } >= 2 -> Kind.TEXT
            else -> Kind.NOISE
        }
        return Row(text, dates, hasTime, amounts, description, kind, opening)
    }

    private val CODES_AND_REFS = Regex(
        "(?<![A-Za-z])(?:${ReceiptParser.ISO_CODES.joinToString("|")}|Php|CR|DR)(?![A-Za-z])|\\p{Sc}|\\b\\d{5,}\\b|[#*]+\\d*",
    )

    /** What's left of a row once dates, times, amounts, codes and reference numbers are out. */
    private fun describe(rest: String): String =
        rest.replace(CODES_AND_REFS, " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '–', '—', '|', '·', '•', ':', ',', '/', '.', '(', ')')
            .trim()

    /** ALL-CAPS words read as names: "LAZADA PH" → "Lazada PH" (short words, like BPI or ATM, stay as printed). */
    private fun tidy(description: String): String =
        description.split(' ').filter { it.isNotBlank() }.joinToString(" ") { word ->
            if (word.length > 3 && word.all { !it.isLetter() || it.isUpperCase() }) {
                word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            } else {
                word
            }
        }.take(80)

    // ----------------------------------------------------------------- the document's dates

    /**
     * Turns raw dates into real ones. The day/month order of numeric dates comes from the document: a number above 12
     * settles it; otherwise the order that keeps the statement's dates closest together (statements cover about a month),
     * then the phone's order, and the dates are marked as unclear. Years not printed are the latest year that keeps the
     * date on or before the newest full date printed (or today).
     */
    private class DateResolver(raw: List<RawDate>, private val today: LocalDate, phoneDayFirst: Boolean?) {
        private val numeric = raw.filterIsInstance<RawDate.Numeric>()
        private val anchor: LocalDate
        private val dayFirst: Boolean
        private val settled: Boolean

        init {
            val full = raw.mapNotNull { r ->
                when (r) {
                    is RawDate.Known -> r.year?.let { y -> runCatching { LocalDate.of(y, r.month, r.day) }.getOrNull() }
                    else -> null
                }
            }.filter { !it.isAfter(today.plusDays(1)) }
            anchor = full.maxOrNull()?.let { if (it.isAfter(today)) today else it }?.let { maxOf(it, today.minusYears(1)) } ?: today
            val forcedDayFirst = numeric.any { it.first > 12 }
            val forcedMonthFirst = numeric.any { it.second > 12 }
            when {
                forcedDayFirst != forcedMonthFirst -> {
                    dayFirst = forcedDayFirst
                    settled = true
                }
                numeric.size >= 2 -> {
                    val spanDayFirst = span(true)
                    val spanMonthFirst = span(false)
                    if (spanDayFirst != spanMonthFirst && minOf(spanDayFirst, spanMonthFirst) <= 62) {
                        dayFirst = spanDayFirst < spanMonthFirst
                        settled = true
                    } else {
                        dayFirst = phoneDayFirst ?: false
                        settled = numeric.all { it.first == it.second }
                    }
                }
                else -> {
                    dayFirst = phoneDayFirst ?: false
                    settled = numeric.all { it.first == it.second || it.first > 12 || it.second > 12 }
                }
            }
        }

        private fun span(dayFirst: Boolean): Long {
            val dates = numeric.mapNotNull { make(it.year, if (dayFirst) it.second else it.first, if (dayFirst) it.first else it.second) }
            if (dates.size < 2) return Long.MAX_VALUE
            return ChronoUnit.DAYS.between(dates.min(), dates.max())
        }

        private fun make(year: Int?, month: Int, day: Int): LocalDate? {
            if (month !in 1..12 || day !in 1..31) return null
            if (year != null) return runCatching { LocalDate.of(year, month, day) }.getOrNull()
            val limit = anchor.plusDays(7)
            val sameYear = runCatching { LocalDate.of(limit.year, month, day) }.getOrNull()
                ?: runCatching { LocalDate.of(limit.year - 1, month, day) }.getOrNull() ?: return null
            return if (sameYear.isAfter(limit)) runCatching { LocalDate.of(limit.year - 1, month, day) }.getOrNull() else sameYear
        }

        fun resolve(raw: RawDate): Pair<LocalDate?, Boolean> {
            val (date, ambiguous) = when (raw) {
                is RawDate.Known -> make(raw.year, raw.month, raw.day) to false
                is RawDate.Numeric -> make(raw.year, if (dayFirst) raw.second else raw.first, if (dayFirst) raw.first else raw.second) to
                    (!settled && raw.first != raw.second && raw.first <= 12 && raw.second <= 12)
                is RawDate.Relative -> today.minusDays(raw.daysAgo) to false
            }
            // Never a future transaction, never older than five years: such a date was misread.
            val plausible = date?.takeIf { !it.isAfter(today) && !it.isBefore(today.minusYears(5)) }
            return plausible to (ambiguous || (date != null && plausible == null))
        }
    }
}
