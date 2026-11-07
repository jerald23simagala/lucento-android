package com.lucento.app.feature.wallets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.preferences.WalletsViewMode
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.NetWorthCalculator
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.WalletUsage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WalletsUiState(
    val loading: Boolean = true,
    val wallets: List<Wallet> = emptyList(),
    val primaryCurrency: String = "USD",
    val summary: NetWorthCalculator.Summary? = null,
    val defaultWalletId: Long? = null,
    /** The hero's net worth over the last months and its month-on-month change, in the hero's currency. */
    val trend: NetWorthTrend? = null,
    val viewMode: WalletsViewMode = WalletsViewMode.ROWS,
    val side: WalletSide = WalletSide.ALL,
    /**
     * Multicurrency: what each wallet in another currency is carried at in the main currency (the average method), and
     * which ones have no rate yet (still outside the main-currency totals).
     */
    val fxBook: com.lucento.app.domain.fx.FxBook? = null,
) {
    /** The hero's total: the main currency, with every valued wallet in another currency converted into it. */
    val heroTotal: Money? get() = summary?.let { it.totalFor(primaryCurrency) ?: it.totals.firstOrNull() }

    /** What the list shows after the side filter. */
    val shown: List<Wallet> get() = WalletsOverview.filter(wallets, side)
}

/** Editable form state for creating or updating a wallet. */
data class WalletDraft(
    val id: Long? = null,
    val name: String = "",
    val type: WalletType = WalletType.CASH,
    val currencyCode: String,
    val amountText: String = "",
    /** "Default wallet": new transactions go here. Only kept for spendable types (cash, bank, e-wallet). */
    val isDefault: Boolean = false,
    // Debt details, kept only for a card or loan (see DebtTerms).
    /** Yearly interest rate as typed, e.g. "12.5"; optional. */
    val interestText: String = "",
    val repayment: DebtRepayment = DebtRepayment.ONE_TIME,
    val termMonthsText: String = "",
    val installmentText: String = "",
    /** When set, saving opens a prefilled recurring payment starting on this day (loans). */
    val reminderDate: LocalDate? = null,
    // Credit cards only: the limit and the billing days (day of the month, 1–31), as typed.
    val creditLimitText: String = "",
    val statementDayText: String = "",
    val dueDayText: String = "",
    /** Bank or provider from the bundled registry (display only); never kept for cash. */
    val institutionId: String? = null,
    /** Multicurrency: for a wallet in another currency, 1 unit = this many of the main currency, as typed ("63.69"). */
    val fxRateText: String = "",
)

sealed interface DraftError {
    data object NameRequired : DraftError
    data object NameTaken : DraftError
    data object AmountInvalid : DraftError
    data class AmountTooPrecise(val digits: Int) : DraftError
    data object AmountTooLarge : DraftError
    /** Ambiguous, or another currency's symbol: [message] says what to type (MoneyParser.clarification). */
    data class AmountUnclear(val message: String) : DraftError
    data class CurrencyLocked(val usage: WalletUsage) : DraftError
    data class TypeLocked(val usage: WalletUsage) : DraftError
    data object NotFound : DraftError
    /** Lucento Pro: the plan doesn't allow this wallet; [message] says the limit and that nothing existing changes. */
    data class PlanLimit(val message: String) : DraftError
    data object InterestInvalid : DraftError
    data object TermInvalid : DraftError
    data object InstallmentInvalid : DraftError
    /** The instalment is ambiguous or carries another currency's symbol (MoneyParser.clarification). */
    data class InstallmentUnclear(val message: String) : DraftError
    data object CreditLimitInvalid : DraftError
    /** The credit limit is ambiguous or carries another currency's symbol (MoneyParser.clarification). */
    data class CreditLimitUnclear(val message: String) : DraftError
    /** A billing day outside 1–31, or only one of the statement and due days. */
    data object BillingDaysInvalid : DraftError
    /** Multicurrency: a wallet in another currency needs its rate into the main currency. */
    data object RateRequired : DraftError
}

/** Plain-language explanation of what still uses a wallet, e.g. "3 transactions and 1 recurring payment". */
fun WalletUsage.describe(): String = listOfNotNull(
    transactionCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "transaction" else "transactions"}" },
    scheduleCount.takeIf { it > 0 }?.let { "$it recurring ${if (it == 1) "payment" else "payments"}" },
).joinToString(" and ")

