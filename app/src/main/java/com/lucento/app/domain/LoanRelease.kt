package com.lucento.app.domain

import com.lucento.app.core.money.FxRate
import com.lucento.app.core.money.Money
import com.lucento.app.domain.fx.FxBook
import com.lucento.app.domain.fx.FxQuotes
import com.lucento.app.domain.fx.FxSource
import com.lucento.app.domain.fx.FxTransfer
import com.lucento.app.domain.fx.FxValue
import java.math.BigDecimal

/**
 * The money that moved when a loan or a receivable began (tester round, 2026-11-27). A new loan can record its proceeds:
 * the account they went into and any charges the lender deducted. A new receivable can record the account the money
 * was lent from. Both are optional and offered only while the wallet is being added.
 *
 * Both follow the debt rules (docs/CALCULATIONS.md) and are written in the same database transaction as the new wallet,
 * which then opens at zero, so what is owed comes from these rows alone and nothing counts twice:
 * - **Loan:** the proceeds are a TRANSFER out of the loan into the account. What is owed rises; it is never income. The
 *   charges are an EXPENSE on the loan under [FEES_CATEGORY]: spending, like the interest in a payment (owner,
 *   2026-11-27). They are keyed [chargesKey] of the transfer, so they count as borrowed ([LoanMath.principal]).
 *   Proceeds + charges = the amount owed entered.
 * - **Receivable:** the amount is a TRANSFER from the account into the receivable. What is owed to the person rises;
 *   it is never spending. There are no charges.
 */
object LoanRelease {
    const val PROCEEDS_NOTE = "Loan proceeds"
    const val LENT_NOTE = "Money lent"
    const val CHARGES_NOTE = "Charges and deductions"

    /** The expense category a loan's charges are recorded under, created the first time one has charges. */
    const val FEES_CATEGORY = "Loan fees"

    /**
     * Its icon. "loan" (the bank building), never "fees": scans and typed messages pick a category by icon for fees,
     * taxes and card interest ([com.lucento.app.domain.capture.merchant.CategoryIntents]), and "fees" comes before
     * "debt" there, so a "fees" Loan fees would take card interest away from Debt interest (QA, 2026-11-27).
     */
    const val FEES_ICON = "loan"

    private const val CHARGES_SUFFIX = "-charges"

    /** Loans (proceeds received) and receivables (money lent) can record one; nothing else does. */
    fun appliesTo(type: WalletType): Boolean = type == WalletType.LOAN || type == WalletType.RECEIVABLE

    /** The charges row's key: the release transfer's group with a suffix, so it can be found from the transfer. */
    fun chargesKey(group: String): String = group + CHARGES_SUFFIX

    sealed interface Plan {
        /**
         * [moved] goes into the account (a loan's proceeds) or comes out of it (a receivable). [charges] stay on the loan
         * as spending; zero when there are none (always for a receivable).
         */
        data class Ready(val moved: Money, val charges: Money) : Plan

        /** Nothing is owed, so nothing can have moved. */
        data object NothingOwed : Plan

        /** The charges are the whole loan or more: nothing would reach the account. */
        data object ChargesTooHigh : Plan
    }

    /**
     * How [owed] (the amount entered for the new wallet) splits. On a loan, [charges] stay on it and the rest goes into
     * the account. A receivable has no charges, so all of [owed] leaves the account. Null [charges] means none.
     */
    fun plan(type: WalletType, owed: Money, charges: Money?): Plan {
        if (owed.amountMinor <= 0L) return Plan.NothingOwed
        val deducted = charges?.takeIf { type == WalletType.LOAN } ?: Money.zero(owed.currencyCode)
        require(deducted.currencyCode == owed.currencyCode) { "Currencies differ" }
        if (deducted.isNegative || deducted.amountMinor >= owed.amountMinor) return Plan.ChargesTooHigh
        return Plan.Ready(moved = owed - deducted, charges = deducted)
    }

    /**
     * Whether [account] can be the other side: in [currency] (Lucento never converts a guessed amount) and an account
     * money moves through. Cards and other loans count: a loan's proceeds can pay a card off directly, and money lent can
     * come from a card's cash advance. Things valued by hand never do, and neither do receivables: a transfer out of one
     * reads as an instalment received, and one into it as more money lent (QA, 2026-11-27).
     */
    fun canUse(account: Wallet, currency: String): Boolean =
        account.type.acceptsTransactions && account.type != WalletType.RECEIVABLE && account.currencyCode == currency

