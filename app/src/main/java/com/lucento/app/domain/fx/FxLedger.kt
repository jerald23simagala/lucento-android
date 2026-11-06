package com.lucento.app.domain.fx

import com.lucento.app.core.money.FxRate
import com.lucento.app.core.money.Money
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Where a movement's main-currency value came from (stored as the name). */
enum class FxSource {
    /** The day's market rate from the exchange-rate service. */
    MARKET,

    /** A rate the person typed. */
    ENTERED,

    /** Offline: the rate of this wallet's most recent movement that had one. Gains and losses count against the average. */
    LAST_RATE,

    /** Offline, or not given: the wallet's average rate. No gain or loss, and it follows the average if history changes. */
    AVERAGE,

    /** A transfer between currencies: the rate what was paid and what was received imply. */
    TRANSFER,
    ;

    companion object {
        fun fromName(name: String?): FxSource? = entries.firstOrNull { it.name == name }
    }
}

/** Ids of the two reporting categories FX results are shown under (never stored; no real category has a negative id). */
object FxCategories {
    const val GAIN_ID = -9001L
    const val LOSS_ID = -9002L
    const val GAIN_NAME = "FX gains"
    const val LOSS_NAME = "FX losses"

    fun nameOf(categoryId: Long?): String? = when (categoryId) {
        GAIN_ID -> GAIN_NAME
        LOSS_ID -> LOSS_NAME
        else -> null
    }
}

/** A foreign wallet valued in the main currency: what it holds (or owes) and what that is carried at. */
data class FxPosition(val walletId: Long, val native: Money, val carrying: Money) {
    /** The average rate: carrying ÷ native, or null when the wallet holds nothing. */
    val averageRate: BigDecimal?
        get() = if (native.isZero) null else carrying.toBigDecimal().divide(native.toBigDecimal(), FxRate.PRECISION).abs()
}

/** A realized FX gain (positive) or loss (negative), in the main currency, from one movement out of a foreign wallet. */
data class FxRealized(val walletId: Long, val transactionId: Long, val date: LocalDate, val amount: Money, val createdAtEpochMillis: Long)

/**
 * The average method over every foreign wallet (docs/CALCULATIONS.md "Exchange rates"). Pure and deterministic: rebuilt
 * from the history each time, so editing or deleting an old movement corrects everything after it.
 */
data class FxBook(
    val mainCurrency: String,
    /** Foreign wallets that could be valued. */
    val positions: Map<Long, FxPosition>,
    /** Main-currency value of each movement in a valued foreign wallet, by transaction id. */
    val values: Map<Long, Money>,
    val realized: List<FxRealized>,
    /** Foreign wallets with no rate to value them by yet: left in their own currency. */
    val unvalued: Set<Long>,
)

object FxLedger {

    /**
     * Replays each foreign wallet's movements in date order (then entry order). Increases — money in, or more owed on a
     * card or loan — are added at their own main-currency value; decreases leave at the average carrying rate, and the
     * difference from their own value is the realized gain or loss. Movements without a value of their own
     * ([FxSource.AVERAGE], or written before multicurrency) follow the average, so they never make a gain or loss.
     */
    fun build(mainCurrency: String, wallets: List<Wallet>, transactions: List<Transaction>): FxBook {
        val byWallet = transactions.filter { !it.fxResult }.groupBy { it.walletId }
        val positions = LinkedHashMap<Long, FxPosition>()
        val values = HashMap<Long, Money>()
        val realized = ArrayList<FxRealized>()
        val unvalued = LinkedHashSet<Long>()
        for (wallet in wallets) {
            if (wallet.currencyCode == mainCurrency) continue
            val replay = replay(mainCurrency, wallet, byWallet[wallet.id].orEmpty())
            if (replay == null) {
                unvalued += wallet.id
            } else {
                positions[wallet.id] = replay.position
                values.putAll(replay.values)
                realized.addAll(replay.realized)
            }
        }
        return FxBook(mainCurrency, positions, values, realized, unvalued)
    }

    private class Replay(val position: FxPosition, val values: Map<Long, Money>, val realized: List<FxRealized>)

