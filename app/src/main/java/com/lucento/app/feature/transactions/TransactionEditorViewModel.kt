package com.lucento.app.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.data.repository.saveOrNull
import com.lucento.app.data.repository.saveTransferOrNull
import com.lucento.app.domain.transactable
import com.lucento.app.domain.Category
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Transfer
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

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

/** Editable form state for creating or updating a transfer between two of the user's wallets. */
data class TransferDraft(
    /** Transfer group when editing; null for a new transfer. */
    val group: String? = null,
    val clientUuid: String = UUID.randomUUID().toString(),
    val fromWalletId: Long? = null,
    val toWalletId: Long? = null,
    val amountText: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
)

fun Transfer.toDraft(): TransferDraft = TransferDraft(
    group = group,
    fromWalletId = fromWalletId,
    toWalletId = toWalletId,
    amountText = MoneyFormatter.formatPlain(amount),
    date = date,
    note = note,
)

sealed interface TransferDraftError {
    data object FromRequired : TransferDraftError
    data object ToRequired : TransferDraftError
    data object SameWallet : TransferDraftError
    data object CurrencyMismatch : TransferDraftError
    data object AmountInvalid : TransferDraftError
    data object AmountZero : TransferDraftError
    data class AmountTooPrecise(val digits: Int) : TransferDraftError
    data object AmountTooLarge : TransferDraftError
    data object Rejected : TransferDraftError
}

sealed interface TransactionDraftError {
    data object WalletRequired : TransactionDraftError
    data object AmountInvalid : TransactionDraftError
    data object AmountZero : TransactionDraftError
    data class AmountTooPrecise(val digits: Int) : TransactionDraftError
    data object AmountTooLarge : TransactionDraftError
    /** The save was rejected by repository rules (e.g. the wallet or category changed underneath). */
    data object Rejected : TransactionDraftError
}

data class TransactionEditorState(
    val loaded: Boolean = false,
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Every transfer by group, so tapping either leg anywhere opens the whole transfer. */
    val transfers: Map<String, Transfer> = emptyMap(),
) {
    val walletsById: Map<Long, Wallet> = wallets.associateBy { it.id }
}

/**
 * Owns transaction creation, editing and deletion for the whole app, so the same editor opens from
 * Home's quick actions, recent rows and the Transactions screen. Scoped to the activity.
 */
class TransactionEditorViewModel(
    private val transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
) : ViewModel() {

    val state: StateFlow<TransactionEditorState> =
        combine(wallets.observeWallets(), categories.observeCategories(), transactions.observeTransactions()) { walletList, categoryList, all ->
            TransactionEditorState(loaded = true, wallets = walletList.transactable(), categories = categoryList, transfers = Transfer.pair(all))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionEditorState())

    /** The wallet used for the most recent successful save in this session; pre-selected next time. */
    private var lastWalletId: Long? = null

    /** A fresh draft of [type], pre-selecting the last-used wallet when it still exists. */
    fun newDraft(type: TransactionType = TransactionType.EXPENSE, walletId: Long? = null): TransactionDraft {
        val wallets = state.value.wallets
        val chosen = walletId?.takeIf { id -> wallets.any { it.id == id } }
            ?: lastWalletId?.takeIf { id -> wallets.any { it.id == id } }
            ?: wallets.firstOrNull()?.id
        return TransactionDraft(type = type, walletId = chosen)
    }

    private var saving = false

    /**
     * Validates and persists [draft]. [onResult] receives null on success or the first error.
     * Re-entrant taps while a save is in flight are ignored, and the draft's idempotency key
     * guarantees a retry cannot create a second transaction.
     */
    fun save(draft: TransactionDraft, decimalSeparator: Char, onResult: (TransactionDraftError?) -> Unit) {
        if (saving) return
        val wallet = state.value.walletsById[draft.walletId] ?: return onResult(TransactionDraftError.WalletRequired)

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
                transactions.saveOrNull(
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
            val saved = result is TransactionRepository.SaveResult.Saved
            if (saved) lastWalletId = wallet.id
            onResult(if (saved) null else TransactionDraftError.Rejected)
        }
    }

    /** A fresh transfer draft; the source defaults to the last-used wallet, the destination to another one. */
    fun newTransferDraft(fromWalletId: Long? = null, toWalletId: Long? = null): TransferDraft {
        val wallets = state.value.wallets
        val from = fromWalletId ?: lastWalletId?.takeIf { id -> wallets.any { it.id == id } } ?: wallets.firstOrNull()?.id
        val fromCurrency = wallets.firstOrNull { it.id == from }?.currencyCode
        val to = toWalletId ?: wallets.firstOrNull { it.id != from && it.currencyCode == fromCurrency }?.id
        return TransferDraft(fromWalletId = from, toWalletId = to)
    }

    private var savingTransfer = false

    /** Validates and saves a transfer; [onResult] receives null on success or the first error. */
    fun saveTransfer(draft: TransferDraft, decimalSeparator: Char, onResult: (TransferDraftError?) -> Unit) {
        if (savingTransfer) return
        val wallets = state.value.walletsById
        val from = wallets[draft.fromWalletId] ?: return onResult(TransferDraftError.FromRequired)
        val to = wallets[draft.toWalletId] ?: return onResult(TransferDraftError.ToRequired)
        if (from.id == to.id) return onResult(TransferDraftError.SameWallet)
        if (from.currencyCode != to.currencyCode) return onResult(TransferDraftError.CurrencyMismatch)
        val amount = when (val parsed = MoneyParser.parse(draft.amountText, from.currencyCode, decimalSeparator)) {
            is MoneyParser.Result.Success -> parsed.money
            MoneyParser.Result.Empty, MoneyParser.Result.Invalid -> return onResult(TransferDraftError.AmountInvalid)
            is MoneyParser.Result.TooManyDecimals -> return onResult(TransferDraftError.AmountTooPrecise(parsed.allowed))
            MoneyParser.Result.TooLarge -> return onResult(TransferDraftError.AmountTooLarge)
        }
        if (amount.amountMinor <= 0L) return onResult(TransferDraftError.AmountZero)

        savingTransfer = true
        viewModelScope.launch {
            val result = try {
                transactions.saveTransferOrNull(
                    TransactionRepository.TransferInput(
                        group = draft.group,
                        clientUuid = draft.clientUuid,
                        fromWalletId = from.id,
                        toWalletId = to.id,
                        amount = amount,
                        date = draft.date,
                        note = draft.note,
                    )
                )
            } finally {
                savingTransfer = false
            }
            onResult(
                when (result) {
                    is TransactionRepository.TransferResult.Saved -> {
                        lastWalletId = from.id
                        null
                    }
                    TransactionRepository.TransferResult.SameWallet -> TransferDraftError.SameWallet
                    TransactionRepository.TransferResult.CurrencyMismatch -> TransferDraftError.CurrencyMismatch
                    TransactionRepository.TransferResult.AmountNotPositive -> TransferDraftError.AmountZero
                    TransactionRepository.TransferResult.WalletNotFound,
                    TransactionRepository.TransferResult.TransferNotFound,
                    null -> TransferDraftError.Rejected
                }
            )
        }
    }

    fun delete(id: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            transactions.delete(id)
            onDeleted()
        }
    }

    companion object {
        val Factory = containerFactory {
            TransactionEditorViewModel(it.transactions, it.wallets, it.categories)
        }
    }
}
