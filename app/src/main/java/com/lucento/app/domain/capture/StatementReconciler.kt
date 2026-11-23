package com.lucento.app.domain.capture

import com.lucento.app.core.money.CurrencyRules
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletBalanceCalculator
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A scanned line not added as a row because it is saved already ([saved]), or already waiting among the rows of this
 * batch (null [saved]); the person can still add it ("Add anyway").
 */
data class HeldLine(val candidate: TransactionCandidate, val saved: Transaction?, val reason: String)

/**
 * The statement's closing balance next to the account's balance in Lucento on the same day: before saving the new rows,
 * and after saving all of them. A difference is explained, never adjusted automatically.
 */
data class BalanceCheck(
    val date: LocalDate,
    val currency: String,
    val statement: BigDecimal,
    val beforeSaving: BigDecimal,
    val afterSaving: BigDecimal,
) {
    val difference: BigDecimal get() = statement - afterSaving
    val matches: Boolean get() = difference.signum() == 0
}

/** What a statement scan found besides its rows, for the reply under it. */
data class StatementReport(
    val accountId: Long?,
    val currency: String?,
    val card: Boolean,
    val pages: Int,
    /** Transactions read from the statement (declined lines excluded). */
    val lines: Int,
    /** Already saved: not added as rows. */
    val recorded: Int,
    /** Added, marked: they look like a saved transaction, but not surely. */
    val possible: Int,
    /** Added, marked: the same line appears twice on the statement. */
    val repeated: Int,
    val pending: Int,
    val declined: Int,
    /** Lines dropped because overlapping photos showed them twice. */
    val overlapRemoved: Int,
    /** Saved in the account within the statement's dates, but not on it. */
    val notOnStatement: List<Transaction>,
    val balance: BalanceCheck?,
    val summary: StatementSummary,
    /** The statement's own lines add up from its opening to its closing balance; null when it doesn't print both. */
    val addsUp: Boolean?,
    /** Fees and interest the statement charged, and on how many lines. */
    val fees: BigDecimal,
    val feeLines: Int,
    /** Refunds matched to an earlier saved purchase, and charges cancelled out by a reversal on the statement. */
    val refundsMatched: Int,
    val cancelling: Int,
)

/**
 * A statement checked line by line against what is saved (docs/SCAN.md "Checking a statement"). Nothing saved is ever
 * changed: a line already saved is held back (so it can't be saved twice), a line that only looks saved is added and
 * marked, and differences — lines missing from the statement, a balance that doesn't match, fees, refunds and reversals —
 * are reported for the person to act on.
 */
object StatementReconciler {

    /** Posting dates trail the day a payment was made. */
    const val SAME_DAYS = 3L
    const val NEAR_DAYS = 7L
    const val REFUND_DAYS = 120L

    class Result(val rows: List<TransactionCandidate>, val held: List<HeldLine>, val report: StatementReport)

    private enum class Verdict { NEW, RECORDED, POSSIBLE, REPEATED }

    private data class Leg(val type: TransactionType, val minor: Long, val date: LocalDate)

