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
import com.lucento.app.domain.Transfer
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * User-chosen filter controls; resolved into a domain [TransactionFilter] at query time. The page opens on this month
 * (owner, 2026-11-04), so its Expenses total is the same month's spending as Home's and the Budget page's Spent.
 */
data class TransactionFilterState(
    val query: String = "",
    val type: TransactionType? = null,
    val walletId: Long? = null,
    val categoryId: Long? = null,
    val preset: RangePreset = RangePreset.THIS_MONTH,
    /** One calendar month (from the dashboard's "top spending" tiles); replaces [preset] while set. */
    val month: java.time.YearMonth? = null,
) {
    /** Anything other than the page's opening view (this month, nothing else narrowed); Clear goes back to it. */
    val isActive: Boolean get() = this != TransactionFilterState()

    /** The opening view: only the date range can be the reason a list is empty. */
    val isDefault: Boolean get() = !isActive
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
    val transfers: Map<String, Transfer> = emptyMap(),
    /** Multicurrency: each movement in another currency's value in the main currency, for an "≈ ₱…" line. */
    val fxValues: Map<Long, com.lucento.app.core.money.Money> = emptyMap(),
) {
    val walletsById: Map<Long, Wallet> = wallets.associateBy { it.id }
    val walletNames: Map<Long, String> = wallets.associate { it.id to it.name }
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
        // In combined history a transfer is listed once (its outgoing leg). Filtered to one wallet,
        // that wallet's own leg is shown instead, so its history reads correctly.
        val visible = all.filter { t ->
            when {
                !t.isTransfer -> true
                filter.walletId == null -> t.type == TransactionType.TRANSFER_OUT
                else -> true
            }
        }
        val matches = TransactionQueries.filter(
            transactions = visible,
            filter = TransactionFilter(
                query = filter.query,
                type = filter.type,
                walletId = filter.walletId,
                categoryId = filter.categoryId,
                range = filter.month?.let { com.lucento.app.domain.DateRange.monthOf(it.atDay(1)) } ?: filter.preset.resolve(LocalDate.now()),
            ),
            categoryNames = categoryList.associate { it.id to it.name },
            walletNames = walletList.associate { it.id to it.name },
        )
        // Multicurrency: rows keep their own currency; the day and page totals are in the main currency, each movement
        // in another currency at its own value (docs/CALCULATIONS.md "Exchange rates").
        val ledger = com.lucento.app.domain.fx.FxTranslation.translate(prefs.primaryCurrency, walletList, all)
        val valued = ledger.transactions.associateBy { it.id }
        fun inMain(list: List<com.lucento.app.domain.Transaction>) = list.map { valued[it.id] ?: it }
        TransactionsUiState(
            loading = false,
            primaryCurrency = prefs.primaryCurrency,
            wallets = walletList,
            categories = categoryList,
            filter = filter,
            sections = TransactionQueries.groupByDay(matches).map { group ->
                DaySection(group, Insights.netByCurrency(inMain(group.items), prefs.primaryCurrency))
            },
            shownTotals = TransactionQueries.totalsByCurrency(
                inMain(matches),
                com.lucento.app.domain.FocusCurrency.of(prefs.primaryCurrency, ledger.wallets, ledger.transactions),
            ),
            fxValues = ledger.book.values,
            totalCount = visible.size,
            matchCount = matches.size,
            transfers = Transfer.pair(all),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setQuery(query: String) = filterState.update { it.copy(query = query.take(60)) }
    fun setType(type: TransactionType?) = filterState.update { it.copy(type = type) }
    fun setWallet(walletId: Long?) = filterState.update { it.copy(walletId = walletId) }
    fun setCategory(categoryId: Long?) = filterState.update { it.copy(categoryId = categoryId) }
    fun setPreset(preset: RangePreset) = filterState.update { it.copy(preset = preset, month = null) }

    private var initialApplied = false

    /** Opened for one category and month (dashboard tile): applied once per screen, then the user's own filters win. */
    fun applyInitial(categoryId: Long?, month: java.time.YearMonth?) {
        if (initialApplied || (categoryId == null && month == null)) return
        initialApplied = true
        filterState.update { it.copy(categoryId = categoryId, month = month) }
    }
    fun clearFilters() = filterState.update { TransactionFilterState() }

    companion object {
        val Factory = containerFactory {
            TransactionsViewModel(it.transactions, it.wallets, it.categories, it.preferences)
        }
    }
}
