package com.lucento.app.feature.transactions

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

data class TransactionDetailUiState(
    val loading: Boolean = true,
    /** Null once loaded means the transaction no longer exists (deleted here or elsewhere). */
    val transaction: Transaction? = null,
    val category: Category? = null,
    val wallet: Wallet? = null,
    /** Both legs, when the transaction is one side of a transfer. */
    val transfer: Transfer? = null,
    val walletNames: Map<Long, String> = emptyMap(),
)

/** Read-only view of one transaction; it follows edits made on the editor page. */
class TransactionDetailViewModel(
    transactionId: Long,
    transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<TransactionDetailUiState> = combine(
        transactions.observeTransactions(),
        wallets.observeWallets(),
        categories.observeCategories(),
    ) { all, walletList, categoryList ->
        val transaction = all.firstOrNull { it.id == transactionId }
        TransactionDetailUiState(
            loading = false,
            transaction = transaction,
            category = transaction?.categoryId?.let { id -> categoryList.firstOrNull { it.id == id } },
            wallet = transaction?.let { t -> walletList.firstOrNull { it.id == t.walletId } },
            transfer = transaction?.transferGroup?.let { Transfer.pair(all)[it] },
            walletNames = walletList.associate { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionDetailUiState())

    companion object {
        fun factory(transactionId: Long) = containerFactory {
            TransactionDetailViewModel(transactionId, it.transactions, it.wallets, it.categories)
        }
    }
}
