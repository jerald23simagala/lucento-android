package com.lucento.app.core.money

import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Parses user-entered amounts into minor units without floating point (P0-3).
 *
 * The phone's decimal separator ([parse]'s `decimalSeparator`) is trusted as a decimal mark. The other separator is a
 * thousands separator only where it sits in valid groups (1–3 digits, then groups of 3, or Indian 2-digit groups ending
 * in 3); otherwise it is read as a decimal mark if the currency's precision allows. A number that still has two
 * reasonable readings is [Result.Ambiguous] — it is never guessed, and never multiplied by 100 or 1,000 because a
 * separator was dropped. Spaces, no-break spaces and apostrophes are grouping only. Unicode digits are accepted; a
 * currency code or symbol is accepted only when it belongs to the wallet's currency; signs other than a leading "+"
 * are rejected. See docs/handover/P0_3_AMOUNT_PARSING_REPORT.md for the full rule table.
 */
object MoneyParser {

    /** Upper bound for a single entered amount, in major units (one trillion). */
    val MAX_MAJOR_UNITS: BigDecimal = BigDecimal("1000000000000")

    /** More digits than this is never a real amount; checked before any BigDecimal is built. */
    private const val MAX_DIGITS = 24

    sealed interface Result {
        data class Success(val money: Money) : Result
        data object Empty : Result
        data object Invalid : Result
        data class TooManyDecimals(val allowed: Int) : Result
        data object TooLarge : Result
        /** Two reasonable readings (e.g. "1.000" as 1.00 or 1,000); the user has to say which. Smallest first. */
        data class Ambiguous(val options: List<Money>) : Result
        /** A currency code or symbol that isn't the wallet's own. Nothing is converted. */
        data class WrongCurrency(val walletCurrency: String) : Result
    }

    fun parse(input: String, currencyCode: String, decimalSeparator: Char = '.', locale: Locale = Locale.getDefault()): Result {
        require(decimalSeparator == '.' || decimalSeparator == ',') { "Unsupported decimal separator" }
        val digits = CurrencyRules.fractionDigits(currencyCode)
        val number = when (val stripped = stripCurrency(input, currencyCode, locale)) {
            is Stripped.Number -> stripped.text
            Stripped.Foreign -> return Result.WrongCurrency(currencyCode)
            Stripped.Invalid -> return Result.Invalid
        }
        return when (val reading = readNumber(number, decimalSeparator, digits)) {
            Reading.Empty -> Result.Empty
            Reading.Invalid -> Result.Invalid
            Reading.TooPrecise -> Result.TooManyDecimals(digits)
            Reading.TooLarge -> Result.TooLarge
            is Reading.Value -> toMoney(reading.value, currencyCode, digits)
            is Reading.Choice -> {
                val options = reading.values.map { toMoney(it, currencyCode, digits) }
                val valid = options.filterIsInstance<Result.Success>().map { it.money }
                when (valid.size) {
                    0 -> Result.TooLarge
                    1 -> Result.Success(valid.single())
                    else -> Result.Ambiguous(valid)
                }
            }
        }
    }

    fun parseOrNull(input: String, currencyCode: String, decimalSeparator: Char = '.'): Money? =
        (parse(input, currencyCode, decimalSeparator) as? Result.Success)?.money

    /**
     * A plain-language message for the two results that need the user to restate the amount, or null for the others
     * (screens already word those). Suggested rewrites parse to exactly one value.
     */
    fun clarification(result: Result, decimalSeparator: Char, locale: Locale = Locale.getDefault()): String? = when (result) {
        is Result.Ambiguous -> {
            val shown = result.options.joinToString(" or ") { MoneyFormatter.format(it, locale) }
            val typed = result.options.joinToString(" or ") { "“${unambiguous(it, decimalSeparator)}”" }
            "This could be $shown. Type $typed to say which."
        }
        is Result.WrongCurrency -> "This account is in ${result.walletCurrency}. Enter the amount without another currency’s symbol."
        else -> null
    }

