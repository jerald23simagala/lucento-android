package com.lucento.app.domain.capture

import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/**
 * Turns one message into every transaction it describes, offline and deterministically:
 * "Spent 250 on lunch, 180 for coffee, paid 1200 for groceries, and received 35000 salary" → four candidates.
 *
 * 1. Number words become digits ([NumberWords]).
 * 2. The message is split into clauses at line breaks, sentence ends, `;`, list commas (never inside "1,200" or
 *    "October 12, 2026") and joining words ("and", "then", "plus", "also").
 * 3. A clause with no amount joins its neighbour ("fish and chips 300"), unless it states a type and a category on its
 *    own ("…and paid the electric bill"), in which case it becomes a row that asks for its amount.
 * 4. A clause with several prices and no punctuation is split by word order ("lunch 250 coffee 180",
 *    "spent 250 on lunch 180 on coffee").
 * 5. Each clause is read for amount, type, account, category, date and a short description. A clause that doesn't say
 *    income or expense follows its category, then the clause before it. A date written once ("yesterday I spent…")
 *    applies to the whole message.
 *
 * Nothing is invented: a missing amount stays empty, and competing amounts are offered as choices.
 */
object BatchEntryParser {

    const val MAX_ROWS = 30
    const val MAX_INPUT = 2_000

    fun parse(text: String, context: InterpretationContext): List<TransactionCandidate> {
        val input = NumberWords.normalize(text.take(MAX_INPUT)).trim()
        if (input.isBlank()) return emptyList()
        val raws = segments(input, context.today)
            .map { read(it, context) }
            .filter { it.relevant }
            .take(MAX_ROWS)
        if (raws.isEmpty()) return emptyList()

        val explicitDates = raws.mapNotNull { it.explicitDate }.distinct()
        val shared = explicitDates.singleOrNull()
        var carriedDate: LocalDate? = null
        var previousType: TransactionType? = null
        var previous: TransactionCandidate? = null
        return raws.map { raw ->
            val date = raw.explicitDate ?: shared ?: carriedDate ?: context.today
            if (raw.explicitDate != null) carriedDate = raw.explicitDate
            val transfer = previous?.takeIf { it.type.isTransfer }
            val candidate = if (transfer != null && isFee(raw, context)) {
                feeFor(transfer, raw, date, context)
            } else {
                val type = raw.explicitType
                    ?: raw.inferredType
                    ?: previousType
                    ?: context.typeHint?.takeIf { !it.isTransfer }
                    ?: TransactionType.EXPENSE
                if (!type.isTransfer) previousType = type
                build(raw, type, date, context)
            }
            previous = candidate
            candidate
        }
    }

    private val FEE = Regex("\\b(fee|fees|charge|charges|service charge)\\b")

    /** "…, fee 15" right after a transfer: the transfer's own cost, not another transfer. */
    private fun isFee(raw: Raw, context: InterpretationContext): Boolean =
        FEE.containsMatchIn(raw.lower) &&
            (raw.explicitType == null || raw.explicitType == TransactionType.EXPENSE) &&
            QuickEntryParser.parse(raw.clean, context.wallets, context.categories, context.today).let { p ->
                // A fee that names its own account is left to the normal rules.
                p.walletId == null || context.wallets.size == 1
            }

    /** The fee as an expense on the account the transfer left from (switching that account moves the fee too). */
    private fun feeFor(transfer: TransactionCandidate, raw: Raw, date: LocalDate, context: InterpretationContext): TransactionCandidate {
        val base = build(raw, TransactionType.EXPENSE, date, context)
        val generic = base.description.lowercase(Locale.ROOT) in setOf("", "fee", "fees", "charge", "charges", "transfer fee", "service charge")
        return base.copy(
            description = if (generic) "Transfer fee" else base.description,
            walletId = transfer.walletId ?: base.walletId,
            walletFromText = transfer.walletFromText,
        )
    }

    // ------------------------------------------------------------------ reading one clause

    private data class Raw(
        val source: String,
        val clean: String,
        val lower: String,
        val explicitDate: LocalDate?,
        val amount: BigDecimal?,
        val options: List<BigDecimal>,
        val amountFlag: CandidateFlag?,
        val explicitType: TransactionType?,
        val inferredType: TransactionType?,
        val relevant: Boolean,
    )

