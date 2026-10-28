package com.lucento.app.domain.capture

import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * A transaction proposed from free text. Every field is a suggestion: the UI always shows an editable
 * review before anything is saved. Null fields were not understood.
 */
data class QuickEntryProposal(
    val type: TransactionType,
    /** Amount in major units exactly as written (e.g. 250, 4.50); never rounded. */
    val amount: BigDecimal?,
    val walletId: Long?,
    /** Destination wallet for transfers. */
    val toWalletId: Long?,
    val categoryId: Long?,
    val date: LocalDate,
    val note: String,
    /** ISO code of a currency named in the text ("pesos", "€"), if any. */
    val mentionedCurrency: String?,
    /** Plain-language notes about what could not be understood or looks inconsistent. */
    val warnings: List<String>,
)

/**
 * Deterministic, offline interpreter for short English descriptions such as
 * "Spent 250 pesos on lunch from my cash wallet" or "got paid 52,000 salary yesterday".
 *
 * Nothing leaves the device. The parser never guesses an amount: if no number is found the amount is
 * null and the user types it in the review.
 */
object QuickEntryParser {

    fun parse(
        text: String,
        wallets: List<Wallet>,
        categories: List<Category>,
        today: LocalDate,
    ): QuickEntryProposal {
        val original = text.trim()
        val lower = " " + original.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") + " "
        val warnings = ArrayList<String>()

        val (date, withoutDate) = extractDate(lower, today)
        val mentionedCurrency = detectCurrency(withoutDate)
        val amount = extractAmount(withoutDate)
        if (amount == null) warnings += "No amount found — enter it below."

        val active = wallets
        val type = detectType(withoutDate).let { t ->
            if (t != TransactionType.TRANSFER_OUT && sendsToOwnWallet(withoutDate, active)) TransactionType.TRANSFER_OUT else t
        }
        val mentions = findWalletMentions(withoutDate, active)

        var walletId: Long? = null
        var toWalletId: Long? = null
        if (type == TransactionType.TRANSFER_OUT) {
            // "paid my credit card 3000", "topped up GCash 500": the one account named is where the money goes.
            val into = mentions.singleOrNull()?.takeIf { !it.afterFrom && INTO_VERBS.containsMatchIn(withoutDate) }
            val from = if (into != null) null else mentions.firstOrNull { it.afterFrom } ?: mentions.firstOrNull { !it.afterTo }
            val to = into
                ?: mentions.firstOrNull { it.afterTo && it.wallet.id != from?.wallet?.id }
                ?: mentions.firstOrNull { it.wallet.id != from?.wallet?.id }
            walletId = from?.wallet?.id
            toWalletId = to?.wallet?.id
            // "withdrew 2000" with no accounts named: bank → cash when each is unambiguous.
            if (walletId == null && toWalletId == null && withoutDate.contains("withdr")) {
                walletId = active.singleOrNull { it.type == WalletType.BANK }?.id
                toWalletId = active.singleOrNull { it.type == WalletType.CASH }?.id
            }
            if (walletId == null || toWalletId == null) warnings += "Choose both accounts for this transfer."
        } else {
            walletId = mentions.firstOrNull()?.wallet?.id
                ?: mentionedCurrency?.let { code -> active.filter { it.currencyCode == code }.singleOrNull()?.id }
            if (walletId == null && active.size > 1) warnings += "Choose which account this belongs to."
            if (walletId == null && active.size == 1) walletId = active.single().id
        }

        val wallet = active.firstOrNull { it.id == walletId }
        if (mentionedCurrency != null && wallet != null && wallet.currencyCode != mentionedCurrency) {
            warnings += "You mentioned $mentionedCurrency but ${wallet.name} uses ${wallet.currencyCode}. Lucento won’t convert it."
        }

        val categoryId = if (type.isTransfer) null else detectCategory(withoutDate, type, categories)
        val note = extractNote(original, withoutDate)

        return QuickEntryProposal(
            type = type,
            amount = amount,
            walletId = walletId,
            toWalletId = toWalletId,
            categoryId = categoryId,
            date = date,
            note = note,
            mentionedCurrency = mentionedCurrency,
            warnings = warnings,
        )
    }

