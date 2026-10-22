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

        val type = detectType(withoutDate)
        val active = wallets
        val mentions = findWalletMentions(withoutDate, active)

        var walletId: Long? = null
        var toWalletId: Long? = null
        if (type == TransactionType.TRANSFER_OUT) {
            val from = mentions.firstOrNull { it.afterFrom } ?: mentions.firstOrNull { !it.afterTo }
            val to = mentions.firstOrNull { it.afterTo && it.wallet.id != from?.wallet?.id }
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
    private val transferWords = listOf(
        " transfer", " transferred ", " moved ", " move ", " withdrew ", " withdraw ", " deposited ", " deposit ",
        " top up ", " topped up ", " top-up ", " cash in ", " paid off my ", " pay off my ",
    )

    internal fun detectType(lower: String): TransactionType = when {
        transferWords.any { lower.contains(it) } -> TransactionType.TRANSFER_OUT
        // "paid my credit card" is a transfer into the card, not new spending.
        Regex(" (paid|pay) (my )?(credit )?card ").containsMatchIn(lower) -> TransactionType.TRANSFER_OUT
        incomeWords.any { lower.contains(it) } -> TransactionType.INCOME
        else -> TransactionType.EXPENSE
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

    private val AMOUNT = Regex("(?<![\\w.,])(\\d[\\d.,]*)(k|m)?(?![\\w])")

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

    /** Keywords per default category icon key. Custom category names are matched directly first. */
    private val categoryKeywords: Map<String, List<String>> = mapOf(
        "dining" to listOf("lunch", "dinner", "breakfast", "brunch", "coffee", "cafe", "restaurant", "food", "meal", "snack", "pizza", "burger", "drinks", "takeout", "merienda"),
        "groceries" to listOf("grocery", "groceries", "supermarket", "market", "palengke", "produce"),
        "transport" to listOf("grab", "taxi", "uber", "bus", "jeep", "jeepney", "train", "mrt", "lrt", "fuel", "gas", "petrol", "diesel", "parking", "toll", "fare", "ride", "tricycle"),
        "housing" to listOf("rent", "mortgage", "condo dues", "association dues"),
        "utilities" to listOf("electricity", "electric", "power bill", "water bill", "internet", "wifi", "phone bill", "utility", "utilities", "load"),
        "health" to listOf("medicine", "pharmacy", "doctor", "hospital", "clinic", "dentist", "vitamins", "checkup"),
        "education" to listOf("tuition", "school", "books", "course", "class", "training"),
        "entertainment" to listOf("movie", "movies", "cinema", "concert", "game", "games", "bowling", "karaoke"),
        "shopping" to listOf("clothes", "shoes", "shopping", "lazada", "shopee", "amazon", "mall", "gadget"),
        "travel" to listOf("hotel", "flight", "airbnb", "trip", "travel", "vacation"),
        "personal" to listOf("haircut", "salon", "spa", "barber", "skincare"),
        "gift" to listOf("gift", "donation", "donate", "charity", "present"),
        "subscriptions" to listOf("netflix", "spotify", "subscription", "youtube premium", "icloud", "disney+", "membership"),
        "work" to listOf("salary", "payroll", "paycheck", "wage", "wages"),
        "business" to listOf("client", "sales", "invoice", "freelance", "commission", "sold"),
        "investments" to listOf("dividend", "dividends", "interest", "stocks"),
        "refund" to listOf("refund", "refunded", "reimbursement", "cashback"),
    )

    internal fun detectCategory(lower: String, type: TransactionType, categories: List<Category>): Long? {
        val kind = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val pool = categories.filter { it.kind == kind && !it.archived }
        pool.sortedByDescending { it.name.length }.firstOrNull { c ->
            Regex("(?<![\\w])" + Regex.escape(c.name.lowercase(Locale.ROOT)) + "(?![\\w])").containsMatchIn(lower)
        }?.let { return it.id }
        var best: Pair<Category, Int>? = null
        pool.forEach { c ->
            val words = categoryKeywords[c.iconKey] ?: return@forEach
            words.forEach { word ->
                val idx = Regex("(?<![\\w])" + Regex.escape(word) + "(?![\\w])").find(lower)?.range?.first
                if (idx != null && (best == null || idx < best!!.second)) best = c to idx
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