    private fun read(segment: String, context: InterpretationContext): Raw {
        val clean = cleanClause(segment)
        val padded = " " + clean.lowercase(Locale.ROOT) + " "
        val (date, lower) = QuickEntryParser.extractDate(padded, context.today)
        val explicitDate = if (lower != padded) date else null

        val tokens = QuickEntryParser.amountTokens(lower)
        val priced = tokens.filter { it.priceLike }
        val pool = priced.ifEmpty { tokens }
        var amount: BigDecimal? = null
        var options: List<BigDecimal> = emptyList()
        var flag: CandidateFlag? = null
        when {
            pool.isEmpty() -> flag = CandidateFlag.AMOUNT_MISSING
            pool.size == 1 -> amount = pool.single().value
            pool.count { it.hasCurrency } == 1 -> amount = pool.single { it.hasCurrency }.value
            pool.map { it.value }.distinct().size == 1 -> amount = pool.first().value
            else -> {
                flag = CandidateFlag.AMOUNT_AMBIGUOUS
                options = pool.sortedWith(compareByDescending<QuickEntryParser.AmountToken> { it.hasCurrency }.thenByDescending { it.value })
                    .map { it.value }
                    .distinct()
                    .take(4)
            }
        }

        val explicitType = QuickEntryParser.explicitType(lower).let { t ->
            if (t != TransactionType.TRANSFER_OUT && QuickEntryParser.sendsToOwnWallet(lower, context.wallets)) TransactionType.TRANSFER_OUT else t
        }
        val expense = QuickEntryParser.detectCategory(lower, TransactionType.EXPENSE, context.categories)
        val income = QuickEntryParser.detectCategory(lower, TransactionType.INCOME, context.categories)
        val inferred = when {
            expense != null && income == null -> TransactionType.EXPENSE
            income != null && expense == null -> TransactionType.INCOME
            else -> null
        }
        val relevant = tokens.isNotEmpty() || explicitType != null || expense != null || income != null
        return Raw(segment.trim(), clean, lower, explicitDate, amount, options, flag, explicitType, inferred, relevant)
    }

    private fun build(raw: Raw, type: TransactionType, date: LocalDate, context: InterpretationContext): TransactionCandidate {
        // Account mentions, transfer roles and the named currency come from the single-entry parser.
        val proposal = QuickEntryParser.parse(raw.clean, context.wallets, context.categories, context.today)
        val defaultWallet = context.wallets.firstOrNull { it.id == context.defaultWalletId }?.id ?: context.wallets.firstOrNull()?.id
        var walletId: Long?
        var toWalletId: Long? = null
        if (type.isTransfer) {
            walletId = proposal.walletId
            toWalletId = proposal.toWalletId
            if (walletId == null && toWalletId != null) walletId = defaultWallet?.takeIf { it != toWalletId }
        } else {
            walletId = proposal.walletId ?: defaultWallet
        }
        val categoryId = if (type.isTransfer) null else QuickEntryParser.detectCategory(raw.lower, type, context.categories)
        val flags = buildSet {
            raw.amountFlag?.let { add(it) }
            if (!type.isTransfer && categoryId == null) add(CandidateFlag.CATEGORY_UNMATCHED)
        }
        return TransactionCandidate(
            description = describe(raw.clean, raw.lower, context.wallets, type.isTransfer),
            type = type,
            amount = raw.amount,
            amountOptions = raw.options,
            walletId = walletId,
            toWalletId = toWalletId,
            categoryId = categoryId,
            date = date,
            mentionedCurrency = proposal.mentionedCurrency,
            sourceText = raw.source,
            flags = flags,
            walletFromText = proposal.walletId != null,
        )
    }