    /**
     * [account]'s balance once the release is recorded, as the editor's summary shows it (an amount owed for a card or
     * loan): a loan's proceeds arrive in it, a receivable's money leaves it ([WalletBalanceCalculator]'s directions).
     */
    fun accountAfter(account: Wallet, type: WalletType, plan: Plan.Ready): Money {
        val arrives = type == WalletType.LOAN
        // Money arriving raises an asset and lowers what a card or loan owes; money leaving does the opposite.
        val raises = arrives != account.type.isLiability
        return if (raises) account.balance + plan.moved else account.balance - plan.moved
    }

    /**
     * True when [account] was added to Lucento after [date]: its starting balance was then probably typed with this money
     * already in it, and recording it again would count it twice (QA, 2026-11-27). The editor warns; it doesn't refuse,
     * since a balance can be typed without it.
     */
    fun addedAfter(account: Wallet, date: java.time.LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Boolean =
        java.time.Instant.ofEpochMilli(account.createdAtEpochMillis).atZone(zone).toLocalDate().isAfter(date)

    /**
     * The account to start with in [currency]. Spending money comes first, the one with the most money. Otherwise
     * another cash, bank, e-wallet or savings account. Never a card, loan, receivable or investment. Null when there is
     * none: the person picks one.
     */
    fun suggestedAccount(wallets: List<Wallet>, currency: String): Wallet? {
        val accounts = wallets.filter { canUse(it, currency) && it.type in SUGGESTED_TYPES }
        return accounts.filter { it.isSpendingMoney }.maxByOrNull { it.balance.amountMinor }
            ?: accounts.maxByOrNull { it.balance.amountMinor }
    }

    private val SUGGESTED_TYPES = setOf(WalletType.CASH, WalletType.BANK, WalletType.E_WALLET, WalletType.SAVINGS)

    /**
     * The charges deducted from each release on loan [walletId], by the release's transfer group. These are EXPENSE
     * rows keyed [chargesKey] of a transfer out of the same loan. A row moved to another wallet, or one whose transfer
     * was deleted, no longer counts.
     */
    fun chargesByRelease(history: List<Transaction>, walletId: Long): Map<String, Money> {
        val releases = history.mapNotNullTo(HashSet()) { t ->
            t.transferGroup?.takeIf { t.walletId == walletId && t.type == TransactionType.TRANSFER_OUT }
        }
        return history.mapNotNull { t ->
            val group = t.clientUuid
                ?.takeIf { t.walletId == walletId && t.type == TransactionType.EXPENSE && it.endsWith(CHARGES_SUFFIX) }
                ?.removeSuffix(CHARGES_SUFFIX)
            if (group != null && group in releases) group to t.amount else null
        }.toMap()
    }

    /** Each row's value in the main currency; null values follow their wallet's average. */
    data class Values(val outFx: FxValue? = null, val inFx: FxValue? = null, val chargesFx: FxValue? = null)

    /**
     * Multicurrency: the rows' values for a debt in a currency other than [main], valued the way the composer values a
     * transfer ([FxTransfer.legValues]). The money leaves its source at the source's own value, and the other side takes
     * it in at exactly that. A new loan is the source of its proceeds and holds nothing yet, so its [rate] (typed in the
     * editor) values the proceeds and the charges. A receivable's money comes from the account, at that account's
     * average in [book]. In the main currency, or with no rate to go by, nothing is stored.
     */
    fun values(main: String?, type: WalletType, rate: BigDecimal?, account: Wallet, plan: Plan.Ready, book: FxBook?): Values {
        val code = plan.moved.currencyCode
        if (main == null || code == main) return Values()
        fun atRate(amount: Money): FxValue? = rate?.takeIf { it.signum() > 0 }
            ?.let { FxValue(FxRate(code, main, it).convert(amount).abs(), FxSource.AVERAGE) }
        val source = if (type == WalletType.LOAN) {
            atRate(plan.moved)
        } else {
            book?.let { FxQuotes.quote(main, account, plan.moved, market = null, offline = FxSource.AVERAGE, book = it, lastRate = null)?.asValue }
        }
        val (outFx, inFx) = FxTransfer.legValues(main, plan.moved, plan.moved, source)
        return Values(outFx, inFx, plan.charges.takeIf { it.amountMinor > 0L }?.let(::atRate))
    }
}