    /**
     * [candidates] are [StatementCandidates.from]'s rows for [reading] (one per line, same order); [account] the wallet
     * the statement belongs to.
     */
    fun reconcile(
        candidates: List<TransactionCandidate>,
        reading: StatementReading,
        account: Wallet?,
        context: InterpretationContext,
        pages: Int = 1,
        overlapRemoved: Int = 0,
        locale: Locale = Locale.getDefault(),
    ): Result {
        val day = DateTimeFormatter.ofPattern("MMM d", locale)
        val legs = candidates.map { c -> account?.let { legOf(c, it) } }
        val pool = if (account == null) emptyList() else context.history.filter {
            it.walletId == account.id && !it.type.isAdjustment && !it.fxResult && it.currencyCode == account.currencyCode
        }
        val verdicts = Array(candidates.size) { Verdict.NEW }
        val matched = arrayOfNulls<Transaction>(candidates.size)
        val hints = arrayOfNulls<String>(candidates.size)
        val used = HashSet<Long>()

        // 1. Saved already: same account, amount and type, within a few days — closest date first, then the closest words.
        legs.withIndex().flatMap { (i, leg) ->
            leg ?: return@flatMap emptyList()
            pool.filter { it.amount.amountMinor == leg.minor && it.type == leg.type && days(it.date, leg.date) <= SAME_DAYS }
                .map { Triple(i, it, days(it.date, leg.date) * 100 - similarity(candidates[i].description, it.note)) }
        }.sortedBy { it.third }.forEach { (i, t, _) ->
            if (verdicts[i] == Verdict.NEW && t.id !in used) {
                verdicts[i] = Verdict.RECORDED
                matched[i] = t
                used += t.id
            }
        }

        // 2. Looks saved, not surely: the same amount in the account a week apart or as another type, or in another account.
        val others = context.history.filter { t -> account != null && t.walletId != account.id && t.currencyCode == account.currencyCode && !t.type.isAdjustment && !t.fxResult }
        legs.forEachIndexed { i, leg ->
            if (leg == null || verdicts[i] != Verdict.NEW) return@forEachIndexed
            val near = pool.filter { it.id !in used && it.amount.amountMinor == leg.minor && days(it.date, leg.date) <= NEAR_DAYS }
                .minByOrNull { days(it.date, leg.date) }
            val elsewhere = if (near == null) {
                others.filter { it.id !in used && it.amount.amountMinor == leg.minor && days(it.date, leg.date) <= SAME_DAYS && sameDirection(it.type, leg.type) }
                    .minByOrNull { days(it.date, leg.date) }
            } else {
                null
            }
            val found = near ?: elsewhere ?: return@forEachIndexed
            verdicts[i] = Verdict.POSSIBLE
            matched[i] = found
            used += found.id
            val wallet = context.wallets.firstOrNull { it.id == found.walletId }?.name
            val what = found.note.takeIf { it.isNotBlank() }?.let { " (“${it.take(40)}”)" }.orEmpty()
            hints[i] = when {
                elsewhere != null && wallet != null -> "Possibly saved already: the same amount on ${found.date.format(day)} in $wallet$what. Remove this row if it’s the same one."
                found.type != leg.type -> "Possibly saved already as ${typeWord(found.type)} on ${found.date.format(day)}$what. Remove this row if it’s the same one."
                else -> "Possibly saved already: the same amount on ${found.date.format(day)}$what. Remove this row if it’s the same one."
            }
        }

        // 3. Twice on the statement: same day, amount and direction, and the same or nearly the same words (a line read
        // twice from overlapping photos may differ by a misread letter).
        for (j in candidates.indices) {
            if (verdicts[j] == Verdict.RECORDED) continue
            val lj = legs[j] ?: continue
            val twin = (0 until j).firstOrNull { i ->
                val li = legs[i]
                li != null && li == lj && alike(candidates[i].description, candidates[j].description)
            }
            if (twin != null && verdicts[j] == Verdict.NEW) {
                verdicts[j] = Verdict.REPEATED
                hints[j] = "This appears twice on the statement — remove one if it’s the same charge."
            }
        }

        // 4. A charge and its reversal or refund on the same statement cancel out.
        var cancelling = 0
        reading.lines.forEachIndexed { i, line ->
            if (i >= candidates.size || line.flow != StatementFlow.IN || (line.kind != LineKind.REVERSAL && line.kind != LineKind.REFUND)) return@forEachIndexed
            val j = reading.lines.indices.firstOrNull { k ->
                k != i && k < candidates.size && reading.lines[k].flow == StatementFlow.OUT && reading.lines[k].amount.compareTo(line.amount) == 0 &&
                    sharesWord(reading.lines[k].description, line.description)
            } ?: return@forEachIndexed
            cancelling++
            if (verdicts[i] == Verdict.NEW && hints[i] == null) hints[i] = "Cancels out the ${dateOf(candidates[j], day)} charge on this statement — if nothing changed hands, remove both."
            if (verdicts[j] == Verdict.NEW && hints[j] == null) hints[j] = "Cancelled by the ${dateOf(candidates[i], day)} ${if (line.kind == LineKind.REFUND) "refund" else "reversal"} on this statement."
        }

        // 5. A refund of a purchase saved earlier.
        var refunds = 0
        reading.lines.forEachIndexed { i, line ->
            if (i >= candidates.size || line.kind != LineKind.REFUND || line.flow != StatementFlow.IN || verdicts[i] != Verdict.NEW || hints[i] != null) return@forEachIndexed
            val c = candidates[i]
            val minor = legs[i]?.minor ?: return@forEachIndexed
            val purchase = context.history.filter { t ->
                t.type == TransactionType.EXPENSE && t.amount.amountMinor == minor && t.currencyCode == (account?.currencyCode ?: t.currencyCode) &&
                    !t.date.isAfter(c.date) && ChronoUnit.DAYS.between(t.date, c.date) <= REFUND_DAYS && sharesWord(t.note, c.description)
            }.maxByOrNull { it.date } ?: return@forEachIndexed
            refunds++
            hints[i] = "Refund of your ${purchase.date.format(day)} purchase${purchase.note.takeIf { it.isNotBlank() }?.let { " (“${it.take(40)}”)" }.orEmpty()}."
        }

        val held = ArrayList<HeldLine>()
        val rows = ArrayList<TransactionCandidate>()
        candidates.forEachIndexed { i, c ->
            if (verdicts[i] == Verdict.RECORDED) {
                val t = matched[i]!!
                held += HeldLine(c, t, "Saved ${t.date.format(day)}${t.note.takeIf { it.isNotBlank() }?.let { " as “${it.take(40)}”" }.orEmpty()}")
            } else {
                rows += c.copy(flags = c.flags + CandidateFlag.RECONCILED, hint = hints[i] ?: c.hint)
            }
        }

        // Saved in the account during the statement's dates but not on it: only for a whole statement (its period or
        // both balances printed); a photo of part of an app's history would list everything else as missing.
        val dates = reading.lines.mapNotNull { it.date }
        val complete = reading.summary.periodStart != null || (reading.summary.opening != null && reading.summary.closing != null)
        val from = reading.summary.periodStart ?: dates.minOrNull()
        val to = reading.summary.periodEnd ?: dates.maxOrNull()
        val notOnStatement = if (!complete || from == null || to == null) {
            emptyList()
        } else {
            pool.filter { it.id !in used && !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }
        }

        val balance = if (account == null) null else balanceCheck(reading, account, context.history, rows, held.size, context.today)
        val feeLines = reading.lines.filter { (it.kind == LineKind.FEE || it.kind == LineKind.INTEREST_CHARGE) && it.flow == StatementFlow.OUT }
        val report = StatementReport(
            accountId = account?.id,
            currency = account?.currencyCode ?: reading.currencyCode,
            card = account?.type?.isLiability ?: reading.looksLikeCard,
            pages = pages,
            lines = candidates.size,
            recorded = held.size,
            possible = verdicts.count { it == Verdict.POSSIBLE },
            repeated = verdicts.count { it == Verdict.REPEATED },
            pending = reading.lines.count { it.status == LineStatus.PENDING },
            declined = reading.declined,
            overlapRemoved = overlapRemoved,
            notOnStatement = notOnStatement,
            balance = balance,
            summary = reading.summary,
            addsUp = reading.addsUp,
            fees = feeLines.fold(BigDecimal.ZERO) { acc, l -> acc + l.amount },
            feeLines = feeLines.size,
            refundsMatched = refunds,
            cancelling = cancelling,
        )
        return Result(rows, held, report)
    }