    private fun cleanClause(segment: String): String = segment
        .replace(Regex("[,;!?]+(?=\\s|$)"), " ")
        .replace(Regex("\\.+\\s*$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    // ------------------------------------------------------------------ description

    private val FILLER = setOf(
        "i", "i'm", "im", "i've", "ive", "we", "my", "me", "our", "a", "an", "the", "some", "spent", "spend", "paid", "pay",
        "bought", "buy", "purchased", "purchase", "got", "get", "received", "receive", "earned", "earn", "add", "added",
        "record", "log", "for", "on", "at", "from", "to", "into", "in", "of", "with", "using", "via", "worth", "about",
        "around", "just", "also", "and", "then", "plus", "total", "was", "were", "is", "it", "cost", "costs", "another",
        "more", "again", "only", "roughly", "approx", "approximately", "k", "m", "please",
    )
    private val CURRENCY_WORDS = setOf(
        "php", "usd", "eur", "gbp", "jpy", "inr", "idr", "peso", "pesos", "dollar", "dollars", "euro", "euros", "pound",
        "pounds", "yen", "rupee", "rupees", "rupiah", "bucks",
    )
    private val TRANSFER_WORDS = setOf(
        "transfer", "transferred", "moved", "move", "sent", "send", "withdrew", "withdraw", "deposited", "deposit", "top", "topped", "up",
        "cash-in", "off", "wallet", "account", "bank", "card", "credit", "savings", "e-wallet", "ewallet", "cash", "loan",
    )

    /**
     * A short label for the row: the phrase after "on/for/at" ("lunch"), otherwise the words left once amounts,
     * currencies, verbs, dates and account names are removed ("Freelance work", "Electricity bill"). The user's own
     * capitalisation is kept. Empty when nothing is left; the row then shows its category.
     */
    internal fun describe(clean: String, lowerWithoutDate: String, wallets: List<Wallet>, transfer: Boolean): String {
        if (!transfer) {
            QuickEntryParser.extractNote(clean, lowerWithoutDate).takeIf { it.isNotBlank() }?.let { return it }
        }
        var rest = lowerWithoutDate
        wallets.sortedByDescending { it.name.length }.forEach { w ->
            val name = w.name.lowercase(Locale.ROOT).trim()
            if (name.isNotEmpty()) rest = rest.replace(Regex("(?<![\\w])" + Regex.escape(name) + "(?![\\w])"), " ")
        }
        rest = rest.replace(QuickEntryParser.AMOUNT, " ").replace(Regex("[₱€£¥₹$]"), " ")
        val casing = HashMap<String, String>()
        clean.split(WORD_SPLIT).forEach { word -> casing.putIfAbsent(word.lowercase(Locale.ROOT), word) }
        val words = rest.split(WORD_SPLIT).filter { w ->
            w.isNotBlank() && w.any { it.isLetter() } && w !in FILLER && w !in CURRENCY_WORDS && (!transfer || w !in TRANSFER_WORDS)
        }
        var phrase = words.joinToString(" ") { casing[it] ?: it }
        if (phrase.length > 60) phrase = phrase.take(60).substringBeforeLast(' ')
        return phrase.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}'&+\\-]+")

    // ------------------------------------------------------------------ segmentation

    private const val MONTHS = "(?:january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)"
    private val MONTH_DAY_END = Regex("(?i)$MONTHS\\.?\\s+\\d{1,2}(?:st|nd|rd|th)?\\s*$")
    private val DAY_MONTH_END = Regex("(?i)\\d{1,2}(?:st|nd|rd|th)?\\s+(?:of\\s+)?$MONTHS\\s*$")
    private val YEAR_START = Regex("^\\s*(?:19|20)\\d{2}(?!\\d)")
    private val MONTH_ABBREV_END = Regex("(?i)(?<![a-z])(?:jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)$")
    private val CONJUNCTION = Regex("(?i)\\s+(?:and then|and also|as well as|and|then|plus|also|&)\\s+")
    private val LEADING_CONJUNCTION = Regex("(?i)^\\s*(?:and then|and also|and|then|plus|also|&)\\s+")

    private data class Piece(val text: String, val joiner: String)

    /** The clauses of [text], in order; each becomes at most a few candidates. */
    internal fun segments(text: String, today: LocalDate): List<String> {
        val pieces = ArrayList<Piece>()
        text.split(Regex("\\r?\\n")).forEach { line -> splitLine(line, pieces) }
        return merge(pieces, today)
            .flatMap { splitByWordOrder(it) }
            .map { it.trim() }
            .filter { s -> s.any { it.isLetterOrDigit() } }
    }

    private fun splitLine(line: String, out: MutableList<Piece>) {
        val punctuated = ArrayList<Piece>()
        var start = 0
        var joiner = " "
        for (i in line.indices) {
            val boundary = when (line[i]) {
                ';', '!', '?' -> true
                ',' -> !isThousandsComma(line, i) && !isYearComma(line, i)
                '.' -> i + 1 < line.length && line[i + 1].isWhitespace() && !MONTH_ABBREV_END.containsMatchIn(line.substring(0, i))
                else -> false
            }
            if (boundary) {
                punctuated += Piece(line.substring(start, i), joiner)
                joiner = line[i] + " "
                start = i + 1
            }
        }
        punctuated += Piece(line.substring(start), joiner)
        punctuated.forEach { piece ->
            val body = piece.text.replace(LEADING_CONJUNCTION, "")
            var cursor = 0
            var pieceJoiner = piece.joiner
            CONJUNCTION.findAll(body).forEach { m ->
                out += Piece(body.substring(cursor, m.range.first), pieceJoiner)
                pieceJoiner = m.value
                cursor = m.range.last + 1
            }
            out += Piece(body.substring(cursor), pieceJoiner)
        }
    }

    private fun isThousandsComma(line: String, i: Int): Boolean =
        i > 0 && line[i - 1].isDigit() &&
            (1..3).all { k -> i + k < line.length && line[i + k].isDigit() } &&
            (i + 4 >= line.length || !line[i + 4].isDigit())

    private fun isYearComma(line: String, i: Int): Boolean {
        val before = line.substring(0, i)
        return (MONTH_DAY_END.containsMatchIn(before) || DAY_MONTH_END.containsMatchIn(before)) &&
            YEAR_START.containsMatchIn(line.substring(i + 1))
    }

    /** Joins clauses that have no amount to a neighbour (see the class comment). */
    private fun merge(pieces: List<Piece>, today: LocalDate): List<String> {
        val result = ArrayList<String>()
        var pending: String? = null
        pieces.forEach { piece ->
            val text = piece.text.trim()
            if (text.isEmpty()) return@forEach
            val joiner = piece.joiner.ifBlank { " " }
            if (standsAlone(text, today)) {
                result += (pending?.let { it + joiner } ?: "") + text
                pending = null
            } else if (pending == null && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + joiner + text
            } else {
                pending = (pending?.let { it + joiner } ?: "") + text
            }
        }
        pending?.let { rest -> if (result.isEmpty()) result += rest else result[result.lastIndex] = result.last() + " " + rest }
        return result
    }

    private fun standsAlone(text: String, today: LocalDate): Boolean {
        val padded = " " + cleanClause(text).lowercase(Locale.ROOT) + " "
        val lower = QuickEntryParser.extractDate(padded, today).second
        if (QuickEntryParser.amountTokens(lower).isNotEmpty()) return true
        // "…and paid the electric bill": a type and a category of its own make a row that asks for its amount.
        return QuickEntryParser.explicitType(lower) != null && CATEGORY_HINT.containsMatchIn(lower)
    }

    /** Words that name what money was for, used only to decide whether an amount-less clause is its own row. */
    private val CATEGORY_HINT = Regex(
        "\\b(bill|rent|salary|groceries|grocery|lunch|dinner|breakfast|coffee|fare|tuition|fee|fees|subscription|insurance|" +
            "electricity|electric|water|internet|phone|load|gas|fuel|parking|medicine|doctor|gift|refund|bonus|allowance)\\b",
    )

    /**
     * Several prices in one unpunctuated clause. If words come before the first price, prices close each item
     * ("lunch 250 coffee 180"); otherwise prices open them ("spent 250 on lunch 180 on coffee"). A split is made only
     * when both sides keep a word of their own, so "12 eggs 95" stays one clause with two amounts to choose from.
     */
    private fun splitByWordOrder(segment: String): List<String> {
        val lower = segment.lowercase(Locale.ROOT)
        val dates = DATE_LIKE.findAll(lower).map { it.range }.toList()
        val tokens = QuickEntryParser.amountTokens(lower).filter { t -> t.priceLike && dates.none { t.start in it } }
        if (tokens.size < 2) return listOf(segment)
        val descriptionFirst = hasWords(lower.substring(0, tokens[0].start))
        val cuts = ArrayList<Int>()
        if (descriptionFirst) {
            for (k in 0 until tokens.lastIndex) {
                var cut = tokens[k].end
                QuickEntryParser.currencyAfter.find(lower.substring(cut))?.let { cut += it.range.last + 1 }
                if (cut > tokens[k + 1].start) continue
                val before = lower.substring(cuts.lastOrNull() ?: 0, tokens[k].start)
                if (hasWords(lower.substring(cut, tokens[k + 1].start)) && hasWords(before)) cuts += cut
            }
        } else {
            for (k in 1..tokens.lastIndex) {
                var cut = tokens[k].start
                QuickEntryParser.currencyBefore.find(lower.substring(0, cut))?.let { cut = it.range.first }
                if (cut < tokens[k - 1].end) continue
                val after = lower.substring(tokens[k].end, tokens.getOrNull(k + 1)?.start ?: lower.length)
                if (hasWords(after) && hasWords(lower.substring(tokens[k - 1].end, cut))) cuts += cut
            }
        }
        if (cuts.isEmpty()) return listOf(segment)
        val parts = ArrayList<String>()
        var from = 0
        cuts.forEach { cut ->
            parts += segment.substring(from, cut)
            from = cut
        }
        parts += segment.substring(from)
        return parts
    }

    private val DATE_LIKE = Regex(
        "(?<![a-z])$MONTHS\\.?\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+(?:19|20)\\d{2})?(?!\\d)|(?<![\\d.,])\\d{1,2}(?:st|nd|rd|th)?\\s+(?:of\\s+)?$MONTHS(?![a-z])(?:,?\\s+(?:19|20)\\d{2})?|(?<!\\d)\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?(?!\\d)",
    )

    private fun hasWords(text: String): Boolean = text.split(WORD_SPLIT).any { w ->
        w.any { it.isLetter() } && w !in FILLER && w !in CURRENCY_WORDS && w !in DATE_WORDS
    }

    private val DATE_WORDS = setOf("today", "yesterday", "tonight", "morning", "last", "ago", "day", "days", "this", "earlier")
}
