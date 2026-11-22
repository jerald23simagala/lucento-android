package com.lucento.app.domain.capture.merchant

import java.text.Normalizer
import java.util.Locale

/** What a name printed on a financial document stands for. */
enum class EntityKind {
    /** Sold the goods or service: a shop, restaurant, fuel station, airline, online store. */
    MERCHANT,
    /** Bills the person: utilities, telecoms, insurers, agencies, lenders, schools. */
    BILLER,
    /** Holds the account or issued the card. */
    BANK,
    /** A stored-value or person-to-person app: GCash, Maya, PayPal, Venmo, Cash App. */
    EWALLET,
    /** The card or mobile-pay scheme: Visa, Mastercard, American Express, Apple Pay. */
    CARD_NETWORK,
    /** Takes payments for someone else and prints its prefix before the merchant: Square "SQ *", Toast "TST*". */
    PROCESSOR;

    /** Who was paid, as opposed to how. */
    val isPayee: Boolean get() = this == MERCHANT || this == BILLER

    /** How it was paid: the card scheme, the app or the bank. */
    val isInstrument: Boolean get() = this == BANK || this == EWALLET || this == CARD_NETWORK
}

/** One business or payment provider from a bundled dictionary (assets/merchants, docs/SCAN.md "Merchant dictionaries"). */
data class FinancialEntity(
    val name: String,
    val kind: EntityKind,
    /** Lower-case names as printed on receipts and statements, matched as whole words. */
    val aliases: List<String>,
    /** Common words ("target", "shell") that count only at the start of a descriptor or a receipt's header line. */
    val weakAliases: List<String> = emptyList(),
    /** Descriptor prefixes a processor or app prints before the real merchant ("sq *", "paypal *"). */
    val prefixes: List<String> = emptyList(),
    /** Payees only: the category intent, in the vocabulary of category icon keys ([CategoryIntents]). */
    val category: String? = null,
    /** ISO 3166 countries where it trades under this name. One country is evidence of the document's country. */
    val countries: Set<String> = emptySet(),
    /** A card scheme that is also the issuer (American Express, Discover). */
    val issuesCards: Boolean = false,
    /** The dictionary it came from ("US", "PH", "GLOBAL", ...). */
    val region: String = "",
)

/** Where a name was found in [MerchantDirectory.normalize]d text. [weak]: found by a weak alias. */
data class EntityMention(val entity: FinancialEntity, val start: Int, val end: Int, val weak: Boolean)

/**
 * The merchant and payment-provider dictionaries, split by region and versioned (assets/merchants/<region>.json). The
 * engine below is the same for every region, so a dictionary grows by editing data, never parsing code; one person's own
 * habits come from [MerchantMemory], not from here. Everything is looked up on the phone.
 */
class MerchantDirectory(val entities: List<FinancialEntity>, val versions: Map<String, Int> = emptyMap()) {

    private class Entry(val alias: String, val entity: FinancialEntity, val weak: Boolean)

    /** Aliases by their first word, longest first, so a scan visits each word of the text once. */
    private val byFirstWord: Map<String, List<Entry>> = buildMap<String, MutableList<Entry>> {
        entities.forEach { e ->
            e.aliases.forEach { add(it, e, weak = false) }
            e.weakAliases.forEach { add(it, e, weak = true) }
        }
    }.mapValues { (_, list) -> list.sortedByDescending { it.alias.length } }

    private fun MutableMap<String, MutableList<Entry>>.add(raw: String, entity: FinancialEntity, weak: Boolean) {
        val alias = normalize(raw)
        if (alias.length < 3 || !alias.first().isLetterOrDigit()) return
        getOrPut(firstWord(alias, 0)) { ArrayList() }.add(Entry(alias, entity, weak))
    }

    private val prefixEntries: List<Pair<String, FinancialEntity>> = entities
        .flatMap { e -> e.prefixes.map { normalize(it) to e } }
        .filter { it.first.length >= 2 }
        .sortedByDescending { it.first.length }

    private val byName: Map<String, FinancialEntity> = entities.associateBy { normalize(it.name) }

    fun named(name: String): FinancialEntity? = byName[normalize(name)]

    /**
     * Every dictionary name in [text], left to right, longest match first and never overlapping. [text] is normalized
     * here; positions refer to the normalized text.
     */
    fun mentions(text: String): List<EntityMention> {
        val s = normalize(text)
        val found = ArrayList<EntityMention>()
        var i = 0
        while (i < s.length) {
            if (!s[i].isLetterOrDigit() || (i > 0 && s[i - 1].isLetterOrDigit())) {
                i++
                continue
            }
            val hit = byFirstWord[firstWord(s, i)]?.firstOrNull { e -> matchesAt(s, i, e.alias) }
            if (hit != null) {
                found += EntityMention(hit.entity, i, i + hit.alias.length, hit.weak)
                i += hit.alias.length
            } else {
                i++
            }
        }
        return found
    }

    /**
     * A processor's prefix in a card descriptor ("SQ *BLUE BOTTLE", "PAYPAL *NETFLIX", "PURCHASE 10/05 TST* THE HABIT"):
     * the processor and the words after its prefix (normalized), or null.
     */
    fun processor(descriptor: String): Pair<FinancialEntity, String>? {
        val s = normalize(descriptor)
        prefixEntries.forEach { (prefix, entity) ->
            var from = 0
            while (true) {
                val at = s.indexOf(prefix, from)
                if (at < 0) break
                val startsWord = at == 0 || !s[at - 1].isLetterOrDigit()
                // A prefix ending in a letter ("sp ") must be followed by a separator to be a prefix at all.
                val endsWell = !prefix.last().isLetterOrDigit() || at + prefix.length >= s.length || !s[at + prefix.length].isLetterOrDigit()
                if (startsWord && endsWell) {
                    val rest = s.substring(at + prefix.length).trimStart(' ', '*', '#', '-', '.').trim()
                    if (rest.any { it.isLetter() }) return entity to rest
                }
                from = at + 1
            }
        }
        return null
    }