    /**
     * The statement's closing balance against the account's balance in Lucento on the statement's last day, before and
     * after saving the rows offered. Skipped when the account was created after that day (its opening balance is then a
     * later figure) or the statement prints no closing balance.
     */
    private fun balanceCheck(
        reading: StatementReading,
        account: Wallet,
        history: List<Transaction>,
        rows: List<TransactionCandidate>,
        heldCount: Int,
        today: LocalDate,
    ): BalanceCheck? {
        val closing = reading.summary.closing ?: return null
        val date = reading.summary.periodEnd ?: reading.lines.mapNotNull { it.date }.maxOrNull() ?: today
        val created = Instant.ofEpochMilli(account.createdAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        if (created.isAfter(date)) return null
        val digits = CurrencyRules.fractionDigits(account.currencyCode)
        val before = WalletBalanceCalculator.balanceOn(account.openingBalance, account.type, account.id, history, date)
        val beforeMajor = BigDecimal.valueOf(before.amountMinor, digits)
        val effect = rows.filter { !it.date.isAfter(date) }.mapNotNull { c -> legOf(c, account)?.let { c to it } }.fold(BigDecimal.ZERO) { acc, (c, leg) ->
            val amount = c.amount ?: return@fold acc
            val inward = leg.type == TransactionType.INCOME || leg.type == TransactionType.TRANSFER_IN
            if (account.type.isLiability) (if (inward) acc - amount else acc + amount) else (if (inward) acc + amount else acc - amount)
        }
        return BalanceCheck(date, account.currencyCode, closing.setScale(digits, RoundingMode.HALF_EVEN), beforeMajor, (beforeMajor + effect).setScale(digits, RoundingMode.HALF_EVEN))
    }

    /** The row as a movement in [account]: its type there, its amount in minor units, its date; null when it isn't one. */
    private fun legOf(c: TransactionCandidate, account: Wallet): Leg? {
        val amount = c.amount ?: return null
        val digits = CurrencyRules.fractionDigits(account.currencyCode)
        val minor = runCatching { amount.setScale(digits, RoundingMode.UNNECESSARY).movePointRight(digits).longValueExact() }.getOrNull() ?: return null
        val type = when {
            c.type == TransactionType.TRANSFER_OUT && c.walletId == account.id -> TransactionType.TRANSFER_OUT
            c.type == TransactionType.TRANSFER_OUT && c.toWalletId == account.id -> TransactionType.TRANSFER_IN
            c.type.isTransfer -> return null
            c.walletId != account.id -> return null
            else -> c.type
        }
        return Leg(type, minor, c.date)
    }

    private fun sameDirection(a: TransactionType, b: TransactionType): Boolean = a.isInflow == b.isInflow

    private fun typeWord(type: TransactionType) = when (type) {
        TransactionType.EXPENSE -> "an expense"
        TransactionType.INCOME -> "income"
        else -> "a transfer"
    }

    private fun dateOf(c: TransactionCandidate, format: DateTimeFormatter) = c.date.format(format)

    private fun days(a: LocalDate, b: LocalDate) = kotlin.math.abs(ChronoUnit.DAYS.between(a, b))

    private val WORD = Regex("[\\p{L}]{3,}")
    private val NOISE = setOf("the", "and", "pos", "purchase", "payment", "debit", "credit", "card", "refund", "reversal", "return", "online", "transfer", "store", "inc", "com", "www")

    private fun words(text: String) = WORD.findAll(text.lowercase(Locale.ROOT)).map { it.value }.filter { it !in NOISE }.toSet()

    private fun sharesWord(a: String, b: String): Boolean = (words(a) intersect words(b)).isNotEmpty()

    /** 0–99: how alike a statement's description and a saved note read, by shared words. */
    private fun similarity(a: String, b: String): Long {
        val x = words(a)
        val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return 0
        return ((x intersect y).size * 99L) / maxOf(x.size, y.size)
    }

    private fun key(text: String) = text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

    /** The same words, a misread letter or two apart, or sharing a distinctive word. */
    private fun alike(a: String, b: String): Boolean {
        val x = key(a)
        val y = key(b)
        if (x == y) return true
        if (x.isNotEmpty() && y.isNotEmpty() && kotlin.math.abs(x.length - y.length) <= 2) {
            var previous = IntArray(y.length + 1) { it }
            for (i in 1..x.length) {
                val current = IntArray(y.length + 1)
                current[0] = i
                for (j in 1..y.length) current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
                previous = current
            }
            if (previous[y.length] <= maxOf(1, maxOf(x.length, y.length) / 8)) return true
        }
        return (words(a) intersect words(b)).any { it.length >= 4 }
    }
}