    // ---------------------------------------------------------------- type

    private val incomeWords = listOf(
        " received ", " receive ", " got paid", " earned ", " earn ", " salary", " income", " paid me",
        " refund", " bonus", " sold ", " payment from", " allowance", " dividend", " commission",
    )

    /** "got 20 from mom" and "received 20 from a friend": money coming from someone, not a purchase. */
    private val GOT_OR_RECEIVED_FROM = Regex("""\b(got|received|receive)\b.+\bfrom\b""")

    /** "mom gave me 20". */
    private val GAVE_ME = Regex("""\b(gave|give|gives) me\b""")
    private val transferWords = listOf(
        " transfer", " transferred ", " moved ", " move ", " withdrew ", " withdraw ", " deposited ", " deposit ",
        " top up ", " topped up ", " top-up ", " cash in ", " paid off my ", " pay off my ",
    )

    /** Verbs whose one named account is the destination of the transfer. */
    private val INTO_VERBS = Regex(" (top up|topped up|top-up|topup|cash in|cashed in|(paid|pay) (my )?(credit )?card|(paid|pay) off my) ")
    private val SEND_WORDS = Regex(" (sent|send|sending) ")

    /** "sent 5000 to savings": sending money to one of the user's own accounts is a transfer, not spending. */
    internal fun sendsToOwnWallet(lower: String, wallets: List<Wallet>): Boolean =
        SEND_WORDS.containsMatchIn(lower) && findWalletMentions(lower, wallets).any { it.afterTo }

    private val expenseWords = listOf(
        " spent ", " spend ", " spending ", " paid ", " pay ", " paying ", " bought ", " buy ", " purchased ",
        " purchase ", " cost ", " costs ", " expense ", " charged ", " ordered ", " tipped ",
    )

    internal fun detectType(lower: String): TransactionType = explicitType(lower) ?: TransactionType.EXPENSE

    /**
     * The type the words themselves state, or null when nothing in the text says it ("lunch 250"). Batch entry uses the
     * null to let a clause follow its neighbours ("received 5000 from a client and 2000 from a friend").
     */
    internal fun explicitType(lower: String): TransactionType? = when {
        transferWords.any { lower.contains(it) } -> TransactionType.TRANSFER_OUT
        // "paid my credit card" is a transfer into the card, not new spending.
        Regex(" (paid|pay) (my )?(credit )?card ").containsMatchIn(lower) -> TransactionType.TRANSFER_OUT
        incomeWords.any { lower.contains(it) } ||
            GOT_OR_RECEIVED_FROM.containsMatchIn(lower) ||
            GAVE_ME.containsMatchIn(lower) -> TransactionType.INCOME
        expenseWords.any { lower.contains(it) } -> TransactionType.EXPENSE
        else -> null
    }

    // ---------------------------------------------------------------- date

    private val weekdays = DayOfWeek.entries.associateBy { it.name.lowercase(Locale.ROOT) }

    internal fun extractDate(lower: String, today: LocalDate): Pair<LocalDate, String> {
        Regex(" (the )?day before yesterday ").find(lower)?.let {
            return today.minusDays(2) to lower.replaceRange(it.range, " ")
        }
        Regex(" yesterday ").find(lower)?.let { return today.minusDays(1) to lower.replaceRange(it.range, " ") }
        Regex(" today | this morning | tonight | earlier ").find(lower)?.let { return today to lower.replaceRange(it.range, " ") }
        Regex(" (\\d{1,2}|a|one|two|three|four|five|six|seven) days? ago ").find(lower)?.let { m ->
            val n = when (val word = m.groupValues[1]) {
                "a", "one" -> 1L
                "two" -> 2L
                "three" -> 3L
                "four" -> 4L
                "five" -> 5L
                "six" -> 6L
                "seven" -> 7L
                else -> word.toLong()
            }
            return today.minusDays(n.coerceAtMost(366)) to lower.replaceRange(m.range, " ")
        }
        monthNameDate(lower, today)?.let { return it }
        Regex(" (last |on |this )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday) ").find(lower)?.let { m ->
            val day = weekdays.getValue(m.groupValues[2])
            var date = today
            // Most recent such day; "last friday" on a Friday means a week ago.
            val includeToday = m.groupValues[1].trim() != "last"
            if (!includeToday || date.dayOfWeek != day) {
                date = date.minusDays(1)
                while (date.dayOfWeek != day) date = date.minusDays(1)
            }
            return date to lower.replaceRange(m.range, " ")
        }
        return today to lower
    }