    /** Digits only for whole amounts ("1000"); the currency's full decimals for the rest ("1.00", "1,00"). */
    internal fun unambiguous(money: Money, decimalSeparator: Char): String {
        val value = money.toBigDecimal()
        return if (value.stripTrailingZeros().scale() <= 0) {
            value.setScale(0).toPlainString()
        } else {
            value.toPlainString().replace('.', decimalSeparator)
        }
    }

    // ------------------------------------------------------------------ reading the number

    /** The number part of an input, read without any currency. Shared with the composer (QuickEntryParser). */
    internal sealed interface Reading {
        data class Value(val value: BigDecimal) : Reading
        /** Both readings are valid; smallest first. */
        data class Choice(val values: List<BigDecimal>) : Reading
        data object Empty : Reading
        data object Invalid : Reading
        data object TooPrecise : Reading
        data object TooLarge : Reading
    }

    internal fun readNumber(input: String, decimalSeparator: Char, fractionDigits: Int): Reading {
        val s = canonical(input, decimalSeparator) ?: return Reading.Invalid
        if (s.isEmpty()) return Reading.Empty
        val digitCount = s.count { it in '0'..'9' }
        if (digitCount == 0) return Reading.Invalid
        if (digitCount > MAX_DIGITS) return Reading.TooLarge

        val hard = s.filter { it == SPACE || it == APOSTROPHE }.toSet()
        if (hard.size > 1) return Reading.Invalid
        val dots = s.count { it == '.' }
        val commas = s.count { it == ',' }

        if (hard.isNotEmpty()) {
            // "1 234,56", "1'234.50": the space or apostrophe groups; a single '.' or ',' is the decimal mark.
            val group = hard.single()
            return when {
                dots + commas == 0 -> grouped(s, group)?.let { Reading.Value(BigDecimal(it)) } ?: Reading.Invalid
                dots + commas == 1 -> {
                    val at = s.indexOfFirst { it == '.' || it == ',' }
                    val whole = s.substring(0, at)
                    val fraction = s.substring(at + 1)
                    if (fraction.any { it !in '0'..'9' }) return Reading.Invalid
                    val digits = (if (whole.contains(group)) grouped(whole, group) else whole.takeIf { it.all { c -> c in '0'..'9' } })
                        ?: return Reading.Invalid
                    // Zeros past the precision are dropped only after the phone's own decimal mark: "1 000,000" on a
                    // '.' phone is not 1000.
                    decimal(digits, fraction, fractionDigits, allowZeros = s[at] == decimalSeparator)
                }
                else -> Reading.Invalid
            }
        }

        return when {
            dots + commas == 0 -> Reading.Value(BigDecimal(s))
            dots > 0 && commas > 0 -> {
                // Both marks: the last one is the decimal and must appear once; the other groups the whole part.
                val mark = s.last { it == '.' || it == ',' }
                if (s.count { it == mark } != 1) return Reading.Invalid
                val at = s.lastIndexOf(mark)
                val group = if (mark == '.') ',' else '.'
                val whole = grouped(s.substring(0, at), group) ?: return Reading.Invalid
                decimal(whole, s.substring(at + 1), fractionDigits, allowZeros = true)
            }
            else -> {
                val mark = if (dots > 0) '.' else ','
                if (s.count { it == mark } > 1) {
                    // Repeated: only a thousands separator can repeat, and never the phone's own decimal mark.
                    if (mark == decimalSeparator) return Reading.Invalid
                    return grouped(s, mark)?.let { Reading.Value(BigDecimal(it)) } ?: Reading.Invalid
                }
                single(s, mark, decimalSeparator, fractionDigits)
            }
        }
    }

