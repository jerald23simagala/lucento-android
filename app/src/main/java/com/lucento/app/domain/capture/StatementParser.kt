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
    /** The amount sits in a money-out or money-in column (Debit / Credit, Withdrawals / Deposits). */
    COLUMN,
    /** A sign or mark next to the amount: −, +, ( ), CR, DR. */
    MARKER,
    /** The line sits in a section of the statement that says ("DEPOSITS AND ADDITIONS", "PAYMENTS AND CREDITS"). */
    SECTION,
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
    /** What the line is ([FinancialTerms]): a purchase, a payment to the card, a fee, a refund, an ATM withdrawal... */
    val kind: LineKind = LineKind.OTHER,
    /** Pending lines may still change at the bank; declined ones are never read as transactions. */
    val status: LineStatus = LineStatus.POSTED,
)

/** What a statement says about itself: balances, a card's minimum payment and limits, the period covered. */
data class StatementSummary(
    /** The balance the period opened with (opening, beginning or previous balance; a card's previous balance owed). */
    val opening: BigDecimal? = null,
    /** The balance it closed with (ending, closing or new balance; a card's statement balance owed). */
    val closing: BigDecimal? = null,
    val minimumDue: BigDecimal? = null,
    val dueDate: LocalDate? = null,
    val creditLimit: BigDecimal? = null,
    val availableCredit: BigDecimal? = null,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
)

data class StatementReading(
    val lines: List<StatementLine>,
    /** Currency printed on the statement (symbol or ISO code), or the one that clearly dominates; null when none. */
    val currencyCode: String?,
    val fractionDigits: Int,
    /** True when a printed running balance confirmed the direction of every line that carries one. */
    val balanceChecked: Boolean,
    /** Lines past [StatementParser.MAX_LINES] that were left out. */
    val leftOut: Int,
    /** It reads like a credit card statement (charges, "minimum due", posting dates). */
    val looksLikeCard: Boolean,
    val summary: StatementSummary = StatementSummary(),
    /** Declined or failed lines, shown on the statement but not money that moved; never read as transactions. */
    val declined: Int = 0,
    /**
     * Whether the opening balance and the lines read add up to the closing balance; null when either balance isn't
     * printed. False means a line was missed or misread (or the statement has lines this scan didn't cover).
     */
    val addsUp: Boolean? = null,
)

/**
 * Reads statements and transaction histories from on-device OCR rows ([ReceiptParser.layoutRows]) or a PDF's text, and
 * tells them apart from receipts. Deterministic rules, never presented as AI (docs/CAPTURE.md "Scanning", docs/SCAN.md).
 *
 * - **Receipt or statement?** ([classify]) A receipt is one payment: many priced lines, one date, a total. A statement
 *   lists many payments: several lines that each carry a date and an amount (or dates heading groups of amounts, as in
 *   an e-wallet's history), running balances, money-in and money-out marks.
 * - **Each line** has a date (its own, the date heading its group, or the date printed under it), a description (what's
 *   left of the line), an amount, and a direction. The direction comes from the running balance when one is printed
 *   (the balance went down by the amount: money out), else the column the amount sits in, else a sign or mark next to
 *   the amount, else the section of the statement, else the words; a card statement's lines are charges unless they say
 *   payment, credit or refund. Anything still unclear is marked for the user.
 * - Summary lines (opening and closing balances, totals, minimum due, limits) are never transactions; they become the
 *   statement's [StatementSummary], and an opening balance anchors the running-balance check.
 */
object StatementParser {

    /** A statement of several pages or a PDF reads up to this many lines at once; the rest are never dropped silently. */
    const val MAX_LINES = 200

    // ----------------------------------------------------------------- dates

