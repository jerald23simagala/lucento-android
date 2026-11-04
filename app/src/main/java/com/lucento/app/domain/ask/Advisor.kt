package com.lucento.app.domain.ask

import com.lucento.app.core.money.CurrencyRules
import com.lucento.app.core.money.Money
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * The arithmetic in an AI answer, done on the phone. The model never writes an amount: it writes `[[= expression ]]`
 * using figure ids, numbers it chooses, `+ - * /` and brackets ("[[= avg_out_3m * 12]]", "[[= g0_left / avg_net_3m]]"),
 * and the phone works it out exactly from its own figures. Units are checked: amounts add only to amounts of the same
 * currency, an amount can be multiplied or divided by a plain number, and an amount divided by an amount is a plain
 * number (months, a ratio). Anything else — an unknown id, a figure that isn't a number, mixed currencies, division by
 * zero — gives no result ("—"), never a guess.
 */
object AskCalc {

    sealed interface Value {
        data class Amount(val value: BigDecimal, val currencyCode: String) : Value
        data class Number(val value: BigDecimal) : Value
    }

    const val MAX_EXPRESSION = 200
    private const val MAX_DEPTH = 12
    private val CONTEXT = MathContext(24, RoundingMode.HALF_EVEN)

    fun evaluate(expression: String, snapshot: AskSnapshot): Value? {
        if (expression.length > MAX_EXPRESSION) return null
        val tokens = tokenize(expression) ?: return null
        return runCatching {
            val parser = Parser(tokens, snapshot)
            val value = parser.expression(0)
            if (parser.position != tokens.size) null else value
        }.getOrNull()
    }

    /** The value as it reads in an answer: an amount in its currency, a number with at most one decimal. */
    fun format(value: Value, locale: Locale = Locale.getDefault()): String = when (value) {
        is Value.Amount -> {
            val digits = CurrencyRules.fractionDigits(value.currencyCode)
            val minor = value.value.setScale(digits, RoundingMode.HALF_UP).movePointRight(digits).longValueExact()
            Figures.plain(Money(minor, value.currencyCode))
        }
        is Value.Number -> NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = if (value.value.abs() >= BigDecimal(100)) 0 else 1
            minimumFractionDigits = 0
        }.format(value.value)
    }

    private sealed interface Token {
        data class Num(val value: BigDecimal) : Token
        data class Id(val name: String) : Token
        data class Op(val symbol: Char) : Token
    }

    private fun tokenize(text: String): List<Token>? {
        val out = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c in "+-*/()" -> { out += Token.Op(c); i++ }
                c == '×' -> { out += Token.Op('*'); i++ }
                c == '÷' -> { out += Token.Op('/'); i++ }
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++
                    out += Token.Num(text.substring(start, i).toBigDecimalOrNull() ?: return null)
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                    out += Token.Id(text.substring(start, i))
                }
                else -> return null
            }
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private class Parser(private val tokens: List<Token>, private val snapshot: AskSnapshot) {
        var position = 0

        fun expression(depth: Int): Value {
            require(depth <= MAX_DEPTH)
            var left = term(depth)
            while (true) {
                val op = (tokens.getOrNull(position) as? Token.Op)?.symbol
                if (op != '+' && op != '-') return left
                position++
                left = combine(left, term(depth), op)
            }
        }

        private fun term(depth: Int): Value {
            var left = factor(depth)
            while (true) {
                val op = (tokens.getOrNull(position) as? Token.Op)?.symbol
                if (op != '*' && op != '/') return left
                position++
                left = combine(left, factor(depth), op)
            }
        }

        private fun factor(depth: Int): Value = when (val token = tokens.getOrNull(position++) ?: error("end")) {
            is Token.Num -> Value.Number(token.value)
            is Token.Id -> figure(token.name)
            is Token.Op -> when (token.symbol) {
                '-' -> negate(factor(depth))
                '+' -> factor(depth)
                '(' -> expression(depth + 1).also { require((tokens.getOrNull(position++) as? Token.Op)?.symbol == ')') }
                else -> error("unexpected")
            }
        }

        private fun figure(id: String): Value {
            val f = snapshot.byId[id] ?: error("unknown $id")
            f.amount?.let { return Value.Amount(it.toBigDecimal(), it.currencyCode) }
            f.number?.let { return Value.Number(it) }
            error("not a number")
        }

        private fun negate(v: Value): Value = when (v) {
            is Value.Amount -> v.copy(value = v.value.negate())
            is Value.Number -> v.copy(value = v.value.negate())
        }

        private fun combine(a: Value, b: Value, op: Char): Value = when (op) {
            '+', '-' -> when {
                a is Value.Amount && b is Value.Amount && a.currencyCode == b.currencyCode ->
                    Value.Amount(if (op == '+') a.value + b.value else a.value - b.value, a.currencyCode)
                a is Value.Number && b is Value.Number -> Value.Number(if (op == '+') a.value + b.value else a.value - b.value)
                else -> error("units")
            }
            '*' -> when {
                a is Value.Amount && b is Value.Number -> Value.Amount(a.value.multiply(b.value, CONTEXT), a.currencyCode)
                a is Value.Number && b is Value.Amount -> Value.Amount(a.value.multiply(b.value, CONTEXT), b.currencyCode)
                a is Value.Number && b is Value.Number -> Value.Number(a.value.multiply(b.value, CONTEXT))
                else -> error("units")
            }
            else -> {
                val divisor = when (b) {
                    is Value.Amount -> b.value
                    is Value.Number -> b.value
                }
                require(divisor.signum() != 0)
                when {
                    a is Value.Amount && b is Value.Number -> Value.Amount(a.value.divide(divisor, CONTEXT), a.currencyCode)
                    a is Value.Amount && b is Value.Amount && a.currencyCode == b.currencyCode -> Value.Number(a.value.divide(divisor, CONTEXT))
                    a is Value.Number && b is Value.Number -> Value.Number(a.value.divide(divisor, CONTEXT))
                    else -> error("units")
                }
            }
        }
    }
}

