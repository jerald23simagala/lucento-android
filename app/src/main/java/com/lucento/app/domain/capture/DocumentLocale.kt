package com.lucento.app.domain.capture

import com.lucento.app.domain.capture.merchant.MerchantDirectory

/** Where a document is from, as far as its own text shows; [code] is ISO 3166-1 alpha-2, null when it doesn't say. */
data class CountryReading(val code: String?, val score: Int = 0) {
    /** Strong enough to settle a currency the document doesn't print. */
    val firm: Boolean get() = code != null && score >= 3

    companion object {
        val UNKNOWN = CountryReading(null)
    }
}

/** How a country writes numeric dates. */
enum class DateOrder { MONTH_FIRST, DAY_FIRST, YEAR_FIRST }

/**
 * A document's country from its own words — an address, a tax name, a phone format, a country name, a script — and
 * what that means for its dates and currency (docs/SCAN.md "Country, currency and dates"). Deliberately cautious: a
 * country is named only when its evidence clearly outweighs any other's.
 */
object DocumentLocale {

    private class Cue(val country: String, val pattern: Regex, val weight: Int)

    private fun cue(country: String, weight: Int, pattern: String, ignoreCase: Boolean = true) =
        Cue(country, if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern), weight)

    private val US_STATES = "AL|AK|AZ|AR|CA|CO|CT|DE|DC|FL|GA|HI|ID|IL|IN|IA|KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|OH|OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY"

    private val cues = listOf(
        // United States: "SEATTLE, WA 98101", sales tax, toll-free numbers.
        cue("US", 3, "\\b($US_STATES)\\s+\\d{5}(?:-\\d{4})?\\b", ignoreCase = false),
        cue("US", 2, "\\bsales\\s+tax\\b"),
        cue("US", 3, "\\bunited\\s+states\\b|\\bU\\.S\\.A\\.?|\\bUSA\\b"),
        cue("US", 1, "\\b1[- ]?8(?:00|33|44|55|66|77|88)[- ]?\\d{3}[- ]?\\d{4}\\b"),
        cue("US", 1, "\\bzip(?:\\s*code)?\\b|\\bACH\\b|\\bZelle\\b|\\bVenmo\\b|\\bcheck\\s*#?\\s*\\d{3,}"),
        // Philippines: BIR receipt markings, VATable sales, city names.
        cue("PH", 3, "\\bVAT\\s*-?\\s*REG(?:\\.|ISTERED)?\\s*TIN\\b|\\bTIN\\s*[:#]?\\s*\\d{3}-\\d{3}-\\d{3}"),
        cue("PH", 3, "\\bphilippines\\b|\\bPHILS?\\b|\\bmetro manila\\b"),
        cue("PH", 2, "\\bVATable\\b|\\bVAT-?exempt\\b|\\bzero-?rated\\b|\\bPTU\\s*(?:no|#)|\\bBIR\\b|\\bMIN\\s*[:#]|\\bSN\\s*[:#]\\s*\\w"),
        cue("PH", 2, "\\b(?:quezon city|makati|pasig|taguig|mandaluyong|pasay|para[nñ]aque|caloocan|cebu city|davao city|iloilo|baguio)\\b"),
        cue("PH", 2, "\\bInstaPay\\b|\\bPESONet\\b|\\bGCash\\b|\\bMeralco\\b"),
        cue("PH", 1, "\\+63\\s?9\\d{2}|\\b09\\d{2}[- ]?\\d{3}[- ]?\\d{4}\\b"),
        // Canada
        cue("CA", 3, "\\b[ABCEGHJ-NPRSTVXY]\\d[ABCEGHJ-NPRSTV-Z]\\s?\\d[ABCEGHJ-NPRSTV-Z]\\d\\b", ignoreCase = false),
        cue("CA", 3, "\\bGST/HST\\b|\\bHST\\b|\\bQST\\b|\\bTPS\\b|\\bTVQ\\b|\\bcanada\\b"),
        cue("CA", 1, "\\bPST\\b|\\bInterac\\b"),
        // United Kingdom
        cue("GB", 3, "\\b[A-Z]{1,2}\\d[A-Z\\d]?\\s\\d[A-Z]{2}\\b", ignoreCase = false),
        cue("GB", 3, "\\bunited kingdom\\b|\\bVAT\\s*(?:reg(?:istration)?\\.?\\s*)?no\\.?\\s*:?\\s*GB"),
        cue("GB", 1, "\\blondon\\b|\\bengland\\b|\\bscotland\\b|\\bpaid\\s+out\\b|\\bsort\\s+code\\b"),
        // Australia, New Zealand
        cue("AU", 3, "\\bABN\\b|\\baustralia\\b|\\b(?:NSW|VIC|QLD|TAS|ACT)\\s+\\d{4}\\b"),
        cue("AU", 1, "\\bpty\\.?\\s+ltd\\b|\\bBSB\\b"),
        cue("NZ", 3, "\\bnew zealand\\b|\\bGST\\s*(?:no|number)\\b.*\\bNZ\\b|\\bauckland\\b|\\bwellington\\b"),
        // Asia
        cue("SG", 3, "\\bsingapore\\b|\\bUEN\\b|\\bGST\\s*reg(?:istration)?\\.?\\s*no\\b"),
        cue("HK", 3, "\\bhong kong\\b|\\bkowloon\\b"),
        cue("MY", 3, "\\bmalaysia\\b|\\bkuala lumpur\\b|\\bSST\\b"),
        cue("ID", 3, "\\bindonesia\\b|\\bNPWP\\b|\\bjakarta\\b"),
        cue("TH", 3, "\\bthailand\\b|\\bbangkok\\b"),
        cue("VN", 3, "\\bviet\\s?nam\\b|\\bha noi\\b|\\bho chi minh\\b"),
        cue("IN", 3, "\\bGSTIN\\b|\\bCGST\\b|\\bSGST\\b|\\bIGST\\b|\\bindia\\b|\\bUPI\\b"),
        cue("IN", 1, "\\bRs\\.?\\s?\\d|\\bIFSC\\b"),
        cue("JP", 3, "[\\u3040-\\u30FF]|消費税|税込|円|\\bjapan\\b|\\btokyo\\b"),
        cue("KR", 3, "[\\uAC00-\\uD7AF]|\\bkorea\\b|\\bseoul\\b"),
        cue("CN", 3, "人民币|发票|增值税|\\bRMB\\b"),
        // Han characters with no kana or hangul: Chinese (Japanese and Korean text carry their own scripts).
        cue("CN", 2, "^(?![\\s\\S]*[\\u3040-\\u30FF\\uAC00-\\uD7AF])[\\s\\S]*[\\u4E00-\\u9FFF]{2}"),
        cue("TW", 3, "統一編號|\\bNT\\$|\\btaiwan\\b|\\btaipei\\b"),
        // Middle East
        cue("AE", 3, "\\bunited arab emirates\\b|\\bUAE\\b|\\bdubai\\b|\\babu dhabi\\b|\\bsharjah\\b"),
        cue("AE", 1, "\\bTRN\\b"),
        cue("SA", 3, "\\bsaudi\\b|\\briyadh\\b|\\bjeddah\\b"),
        // Europe
        cue("CH", 3, "\\bMWST\\b|\\bschweiz\\b|\\bsuisse\\b|\\bsvizzera\\b|\\bCHE-\\d{3}"),
        cue("DE", 3, "\\bdeutschland\\b|\\bUSt-?IdNr\\b|\\bSteuer-?Nr\\b|\\bBuchungstag\\b|\\bVerwendungszweck\\b"),
        cue("DE", 1, "\\bMwSt\\b|\\bSumme\\b|\\bGutschrift\\b|\\bLastschrift\\b"),
        cue("FR", 3, "\\bfrance\\b|\\bSIRET\\b|\\bSIREN\\b"),
        cue("FR", 1, "\\bTVA\\b|\\bTTC\\b|\\blibell[ée]\\b"),
        cue("ES", 3, "\\bespa[ñn]a\\b|\\bNIF\\b|\\bCIF\\b"),
        cue("IT", 3, "\\bitalia\\b|\\bpartita\\s+iva\\b|\\bP\\.?\\s?IVA\\b"),
        cue("NL", 3, "\\bnederland\\b|\\bBTW\\b|\\bKvK\\b"),
        cue("IE", 3, "\\bireland\\b|\\beircode\\b"),
        // The Nordics share "kr": the country settles which krona or krone.
        cue("SE", 3, "\\bsverige\\b|\\bstockholm\\b|\\bg[öo]teborg\\b|\\bmalm[öo]\\b|\\bswish\\b|\\bkvitto\\b"),
        cue("SE", 1, "\\borg\\.?\\s*nr\\b|\\bmoms\\b"),
        cue("NO", 3, "\\bnorge\\b|\\boslo\\b|\\bbergen\\b|\\bvipps\\b|\\bkvittering\\b"),
        cue("NO", 2, "\\bmva\\b"),
        cue("DK", 3, "\\bdanmark\\b|\\bk[øo]benhavn\\b|\\bcvr\\b|\\bmobilepay\\b"),
        // Americas
        cue("MX", 3, "\\bRFC\\b|\\bm[ée]xico\\b|\\bCDMX\\b"),
        cue("BR", 3, "\\bCNPJ\\b|\\bCPF\\b|\\bbrasil\\b"),
    )

    /**
     * The country of [lines]. [hints] are countries of recognised names that trade in one country only (each counts
     * as a cue of weight 2). Null unless the best country scores at least 2 and more than any other.
     */
    fun country(lines: List<String>, hints: Collection<String> = emptyList()): CountryReading {
        val text = lines.joinToString("\n")
        val scores = HashMap<String, Int>()
        cues.forEach { c -> if (c.pattern.containsMatchIn(text)) scores.merge(c.country, c.weight, Int::plus) }
        hints.forEach { scores.merge(it, 2, Int::plus) }
        val ranked = scores.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull() ?: return CountryReading.UNKNOWN
        val second = ranked.getOrNull(1)?.value ?: 0
        return if (best.value >= 2 && best.value > second) CountryReading(best.key, best.value - second) else CountryReading.UNKNOWN
    }

    /** How [country] writes numeric dates; null when it varies (Canada) or the country is unknown. */
    fun dateOrder(country: String?): DateOrder? = when (country) {
        null, "CA" -> null
        "US", "PH", "FM", "PW", "MH" -> DateOrder.MONTH_FIRST
        "JP", "KR", "CN", "TW", "HU", "MN", "IR", "LT" -> DateOrder.YEAR_FIRST
        else -> DateOrder.DAY_FIRST
    }

    /** The currency of [country], for a document that prints amounts without one. */
    fun currencyOf(country: String?): String? = when (country) {
        null -> null
        "US", "EC", "SV", "PA", "PR", "FM", "PW", "MH" -> "USD"
        "PH" -> "PHP"
        "GB" -> "GBP"
        "DE", "FR", "ES", "IT", "NL", "BE", "AT", "IE", "PT", "FI", "GR", "LU", "SK", "SI", "EE", "LV", "LT", "MT", "CY", "HR" -> "EUR"
        "CH", "LI" -> "CHF"
        "JP" -> "JPY"
        "KR" -> "KRW"
        "CN" -> "CNY"
        "TW" -> "TWD"
        "HK" -> "HKD"
        "SG" -> "SGD"
        "MY" -> "MYR"
        "ID" -> "IDR"
        "TH" -> "THB"
        "VN" -> "VND"
        "IN" -> "INR"
        "AE" -> "AED"
        "SA" -> "SAR"
        "CA" -> "CAD"
        "AU" -> "AUD"
        "NZ" -> "NZD"
        "MX" -> "MXN"
        "BR" -> "BRL"
        "SE" -> "SEK"
        "NO" -> "NOK"
        "DK" -> "DKK"
        "PL" -> "PLN"
        "CZ" -> "CZK"
        "HU" -> "HUF"
        "TR" -> "TRY"
        "ZA" -> "ZAR"
        else -> null
    }
}

