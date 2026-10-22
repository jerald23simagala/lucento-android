package com.lucento.app.feature.wallets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.NetWorthCalculator
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WalletsUiState(
    val loading: Boolean = true,
    val wallets: List<Wallet> = emptyList(),
    val primaryCurrency: String = "USD",
    val summary: NetWorthCalculator.Summary? = null,
)

/** Editable form state for creating or updating a wallet. */
data class WalletDraft(
    val id: Long? = null,
    val name: String = "",
    val type: WalletType = WalletType.CASH,
    val currencyCode: String,
    val amountText: String = "",
)

sealed interface DraftError {
    data object NameRequired : DraftError
    data object NameTaken : DraftError
    data object AmountInvalid : DraftError
    data class AmountTooPrecise(val digits: Int) : DraftError
    data object AmountTooLarge : DraftError
}

class WalletsViewModel(
    private val wallets: WalletRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<WalletsUiState> =
        combine(wallets.observeWallets(), preferences.preferences) { list, prefs ->
            WalletsUiState(
                loading = false,
                wallets = list,
                primaryCurrency = prefs.primaryCurrency,
                summary = if (list.isEmpty()) null else NetWorthCalculator.summarize(list, prefs.primaryCurrency),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletsUiState())

    private var saving = false

    /**
     * Validates and persists [draft]. [onResult] receives null on success or the first error.
     * Re-entrant taps while a save is in flight are ignored to prevent duplicate wallets.
     */
    fun save(draft: WalletDraft, decimalSeparator: Char, onResult: (DraftError?) -> Unit) {
        if (saving) return
        if (draft.name.isBlank()) return onResult(DraftError.NameRequired)

        val amount = when (val parsed = MoneyParser.parse(draft.amountText, draft.currencyCode, decimalSeparator)) {
            is MoneyParser.Result.Success -> parsed.money
            MoneyParser.Result.Empty -> com.lucento.app.core.money.Money.zero(draft.currencyCode)
            MoneyParser.Result.Invalid -> return onResult(DraftError.AmountInvalid)
            is MoneyParser.Result.TooManyDecimals -> return onResult(DraftError.AmountTooPrecise(parsed.allowed))
            MoneyParser.Result.TooLarge -> return onResult(DraftError.AmountTooLarge)
        }

        saving = true
        viewModelScope.launch {
            val result = try {
                if (draft.id == null) wallets.create(draft.name, draft.type, amount)
                else wallets.update(draft.id, draft.name, draft.type, amount)
            } finally {
                saving = false
            }
            onResult(
                when (result) {
                    is WalletRepository.SaveResult.Saved -> null
                    WalletRepository.SaveResult.BlankName -> DraftError.NameRequired
                    WalletRepository.SaveResult.DuplicateName -> DraftError.NameTaken
                    WalletRepository.SaveResult.NegativeBalance -> DraftError.AmountInvalid
                }
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { wallets.delete(id) }
    }

    companion object {
        val Factory = containerFactory { WalletsViewModel(it.wallets, it.preferences) }
    }
}
