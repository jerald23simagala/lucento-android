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
import com.lucento.app.domain.WalletUsage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
}

/** Plain-language explanation of what still uses a wallet, e.g. "3 transactions and 1 recurring payment". */
fun WalletUsage.describe(): String = listOfNotNull(
    transactionCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "transaction" else "transactions"}" },
    scheduleCount.takeIf { it > 0 }?.let { "$it recurring ${if (it == 1) "payment" else "payments"}" },
).joinToString(" and ")

class WalletsViewModel(
    private val wallets: WalletRepository,
    private val preferences: UserPreferencesRepository,
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
            if (result is WalletRepository.SaveResult.Saved) applyDefault(result.id, draft)
            onResult(
                when (result) {
                    is WalletRepository.SaveResult.Saved -> null
                    WalletRepository.SaveResult.BlankName -> DraftError.NameRequired
                    WalletRepository.SaveResult.DuplicateName -> DraftError.NameTaken
                    WalletRepository.SaveResult.NegativeBalance -> DraftError.AmountInvalid
                    is WalletRepository.SaveResult.CurrencyLocked -> DraftError.CurrencyLocked(result.usage)
                    is WalletRepository.SaveResult.TypeLocked -> DraftError.TypeLocked(result.usage)
                    WalletRepository.SaveResult.NotFound -> DraftError.NotFound
                }
            )
        }
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
        val Factory = containerFactory { WalletsViewModel(it.wallets, it.preferences) }
    }
}
