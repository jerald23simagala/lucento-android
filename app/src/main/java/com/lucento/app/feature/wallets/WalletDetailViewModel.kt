package com.lucento.app.feature.wallets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.data.repository.adjustBalanceOrNull
import com.lucento.app.domain.Category
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Transfer
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WalletDetailUiState(
    val loading: Boolean = true,
    /** Null once loaded means the wallet no longer exists (deleted or archived). */
    val wallet: Wallet? = null,
    val wallets: List<Wallet> = emptyList(),
    /** This wallet's own movements, newest first, including its side of each transfer. */
    val history: List<Transaction> = emptyList(),
    val transfers: Map<String, Transfer> = emptyMap(),
    val categoriesById: Map<Long, Category> = emptyMap(),
) {
    val walletNames: Map<Long, String> = wallets.associate { it.id to it.name }
}

/** Why a balance override was refused; nothing is written when one is. */
sealed interface AdjustError {
    data object AmountInvalid : AdjustError
    data class AmountTooPrecise(val digits: Int) : AdjustError
    data object AmountTooLarge : AdjustError
    data object FutureDate : AdjustError
    /** The actual balance already equals what Lucento tracked on that date. */
    data object Unchanged : AdjustError
    /** The wallet is gone, or the database refused the write. */
    data object Failed : AdjustError
}

class WalletDetailViewModel(
    private val walletId: Long,
    wallets: WalletRepository,
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<WalletDetailUiState> = combine(
        wallets.observeWallets(),
        transactions.observeTransactions(),
        categories.observeCategories(),
    ) { walletList, all, categoryList ->
        WalletDetailUiState(
            loading = false,
            wallet = walletList.firstOrNull { it.id == walletId },
            wallets = walletList,
            history = all.filter { it.walletId == walletId },
            transfers = Transfer.pair(all),
            categoriesById = categoryList.associateBy { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletDetailUiState())

    private var adjusting = false

    /**
     * Sets the wallet's balance at the end of [date] to [actualText] (the amount owed for a card or loan); the gap is
     * recorded as a balance adjustment. [clientUuid] is fixed per sheet, so a double tap can't record it twice.
     * [onResult] receives null on success.
     */
    fun adjust(actualText: String, decimalSeparator: Char, date: LocalDate, clientUuid: String, onResult: (AdjustError?) -> Unit) {
        if (adjusting) return
        val wallet = uiState.value.wallet ?: return onResult(AdjustError.Failed)
        val actual = when (val parsed = MoneyParser.parse(actualText, wallet.currencyCode, decimalSeparator)) {
            is MoneyParser.Result.Success -> parsed.money
            MoneyParser.Result.Empty -> com.lucento.app.core.money.Money.zero(wallet.currencyCode)
            MoneyParser.Result.Invalid -> return onResult(AdjustError.AmountInvalid)
            is MoneyParser.Result.TooManyDecimals -> return onResult(AdjustError.AmountTooPrecise(parsed.allowed))
            MoneyParser.Result.TooLarge -> return onResult(AdjustError.AmountTooLarge)
        }
        adjusting = true
        viewModelScope.launch {
            val result = try {
                transactions.adjustBalanceOrNull(walletId, actual, date, clientUuid)
            } finally {
                adjusting = false
            }
            onResult(
                when (result) {
                    is TransactionRepository.AdjustResult.Adjusted -> null
                    TransactionRepository.AdjustResult.Unchanged -> AdjustError.Unchanged
                    TransactionRepository.AdjustResult.FutureDate -> AdjustError.FutureDate
                    else -> AdjustError.Failed
                }
            )
        }
    }

    companion object {
        fun factory(walletId: Long) = containerFactory {
            WalletDetailViewModel(walletId, it.wallets, it.transactions, it.categories)
        }
    }
}