class WalletsViewModel(
    private val wallets: WalletRepository,
    private val preferences: UserPreferencesRepository,
    private val categories: CategoryRepository? = null,
    /** For the hero's balance history; the app shares one transactions query across screens. */
    transactions: TransactionRepository? = null,
) : ViewModel() {

    private val side = MutableStateFlow(WalletSide.ALL)

    /** Balances, net worth and the trend: rebuilt only when wallets, transactions or the main currency change. */
    private val overview = combine(
        wallets.observeWallets(),
        transactions?.observeTransactions() ?: flowOf(emptyList()),
        preferences.preferences.map { it.primaryCurrency }.distinctUntilChanged(),
    ) { list, all, primary ->
        // Multicurrency: totals in the main currency; the list keeps each wallet's own currency (docs/CALCULATIONS.md).
        val ledger = com.lucento.app.domain.fx.FxTranslation.translate(primary, list, all)
        val summary = if (list.isEmpty()) null else NetWorthCalculator.summarize(ledger.wallets, primary)
        val currency = summary?.let { (it.totalFor(primary) ?: it.totals.first()).currencyCode }
        Overview(list, summary, currency?.let { WalletsOverview.trend(ledger.wallets, ledger.withFxResults, it, LocalDate.now()) }, ledger.book)
    }.flowOn(Dispatchers.Default)

    private data class Overview(
        val wallets: List<Wallet>,
        val summary: NetWorthCalculator.Summary?,
        val trend: NetWorthTrend?,
        val book: com.lucento.app.domain.fx.FxBook,
    )

    val uiState: StateFlow<WalletsUiState> =
        combine(overview, preferences.preferences, side) { overview, prefs, side ->
            WalletsUiState(
                loading = false,
                wallets = overview.wallets,
                primaryCurrency = prefs.primaryCurrency,
                summary = overview.summary,
                defaultWalletId = prefs.defaultWalletId,
                trend = overview.trend,
                viewMode = prefs.walletsViewMode,
                side = side,
                fxBook = overview.book,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletsUiState())

    fun setSide(value: WalletSide) {
        side.value = value
    }

    /** Remembered on this device (DataStore `wallets_view_mode`), so Wallets opens the way it was left. */
    fun setViewMode(mode: WalletsViewMode) {
        viewModelScope.launch { runCatching { preferences.setWalletsViewMode(mode) } }
    }

    private var saving = false

    /**
     * Validates and persists [draft]. [onResult] receives the first error (null on success) and the saved wallet's id.
     * Re-entrant taps while a save is in flight are ignored to prevent duplicate wallets. Creating a card or loan also
     * sets up the "Debt interest" expense category its payments' interest is recorded under.
     */
    fun save(draft: WalletDraft, decimalSeparator: Char, onResult: (DraftError?, Long?) -> Unit) {
        if (saving) return
        if (draft.name.isBlank()) return onResult(DraftError.NameRequired, null)

        val amount = when (val parsed = MoneyParser.parse(draft.amountText, draft.currencyCode, decimalSeparator)) {
            is MoneyParser.Result.Success -> parsed.money
            MoneyParser.Result.Empty -> com.lucento.app.core.money.Money.zero(draft.currencyCode)
            MoneyParser.Result.Invalid -> return onResult(DraftError.AmountInvalid, null)
            is MoneyParser.Result.TooManyDecimals -> return onResult(DraftError.AmountTooPrecise(parsed.allowed), null)
            MoneyParser.Result.TooLarge -> return onResult(DraftError.AmountTooLarge, null)
            is MoneyParser.Result.Ambiguous, is MoneyParser.Result.WrongCurrency ->
                return onResult(DraftError.AmountUnclear(MoneyParser.clarification(parsed, decimalSeparator).orEmpty()), null)
        }
        val debt = if (draft.type.hasTerms) {
            when (val terms = debtTerms(draft, decimalSeparator)) {
                is DebtParse.Valid -> terms.terms
                is DebtParse.Error -> return onResult(terms.error, null)
            }
        } else {
            null
        }

        saving = true
        viewModelScope.launch {
            // Multicurrency: a wallet in another currency is valued at a rate into the main currency (required).
            val main = preferences.preferences.first().primaryCurrency
            val rate = if (draft.currencyCode == main) null else com.lucento.app.core.money.FxRate.parse(draft.fxRateText)
            if (draft.currencyCode != main && rate == null) {
                saving = false
                return@launch onResult(DraftError.RateRequired, null)
            }
            val result = try {
                val institution = draft.institutionId.takeIf { draft.type != WalletType.CASH }
                if (draft.id == null) {
                    wallets.create(draft.name, draft.type, amount, debt, institution, fxBasisRate = rate)
                } else {
                    wallets.update(draft.id, draft.name, draft.type, amount, debt, institution).also { saved ->
                        if (saved is WalletRepository.SaveResult.Saved && rate != null) wallets.setFxBasis(draft.id, rate)
                    }
                }
            } finally {
                saving = false
            }
            if (result is WalletRepository.SaveResult.Saved) {
                applyDefault(result.id, draft)
                if (draft.id == null && draft.type.isLiability) {
                    categories?.ensure(DebtMath.INTEREST_CATEGORY, CategoryKind.EXPENSE, DebtMath.INTEREST_ICON)
                }
                // A receivable's interest is income; its collections record it under this category (owner round 10).
                if (draft.id == null && draft.type == WalletType.RECEIVABLE) {
                    categories?.ensure(DebtMath.INTEREST_INCOME_CATEGORY, CategoryKind.INCOME, DebtMath.INTEREST_INCOME_ICON)
                }
            }
            onResult(
                when (result) {
                    is WalletRepository.SaveResult.Saved -> null
                    WalletRepository.SaveResult.BlankName -> DraftError.NameRequired
                    WalletRepository.SaveResult.DuplicateName -> DraftError.NameTaken
                    WalletRepository.SaveResult.NegativeBalance -> DraftError.AmountInvalid
                    is WalletRepository.SaveResult.CurrencyLocked -> DraftError.CurrencyLocked(result.usage)
                    is WalletRepository.SaveResult.TypeLocked -> DraftError.TypeLocked(result.usage)
                    WalletRepository.SaveResult.NotFound, WalletRepository.SaveResult.NotPermitted -> DraftError.NotFound
                    is WalletRepository.SaveResult.LimitReached -> DraftError.PlanLimit(planLimitMessage(result))
                    is WalletRepository.SaveResult.ProRequired -> DraftError.PlanLimit(proRequiredMessage(result.feature))
                },
                (result as? WalletRepository.SaveResult.Saved)?.id,
            )
        }
    }

    /** Lucento Pro copy (docs/monetization/CUSTOMER_COPY.md D-1). Reached only once the gates are on. */
    private fun planLimitMessage(result: WalletRepository.SaveResult.LimitReached): String {
        val kind = when (result.feature) {
            com.lucento.app.domain.entitlement.ProFeature.DEBT_ACCOUNTS -> "cards or loans"
            else -> "money wallets"
        }
        val max = result.limit.maxOrNull
        return if (max != null) {
            "You have $max $kind, the most on your plan. Everything in the ones you have keeps working."
        } else {
            "Your plan doesn\u2019t allow another of these. Everything you have keeps working."
        }
    }

    private fun proRequiredMessage(feature: com.lucento.app.domain.entitlement.ProFeature): String = when (feature) {
        com.lucento.app.domain.entitlement.ProFeature.RECEIVABLES -> "Receivables are part of Lucento Pro."
        com.lucento.app.domain.entitlement.ProFeature.VALUED_ASSETS -> "Assets at a value you set are part of Lucento Pro."
        com.lucento.app.domain.entitlement.ProFeature.FOREIGN_CURRENCY_WALLETS -> "Wallets in other currencies are part of Lucento Pro. A wallet in your main currency is always free."
        else -> "This kind of wallet is part of Lucento Pro."
    }

    private sealed interface DebtParse {
        data class Valid(val terms: DebtTerms) : DebtParse
        data class Error(val error: DraftError) : DebtParse
    }

    /**
     * The draft's debt details: an optional rate, and for monthly repayment the term and instalment (both required).
     * A credit card adds an optional limit and optional billing days, which come as a pair.
     */
    private fun debtTerms(draft: WalletDraft, decimalSeparator: Char): DebtParse {
        val base = when (val terms = repaymentTerms(draft, decimalSeparator)) {
            is DebtParse.Valid -> terms.terms
            is DebtParse.Error -> return terms
        }
        if (draft.type != WalletType.CREDIT_CARD) return DebtParse.Valid(base)
        val parsedLimit = MoneyParser.parse(draft.creditLimitText, draft.currencyCode, decimalSeparator)
        MoneyParser.clarification(parsedLimit, decimalSeparator)?.let { return DebtParse.Error(DraftError.CreditLimitUnclear(it)) }
        val limit = when (parsedLimit) {
            MoneyParser.Result.Empty -> null
            is MoneyParser.Result.Success -> parsedLimit.money.takeIf { it.amountMinor > 0 } ?: return DebtParse.Error(DraftError.CreditLimitInvalid)
            else -> return DebtParse.Error(DraftError.CreditLimitInvalid)
        }
        val statement = draft.statementDayText.trim()
        val due = draft.dueDayText.trim()
        val days = when {
            statement.isEmpty() && due.isEmpty() -> null to null
            else -> {
                val s = statement.toIntOrNull()?.takeIf { it in 1..31 }
                val d = due.toIntOrNull()?.takeIf { it in 1..31 }
                if (s == null || d == null) return DebtParse.Error(DraftError.BillingDaysInvalid)
                s to d
            }
        }
        return DebtParse.Valid(base.copy(creditLimit = limit, statementDay = days.first, dueDay = days.second))
    }

    /** The rate and the repayment plan; a card keeps whatever plan it already had (cards no longer choose one). */
    private fun repaymentTerms(draft: WalletDraft, decimalSeparator: Char): DebtParse {
        val rate = when (val r = DebtMath.parseRate(draft.interestText)) {
            DebtMath.RateResult.Empty -> null
            is DebtMath.RateResult.Valid -> r.bps
            DebtMath.RateResult.Invalid -> return DebtParse.Error(DraftError.InterestInvalid)
        }
        if (draft.repayment == DebtRepayment.ONE_TIME) return DebtParse.Valid(DebtTerms(interestRateBps = rate))
        val months = draft.termMonthsText.trim().toIntOrNull()?.takeIf { it in 1..DebtTerms.MAX_TERM_MONTHS }
            ?: return DebtParse.Error(DraftError.TermInvalid)
        val parsedInstallment = MoneyParser.parse(draft.installmentText, draft.currencyCode, decimalSeparator)
        MoneyParser.clarification(parsedInstallment, decimalSeparator)?.let { return DebtParse.Error(DraftError.InstallmentUnclear(it)) }
        val installment = (parsedInstallment as? MoneyParser.Result.Success)
            ?.money?.takeIf { it.amountMinor > 0 }
            ?: return DebtParse.Error(DraftError.InstallmentInvalid)
        return DebtParse.Valid(DebtTerms(interestRateBps = rate, repayment = DebtRepayment.MONTHLY, termMonths = months, installment = installment))
    }

    /** Makes the saved wallet the default, or stops it being one (unticked, or changed to a non-spending type). */
    private suspend fun applyDefault(id: Long, draft: WalletDraft) {
        val current = preferences.preferences.first().defaultWalletId
        when {
            draft.isDefault && draft.type.isSpendable -> if (current != id) preferences.setDefaultWallet(id)
            current == id -> preferences.setDefaultWallet(null)
        }
    }

    /** Deletes an empty wallet; [onResult] says whether it was deleted or what still uses it. */
    /** Lucento Shared: the Owner labels a wallet Shared, theirs or their Partner's (an organizational label). */
    fun setClassification(id: Long, classification: com.lucento.app.domain.WalletClassification) {
        viewModelScope.launch { wallets.setClassification(id, classification) }
    }

    fun delete(id: Long, onResult: (WalletRepository.DeleteResult) -> Unit) {
        viewModelScope.launch { onResult(wallets.delete(id)) }
    }

    companion object {
        val Factory = containerFactory { WalletsViewModel(it.wallets, it.preferences, it.categories, it.transactions) }
    }
}