    private val months = mapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3, "april" to 4, "apr" to 4,
        "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7, "august" to 8, "aug" to 8, "september" to 9,
        "sept" to 9, "sep" to 9, "october" to 10, "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12,
    )
    private const val MONTH = "(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)\\.?"

    /**
     * "October 12", "Oct 12, 2026", "12 October", "the 12th of October". Without a year it is the most recent such
     * date (a date later this year is read as last year's): money is recorded after it moves. An impossible date
     * ("February 30") is not a date.
     */
    private fun monthNameDate(lower: String, today: LocalDate): Pair<LocalDate, String>? {
        val monthFirst = Regex(" (?:on )?$MONTH (\\d{1,2})(?:st|nd|rd|th)?(?:,? (\\d{4}))?(?=[\\s,.;!?])")
        val dayFirst = Regex(" (?:on )?(?:the )?(\\d{1,2})(?:st|nd|rd|th)? (?:of )?$MONTH(?:,? (\\d{4}))?(?=[\\s,.;!?])")
        val (match, month, day, year) = monthFirst.find(lower)?.let { m ->
            Quad(m, months.getValue(m.groupValues[1]), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull())
        } ?: dayFirst.find(lower)?.let { m ->
            Quad(m, months.getValue(m.groupValues[2]), m.groupValues[1].toInt(), m.groupValues[3].toIntOrNull())
        } ?: return null
        // "may" is also a verb; only read it as a month when a day number sits right next to it.
        val date = runCatching {
            if (year != null) {
                LocalDate.of(year, month, day)
            } else {
                val thisYear = LocalDate.of(today.year, month, day)
                if (thisYear.isAfter(today)) LocalDate.of(today.year - 1, month, day) else thisYear
            }
        }.getOrNull() ?: return null
        return date to lower.replaceRange(match.range, " ")
    }

    private data class Quad(val match: MatchResult, val month: Int, val day: Int, val year: Int?)

    // ---------------------------------------------------------------- currency & amount

    private val currencyWords: List<Pair<Regex, String>> = listOf(
        Regex("₱|\\bphp\\b|\\bpesos?\\b") to "PHP",
        Regex("€|\\beur\\b|\\beuros?\\b") to "EUR",
        Regex("£|\\bgbp\\b|\\bpounds?\\b") to "GBP",
        Regex("¥|\\bjpy\\b|\\byen\\b") to "JPY",
        Regex("₹|\\binr\\b|\\brupees?\\b") to "INR",
        Regex("\\bidr\\b|\\brupiah\\b") to "IDR",
        Regex("\\busd\\b|\\bdollars?\\b|\\$") to "USD",
    )

    internal fun detectCurrency(lower: String): String? = currencyWords.firstOrNull { (re, _) -> re.containsMatchIn(lower) }?.second

    /**
     * Finds the first written amount. Accepts "1,250.50", "1.250,50", "250", "4.5", "1.5k" and a leading or
     * trailing currency sign. A comma or dot followed by exactly three digits is a thousands separator.
     */
    internal fun extractAmount(lower: String): BigDecimal? {
        val match = AMOUNT.find(lower) ?: return null
        val raw = match.groupValues[1]
        val suffix = match.groupValues[2]
        val normalized = normalizeNumber(raw) ?: return null
        val value = runCatching { BigDecimal(normalized) }.getOrNull() ?: return null
        val scaled = when (suffix) {
            "k" -> value.multiply(BigDecimal(1000))
            "m" -> value.multiply(BigDecimal(1_000_000))
            else -> value
        }
        return if (scaled.signum() > 0) scaled.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it } else null
    }

    internal val AMOUNT = Regex("(?<![\\w.,])(\\d[\\d.,]*)(k|m)?(?![\\w])")

    /** A number written in the text, with what is around it, so batch entry can tell a price from a quantity. */
    internal data class AmountToken(
        val value: BigDecimal,
        val start: Int,
        val end: Int,
        /** A currency sign or word sits right next to it ("₱250", "250 pesos"). */
        val hasCurrency: Boolean,
        /** Looks like a price: two or more digits, a decimal part, a k/m suffix or a currency next to it. */
        val priceLike: Boolean,
    )

    internal val currencyBefore = Regex("(₱|€|£|¥|₹|\\$|\\bphp|\\busd|\\beur|\\bgbp|\\bjpy|\\binr|\\bidr)\\s?$")
    internal val currencyAfter = Regex("^\\s?(₱|€|£|¥|₹|\\$|php\\b|usd\\b|eur\\b|gbp\\b|jpy\\b|inr\\b|idr\\b|pesos?\\b|dollars?\\b|euros?\\b|pounds?\\b|yen\\b|rupees?\\b|rupiah\\b|bucks\\b)")

    /** Every positive number in [lower] (already lower-cased, dates removed), in order. */
    internal fun amountTokens(lower: String): List<AmountToken> = AMOUNT.findAll(lower).mapNotNull { m ->
        val raw = m.groupValues[1]
        val value = extractAmount(" " + m.value + " ") ?: return@mapNotNull null
        val before = lower.substring(0, m.range.first)
        val after = lower.substring(m.range.last + 1)
        val currency = currencyBefore.containsMatchIn(before) || currencyAfter.containsMatchIn(after)
        val digits = raw.count { it.isDigit() }
        val priceLike = currency || digits >= 2 || m.groupValues[2].isNotEmpty() || raw.trimEnd('.', ',').any { it == '.' || it == ',' }
        AmountToken(value, m.range.first, m.range.last + 1, currency, priceLike)
    }.toList()

    internal fun normalizeNumber(raw: String): String? {
        val s = raw.trimEnd('.', ',')
        if (s.isEmpty()) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        return when {
            lastDot < 0 && lastComma < 0 -> s
            lastDot >= 0 && lastComma >= 0 -> {
                // Both present: the later one is the decimal separator.
                if (lastDot > lastComma) s.replace(",", "") else s.replace(".", "").replace(',', '.')
            }
            else -> {
                val sep = if (lastDot >= 0) '.' else ','
                val parts = s.split(sep)
                val groupedThousands = parts.size > 1 && parts.drop(1).all { it.length == 3 } && parts.first().length in 1..3
                when {
                    groupedThousands -> parts.joinToString("")
                    parts.size == 2 -> parts[0] + "." + parts[1]
                    else -> null
                }
            }
        }
    }

    // ---------------------------------------------------------------- wallets

    private data class WalletMention(val wallet: Wallet, val index: Int, val afterFrom: Boolean, val afterTo: Boolean)

    private fun findWalletMentions(lower: String, wallets: List<Wallet>): List<WalletMention> {
        val found = ArrayList<WalletMention>()
        // Exact names first (longest first so "Cash savings" wins over "Cash").
        wallets.sortedByDescending { it.name.length }.forEach { w ->
            val name = w.name.lowercase(Locale.ROOT).trim()
            if (name.isEmpty()) return@forEach
            val idx = Regex("(?<![\\w])" + Regex.escape(name) + "(?![\\w])").find(lower)?.range?.first ?: return@forEach
            if (found.none { it.wallet.id == w.id || overlaps(it, idx, name.length) }) {
                found += mention(w, idx, lower)
            }
        }
        // Then generic type words, only when they identify exactly one wallet.
        typeWords.forEach { (re, type) ->
            val m = re.find(lower) ?: return@forEach
            val candidates = wallets.filter { it.type == type }
            val only = candidates.singleOrNull() ?: return@forEach
            if (found.none { it.wallet.id == only.id }) found += mention(only, m.range.first, lower)
        }
        return found.sortedBy { it.index }
    }

    private fun overlaps(existing: WalletMention, idx: Int, length: Int): Boolean =
        idx < existing.index + existing.wallet.name.length && existing.index < idx + length

    private val typeWords = listOf(
        Regex("\\bcredit card\\b|\\bcard\\b") to WalletType.CREDIT_CARD,
        Regex("\\bcash\\b") to WalletType.CASH,
        Regex("\\bbank\\b|\\baccount\\b") to WalletType.BANK,
        Regex("\\be-?wallet\\b") to WalletType.E_WALLET,
        Regex("\\bsavings\\b") to WalletType.SAVINGS,
        Regex("\\bloan\\b") to WalletType.LOAN,
    )

    private val fromWords = setOf("from", "using", "with", "via", "out")
    private val toWords = setOf("to", "into", "in")

    private fun mention(wallet: Wallet, index: Int, lower: String): WalletMention {
        val before = lower.substring(0, index).trimEnd()
        // The nearest preposition within the three preceding words decides the role.
        val preposition = before.split(' ').takeLast(3).lastOrNull { it in fromWords || it in toWords }
        return WalletMention(wallet, index, afterFrom = preposition in fromWords, afterTo = preposition in toWords)
    }

    // ---------------------------------------------------------------- categories

    /**
     * Keywords per category icon key (the defaults and the onboarding presets). Custom category names are matched
     * directly first. Order matters: more specific keys come first, so "coffee" goes to a "Cafes and coffee" category
     * when the user has one and to "Restaurants"/"Dining" otherwise.
     */
    private val categoryKeywords: List<Pair<String, List<String>>> = listOf(
        "cafe" to listOf("coffee", "cafe", "café", "latte", "cappuccino", "espresso", "starbucks", "milk tea", "tea"),
        "takeout" to listOf("takeout", "take-out", "delivery", "grabfood", "foodpanda", "food delivery"),
        "work_meals" to listOf("work lunch", "office lunch"),
        "convenience" to listOf("7-eleven", "7 eleven", "convenience store", "snacks", "snack"),
        "dining" to listOf("lunch", "dinner", "breakfast", "brunch", "coffee", "cafe", "restaurant", "food", "meal", "snack", "pizza", "burger", "drinks", "takeout", "merienda", "jollibee", "mcdonalds", "mcdonald's"),
        "groceries" to listOf("grocery", "groceries", "supermarket", "market", "palengke", "produce", "puregold", "savemore"),
        "fuel" to listOf("fuel", "gas", "petrol", "diesel", "gasoline"),
        "taxi" to listOf("grab", "taxi", "uber", "angkas", "joyride", "lyft"),
        "parking" to listOf("parking", "toll", "tolls", "rfid"),
        "transport" to listOf("grab", "taxi", "uber", "bus", "jeep", "jeepney", "train", "mrt", "lrt", "fuel", "gas", "petrol", "diesel", "parking", "toll", "fare", "ride", "tricycle", "commute", "beep"),
        "water" to listOf("water bill", "maynilad", "manila water"),
        "internet" to listOf("internet", "wifi", "wi-fi", "fiber", "fibre", "broadband", "pldt", "converge"),
        "phone" to listOf("phone bill", "load", "postpaid", "mobile plan", "phone plan"),
        "heating" to listOf("lpg", "gas tank", "heating"),
        "utilities" to listOf("electricity", "electric", "power bill", "meralco", "water bill", "internet", "wifi", "phone bill", "utility", "utilities", "load"),
        "mortgage" to listOf("mortgage", "amortization", "amortisation"),
        "housing" to listOf("rent", "mortgage", "condo dues", "association dues"),
        "pharmacy" to listOf("medicine", "medicines", "pharmacy", "drugstore", "mercury drug", "prescription", "vitamins"),
        "dental" to listOf("dentist", "dental"),
        "vision" to listOf("optometrist", "eyeglasses", "glasses", "contact lenses"),
        "fitness" to listOf("gym", "fitness", "yoga", "pilates"),
        "health" to listOf("medicine", "pharmacy", "doctor", "hospital", "clinic", "dentist", "vitamins", "checkup", "check-up", "consultation"),
        "grooming" to listOf("haircut", "barber", "salon"),
        "personal" to listOf("haircut", "salon", "spa", "barber", "skincare", "toiletries"),
        "pets" to listOf("pet food", "dog food", "cat food", "vet", "veterinary"),
        "baby" to listOf("diapers", "baby"),
        "tuition" to listOf("tuition", "course", "class", "training"),
        "education" to listOf("tuition", "school", "books", "course", "class", "training", "school supplies"),
        "books" to listOf("book", "books", "kindle"),
        "streaming" to listOf("netflix", "spotify", "youtube premium", "disney+", "hbo", "prime video", "apple music"),
        "subscriptions" to listOf("netflix", "spotify", "subscription", "youtube premium", "icloud", "disney+", "membership", "software"),
        "games" to listOf("game", "games", "steam", "playstation", "xbox", "nintendo"),
        "entertainment" to listOf("movie", "movies", "cinema", "concert", "game", "games", "bowling", "karaoke", "tickets"),
        "hotel" to listOf("hotel", "airbnb", "hostel", "resort"),
        "travel" to listOf("hotel", "flight", "airbnb", "trip", "travel", "vacation", "airfare"),
        "shopping" to listOf("clothes", "shoes", "shirt", "shirts", "shopping", "lazada", "shopee", "amazon", "mall", "gadget"),
        "home_supplies" to listOf("household", "cleaning supplies", "detergent"),
        "gift" to listOf("gift", "gifts", "birthday gift", "present"),
        "donation" to listOf("donation", "donate", "charity"),
        "bank_fees" to listOf("bank fee", "atm fee", "service charge"),
        "tax" to listOf("tax", "taxes"),
        "work" to listOf("salary", "payroll", "paycheck", "wage", "wages", "sweldo"),
        "business" to listOf("client", "sales", "invoice", "freelance", "commission", "sold", "project"),
        "investments" to listOf("dividend", "dividends", "interest", "stocks"),
        "refund" to listOf("refund", "refunded", "reimbursement", "cashback"),
    )

    internal fun detectCategory(lower: String, type: TransactionType, categories: List<Category>): Long? {
        val kind = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val pool = categories.filter { it.kind == kind && !it.archived }
        pool.sortedByDescending { it.name.length }.firstOrNull { c ->
            Regex("(?<![\\w])" + Regex.escape(c.name.lowercase(Locale.ROOT)) + "(?![\\w])").containsMatchIn(lower)
        }?.let { return it.id }
        // Earliest keyword in the text wins; on a tie (the same word), the more specific key wins.
        var best: Triple<Category, Int, Int>? = null
        pool.forEach { c ->
            val rank = categoryKeywords.indexOfFirst { it.first == c.iconKey }
            if (rank < 0) return@forEach
            categoryKeywords[rank].second.forEach { word ->
                val idx = Regex("(?<![\\w])" + Regex.escape(word) + "(?![\\w])").find(lower)?.range?.first ?: return@forEach
                val current = best
                if (current == null || idx < current.second || (idx == current.second && rank < current.third)) best = Triple(c, idx, rank)
            }
        }
        return best?.first?.id
    }

    // ---------------------------------------------------------------- note

    /**
     * A short description: the words after "on"/"for"/"at" up to an account or date phrase, e.g.
     * "lunch" from "spent 250 on lunch from my cash wallet". Falls back to empty (the category names it).
     */
    internal fun extractNote(original: String, lowerWithoutDate: String): String {
        val m = Regex(" (?:on|for|at) (.+?)(?= from | using | with | via | to | into | in | paid | yesterday | today |\\s*$)")
            .find(lowerWithoutDate) ?: return ""
        val phrase = m.groupValues[1]
            .replace(AMOUNT, " ")
            .replace(Regex("₱|€|£|¥|₹|\\$|\\bpesos?\\b|\\bdollars?\\b|\\beuros?\\b"), " ")
            .replace(Regex("^(my|a|an|the|some) "), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (phrase.isEmpty() || phrase.length > 60) return ""
        // Keep the user's own capitalisation where the phrase appears verbatim.
        val start = original.lowercase(Locale.ROOT).indexOf(phrase)
        val text = if (start >= 0) original.substring(start, start + phrase.length) else phrase
        return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}
