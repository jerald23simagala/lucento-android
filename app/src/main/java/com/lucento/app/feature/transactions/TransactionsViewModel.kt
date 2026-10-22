package com.lucento.app.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.Money
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.Category
import com.lucento.app.domain.DayGroup
import com.lucento.app.domain.Insights
import com.lucento.app.domain.PeriodTotals
import com.lucento.app.domain.RangePreset
import com.lucento.app.domain.TransactionFilter
import com.lucento.app.domain.TransactionQueries
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** User-chosen filter controls; resolved into a domain [TransactionFilter] at query time. */
data class TransactionFilterState(
    val query: String = "",
    val type: TransactionType? = null,
    val walletId: Long? = null,
    val categoryId: Long? = null,
    val preset: RangePreset = RangePreset.ALL,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || type != null || walletId != null || categoryId != null || preset != RangePreset.ALL
}

/** A day of history with its net movement per currency (primary currency first). */
data class DaySection(val group: DayGroup, val net: List<Money>)

data class TransactionsUiState(
    val loading: Boolean = true,
    val primaryCurrency: String = "USD",
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filter: TransactionFilterState = TransactionFilterState(),
    val sections: List<DaySection> = emptyList(),
    /** In/out totals of the transactions currently shown, per currency, primary first. */
    val shownTotals: List<PeriodTotals> = emptyList(),
    val totalCount: Int = 0,
    val matchCount: Int = 0,
) {
    val walletsById: Map<Long, Wallet> = wallets.associateBy { it.id }
    val categoriesById: Map<Long, Category> = categories.associateBy { it.id }
}

class TransactionsViewModel(
    transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val filterState = MutableStateFlow(TransactionFilterState())

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactions.observeTransactions(),
        wallets.observeWallets(),
        categories.observeCategories(),
        filterState,
        preferences.preferences,
    ) { all, walletList, categoryList, filter, prefs ->
        val matches = TransactionQueries.filter(
            transactions = all,
            filter = TransactionFilter(
                query = filter.query,
                type = filter.type,
                walletId = filter.walletId,
                categoryId = filter.categoryId,
                range = filter.preset.resolve(LocalDate.now()),
            ),
            categoryNames = categoryList.associate { it.id to it.name },
            walletNames = walletList.associate { it.id to it.name },
        )
        TransactionsUiState(
            loading = false,
            primaryCurrency = prefs.primaryCurrency,
            wallets = walletList,
            categories = categoryList,
            filter = filter,
            sections = TransactionQueries.groupByDay(matches).map { group ->
                DaySection(group, Insights.netByCurrency(group.items, prefs.primaryCurrency))
            },
            shownTotals = TransactionQueries.totalsByCurrency(matches, prefs.primaryCurrency),
            totalCount = all.size,
            matchCount = matches.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setQuery(query: String) = filterState.update { it.copy(query = query.take(60)) }
    fun setType(type: TransactionType?) = filterState.update { it.copy(type = type) }
    fun setWallet(walletId: Long?) = filterState.update { it.copy(walletId = walletId) }
    fun setCategory(categoryId: Long?) = filterState.update { it.copy(categoryId = categoryId) }
    fun setPreset(preset: RangePreset) = filterState.update { it.copy(preset = preset) }
    fun clearFilters() = filterState.update { TransactionFilterState() }

    companion object {
        val Factory = containerFactory {
            TransactionsViewModel(it.transactions, it.wallets, it.categories, it.preferences)
        }
    }
}
