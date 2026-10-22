package com.lucento.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.Budget
import com.lucento.app.domain.Category
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class HomeViewModel(
    transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    recurring: RecurringRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private data class Core(
        val transactions: List<Transaction>,
        val wallets: List<Wallet>,
        val categories: List<Category>,
        val budgets: List<Budget>,
        val schedules: List<RecurringSchedule>,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            transactions.observeTransactions(),
            wallets.observeWallets(),
            categories.observeCategories(),
            budgets.observeBudgets(),
            recurring.observeSchedules(),
        ) { all, walletList, categoryList, budgetList, scheduleList ->
            Core(all, walletList, categoryList, budgetList, scheduleList)
        },
        preferences.preferences,
    ) { core, prefs ->
        HomeStateBuilder.build(
            all = core.transactions,
            wallets = core.wallets,
            categories = core.categories,
            primaryCurrency = prefs.primaryCurrency,
            today = LocalDate.now(),
            budgets = core.budgets,
            schedules = core.schedules,
            features = prefs.features,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        val Factory = containerFactory {
            HomeViewModel(it.transactions, it.wallets, it.categories, it.budgets, it.recurring, it.preferences)
        }
    }
}