    private fun replay(main: String, wallet: Wallet, movements: List<Transaction>): Replay? {
        val currency = wallet.currencyCode
        val ordered = movements
            .filter { it.currencyCode == currency }
            .sortedWith(compareBy<Transaction>({ it.date }, { it.createdAtEpochMillis }, { it.id }))
        var fallback: BigDecimal? = wallet.fxBasisRate
        // Without a basis, the first movement with a value of its own can still start the wallet off (from zero).
        if (fallback == null && !wallet.openingBalance.isZero) return null
        var held = wallet.openingBalance.amountMinor
        var carried = fallback?.let { convert(held, currency, main, it) } ?: 0L
        val values = HashMap<Long, Money>()
        val realized = ArrayList<FxRealized>()

        for (t in ordered) {
            val amount = t.amount.amountMinor
            val own = t.baseAmount?.takeIf { it.currencyCode == main && t.fxSource != FxSource.AVERAGE }?.amountMinor
            if (own != null && amount != 0L) fallback = rateOf(own, amount, currency, main)
            val liability = wallet.type.isLiability
            val increase = if (liability) !t.type.isInflow else t.type.isInflow
            var gain = 0L
            if (increase) {
                val value = own ?: averageValue(amount, held, carried, currency, main, fallback) ?: return null
                held = Math.addExact(held, amount)
                carried = Math.addExact(carried, value)
                values[t.id] = Money(value, main)
            } else {
                val cost = costOf(amount, held, carried, own, currency, main, fallback) ?: return null
                val value = own ?: cost
                gain = if (liability) Math.subtractExact(cost, value) else Math.subtractExact(value, cost)
                held = Math.subtractExact(held, amount)
                carried = Math.subtractExact(carried, cost)
                values[t.id] = Money(value, main)
            }
            // Nothing held (or owed) any more: whatever is still carried is settled now — on an asset, carried value left
            // over is a loss; on a card or loan, value still owed in the books is a gain. Only an overdraft brought back
            // to zero can leave any.
            if (held == 0L && carried != 0L) {
                gain = Math.addExact(gain, if (liability) carried else -carried)
                carried = 0L
            }
            if (gain != 0L) realized += FxRealized(wallet.id, t.id, t.date, Money(gain, main), t.createdAtEpochMillis)
        }
        return Replay(FxPosition(wallet.id, Money(held, currency), Money(carried, main)), values, realized)
    }

    /** An increase without a value of its own: at the average while something is held, otherwise at the fallback rate. */
    private fun averageValue(amount: Long, held: Long, carried: Long, currency: String, main: String, fallback: BigDecimal?): Long? =
        if (held > 0L) proportion(carried, amount, held) else fallback?.let { convert(amount, currency, main, it) }

    /**
     * What a decrease takes off the carrying value: its share of the average while enough is held; beyond what is held
     * (an overdrawn wallet) the excess goes at the movement's own rate, or the fallback.
     */
    private fun costOf(amount: Long, held: Long, carried: Long, own: Long?, currency: String, main: String, fallback: BigDecimal?): Long? {
        val covered = if (held > 0L) minOf(amount, held) else 0L
        val excess = amount - covered
        val fromAverage = if (covered == 0L) 0L else if (covered == held) carried else proportion(carried, covered, held)
        if (excess == 0L) return fromAverage
        val excessValue = when {
            own != null -> proportion(own, excess, amount)
            fallback != null -> convert(excess, currency, main, fallback)
            else -> return null
        }
        return Math.addExact(fromAverage, excessValue)
    }

    /**
     * The rate of [wallet]'s most recent movement that had a value of its own (not [FxSource.AVERAGE]) — the offline
     * "last rate" choice. Null when there is none yet.
     */
    fun lastRate(mainCurrency: String, wallet: Wallet, transactions: List<Transaction>): BigDecimal? =
        transactions
            .filter {
                it.walletId == wallet.id && !it.fxResult && it.currencyCode == wallet.currencyCode && !it.amount.isZero &&
                    it.fxSource != FxSource.AVERAGE && it.baseAmount?.currencyCode == mainCurrency
            }
            .maxWithOrNull(compareBy<Transaction>({ it.date }, { it.createdAtEpochMillis }, { it.id }))
            ?.let { rateOf(it.baseAmount!!.amountMinor, it.amount.amountMinor, wallet.currencyCode, mainCurrency) }