    private fun matchesAt(s: String, at: Int, alias: String): Boolean {
        if (!s.regionMatches(at, alias, 0, alias.length)) return false
        val end = at + alias.length
        return !alias.last().isLetterOrDigit() || end >= s.length || !s[end].isLetterOrDigit()
    }

    companion object {
        val EMPTY = MerchantDirectory(emptyList())

        /** Reads the dictionary files in assets/merchants. A file or entity that can't be read is skipped, never fatal. */
        fun parse(files: List<String>): MerchantDirectory {
            val versions = LinkedHashMap<String, Int>()
            val entities = files.flatMap { text ->
                val file = runCatching { MiniJson.parse(text) as Map<*, *> }.getOrNull() ?: return@flatMap emptyList()
                val region = file["region"] as? String ?: return@flatMap emptyList()
                versions[region] = (file["version"] as? Number)?.toInt() ?: 1
                (file["entities"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let { e -> entityOf(e, region) } }
            }
            return MerchantDirectory(entities, versions)
        }

        private fun Map<*, *>.strings(key: String): List<String> = (this[key] as? List<*>).orEmpty().filterIsInstance<String>()

        private fun entityOf(e: Map<*, *>, region: String): FinancialEntity? {
            val name = (e["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val kind = runCatching { EntityKind.valueOf(e["kind"] as? String ?: return null) }.getOrNull() ?: return null
            val weakAliases = e.strings("weakAliases").map { it.trim() }.filter { it.isNotEmpty() }
            val weak = weakAliases.map { normalize(it) }.toSet()
            // The canonical name is an alias too ("Mercury Drug" matches "MERCURY DRUG"), unless it is a common word kept weak.
            val own = name.takeIf { normalize(it) !in weak }
            return FinancialEntity(
                name = name,
                kind = kind,
                aliases = (e.strings("aliases") + listOfNotNull(own)).map { it.trim() }.filter { it.isNotEmpty() && normalize(it) !in weak }.distinct(),
                weakAliases = weakAliases,
                prefixes = e.strings("prefixes").filter { it.isNotBlank() },
                category = (e["category"] as? String)?.takeIf { kind.isPayee },
                countries = e.strings("countries").map { it.uppercase(Locale.ROOT) }.toSet(),
                issuesCards = e["issuesCards"] == true,
                region = region,
            )
        }

        /**
         * The form names are compared in: accents removed, lower case, curly quotes straightened, runs of spaces as one.
         * "Intermarché" and "INTERMARCHE" compare equal.
         */
        fun normalize(text: String): String {
            val stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING, "")
            return stripped.lowercase(Locale.ROOT)
                .replace('’', '\'').replace('‘', '\'').replace('`', '\'')
                .replace(SPACES, " ")
                .trim()
        }

        private val COMBINING = Regex("\\p{Mn}+")
        private val SPACES = Regex("[\\s\\u00A0\\u202F]+")

        private fun firstWord(s: String, at: Int): String {
            var end = at
            while (end < s.length && s[end].isLetterOrDigit()) end++
            return s.substring(at, end)
        }
    }
}

/**
 * A small JSON reader for the dictionaries — objects, arrays, strings (with escapes), numbers, true, false, null — so
 * the domain layer stays plain Kotlin with no library. Throws on malformed input (the caller skips that file).
 */
internal object MiniJson {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.space()
        require(reader.at >= text.length) { "trailing data at ${reader.at}" }
        return value
    }

    private class Reader(val s: String) {
        var at = 0

        fun space() {
            while (at < s.length && s[at].isWhitespace()) at++
        }

        fun value(): Any? {
            space()
            require(at < s.length) { "unexpected end" }
            return when (val c = s[at]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw IllegalArgumentException("unexpected '$c' at $at")
            }
        }

        private fun word(w: String, v: Any?): Any? {
            require(s.startsWith(w, at)) { "expected $w at $at" }
            at += w.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            at++
            space()
            if (s[at] == '}') return out.also { at++ }
            while (true) {
                space()
                val key = string()
                space()
                require(s[at] == ':') { "expected : at $at" }
                at++
                out[key] = value()
                space()
                when (s[at++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw IllegalArgumentException("expected , or } at ${at - 1}")
                }
            }
        }

        private fun array(): List<Any?> {
            val out = ArrayList<Any?>()
            at++
            space()
            if (s[at] == ']') return out.also { at++ }
            while (true) {
                out += value()
                space()
                when (s[at++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw IllegalArgumentException("expected , or ] at ${at - 1}")
                }
            }
        }

        private fun string(): String {
            require(s[at] == '"') { "expected string at $at" }
            at++
            val out = StringBuilder()
            while (true) {
                val c = s[at++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> when (val e = s[at++]) {
                        '"', '\\', '/' -> out.append(e)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            out.append(s.substring(at, at + 4).toInt(16).toChar())
                            at += 4
                        }
                        else -> throw IllegalArgumentException("bad escape at ${at - 1}")
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun number(): Number {
            val start = at
            while (at < s.length && (s[at].isDigit() || s[at] in "+-.eE")) at++
            val text = s.substring(start, at)
            return text.toLongOrNull() ?: text.toDouble()
        }
    }
}
