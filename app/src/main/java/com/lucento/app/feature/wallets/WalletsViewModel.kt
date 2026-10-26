package com.lucento.app.feature.wallets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.WalletRepository
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
)

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
    /** When set, saving opens a prefilled recurring payment starting on this day. */
    val reminderDate: LocalDate? = null,
)

sealed interface DraftError {
    data object NameRequired : DraftError
    data object NameTaken : DraftError
    data object AmountInvalid : DraftError
    data class AmountTooPrecise(val digits: Int) : DraftError
    data object AmountTooLarge : DraftError
    data class CurrencyLocked(val usage: WalletUsage) : DraftError
    data class TypeLocked(val usage: WalletUsage) : DraftError
    data object NotFound : DraftError
    data object InterestInvalid : DraftError
    data object TermInvalid : DraftError
    data object InstallmentInvalid : DraftError
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
) : ViewModel() {

    val uiState: StateFlow<WalletsUiState> =
        combine(wallets.observeWallets(), preferences.preferences) { list, prefs ->
            WalletsUiState(
                loading = false,
                wallets = list,
                primaryCurrency = prefs.primaryCurrency,
                summary = if (list.isEmpty()) null else NetWorthCalculator.summarize(list, prefs.primaryCurrency),
                defaultWalletId = prefs.defaultWalletId,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletsUiState())

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
        }
        val debt = if (draft.type.isLiability) {
            when (val terms = debtTerms(draft, decimalSeparator)) {
                is DebtParse.Valid -> terms.terms
                is DebtParse.Error -> return onResult(terms.error, null)
            }
        } else {
            null
        }

        saving = true
        viewModelScope.launch {
            val result = try {
                if (draft.id == null) wallets.create(draft.name, draft.type, amount, debt)
                else wallets.update(draft.id, draft.name, draft.type, amount, debt)
            } finally {
                saving = false
            }
            if (result is WalletRepository.SaveResult.Saved) {
                applyDefault(result.id, draft)
                if (draft.id == null && draft.type.isLiability) {
                    categories?.ensure(DebtMath.INTEREST_CATEGORY, CategoryKind.EXPENSE, DebtMath.INTEREST_ICON)
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
                    WalletRepository.SaveResult.NotFound -> DraftError.NotFound
                },
                (result as? WalletRepository.SaveResult.Saved)?.id,
            )
        }
    }

    private sealed interface DebtParse {
        data class Valid(val terms: DebtTerms) : DebtParse
        data class Error(val error: DraftError) : DebtParse
    }

    /** The draft's debt details: an optional rate, and for monthly repayment the term and instalment (both required). */
    private fun debtTerms(draft: WalletDraft, decimalSeparator: Char): DebtParse {
        val rate = when (val r = DebtMath.parseRate(draft.interestText)) {
            DebtMath.RateResult.Empty -> null
            is DebtMath.RateResult.Valid -> r.bps
            DebtMath.RateResult.Invalid -> return DebtParse.Error(DraftError.InterestInvalid)
        }
        if (draft.repayment == DebtRepayment.ONE_TIME) return DebtParse.Valid(DebtTerms(interestRateBps = rate))
        val months = draft.termMonthsText.trim().toIntOrNull()?.takeIf { it in 1..DebtTerms.MAX_TERM_MONTHS }
            ?: return DebtParse.Error(DraftError.TermInvalid)
        val installment = (MoneyParser.parse(draft.installmentText, draft.currencyCode, decimalSeparator) as? MoneyParser.Result.Success)
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
    fun delete(id: Long, onResult: (WalletRepository.DeleteResult) -> Unit) {
        viewModelScope.launch { onResult(wallets.delete(id)) }
    }

    companion object {
        val Factory = containerFactory { WalletsViewModel(it.wallets, it.preferences, it.categories) }
    }
}
