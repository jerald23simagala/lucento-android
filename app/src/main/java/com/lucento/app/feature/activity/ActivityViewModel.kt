package com.lucento.app.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.Category
import com.lucento.app.domain.DayGroup
import com.lucento.app.domain.RangePreset
import com.lucento.app.domain.Transaction
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** User-chosen filter controls; resolved into a domain [TransactionFilter] at query time. */
data class ActivityFilterState(
    val query: String = "",
    val type: TransactionType? = null,
    val walletId: Long? = null,
    val categoryId: Long? = null,
    val preset: RangePreset = RangePreset.ALL,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || type != null || walletId != null || categoryId != null || preset != RangePreset.ALL
}

data class ActivityUiState(
    val loading: Boolean = true,
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filter: ActivityFilterState = ActivityFilterState(),
    val groups: List<DayGroup> = emptyList(),
    val totalCount: Int = 0,
    val matchCount: Int = 0,
) {
    val walletsById: Map<Long, Wallet> = wallets.associateBy { it.id }
    val categoriesById: Map<Long, Category> = categories.associateBy { it.id }
}

/** Editable form state for creating or updating a transaction. */
data class TransactionDraft(
    val id: Long? = null,
    /** Idempotency key: generated once per draft, so a double tap or retry cannot create two rows. */
    val clientUuid: String = UUID.randomUUID().toString(),
    val type: TransactionType = TransactionType.EXPENSE,
    val walletId: Long? = null,
    val categoryId: Long? = null,
    val amountText: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
)

fun Transaction.toDraft(): TransactionDraft = TransactionDraft(
    id = id,
    type = type,
    walletId = walletId,
    categoryId = categoryId,
    amountText = MoneyFormatter.formatPlain(amount),
    date = date,
    note = note,
)

sealed interface TransactionDraftError {
    data object WalletRequired : TransactionDraftError
    data object AmountInvalid : TransactionDraftError
    data object AmountZero : TransactionDraftError
    data class AmountTooPrecise(val digits: Int) : TransactionDraftError
    data object AmountTooLarge : TransactionDraftError
    /** The save was rejected by repository rules (e.g. the wallet or category changed underneath). */
    data object Rejected : TransactionDraftError
}

class ActivityViewModel(
    private val transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val filterState = MutableStateFlow(ActivityFilterState())

    val uiState: StateFlow<ActivityUiState> = combine(
        transactions.observeTransactions(),
        wallets.observeWallets(),
        categories.observeCategories(),
        filterState,
        preferences.preferences,
    ) { all, walletList, categoryList, filter, _ ->
        val categoryNames = categoryList.associate { it.id to it.name }
        val walletNames = walletList.associate { it.id to it.name }
        val matches = TransactionQueries.filter(
            transactions = all,
            filter = TransactionFilter(
                query = filter.query,
                type = filter.type,
                walletId = filter.walletId,
                categoryId = filter.categoryId,
                range = filter.preset.resolve(LocalDate.now()),
            ),
            categoryNames = categoryNames,
            walletNames = walletNames,
        )
        ActivityUiState(
            loading = false,
            wallets = walletList,
            categories = categoryList,
            filter = filter,
            groups = TransactionQueries.groupByDay(matches),
            totalCount = all.size,
            matchCount = matches.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    fun setQuery(query: String) = filterState.update { it.copy(query = query.take(60)) }
    fun setType(type: TransactionType?) = filterState.update { it.copy(type = type) }
    fun setWallet(walletId: Long?) = filterState.update { it.copy(walletId = walletId) }
    fun setCategory(categoryId: Long?) = filterState.update { it.copy(categoryId = categoryId) }
    fun setPreset(preset: RangePreset) = filterState.update { it.copy(preset = preset) }
    fun clearFilters() = filterState.update { ActivityFilterState() }

    private var saving = false

    /**
     * Validates and persists [draft]. [onResult] receives null on success or the first error.
     * Re-entrant taps while a save is in flight are ignored, and the draft's idempotency key
     * guarantees a retry cannot create a second transaction.
     */
    fun save(draft: TransactionDraft, decimalSeparator: Char, onResult: (TransactionDraftError?) -> Unit) {
        if (saving) return
        val wallet = uiState.value.walletsById[draft.walletId] ?: return onResult(TransactionDraftError.WalletRequired)

        val amount = when (val parsed = MoneyParser.parse(draft.amountText, wallet.currencyCode, decimalSeparator)) {
            is MoneyParser.Result.Success -> parsed.money
            MoneyParser.Result.Empty, MoneyParser.Result.Invalid -> return onResult(TransactionDraftError.AmountInvalid)
            is MoneyParser.Result.TooManyDecimals -> return onResult(TransactionDraftError.AmountTooPrecise(parsed.allowed))
            MoneyParser.Result.TooLarge -> return onResult(TransactionDraftError.AmountTooLarge)
        }
        if (amount.amountMinor <= 0L) return onResult(TransactionDraftError.AmountZero)

        saving = true
        viewModelScope.launch {
            val result = try {
                transactions.save(
                    TransactionRepository.Input(
                        id = draft.id,
                        clientUuid = draft.clientUuid,
                        type = draft.type,
                        amount = amount,
                        walletId = wallet.id,
                        categoryId = draft.categoryId,
                        date = draft.date,
                        note = draft.note,
                    )
                )
            } finally {
                saving = false
            }
            onResult(if (result is TransactionRepository.SaveResult.Saved) null else TransactionDraftError.Rejected)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { transactions.delete(id) }
    }

    companion object {
        val Factory = containerFactory {
            ActivityViewModel(it.transactions, it.wallets, it.categories, it.preferences)
        }
    }
}
