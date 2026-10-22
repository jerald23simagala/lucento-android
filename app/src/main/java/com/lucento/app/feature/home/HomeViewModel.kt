package com.lucento.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class HomeViewModel(
    transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        transactions.observeTransactions(),
        wallets.observeWallets(),
        categories.observeCategories(),
        preferences.preferences,
    ) { all, walletList, categoryList, prefs ->
        HomeStateBuilder.build(all, walletList, categoryList, prefs.primaryCurrency, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        val Factory = containerFactory { HomeViewModel(it.transactions, it.wallets, it.categories, it.preferences) }
    }
}