/** Where money went, by the words the user wrote on each transaction (the payee or what it was for). */
data class PayeeTotal(val name: String, val total: Money, val count: Int)

/** Spending that comes back every month at about the same amount: a subscription or a regular bill. */
data class RepeatingCharge(val name: String, val latest: Transaction, val months: Int)

/** An expense far above what the same category usually costs. [typical] is the category's median. */
data class UnusualCharge(val transaction: Transaction, val typical: Money)

/** Two expenses that look the same: same account, day, amount and words. */
data class PossibleDuplicate(val first: Transaction, val second: Transaction)

/**
 * Transaction-level patterns Ask Lucento's AI can talk about (owner, 2026-11-04: "transaction level intelligence"),
 * found on the phone so the AI only quotes them. Expenses only, one currency, never converted. The payee is the
 * transaction's note, normalised; transactions without words have none.
 */
object TransactionSignals {

    fun payeeKey(note: String): String? =
        note.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\d{4,}"), " ")
            .replace(Regex("\\s+"), " ").trim().takeIf { k -> k.count { it.isLetter() } >= 3 }?.take(40)

    private fun expenses(transactions: List<Transaction>, currency: String, from: LocalDate, to: LocalDate) =
        transactions.filter { it.type == TransactionType.EXPENSE && it.currencyCode == currency && !it.date.isBefore(from) && !it.date.isAfter(to) }

    /** The payees with the most spending between [from] and [to], largest first. */
    fun topPayees(transactions: List<Transaction>, currency: String, from: LocalDate, to: LocalDate, limit: Int = 8): List<PayeeTotal> =
        expenses(transactions, currency, from, to)
            .groupBy { payeeKey(it.note) }
            .filterKeys { it != null }
            .map { (_, list) ->
                val latest = list.maxWith(compareBy<Transaction> { it.date }.thenBy { it.createdAtEpochMillis })
                PayeeTotal(latest.note.trim().take(40), Money.sum(currency, list.map { it.amount }), list.size)
            }
            .sortedByDescending { it.total.amountMinor }
            .take(limit)

    /**
     * Payees charged in at least three of the last six months at similar amounts (within 15% of their median), or in two
     * consecutive months at the same amount within 2% — the shape of a subscription or a regular bill.
     */
    fun repeating(transactions: List<Transaction>, currency: String, today: LocalDate, limit: Int = 8): List<RepeatingCharge> {
        val from = YearMonth.from(today).minusMonths(5).atDay(1)
        return expenses(transactions, currency, from, today)
            .groupBy { payeeKey(it.note) }
            .filterKeys { it != null }
            .mapNotNull { (_, list) ->
                val months = list.map { YearMonth.from(it.date) }.distinct().sorted()
                val amounts = list.map { it.amount.amountMinor }.sorted()
                val median = amounts[amounts.size / 2].coerceAtLeast(1)
                fun within(share: Double) = amounts.all { kotlin.math.abs(it - median).toDouble() / median <= share }
                val consecutive = months.zipWithNext().any { (a, b) -> a.plusMonths(1) == b }
                val repeats = (months.size >= 3 && within(0.15)) || (months.size >= 2 && consecutive && within(0.02))
                if (!repeats) return@mapNotNull null
                val latest = list.maxWith(compareBy<Transaction> { it.date }.thenBy { it.createdAtEpochMillis })
                RepeatingCharge(latest.note.trim().take(40), latest, months.size)
            }
            .sortedByDescending { it.latest.amount.amountMinor }
            .take(limit)
    }

    /**
     * This month's expenses at least 2.5 times their category's median over the 90 days before (needing four or more
     * earlier expenses in that category), most unusual first.
     */
    fun unusual(transactions: List<Transaction>, currency: String, today: LocalDate, limit: Int = 5): List<UnusualCharge> {
        val monthStart = YearMonth.from(today).atDay(1)
        val history = expenses(transactions, currency, monthStart.minusDays(90), monthStart.minusDays(1)).groupBy { it.categoryId }
        return expenses(transactions, currency, monthStart, today).mapNotNull { t ->
            val before = history[t.categoryId]?.map { it.amount.amountMinor }?.sorted() ?: return@mapNotNull null
            if (before.size < 4) return@mapNotNull null
            val median = before[before.size / 2]
            if (median <= 0 || t.amount.amountMinor * 2 < median * 5) return@mapNotNull null
            UnusualCharge(t, Money(median, currency)) to t.amount.amountMinor.toDouble() / median
        }.sortedByDescending { it.second }.map { it.first }.take(limit)
    }

    /** Pairs of expenses in the last 60 days with the same account, day, amount and words (or none), newest first. */
    fun duplicates(transactions: List<Transaction>, currency: String, today: LocalDate, limit: Int = 5): List<PossibleDuplicate> =
        expenses(transactions, currency, today.minusDays(60), today)
            .groupBy { listOf(it.walletId, it.date, it.amount.amountMinor, payeeKey(it.note).orEmpty()) }
            .values.filter { it.size >= 2 }
            .map { list -> list.sortedBy { it.createdAtEpochMillis }.let { PossibleDuplicate(it[0], it[1]) } }
            .sortedByDescending { it.first.date }
            .take(limit)
}
