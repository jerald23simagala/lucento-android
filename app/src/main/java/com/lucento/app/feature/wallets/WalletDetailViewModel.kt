package com.lucento.app.feature.wallets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.Category
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Transfer
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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

class WalletDetailViewModel(
    walletId: Long,
    wallets: WalletRepository,
    transactions: TransactionRepository,
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

    companion object {
        fun factory(walletId: Long) = containerFactory {
            WalletDetailViewModel(walletId, it.wallets, it.transactions, it.categories)
        }
    }
}