    private const val MONTH = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)(?![a-z])\\.?"
    private val ISO_DATE = Regex("(?<!\\d)(20\\d{2})\\s*[-/.年년]\\s*(\\d{1,2})\\s*[-/.月월]\\s*(\\d{1,2})(?!\\d)\\s*[日일]?")
    /** Not followed by more of a number ("1,250.00"): a comma or dot may follow only when no digit comes after it. */
    private const val END = "(?!\\d|[.,:]\\d)"
    private val NUMERIC_DATE = Regex("(?<![\\d.,])(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4}|\\d{2})$END")
    /** "21. Dezember 2026", "21 de noviembre de 2026", "3 févr. 2026": other languages' months, always with a full year. */
    private val TEXT_DMY_INTL = Regex(
        "(?<![\\d.,])(\\d{1,2})\\.?\\s*(?:de\\s+)?(${MonthWords.pattern})(?![\\p{L}])\\.?,?\\s*(?:de\\s+)?(\\d{4})$END",
        RegexOption.IGNORE_CASE,
    )
    private val TEXT_DMY = Regex("(?<![\\d.,])(\\d{1,2})(?:st|nd|rd|th)?[\\s-]?$MONTH(?:[\\s,-]+('?\\d{2}$END|\\d{4}$END))?", RegexOption.IGNORE_CASE)
    private val TEXT_MDY = Regex("(?<![a-z])$MONTH\\s*(\\d{1,2})(?:st|nd|rd|th)?$END(?:,?\\s+(\\d{4})$END)?", RegexOption.IGNORE_CASE)
    /** "11/03", "3-15" — but not a span of days ("7-15 banking days", "3-5 business days"). */
    private val SHORT_DATE = Regex("(?<![\\d.,/-])(\\d{1,2})[/-](\\d{1,2})(?![\\d/-])(?!\\s*(?:banking|business|working|calendar)?\\s*(?:days?|hours?|mins?|weeks?|months?)\\b)", RegexOption.IGNORE_CASE)
    private val DOTTED_SHORT_DATE = Regex("(?<![\\d.,])(\\d{1,2})\\.(\\d{1,2})\\.(?!\\d)")
    /** "02.10" opening a line of a European statement (day.month, no year), and the value date beside it. */
    private val DOTTED_DAY_MONTH = Regex("(?<![\\d.,])(\\d{2})\\.(\\d{2})(?![\\d.,])")
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
    private val CURRENCY = "(?:(?<![A-Za-z])(?:${ReceiptParser.ISO_CODES.joinToString("|")}|Php|Rp|RM|Rs|P)(?![A-Za-z])|\\p{Sc})"
    /**
     * "-500", "-₱500", "- PHP 500", "+500", "(500" — a spaced dash with no currency is punctuation, not a sign, and a
     * dash between digits or letters ("Account Ending 1-41004", "REF A-123") joins them.
     */
    private val SIGN_BEFORE = Regex("(?<![\\p{L}\\p{N}])([-−–+])(?:\\s*$CURRENCY\\s*)?$|\\(\\s*(?:$CURRENCY\\s*)?$")
    private val CURRENCY_BEFORE = Regex("$CURRENCY\\s*$")
    private val MARK_AFTER = Regex("^(?:\\s*\\(?\\s*(cr|dr)\\b\\s*\\)?|(-)(?!\\d)|\\s*(\\)))", RegexOption.IGNORE_CASE)

    /** The space inside "2 145,80" or "1 234 567,89": thousands grouping, when a decimal comma ends the number. */
    private val SPACE_GROUPED = Regex("(?<=\\b\\d{1,3}) (?=\\d{3}(?:[ \\u00A0]\\d{3})*,\\d{2}(?!\\d))")

    /** [left]/[right]: where the amount sits on the page, when positions are known. */
    private data class Amount(
        val value: BigDecimal,
        val flow: StatementFlow,
        val start: Int,
        val end: Int,
        val trailingMinus: Boolean,
        val left: Int? = null,
        val right: Int? = null,
        /** Printed with a minus or in brackets (not CR/DR): a debit on a bank statement, a credit on a card's. */
        val negative: Boolean = false,
    ) {
        val signed: BigDecimal get() = if (negative) value.negate() else value
    }

    // ----------------------------------------------------------------- rows

    private enum class Kind { HEADER, SECTION, SUMMARY, TRANSACTION, DATE_ONLY, TEXT, NOISE }

    private class Row(
        val text: String,
        val dates: List<RawDate>,
        val hasTime: Boolean,
        val amounts: List<Amount>,
        val description: String,
        val kind: Kind,
        val opening: Boolean,
        /** For a [Kind.SECTION] row (or a summary row naming one), the direction the section's lines take. */
        val section: StatementFlow? = null,
        val layout: LayoutRow? = null,
    )

    private val COLUMN_WORDS = Regex(
        "\\b(date|description|particulars|details|debit|credit|amount|balance|withdrawals?|deposits?|reference|ref|transaction|posting|post|sale|trans|" +
            "narration|remarks|check|fecha|concepto|importe|saldo|libell[ée]|d[ée]bit|cr[ée]dit|solde|buchungstag|wertstellung|verwendungszweck|betrag|" +
            "buchungstext|data|descrizione|importo|omschrijving|bedrag|tanggal|keterangan|jumlah|mutasi)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val OPENING = Regex(
        "\\b(beginning|opening|previous|starting)\\s+(statement\\s+)?balance\\b|balance\\s+(brought\\s+)?forward|\\bbrought\\s+forward\\b|\\bb/f\\b|" +
            "saldo\\s+(anterior|inicial|iniziale|awal|alt|vorher|precedente)|ancien\\s+solde|solde\\s+(pr[ée]c[ée]dent|initial)|" +
            "alter\\s+kontostand|anfangssaldo|beginsaldo|vorsaldo|(?:ü|ue)bertrag|saldo\\s+anterior|report\\s+solde|前月繰越|繰越",
        RegexOption.IGNORE_CASE,
    )
    private val CLOSING = Regex(
        "\\b(ending|closing|new|current\\s+statement|statement)\\s+balance\\b|\\bbalance\\s+carried\\s+forward\\b|\\bcarried\\s+forward\\b|\\bc/f\\b|" +
            "saldo\\s+(final|actual|finale|akhir|neu)|nouveau\\s+solde|solde\\s+(final|nouveau)|neuer\\s+kontostand|endsaldo|eindsaldo|" +
            "total\\s+balance(\\s+due)?|total\\s+amount\\s+due|現在残高|残高",
        RegexOption.IGNORE_CASE,
    )

    /** "Kontostand am 30.09.2026", "SOLDE CREDITEUR AU 31.10.2026", "Balance on Aug 31": opening or closing by where it stands. */
    private val BALANCE_AT = Regex(
        "\\bkontostand\\b|\\bsolde\\b(?!\\s+(pr[ée]c[ée]dent|initial|final|nouveau))|\\bsaldo\\s+(al|a|per|am|zum)\\b|\\bbalance\\s+(as\\s+)?(on|at|of)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val SUMMARY = Regex(
        "\\b(ending|closing|current|available|outstanding|ledger|statement|new)\\s+balance\\b|\\bcarried\\s+forward\\b|\\bc/f\\b|" +
            "\\bsub\\s*-?\\s*total\\b|\\btotals?\\b|\\bminimum\\b|\\bamount\\s+due\\b|\\bcredit\\s+(limit|line)\\b|\\bavailable\\s+credit\\b|" +
            "\\bpayment\\s+due\\b|\\bdue\\s+date\\b|\\bpage\\s+\\d|\\bstatement\\s+(date|period)\\b|\\bperiod\\s+covered\\b|\\bcash\\s+advance\\s+limit\\b|" +
            "\\bpast\\s+due\\b|\\bdaily\\s+balance\\b|\\baverage\\s+balance\\b|\\brewards?\\s+balance\\b|" +
            "\\bsaldo\\s+(final|actual|disponible|contable|finale|akhir|neu)\\b|\\bsolde\\b|\\bkontostand\\b|\\bendsaldo\\b|\\beindsaldo\\b|\\bsumme\\b|" +
            "\\bpago\\s+m[íi]nimo\\b|\\bl[íi]mite\\s+de\\s+cr[ée]dito\\b|" +
            // An account summary's arithmetic ("Less: Payments/Credits", "Add: Finance Charges"), limits, rewards.
            "^\\s*(less|add|plus|minus)\\s*:|\\blimit\\b|\\bearned\\s+this\\s+(period|cycle|month|statement)\\b|\\bcash\\s*-?\\s*back\\s+(bonus|earned)|" +
            "\\b(fee|billing|statement|interest|service)\\s+period\\b|\\bavailable\\s+cash\\b|" +
            "\\brewards?\\s+(earned|balance|points)\\b|\\bpoints\\s+(earned|balance)\\b|" +
            // Japanese statements: carried forward, totals, balance.
            "前月繰越|繰越|合計|残高|お支払金額|お預り金額",
        RegexOption.IGNORE_CASE,
    )

    /** A heading that names a transaction's description column: a table of transactions, not of balances or rates. */
    private val TABLE_OF_TRANSACTIONS = Regex(
        "\\b(description|details|transaction|narration|particulars|merchant|payee|reference|libell[ée]|concepto|verwendungszweck|buchungstext|descrizione|omschrijving|keterangan)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Tables that list balances or rates, not transactions: their rows are passed over until the next section. */
    private val SECTION_SKIP = Regex(
        "^(?:daily\\s+(?:ledger\\s+|ending\\s+)?balances?(?:\\s+summary)?|balance\\s+summary|interest\\s+charge\\s+calculation|" +
            "annual\\s+percentage\\s+rates?(?:\\s+apr)?|rewards?\\s+summary|your\\s+rewards|cash\\s*back\\s+summary|(?:monthly\\s+)?(?:service\\s+)?fees?\\s+summary|" +
            "totals?\\s+year\\s+to\\s+date|year\\s+to\\s+date\\s+totals?|summary\\s+of\\s+(?:fees|interest|account))(?:\\s+continued)?$",
        RegexOption.IGNORE_CASE,
    )

    /** Section headings that give their lines' direction. Matched against a row's whole words. */
    private val SECTION_IN = Regex(
        "^(?:(?:electronic\\s+|other\\s+)?deposits?(?:\\s*(?:and|&)\\s*(?:other\\s+)?(?:additions|credits?))?|additions|(?:other\\s+)?credits?|" +
            "money\\s+in|paid\\s+in|incoming(?:\\s+transfers)?|payments?(?:\\s*(?:and|&)?\\s*(?:other\\s+)?credits?)?(?:\\s*(?:and|&)?\\s*adjustments?)?|" +
            "payments?\\s+received|abonos|ingresos|cr[ée]dits|gutschriften|eing[äa]nge|accrediti|bijschrijvingen|dep[óo]sitos)(?:\\s+continued)?$",
        RegexOption.IGNORE_CASE,
    )
    private val SECTION_OUT = Regex(
        "^(?:(?:other\\s+)?withdrawals?(?:\\s*(?:and|&)\\s*(?:other\\s+)?(?:subtractions|debits?))?|subtractions|(?:other\\s+)?debits?|money\\s+out|" +
            "paid\\s+out|outgoing(?:\\s+transfers)?|checks?(?:\\s+paid)?|cheques(?:\\s+paid)?|(?:atm|card)\\s*(?:&|and)?\\s*(?:debit\\s+card\\s+)?withdrawals|" +
            "electronic\\s+(?:withdrawals|payments|debits)|(?:standard\\s+)?purchases?(?:\\s*(?:and|&)\\s*adjustments)?|new\\s+charges|charges|" +
            "card\\s+purchases|fees(?:\\s+charged)?|service\\s+(?:fees|charges)|interest\\s+charged|cash\\s+advances|cargos|retiros|d[ée]bits|" +
            "lastschriften|ausg[äa]nge|addebiti|afschrijvingen)(?:\\s+continued)?$",
        RegexOption.IGNORE_CASE,
    )

    private val STATEMENT_WORDS = Regex(
        "\\b(statement|transaction\\s+history|transactions|posting\\s+date|post\\s+date|sale\\s+date|running\\s+balance|beginning\\s+balance|" +
            "ending\\s+balance|available\\s+balance|withdrawals?|deposits?|debits?|credits?|account\\s+(no|number)|period|kontoauszug|extracto|" +
            "relev[ée]|estratto|rekeningafschrift|mutasi|buchungstag|saldo|solde)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val RECEIPT_WORDS = Regex(
        "\\b(subtotal|sub\\s+total|change|tendered|vat|vatable|qty|cashier|official\\s+receipt|sales\\s+invoice|invoice|" +
            "thank\\s+you\\s+for|items?|table|server|guest|served)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** What only a card statement prints. Two different cues are needed (a bank statement may mention "credit card"). */
    private val CARD_CUES = listOf(
        "credit\\s+card\\s+(statement|account)", "card\\s+(no|number|ending)", "minimum\\s+(amount\\s+|payment\\s+)?due|minimum\\s+payment",
        "credit\\s+limit|credit\\s+line", "statement\\s+balance|new\\s+balance", "post(ing)?\\s+date", "sale\\s+date",
        "finance\\s+charge|interest\\s+charged|purchases?\\s+apr|\\bapr\\b", "cardholder|card\\s+holder", "available\\s+credit",
        "payment\\s+due\\s+date", "pago\\s+m[íi]nimo|tarjeta\\s+de\\s+cr[ée]dito|kreditkarte|carte\\s+de\\s+cr[ée]dit",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

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
    private val CARD_IN_WORDS = Regex("\\b(payment|thank\\s+you|credit|refund|reversal|rebate|cash\\s*back|adjustment\\s+credit|autopay|pymt|pmt)\\b", RegexOption.IGNORE_CASE)

    /** Whether the rows read as one receipt or as a list of transactions. */
    fun classify(lines: List<String>): ScanKind {
        val rows = analyze(lines.map { LayoutRow(it) }, fractionDigitsFor(lines, null))
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
        // A confirmation repeats one amount ("₱645.00 … Refund amount ₱645.00"); a list of payments has several.
        val distinctAmounts = transactions.flatMap { r -> r.amounts.map { it.value } }.distinct().size
        val runningBalance = transactions.count { it.amounts.size >= 2 } >= 2
        return when {
            // A bill or receipt with a few dated lines ("Payment received Sep 21", "due by Nov 15 … $98.00") and its own
            // total to pay is one payment, not a statement.
            withOwnDate <= 3 && !runningBalance && !looksLikeCard(text) && ReceiptParser.hasReceiptTotal(rows.map { it.text }) -> false
            // Several lines that each carry a date and an amount: a statement, whatever else is printed.
            withOwnDate >= 2 -> true
            // Dates heading (or under) groups of amounts, as in an app's history.
            dateRows >= 1 && distinctDates >= 1 && distinctAmounts >= 2 && (signed >= 2 || (statementCues >= 1 && dateRows >= 2)) &&
                receiptCues <= statementCues -> true
            // Money-in and money-out marks on several lines.
            signed >= 3 && distinctAmounts >= 2 && receiptCues == 0 -> true
            else -> false
        }
    }

    /** True when [text] reads like a credit card statement: at least two different things only card statements print. */
    fun looksLikeCard(text: String): Boolean = CARD_CUES.count { it.containsMatchIn(text) } >= 2

    private val SUMMARY_PAGE = Regex(
        "\\b(account\\s+summary|statement\\s+(period|date|summary)|beginning\\s+balance|ending\\s+balance|opening\\s+balance|closing\\s+balance|" +
            "previous\\s+balance|new\\s+balance|kontoauszug|relev[ée]\\s+de\\s+compte|estado\\s+de\\s+cuenta|extracto)\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A statement's summary page (balances, a card's minimum payment and limits, no transaction list of its own): it
     * belongs with the statement pages after it, not read as a receipt.
     */
    fun looksLikeSummaryPage(lines: List<String>): Boolean {
        val text = lines.joinToString("\n")
        return looksLikeCard(text) || (SUMMARY_PAGE.containsMatchIn(text) && !ReceiptParser.hasReceiptTotal(lines))
    }

    /**
     * A running balance on a card statement is what is owed, so it rises with a charge. Read that way only when the
     * document itself is plainly a card statement (three cues): a bank statement scanned while a card was the active
     * wallet keeps the bank's reading.
     */
    private fun balanceIsOwed(text: String): Boolean = CARD_CUES.count { it.containsMatchIn(text) } >= 3

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
    ): StatementReading = parseRows(lines.map { LayoutRow(it.trim()) }.filter { it.text.isNotEmpty() }, today, expectedCurrency, accountIsCard, dayFirst)

    /**
     * [parse] on rows that may carry their pieces' positions ([ReceiptParser.layoutRows], or a PDF's text with
     * positions): an amount then also takes its direction from the column it sits in, and a balance column is found by
     * its heading. [dateOrder] is the document's country's (when known), used before the phone's.
     */
    fun parseRows(
        layout: List<LayoutRow>,
        today: LocalDate,
        expectedCurrency: String? = null,
        accountIsCard: Boolean = false,
        dayFirst: Boolean? = null,
        dateOrder: DateOrder? = null,
    ): StatementReading {
        val clean = layout.filter { it.text.isNotBlank() }
        val texts = clean.map { it.text }
        val detected = DocumentCurrency.dominantPrinted(texts)
        val digits = fractionDigitsFor(texts, detected ?: expectedCurrency)
        val rows = analyze(clean, digits)
        val text = texts.joinToString("\n")
        val card = accountIsCard || looksLikeCard(text)
        val owed = balanceIsOwed(text)

        // Dates: the document's own day/month order, and a year for dates printed without one.
        val countryDayFirst = when (dateOrder) {
            DateOrder.DAY_FIRST -> true
            DateOrder.MONTH_FIRST -> false
            else -> null
        }
        val resolver = DateResolver(rows.flatMap { it.dates }, today, dayFirst, countryDayFirst)

        // Running balances: a header naming "balance", or most amount lines carrying two amounts.
        val transactionRows = rows.withIndex().filter { it.value.kind == Kind.TRANSACTION }
        val headerHasBalance = rows.any { it.kind == Kind.HEADER && Regex("balance|saldo|solde|kontostand", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
        val twoAmountRows = transactionRows.count { it.value.amounts.size >= 2 }
        val balanceColumn = headerHasBalance || (twoAmountRows >= 2 && twoAmountRows * 10 >= transactionRows.size * 6)

        // Stacked layouts (an app's history): a date row heads each group, or sits under each transaction.
        val firstUndated = transactionRows.firstOrNull { it.value.dates.isEmpty() }?.index
        val datesUnder = firstUndated != null &&
            rows.getOrNull(firstUndated - 1)?.kind != Kind.DATE_ONLY &&
            rows.getOrNull(firstUndated + 1)?.kind == Kind.DATE_ONLY

        data class Draft(
            val row: Int,
            val date: RawDate?,
            val description: String,
            val amount: Amount,
            val balance: BigDecimal?,
            val source: String,
            val section: StatementFlow?,
            val column: StatementFlow?,
        )

        // A statement whose lines carry their own dates: there a line without one is a summary, a note, a rate or a
        // foreign-currency detail ("Euro 18.40", "a late fee of up to $41.00"), not a transaction — unless it sits right
        // under a date of its own (a description wrapped above its amount).
        val ownDated = transactionRows.count { it.value.dates.isNotEmpty() }
        val datedStatement = !datesUnder && ownDated >= 4 && ownDated * 10 >= transactionRows.size * 6

        val drafts = ArrayList<Draft>()
        var heading: RawDate? = null
        var opening: BigDecimal? = null
        var section: StatementFlow? = null
        var skipping = false
        var columns: List<Column> = emptyList()
        rows.forEachIndexed { i, row ->
            if (skipping && row.kind == Kind.TRANSACTION) return@forEachIndexed
            if (datedStatement && row.kind == Kind.TRANSACTION && row.dates.isEmpty() &&
                rows.getOrNull(i - 1)?.kind != Kind.DATE_ONLY &&
                // A UK-style statement prints each day's date once: a same-day line still carries its running balance.
                !(balanceColumn && row.amounts.size >= 2) &&
                // A card's interest and fees are often listed without a date of their own.
                FinancialTerms.meaning(row.text, card).kind.let { it != LineKind.INTEREST_CHARGE && it != LineKind.FEE }
            ) {
                return@forEachIndexed
            }
            when (row.kind) {
                Kind.HEADER -> {
                    // A transactions table starts ("Date  Description  Amount"): a balance or rate table above has ended.
                    if (skipping && TABLE_OF_TRANSACTIONS.containsMatchIn(row.text)) skipping = false
                    row.layout?.let { l -> columnsOf(l).takeIf { it.isNotEmpty() }?.let { columns = it } }
                }
                Kind.SECTION -> {
                    skipping = row.section == StatementFlow.UNKNOWN
                    section = row.section?.takeIf { it != StatementFlow.UNKNOWN }
                }
                Kind.DATE_ONLY -> if (!datesUnder) heading = row.dates.first()
                Kind.SUMMARY -> {
                    if (row.opening && opening == null && drafts.isEmpty()) opening = row.amounts.lastOrNull()?.signed
                    // A section's "Total ..." line closes it (and a summary table being passed over).
                    if (TOTAL_WORD.containsMatchIn(row.text)) {
                        section = null
                        skipping = false
                    }
                }
                Kind.TRANSACTION -> {
                    val below = rows.getOrNull(i + 1)
                    val above = rows.getOrNull(i - 1)
                    val date = row.dates.firstOrNull()
                        ?: (if (datesUnder && below?.kind == Kind.DATE_ONLY) below.dates.first() else null)
                        ?: heading
                    if (row.dates.isNotEmpty()) heading = row.dates.first()
                    val placed = place(row.amounts, columns)
                    val amount = placed?.amount ?: if (balanceColumn && row.amounts.size >= 2) row.amounts.first() else row.amounts.last()
                    val balance = if (placed != null) placed.balance else if (balanceColumn && row.amounts.size >= 2) row.amounts.last().signed else null
                    // A line with no words of its own takes them from the line above (an app's title row) or below —
                    // unless those words name a balance ("Venmo balance" over "$137.25").
                    val borrowed = listOf(above, below).firstOrNull { it?.kind == Kind.TEXT }?.description.orEmpty()
                    if (row.description.isBlank() && BALANCE_LABEL.matches(borrowed.trim())) return@forEachIndexed
                    val description = row.description.ifBlank { borrowed }
                    drafts += Draft(i, date, description, amount, balance, row.text, section, placed?.flow)
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
                    // A card's printed balance is what is owed: it rises with a charge (money out of the card account).
                    when {
                        (previous - d.amount.value).compareTo(balance) == 0 -> flows[k] = if (owed) StatementFlow.IN else StatementFlow.OUT
                        (previous + d.amount.value).compareTo(balance) == 0 -> flows[k] = if (owed) StatementFlow.OUT else StatementFlow.IN
                    }
                }
                previous = balance
            }
        }
        var balanceChecked = false
        if (drafts.any { it.balance != null }) {
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

        // A bank statement that prints its debits with a minus ("-45,67") and its credits without one ("2.500,00"), as
        // German and many app histories do: there an unsigned amount is money in.
        val unsignedIsCredit = !card && drafts.count { it.amount.negative } >= 2 &&
            drafts.none { it.amount.flow == StatementFlow.IN } &&
            drafts.any { !it.amount.negative && !it.amount.trailingMinus && it.amount.flow == StatementFlow.UNKNOWN }

        var declined = 0
        val read = drafts.mapIndexedNotNull { k, d ->
            val words = d.description + " " + d.source
            val meaning = FinancialTerms.meaning(words, card)
            if (meaning.status == LineStatus.DECLINED) {
                declined++
                return@mapIndexedNotNull null
            }
            val (flow, evidence) = when {
                flows[k] != null -> flows[k]!! to FlowEvidence.BALANCE
                d.column != null -> d.column to FlowEvidence.COLUMN
                // A trailing minus is a credit on a card statement and a debit on a bank's.
                d.amount.trailingMinus -> (if (card) StatementFlow.IN else StatementFlow.OUT) to FlowEvidence.MARKER
                // A minus or brackets: money out of a bank account, but a payment or credit on a card (it lowers what's owed).
                d.amount.negative && card -> StatementFlow.IN to FlowEvidence.MARKER
                d.amount.flow != StatementFlow.UNKNOWN -> d.amount.flow to FlowEvidence.MARKER
                unsignedIsCredit -> StatementFlow.IN to FlowEvidence.MARKER
                d.section != null -> d.section to FlowEvidence.SECTION
                card -> when {
                    meaning.flow != null && meaning.kind != LineKind.OTHER && meaning.kind != LineKind.PURCHASE -> meaning.flow to FlowEvidence.WORDS
                    CARD_IN_WORDS.containsMatchIn(words) -> StatementFlow.IN to FlowEvidence.WORDS
                    else -> StatementFlow.OUT to FlowEvidence.CARD_DEFAULT
                }
                else -> {
                    val inWords = IN_WORDS.containsMatchIn(words)
                    val outWords = OUT_WORDS.containsMatchIn(words)
                    when {
                        meaning.flow != null -> meaning.flow to FlowEvidence.WORDS
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
                kind = refine(meaning.kind, flow, card),
                status = meaning.status,
            )
        }
        val summary = summaryOf(rows, resolver, opening, drafts.firstOrNull()?.row, drafts.lastOrNull()?.row)
        return StatementReading(
            lines = read.take(MAX_LINES),
            currencyCode = detected,
            fractionDigits = digits,
            balanceChecked = balanceChecked,
            leftOut = (read.size - MAX_LINES).coerceAtLeast(0),
            looksLikeCard = card,
            summary = summary,
            declined = declined,
            addsUp = if (read.size > MAX_LINES) null else addsUp(summary, read, card),
        )
    }

    private val TOTAL_WORD = Regex("\\btotal", RegexOption.IGNORE_CASE)

    /** "Venmo balance", "Available balance", "Wallet balance": a label for a balance. */
    private val BALANCE_LABEL = Regex("^(?:[\\p{L}&]+\\s+){0,3}balance$", RegexOption.IGNORE_CASE)

    /** A sentence of terms or warnings that happens to hold a date and an amount: never a transaction. */
    private val PROSE = Regex(
        "\\b(you\\s+may|if\\s+you|if\\s+we|may\\s+have\\s+to|up\\s+to\\s+\\p{Sc}?\\d|please|will\\s+be\\s+charged|we\\s+will|you\\s+will|" +
            "for\\s+more\\s+information|minimum\\s+payment\\s+warning|late\\s+payment\\s+warning|is\\s+shown\\s+in)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** A kind that contradicts the settled direction gives way: a "payment" that raised a card's debt is a charge. */
    private fun refine(kind: LineKind, flow: StatementFlow, card: Boolean): LineKind = when {
        kind == LineKind.PAYMENT_TO_CARD && card && flow == StatementFlow.OUT -> LineKind.PURCHASE
        kind == LineKind.PURCHASE && flow == StatementFlow.IN -> LineKind.REFUND
        kind == LineKind.FEE && flow == StatementFlow.IN -> LineKind.REFUND
        else -> kind
    }

    /** Opening ± every posted line = closing (on a card, charges raise what's owed and payments lower it). */
    private fun addsUp(summary: StatementSummary, lines: List<StatementLine>, card: Boolean): Boolean? {
        val opening = summary.opening ?: return null
        val closing = summary.closing ?: return null
        if (lines.isEmpty() || lines.any { it.flow == StatementFlow.UNKNOWN }) return null
        val net = lines.filter { it.status == LineStatus.POSTED }.fold(BigDecimal.ZERO) { acc, l ->
            val inward = l.flow == StatementFlow.IN
            if (card) (if (inward) acc - l.amount else acc + l.amount) else (if (inward) acc + l.amount else acc - l.amount)
        }
        return (opening + net).compareTo(closing) == 0
    }

    private fun fractionDigitsFor(lines: List<String>, expectedCurrency: String?): Int =
        CurrencyRules.fractionDigits(ReceiptParser.detectCurrency(lines) ?: expectedCurrency ?: "XXX").let { if (it > 3) 2 else it }

    // ----------------------------------------------------------------- columns

    private enum class Role { OUT, IN, BALANCE, AMOUNT }

    private class Column(val role: Role, val left: Int, val right: Int) {
        val center: Int get() = (left + right) / 2
    }

    private val ROLE_BALANCE = Regex("balance|saldo|solde|kontostand|\\bbal\\b", RegexOption.IGNORE_CASE)
    private val ROLE_OUT = Regex(
        "debit|withdraw|paid\\s*out|money\\s*out|charges?|\\bdr\\b|cargo|retiro|d[ée]bit|\\bsoll\\b|belastung|addebit|uscite|afschrijving|debet|subtraction|outflow|ausgang",
        RegexOption.IGNORE_CASE,
    )
    private val ROLE_IN = Regex(
        "credit|deposit|paid\\s*in|money\\s*in|\\bcr\\b|abono|ingreso|cr[ée]dit|\\bhaben\\b|gutschrift|accredit|entrate|bijschrijving|kredit|addition|inflow|eingang",
        RegexOption.IGNORE_CASE,
    )
    private val ROLE_AMOUNT = Regex("amount|importe|montant|betrag|importo|bedrag|jumlah|valor|\\bamt\\b", RegexOption.IGNORE_CASE)

    /** A header row's money columns, from its pieces' positions ("Withdrawals/Debits", "Deposits/Credits", "Balance"). */
    private fun columnsOf(row: LayoutRow): List<Column> {
        if (row.cells.size < 2) return emptyList()
        val columns = row.cells.mapNotNull { cell ->
            val words = row.text.substring(cell.start, cell.end)
            val role = when {
                ROLE_BALANCE.containsMatchIn(words) -> Role.BALANCE
                ROLE_OUT.containsMatchIn(words) && !ROLE_IN.containsMatchIn(words) -> Role.OUT
                ROLE_IN.containsMatchIn(words) && !ROLE_OUT.containsMatchIn(words) -> Role.IN
                ROLE_AMOUNT.containsMatchIn(words) -> Role.AMOUNT
                else -> null
            }
            role?.let { Column(it, cell.left, cell.right) }
        }
        // Columns are useful only when they tell money out from money in, or the amount from the balance.
        val roles = columns.map { it.role }.toSet()
        return if ((Role.OUT in roles && Role.IN in roles) || Role.BALANCE in roles) columns else emptyList()
    }

    private class Placed(val amount: Amount, val balance: BigDecimal?, val flow: StatementFlow?)

    /** The row's amounts placed under the header's columns; null when positions are missing or don't fit. */
    private fun place(amounts: List<Amount>, columns: List<Column>): Placed? {
        if (columns.isEmpty() || amounts.isEmpty() || amounts.any { it.left == null || it.right == null }) return null
        val spacing = columns.map { it.center }.sorted().zipWithNext { a, b -> b - a }.filter { it > 0 }.minOrNull() ?: 400
        fun distance(a: Amount, c: Column): Int {
            val center = (a.left!! + a.right!!) / 2
            return minOf(kotlin.math.abs(center - c.center), kotlin.math.abs(a.right - c.right))
        }
        val assigned = amounts.map { a -> a to columns.minByOrNull { distance(a, it) }?.takeIf { distance(a, it) <= spacing * 3 / 4 } }
        if (assigned.any { it.second == null }) return null
        val money = assigned.firstOrNull { it.second!!.role != Role.BALANCE } ?: return null
        val balance = assigned.lastOrNull { it.second!!.role == Role.BALANCE }?.first?.signed
        val flow = when (money.second!!.role) {
            Role.OUT -> StatementFlow.OUT
            Role.IN -> StatementFlow.IN
            else -> null
        }
        return Placed(money.first, balance, flow)
    }

    // ----------------------------------------------------------------- the statement's own facts

    private val MINIMUM = Regex("\\bmin(imum)?\\.?\\s+(payment|amount)?\\s*(due)?\\b|\\bminimum\\s+due\\b|pago\\s+m[íi]nimo|paiement\\s+minimum|mindestbetrag", RegexOption.IGNORE_CASE)
    private val DUE_DATE = Regex("\\b(payment\\s+)?due\\s+date\\b|\\bpay\\s+by\\b|\\bdue\\s+on\\b|fecha\\s+l[íi]mite|date\\s+d.[ée]ch[ée]ance|f[äa]lligkeit", RegexOption.IGNORE_CASE)
    private val LIMIT = Regex("\\b(total\\s+)?credit\\s+(limit|line)\\b|l[íi]mite\\s+de\\s+cr[ée]dito|kreditrahmen|plafond", RegexOption.IGNORE_CASE)
    private val AVAILABLE = Regex(
        "\\bavailable\\s+credit\\b|\\bcredit\\s+(line\\s+)?available\\b|\\bavailable\\s+credit\\s+line\\b|\\bavailable\\s+to\\s+spend\\b|cr[ée]dito\\s+disponible",
        RegexOption.IGNORE_CASE,
    )
    private val PERIOD = Regex(
        "\\b(statement|billing)\\s+period\\b|\\bperiod\\s+covered\\b|\\bfor\\s+the\\s+period\\b|\\bstatement\\s+(from|dates?)\\b|\\bopening/closing\\s+date\\b|" +
            "periodo|p[ée]riode|zeitraum|\\bfrom\\b.*\\b(to|through)\\b|\\bthrough\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The statement's own figures. [firstLine]/[lastLine] are the rows of its first and last transactions: an opening
     * balance printed below the first transaction is a page's carry-over, and a balance with a date ("Kontostand am …",
     * "Solde au …") is the opening above the transactions and the closing below them.
     */
    private fun summaryOf(rows: List<Row>, resolver: DateResolver, opening: BigDecimal?, firstLine: Int?, lastLine: Int?): StatementSummary {
        var summary = StatementSummary(opening = opening)
        rows.forEachIndexed { i, row ->
            val text = row.text
            val beforeLines = firstLine == null || i < firstLine
            val afterLines = lastLine != null && i > lastLine
            // An app's "Venmo balance" over its amount: the balance now, as the closing balance.
            if (row.kind == Kind.TEXT && BALANCE_LABEL.matches(row.description.trim())) {
                rows.getOrNull(i + 1)?.takeIf { it.kind == Kind.TRANSACTION && it.description.isBlank() }?.amounts?.lastOrNull()?.let {
                    if (summary.closing == null) summary = summary.copy(closing = it.signed)
                }
            }
            if (row.kind == Kind.SUMMARY) {
                val value = row.amounts.lastOrNull()?.signed
                when {
                    value == null -> Unit
                    OPENING.containsMatchIn(text) -> if (summary.opening == null && beforeLines) summary = summary.copy(opening = value)
                    CLOSING.containsMatchIn(text) -> summary = summary.copy(closing = value)
                    BALANCE_AT.containsMatchIn(text) && beforeLines -> if (summary.opening == null) summary = summary.copy(opening = value)
                    BALANCE_AT.containsMatchIn(text) && afterLines -> summary = summary.copy(closing = value)
                    MINIMUM.containsMatchIn(text) -> if (summary.minimumDue == null) summary = summary.copy(minimumDue = value)
                    AVAILABLE.containsMatchIn(text) -> if (summary.availableCredit == null) summary = summary.copy(availableCredit = value)
                    LIMIT.containsMatchIn(text) -> if (summary.creditLimit == null) summary = summary.copy(creditLimit = value)
                }
            }
            if (DUE_DATE.containsMatchIn(text) && summary.dueDate == null) {
                row.dates.firstOrNull()?.let { resolver.resolveAny(it) }?.let { summary = summary.copy(dueDate = it) }
            }
            if (PERIOD.containsMatchIn(text) && row.dates.size >= 2 && summary.periodStart == null) {
                val a = resolver.resolveAny(row.dates[0])
                val b = resolver.resolveAny(row.dates[1])
                if (a != null && b != null && !b.isBefore(a)) summary = summary.copy(periodStart = a, periodEnd = b)
            }
        }
        return summary
    }

    // ----------------------------------------------------------------- one row

    private fun analyze(rows: List<LayoutRow>, digits: Int): List<Row> = rows.map { analyzeRow(it, digits) }

    private fun analyzeRow(layout: LayoutRow, digits: Int): Row {
        val text = layout.text
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
        TEXT_DMY_INTL.findAll(current()).forEach { m ->
            val month = MonthWords.month(m.groupValues[2]) ?: return@forEach
            found += m.range.first to RawDate.Known(yearOf(m.groupValues[3]), month, m.groupValues[1].toInt())
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
        // A line that opens with "02.10" (day.month) is a European statement's: that and any other "dd.mm" on it are dates.
        if (DOTTED_DAY_MONTH.find(current())?.let { it.range.first == current().indexOfFirst { c -> !c.isWhitespace() } } == true) {
            DOTTED_DAY_MONTH.findAll(current()).forEach { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                if (a in 1..31 && b in 1..12) {
                    found += m.range.first to RawDate.Known(null, b, a)
                    blank(m.range)
                }
            }
        }
        RELATIVE.findAll(current()).forEach { m ->
            found += m.range.first to RawDate.Relative(if (m.value.equals("today", ignoreCase = true)) 0 else 1)
            blank(m.range)
        }
        val dates = found.sortedBy { it.first }.map { it.second }

        // Amounts, with any sign or mark around them. "2 145,80" (a space between thousands, a decimal comma, as French
        // banks print) is one number: the space becomes a non-breaking one, which the number pattern takes as grouping.
        val masked = ReceiptParser.maskNonMoney(current()).replace(SPACE_GROUPED, " ")
        val amounts = ArrayList<Amount>()
        ReceiptParser.numberTokens(masked, digits).forEach { token ->
            val before = masked.substring((token.range.first - 8).coerceAtLeast(0), token.range.first)
            val after = masked.substring(token.range.last + 1, (token.range.last + 6).coerceAtMost(masked.length))
            val sign = SIGN_BEFORE.find(before)
            val mark = MARK_AFTER.find(after)
            val currency = CURRENCY_BEFORE.find(before)
            // A bare integer is money only with a currency or a sign beside it ("₱500", "+500"); otherwise a code or count —
            // except on a dated line in a currency without decimals, where "ATM手数料  110" is ¥110.
            val wholeCurrencyLine = digits == 0 && dates.isNotEmpty() && masked.substring(token.range).length <= 7
            if (token.plainInteger && sign == null && currency == null && !wholeCurrencyLine) return@forEach
            // Digits glued to letters are a code ("P400LPL1234567", "IMPS/P2A"), unless the letters are a currency sign
            // before ("P500", "RM12.50") or a CR/DR mark after ("1,234.56CR").
            val glueAfter = masked.getOrNull(token.range.last + 1)?.isLetter() == true &&
                !Regex("^(?i)(cr|dr)(?![a-z])").containsMatchIn(masked.substring(token.range.last + 1))
            val glueBefore = masked.getOrNull(token.range.first - 1)?.isLetter() == true && currency == null
            if (glueAfter || glueBefore) return@forEach
            var flow = StatementFlow.UNKNOWN
            var trailingMinus = false
            var negative = false
            var start = token.range.first
            var end = token.range.last
            if (sign != null) {
                start = token.range.first - (before.length - sign.range.first)
                val symbol = sign.groupValues[1]
                when {
                    symbol == "+" -> flow = StatementFlow.IN
                    symbol.isNotEmpty() -> {
                        flow = StatementFlow.OUT
                        negative = true
                    }
                    // "(1,234.00)": an accounting negative, when the bracket closes after it.
                    mark?.groupValues?.get(3) == ")" -> {
                        flow = StatementFlow.OUT
                        negative = true
                    }
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
            val cell = layout.cellAt(token.range.first)
            amounts += Amount(
                token.value, flow, start.coerceAtLeast(0), end.coerceAtMost(masked.lastIndex), trailingMinus,
                left = cell?.left, right = cell?.right, negative = negative,
            )
        }
        amounts.forEach { blank(it.start..it.end) }

        val description = describe(String(chars))
        val opening = OPENING.containsMatchIn(text)
        val words = description.replace(Regex("[^\\p{L}&\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        val section = when {
            words.isEmpty() || words.length > 60 -> null
            SECTION_IN.matches(words) -> StatementFlow.IN
            SECTION_OUT.matches(words) -> StatementFlow.OUT
            // A table of balances or rates: UNKNOWN marks it to be passed over.
            SECTION_SKIP.matches(words) -> StatementFlow.UNKNOWN
            else -> null
        }
        // "Venmo balance  $137.25", "Available balance  ₱2,000": a balance, never a transaction.
        val balanceLine = amounts.isNotEmpty() && Regex("^(?:[\\p{L}&]+\\s+){0,3}balance$", RegexOption.IGNORE_CASE).matches(words)
        val kind = when {
            amounts.isEmpty() && dates.isEmpty() && section != null -> Kind.SECTION
            amounts.isEmpty() && COLUMN_WORDS.findAll(text).count() >= 2 && dates.isEmpty() -> Kind.HEADER
            opening || balanceLine || (amounts.isNotEmpty() && SUMMARY.containsMatchIn(text)) -> Kind.SUMMARY
            // "Deposits and Additions  2,500.00" in a summary block: a section's total, not a transaction.
            amounts.isNotEmpty() && section != null && dates.isEmpty() -> Kind.SUMMARY
            // "If we do not receive your payment by 11/08/26, you may have to pay a late fee of up to $41.00."
            amounts.isNotEmpty() && PROSE.containsMatchIn(text) && description.split(' ').size >= 6 -> Kind.TEXT
            amounts.isNotEmpty() -> Kind.TRANSACTION
            dates.isNotEmpty() -> Kind.DATE_ONLY
            description.count { it.isLetter() } >= 2 -> Kind.TEXT
            else -> Kind.NOISE
        }
        return Row(text, dates, hasTime, amounts, description, kind, opening, section, layout)
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
     * then the document's country's order, then the phone's order — and with only the phone's order the dates are marked
     * as unclear. Years not printed are the latest year that keeps the date on or before the newest full date printed (or
     * today).
     */
    private class DateResolver(raw: List<RawDate>, private val today: LocalDate, phoneDayFirst: Boolean?, countryDayFirst: Boolean?) {
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
            val spanDayFirst = span(true)
            val spanMonthFirst = span(false)
            when {
                forcedDayFirst != forcedMonthFirst -> {
                    dayFirst = forcedDayFirst
                    settled = true
                }
                numeric.size >= 2 && spanDayFirst != spanMonthFirst && minOf(spanDayFirst, spanMonthFirst) <= 62 -> {
                    dayFirst = spanDayFirst < spanMonthFirst
                    settled = true
                }
                countryDayFirst != null -> {
                    dayFirst = countryDayFirst
                    settled = true
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

        /** A summary date (a due date may be in the future): the same reading, without the "not in the future" rule. */
        fun resolveAny(raw: RawDate): LocalDate? = when (raw) {
            is RawDate.Known -> make(raw.year, raw.month, raw.day)
            is RawDate.Numeric -> make(raw.year, if (dayFirst) raw.second else raw.first, if (dayFirst) raw.first else raw.second)
            is RawDate.Relative -> today.minusDays(raw.daysAgo)
        }
    }
}