    /** One '.' or ',' and no other separator. */
    private fun single(s: String, mark: Char, decimalSeparator: Char, fractionDigits: Int): Reading {
        val at = s.indexOf(mark)
        val whole = s.substring(0, at)
        val fraction = s.substring(at + 1)
        // A thousands group never follows a leading zero: "0,500" is not 500.
        val groupingOk = fraction.length == 3 && whole.length in 1..3 && whole[0] != '0'
        val groupedValue = BigDecimal(whole + fraction)
        if (mark == decimalSeparator) {
            if (fraction.length <= fractionDigits) return decimal(whole, fraction, fractionDigits, allowZeros = false)
            val asDecimal = decimal(whole, fraction, fractionDigits, allowZeros = true)
            if (asDecimal !is Reading.Value) return asDecimal // too precise: never re-read as a thousands separator
            // "1.000" for a 2-digit currency: 1.00 only by dropping zeros, or 1,000. Ask.
            return if (groupingOk) choice(asDecimal.value, groupedValue) else asDecimal
        }
        val decimalOk = fraction.length <= fractionDigits
        return when {
            groupingOk && decimalOk -> choice(BigDecimal("${whole.ifEmpty { "0" }}.$fraction"), groupedValue)
            groupingOk -> Reading.Value(groupedValue)
            decimalOk -> decimal(whole, fraction, fractionDigits, allowZeros = false)
            else -> Reading.TooPrecise
        }
    }

    private fun choice(a: BigDecimal, b: BigDecimal): Reading =
        if (a.compareTo(b) == 0) Reading.Value(a) else Reading.Choice(listOf(a, b).sorted())

    private fun decimal(whole: String, fraction: String, fractionDigits: Int, allowZeros: Boolean): Reading {
        val value = BigDecimal("${whole.ifEmpty { "0" }}.${fraction.ifEmpty { "0" }}")
        val fits = fraction.length <= fractionDigits || (allowZeros && value.stripTrailingZeros().scale() <= fractionDigits)
        return if (fits) Reading.Value(value) else Reading.TooPrecise
    }

    /**
     * The digits of [text] grouped by [separator] in Western groups (1–3, then 3s) or Indian groups (1–2, then 2s,
     * ending in 3), or null when the grouping is not valid.
     */
    private fun grouped(text: String, separator: Char): String? {
        val groups = text.split(separator)
        if (groups.size < 2 || groups.any { g -> g.isEmpty() || g.any { it !in '0'..'9' } }) return null
        if (groups.first()[0] == '0') return null // "0,000,500" is not 500
        val western = groups.first().length in 1..3 && groups.drop(1).all { it.length == 3 }
        val indian = groups.size >= 3 && groups.first().length in 1..2 && groups.last().length == 3 &&
            groups.subList(1, groups.size - 1).all { it.length == 2 }
        return if (western || indian) groups.joinToString("") else null
    }

    /**
     * ASCII digits, '.', ',', [SPACE] (any space-like grouping) and [APOSTROPHE]; a leading '+' is dropped. Null for
     * anything else (letters, signs, a second '+'). The Arabic decimal separator is a decimal mark only, so it becomes
     * [decimalSeparator] and may not be mixed with '.' or ','.
     */
    private fun canonical(input: String, decimalSeparator: Char): String? {
        val out = StringBuilder()
        var arabicDecimal = false
        var plus = false
        for (ch in input.trim { it.isWhitespace() || it in SPACES }) {
            when {
                ch in '0'..'9' -> out.append(ch)
                Character.isDigit(ch) -> out.append('0' + Character.digit(ch, 10))
                ch == '.' || ch == ',' -> if (arabicDecimal) return null else out.append(ch)
                ch == ARABIC_DECIMAL -> {
                    if (arabicDecimal || out.any { it == '.' || it == ',' }) return null
                    arabicDecimal = true
                    out.append(decimalSeparator)
                }
                ch.isWhitespace() || ch in SPACES || ch == ARABIC_GROUPING -> out.append(SPACE)
                ch in APOSTROPHES -> out.append(APOSTROPHE)
                ch == '+' && out.isEmpty() && !plus -> plus = true
                else -> return null
            }
        }
        if (plus && out.isEmpty()) return null
        return out.toString()
    }

    private fun toMoney(value: BigDecimal, currencyCode: String, fractionDigits: Int): Result {
        if (value > MAX_MAJOR_UNITS) return Result.TooLarge
        val minor = value.movePointRight(fractionDigits).setScale(0, java.math.RoundingMode.UNNECESSARY).longValueExact()
        return Result.Success(Money(minor, currencyCode))
    }