    /** value × part ÷ whole, half-even, exact. */
    private fun proportion(value: Long, part: Long, whole: Long): Long =
        BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(part))
            .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_EVEN)
            .longValueExact()

    private fun convert(amountMinor: Long, from: String, to: String, rate: BigDecimal): Long =
        FxRate(from, to, rate).convert(Money(amountMinor, from)).amountMinor

    private fun rateOf(valueMinor: Long, amountMinor: Long, from: String, to: String): BigDecimal =
        FxRate.implied(Money(amountMinor, from), Money(valueMinor, to)).perUnit
}

/**
 * The ledger as the main-currency totals see it: every valued foreign wallet and its movements re-expressed in the main
 * currency, plus the realized FX results as [Transaction]s (gains as INCOME, losses as EXPENSE, in [FxCategories]).
 * Wallets that can't be valued yet keep their own currency, so the existing per-currency views list them as before.
 *
 * Use [wallets] / [transactions] for figures that are about money recorded (budgets, Spent, Home, the Transactions
 * page) and [withFxResults] where FX results belong (Insights, reports, balance history): gains count as income and
 * losses as expenses there, never as spending against a budget.
 */
data class TranslatedLedger(
    val wallets: List<Wallet>,
    val transactions: List<Transaction>,
    val fxResults: List<Transaction>,
    val book: FxBook,
) {
    val withFxResults: List<Transaction> get() = transactions + fxResults

    /**
     * [categories] plus the two reporting categories FX results are filed under ([FxCategories]), when there are any —
     * so every name lookup in Insights and reports reads "FX gains" / "FX losses". Never saved anywhere.
     */
    fun withFxCategories(categories: List<com.lucento.app.domain.Category>): List<com.lucento.app.domain.Category> {
        if (fxResults.isEmpty()) return categories
        return categories + listOf(
            com.lucento.app.domain.Category(FxCategories.GAIN_ID, FxCategories.GAIN_NAME, com.lucento.app.domain.CategoryKind.INCOME, "fx_gain"),
            com.lucento.app.domain.Category(FxCategories.LOSS_ID, FxCategories.LOSS_NAME, com.lucento.app.domain.CategoryKind.EXPENSE, "fx_loss"),
        )
    }
}

object FxTranslation {

    fun translate(mainCurrency: String, wallets: List<Wallet>, transactions: List<Transaction>): TranslatedLedger {
        val book = FxLedger.build(mainCurrency, wallets, transactions)
        val walletById = wallets.associateBy { it.id }
        val translatedWallets = wallets.map { wallet ->
            val position = book.positions[wallet.id] ?: return@map wallet
            val opening = wallet.fxBasisRate?.let { FxRate(wallet.currencyCode, mainCurrency, it).convert(wallet.openingBalance) }
                ?: Money.zero(mainCurrency)
            wallet.copy(balance = position.carrying, openingBalance = opening)
        }
        val translatedTransactions = transactions.filter { !it.fxResult }.map { t ->
            val value = book.values[t.id] ?: return@map t
            t.copy(amount = value.abs())
        }
        val results = book.realized.map { r ->
            val gain = !r.amount.isNegative
            Transaction(
                // Negative ids: never a stored row's.
                id = -1L - r.transactionId,
                type = if (gain) TransactionType.INCOME else TransactionType.EXPENSE,
                amount = r.amount.abs(),
                walletId = r.walletId,
                categoryId = if (gain) FxCategories.GAIN_ID else FxCategories.LOSS_ID,
                date = r.date,
                note = "FX ${if (gain) "gain" else "loss"} · ${walletById[r.walletId]?.name.orEmpty()}",
                createdAtEpochMillis = r.createdAtEpochMillis,
                fxResult = true,
            )
        }
        return TranslatedLedger(translatedWallets, translatedTransactions, results, book)
    }
}
