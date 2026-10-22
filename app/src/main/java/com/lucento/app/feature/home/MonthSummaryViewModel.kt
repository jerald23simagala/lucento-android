package com.lucento.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.domain.DateRange
import com.lucento.app.domain.PeriodTotals
import com.lucento.app.domain.TransactionQueries
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class MonthSummaryState(
    val loading: Boolean = true,
    /** This calendar month's totals per currency, primary currency first. Empty when nothing was recorded. */
    val totals: List<PeriodTotals> = emptyList(),
    val primaryCurrency: String = "USD",
)

class MonthSummaryViewModel(
    transactions: TransactionRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<MonthSummaryState> =
        combine(transactions.observeTransactions(), preferences.preferences) { all, prefs ->
            MonthSummaryState(
                loading = false,
                totals = TransactionQueries.totalsByCurrency(
                    transactions = all,
                    range = DateRange.monthOf(LocalDate.now()),
                    primaryCurrency = prefs.primaryCurrency,
                ),
                primaryCurrency = prefs.primaryCurrency,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSummaryState())

    companion object {
        val Factory = containerFactory { MonthSummaryViewModel(it.transactions, it.preferences) }
    }
}