    // ------------------------------------------------------------------ currency marks

    private sealed interface Stripped {
        data class Number(val text: String) : Stripped
        data object Foreign : Stripped
        data object Invalid : Stripped
    }

    /** Removes one leading or trailing code or symbol of [currencyCode]; another currency's mark is [Stripped.Foreign]. */
    private fun stripCurrency(input: String, currencyCode: String, locale: Locale): Stripped {
        val text = input.trim { it.isWhitespace() || it in SPACES }
        // The wallet's own code or symbols first, longest first: some contain a dot ("kr.", "د.ب.").
        val own = (symbolsOf(currencyCode, locale) + currencyCode).filter { it.isNotBlank() && it.none(Char::isDigit) }
            .sortedByDescending { it.length }
        for (symbol in own) {
            // Short symbols match exactly: "2k" in a kina (K) wallet is not "2 K" — it would store 2 for 2,000.
            val anyCase = symbol.length >= 3
            if (text.length > symbol.length && text.endsWith(symbol, ignoreCase = anyCase)) {
                return Stripped.Number(text.dropLast(symbol.length).trimEnd { it.isWhitespace() || it in SPACES })
            }
            if (text.length > symbol.length && text.startsWith(symbol, ignoreCase = anyCase)) {
                return Stripped.Number(text.drop(symbol.length).trimStart { it.isWhitespace() || it in SPACES })
            }
        }
        val lead = text.takeWhile(::isMark)
        val trail = if (lead.length == text.length) "" else text.takeLastWhile(::isMark)
        if (lead.isNotEmpty() && trail.isNotEmpty()) return Stripped.Invalid
        val mark = (lead.ifEmpty { trail }).trim { it.isWhitespace() || it in SPACES }
        if (mark.isEmpty()) return Stripped.Number(text)
        val number = if (lead.isNotEmpty()) text.substring(lead.length) else text.substring(0, text.length - trail.length)
        return when {
            mark.equals(currencyCode, ignoreCase = true) || mark in symbolsOf(currencyCode, locale) -> Stripped.Number(number)
            looksLikeCurrency(mark) -> Stripped.Foreign
            else -> Stripped.Invalid
        }
    }

    /** Part of a leading or trailing currency mark: anything that can't be in the number itself (spaces included). */
    private fun isMark(ch: Char): Boolean =
        !(ch.isDigit() || ch == '.' || ch == ',' || ch == '+' || ch == '-' || ch == '−' || ch == '(' || ch == ')' ||
            ch in APOSTROPHES || ch == ARABIC_DECIMAL || ch == ARABIC_GROUPING)

    private fun looksLikeCurrency(mark: String): Boolean =
        mark.any { Character.getType(it) == Character.CURRENCY_SYMBOL.toInt() } ||
            (mark.length == 3 && mark.all { it.isLetter() } && runCatching { Currency.getInstance(mark.uppercase(Locale.ROOT)) }.isSuccess)

    private val symbolCache = ConcurrentHashMap<String, Set<String>>()

    /** The currency's symbols as the user's locale, the root locale and the locales that use it write them. */
    private fun symbolsOf(currencyCode: String, locale: Locale): Set<String> {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull() ?: return emptySet()
        val base = symbolCache.getOrPut(currencyCode) {
            // Every way any locale writes this currency ("¥", "￥", "JP¥" for JPY; "kr." for DKK). Accepting them never
            // changes the value: the wallet's currency is used either way, and nothing is converted.
            buildSet {
                add(currency.getSymbol(Locale.ROOT))
                Locale.getAvailableLocales().forEach { l -> add(currency.getSymbol(l)) }
            }
        }
        return base + currency.getSymbol(locale)
    }

    private const val SPACE = ' '
    private const val APOSTROPHE = '\''
    private val SPACES = setOf(' ', ' ', ' ', ' ')
    private val APOSTROPHES = setOf('\'', '’', 'ʼ')
    private const val ARABIC_DECIMAL = '٫'
    private const val ARABIC_GROUPING = '٬'
}
