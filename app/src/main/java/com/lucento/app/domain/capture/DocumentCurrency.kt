package com.lucento.app.domain.capture

/** How sure a reading is of a document's currency. */
enum class CurrencyCertainty {
    /** An ISO code or a symbol that names one currency is printed (₱, €, £, US$, PHP), or one clearly dominates. */
    PRINTED,
    /** A shared symbol ($, ¥, kr, Rs) or none at all, settled by where the document is from (an address, a tax name). */
    COUNTRY,
    /** A shared symbol the document doesn't settle: the person's only account in such a currency was taken — to check. */
    ACCOUNT,
    /** Nothing says. */
    NONE,
}

data class CurrencyReading(
    val code: String?,
    val certainty: CurrencyCertainty,
    /** The shared symbol printed ("$", "¥", "kr", "Rs") when that is all the document shows. */
    val sharedSymbol: String? = null,
) {
    companion object {
        val NONE = CurrencyReading(null, CurrencyCertainty.NONE)
    }
}

/**
 * The currency of a document. Never the person's default currency just because it is theirs (docs/MULTICURRENCY.md):
 * what is printed decides; a shared symbol is settled by the document's country, else by the person's only account
 * that uses that symbol (marked to check); with nothing printed, a firm country decides; otherwise it stays unknown and
 * the account the row goes to applies, as before.
 */
object DocumentCurrency {

    /** Shared symbols and the currencies that print them. */
    private val families: Map<String, Set<String>> = mapOf(
        "$" to setOf(
            "USD", "CAD", "AUD", "NZD", "SGD", "HKD", "TWD", "MXN", "ARS", "CLP", "COP", "BSD", "BBD", "BMD", "BND", "BZD", "FJD",
            "GYD", "JMD", "KYD", "LRD", "NAD", "SBD", "SRD", "TTD", "XCD",
        ),
        "¥" to setOf("JPY", "CNY"),
        "kr" to setOf("SEK", "NOK", "DKK", "ISK"),
        "Rs" to setOf("INR", "PKR", "LKR", "NPR"),
    )

    /** A bare "$": not part of US$, C$, A$, S$, HK$, NZ$, NT$, MX$, R$ (those name their currency). */
    private val BARE_DOLLAR = Regex("(?<![A-Za-z])\\$")
    private val YEN = Regex("[¥￥]")
    private val KRONA = Regex("(?<![A-Za-z])(?:kr|Kr|KR)\\.?(?=\\s?\\d)|\\d\\s?(?:kr|Kr|KR)\\.?(?![A-Za-z])|(?<![A-Za-z])(?:SEK|NOK|DKK)(?![A-Za-z])")
    private val RUPEE = Regex("(?<![A-Za-z])Rs\\.?(?=\\s?\\d)")

    fun read(lines: List<String>, country: CountryReading, walletCurrencies: Collection<String>): CurrencyReading {
        dominantPrinted(lines)?.let { return CurrencyReading(it, CurrencyCertainty.PRINTED) }
        val text = lines.joinToString("\n")
        val symbol = when {
            BARE_DOLLAR.containsMatchIn(text) -> "$"
            YEN.containsMatchIn(text) -> "¥"
            KRONA.containsMatchIn(text) -> "kr"
            RUPEE.containsMatchIn(text) -> "Rs"
            else -> null
        }
        val local = DocumentLocale.currencyOf(country.code)
        if (symbol != null) {
            val family = families.getValue(symbol)
            if (local != null && local in family) return CurrencyReading(local, CurrencyCertainty.COUNTRY, symbol)
            val owned = walletCurrencies.filter { it in family }.toSet()
            return if (owned.size == 1) CurrencyReading(owned.single(), CurrencyCertainty.ACCOUNT, symbol) else CurrencyReading(null, CurrencyCertainty.NONE, symbol)
        }
        if (local != null && country.firm) return CurrencyReading(local, CurrencyCertainty.COUNTRY)
        return CurrencyReading.NONE
    }

    /**
     * The one currency printed, or the one that clearly dominates when a document also shows another (a hotel bill in
     * EUR with a USD equivalent line, a card statement with a foreign purchase): at least twice the lines of any other.
     */
    internal fun dominantPrinted(lines: List<String>): String? {
        val perLine = lines.map { ReceiptParser.detectCurrencies(it) }
        val counts = perLine.flatten().groupingBy { it }.eachCount()
        if (counts.isEmpty()) return null
        val ranked = counts.entries.sortedByDescending { it.value }
        val best = ranked.first()
        val second = ranked.getOrNull(1)?.value ?: 0
        return best.key.takeIf { second == 0 || best.value >= second * 2 }
    }
}