/**
 * Month names in the languages Lucento's documents most often use: English, Filipino, Spanish, Portuguese, French,
 * German, Italian, Dutch, Indonesian/Malay and the Nordic languages. Words that are also everyday English words in
 * those positions ("out", "set") are left out.
 */
object MonthWords {

    private val written = LinkedHashSet<String>()

    private val words: Map<String, Int> = buildMap {
        fun m(month: Int, vararg names: String) = names.forEach {
            written += it.lowercase(java.util.Locale.ROOT)
            put(MerchantDirectory.normalize(it), month)
        }
        m(1, "jan", "january", "enero", "ene", "janeiro", "janv", "janvier", "januar", "jänner", "januari", "gen", "gennaio")
        m(2, "feb", "febr", "february", "febrero", "pebrero", "fevereiro", "fev", "fév", "fevr", "févr", "fevrier", "février", "februar", "februari", "febbraio")
        m(3, "mar", "march", "marzo", "marso", "março", "marco", "mars", "märz", "marz", "mär", "mrz", "maart", "mrt", "maret", "mac")
        m(4, "apr", "april", "abril", "abr", "avr", "avril", "aprile")
        m(5, "may", "mayo", "maio", "mai", "mei", "maj", "maggio", "mag")
        m(6, "jun", "june", "junio", "hunyo", "junho", "juin", "juni", "giu", "giugno")
        m(7, "jul", "july", "julio", "hulyo", "julho", "juil", "juillet", "juli", "lug", "luglio")
        m(8, "aug", "august", "agosto", "ago", "août", "aout", "augustus", "agustus", "agu", "agt", "ogos", "augusti")
        m(9, "sep", "sept", "september", "septiembre", "setiembre", "setyembre", "setembro", "septembre", "settembre", "sett")
        m(10, "oct", "october", "octubre", "oktubre", "outubro", "octobre", "okt", "oktober", "ott", "ottobre")
        m(11, "nov", "november", "noviembre", "nobyembre", "novembro", "novembre", "nopember")
        m(12, "dec", "déc", "december", "diciembre", "dic", "disyembre", "dezembro", "dez", "decembre", "décembre", "dezember", "dicembre", "desember", "des", "dis", "disember")
    }

    /**
     * A regex alternation of every month word as written (accented and plain), longest first — for use inside a larger,
     * case-insensitive pattern on the document's own text, so match positions stay those of the text.
     */
    val pattern: String = (written + words.keys).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    /** The month [word] names (accents and case ignored, a trailing dot allowed), or null. */
    fun month(word: String): Int? = words[MerchantDirectory.normalize(word).trimEnd('.')]

    private val english = setOf(
        "jan", "january", "feb", "febr", "february", "mar", "march", "apr", "april", "may", "jun", "june", "jul", "july", "aug",
        "august", "sep", "sept", "september", "oct", "october", "nov", "november", "dec", "december",
    )

    /** An English month word: these may come with a two-digit year or none; other languages' need a four-digit year. */
    fun isEnglish(word: String): Boolean = MerchantDirectory.normalize(word).trimEnd('.') in english
}
