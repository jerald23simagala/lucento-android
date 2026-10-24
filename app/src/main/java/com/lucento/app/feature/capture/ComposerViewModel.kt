package com.lucento.app.feature.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.data.repository.saveBatchOrNull
import com.lucento.app.domain.Category
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.capture.BatchReview
import com.lucento.app.domain.capture.BatchSummary
import com.lucento.app.domain.capture.CaptureSource
import com.lucento.app.domain.capture.Interpretation
import com.lucento.app.domain.capture.InterpretationContext
import com.lucento.app.domain.capture.LocalTransactionInterpreter
import com.lucento.app.domain.capture.ReceiptParser
import com.lucento.app.domain.capture.ReviewRow
import com.lucento.app.domain.capture.ReviewSetup
import com.lucento.app.domain.capture.RowEdit
import com.lucento.app.domain.capture.RowStatus
import com.lucento.app.domain.capture.TransactionInterpreter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** One thing the user sent: a typed or spoken message, or a receipt photo. */
data class ComposerMessage(
    val id: Long,
    val source: CaptureSource,
    val text: String,
    val state: MessageState,
)

sealed interface MessageState {
    data object Reading : MessageState
    /** Interpreted; [found] rows came from it, [saved] of them have been saved since. */
    data class Read(val found: Int, val saved: Int = 0) : MessageState
    data class NothingFound(val message: String) : MessageState
}

data class ComposerState(
    val loaded: Boolean = false,
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val messages: List<ComposerMessage> = emptyList(),
    val rows: List<ReviewRow> = emptyList(),
    val statuses: Map<String, RowStatus> = emptyMap(),
    val summary: BatchSummary = BatchSummary(0, 0, emptyList(), emptyList(), 0),
    val saving: Boolean = false,
    val saveError: String? = null,
    val transfersAllowed: Boolean = false,
    /** The message being corrected; sending replaces its rows but keeps the user's edits. */
    val editingMessageId: Long? = null,
)

/**
 * The conversational composer: messages in, editable rows out, one save for the whole batch.
 *
 * Interpretation goes through [TransactionInterpreter] (on this phone by default); rows are checked with
 * [BatchReview]; saving goes through [TransactionRepository.saveBatch], the same validation and persistence as every
 * other transaction, written atomically with each row's own idempotency key. Nothing is saved without the user's tap.
 */
class ComposerViewModel(
    private val transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    private val interpreter: TransactionInterpreter,
    private val separator: Char = MoneyFormatter.decimalSeparator(),
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private data class Session(
        val messages: List<ComposerMessage> = emptyList(),
        val rows: List<ReviewRow> = emptyList(),
        val saving: Boolean = false,
        val saveError: String? = null,
        val editingMessageId: Long? = null,
        val defaultWalletId: Long? = null,
        val typeHint: TransactionType? = null,
        val transfersEnabled: Boolean = true,
    )

    private val session = MutableStateFlow(Session())
    private var nextMessageId = 1L

    /** Saved transactions, kept for choosing the usual account. */
    @Volatile private var latestHistory: List<Transaction> = emptyList()

    val state: StateFlow<ComposerState> = combine(
        wallets.observeWallets(),
        categories.observeCategories(),
        transactions.observeTransactions(),
        session,
    ) { walletList, categoryList, history, s ->
        val transfersAllowed = s.transfersEnabled && walletList.size > 1
        val setup = ReviewSetup(walletList, categoryList, separator, transfersAllowed, history)
        val statuses = s.rows.associate { it.key to BatchReview.check(it, setup) }
        latestHistory = history
        ComposerState(
            loaded = true,
            wallets = walletList,
            categories = categoryList,
            messages = s.messages,
            rows = s.rows,
            statuses = statuses,
            summary = BatchReview.summarize(s.rows, statuses),
            saving = s.saving,
            saveError = s.saveError,
            transfersAllowed = transfersAllowed,
            editingMessageId = s.editingMessageId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComposerState())

    /** Where the composer was opened from: a wallet pre-selects its account; "Income" makes income the default. */
    fun configure(walletId: Long?, typeHint: TransactionType?, transfersEnabled: Boolean) {
        session.update { it.copy(defaultWalletId = walletId, typeHint = typeHint, transfersEnabled = transfersEnabled) }
    }

    // ------------------------------------------------------------------ messages

    fun send(text: String, source: CaptureSource) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val replacing = session.value.editingMessageId
        val id = nextMessageId++
        session.update { s ->
            val kept = if (replacing != null) s.messages.filterNot { it.id == replacing } else s.messages
            s.copy(messages = kept + ComposerMessage(id, source, trimmed, MessageState.Reading), editingMessageId = null, saveError = null)
        }
        viewModelScope.launch {
            val context = context()
            val result = runCatching { interpreter.interpretText(trimmed, context) }
                .getOrElse { e -> if (e is CancellationException) throw e else Interpretation.NothingFound("That couldn’t be read. Try rewording it.") }
            receive(id, source, result, replacing)
        }
    }

    /** Reads a receipt image on this phone (ML Kit, bundled model) and adds its row. [cleanup] is deleted afterwards. */
    fun readReceipt(appContext: Context, uri: Uri, cleanup: File?) {
        val id = nextMessageId++
        session.update { s -> s.copy(messages = s.messages + ComposerMessage(id, CaptureSource.RECEIPT, "Receipt photo", MessageState.Reading), saveError = null) }
        viewModelScope.launch {
            val result = try {
                val lines = ReceiptParser.mergeRows(ReceiptOcr.recognize(appContext, uri))
                interpreter.interpretReceipt(lines, context())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Interpretation.NothingFound("Lucento couldn’t read that image. Try another photo, or type the details instead.")
            } finally {
                // The photo was only needed for reading; remove it so receipts don't pile up on the device.
                cleanup?.delete()
            }
            receive(id, CaptureSource.RECEIPT, result, replacing = null)
        }
    }

    fun receiptUnavailable(message: String) {
        val id = nextMessageId++
        session.update { s -> s.copy(messages = s.messages + ComposerMessage(id, CaptureSource.RECEIPT, "Receipt photo", MessageState.NothingFound(message))) }
    }

    private suspend fun context(): InterpretationContext {
        val loaded = state.first { it.loaded }
        val s = session.value
        val default = s.defaultWalletId?.takeIf { id -> loaded.wallets.any { it.id == id } }
            ?: lastUsedWallet(loaded.wallets)
        return InterpretationContext(loaded.wallets, loaded.categories, today(), default, s.typeHint)
    }

    /** The account of the most recently recorded income or expense, so the usual account is the default. */
    private fun lastUsedWallet(wallets: List<Wallet>): Long? {
        val ids = wallets.map { it.id }.toSet()
        return latestHistory.filter { !it.isTransfer && it.walletId in ids }.maxByOrNull { it.createdAtEpochMillis }?.walletId
            ?: wallets.firstOrNull()?.id
    }

    private fun receive(id: Long, source: CaptureSource, result: Interpretation, replacing: Long?) {
        session.update { s ->
            val (messageState, newRows) = when (result) {
                is Interpretation.Candidates -> {
                    val rows = result.items.map { ReviewRow.from(it, UUID.randomUUID().toString(), id, source, separator) }
                    MessageState.Read(rows.size) to rows
                }
                is Interpretation.NothingFound -> MessageState.NothingFound(result.message) to emptyList()
            }
            val previous = if (replacing != null) s.rows.filter { it.messageId == replacing } else emptyList()
            val categories = state.value.categories
            val merged = BatchReview.carryOver(previous, newRows, categories).map { it.copy(messageId = id) }
            val others = if (replacing != null) s.rows.filterNot { it.messageId == replacing } else s.rows
            s.copy(
                messages = s.messages.map { if (it.id == id) it.copy(state = messageState) else it },
                rows = others + merged,
            )
        }
    }

    /** Puts a sent message back into the input for correction; returns its text, or null for receipts. */
    fun startEditing(messageId: Long): String? {
        val message = session.value.messages.firstOrNull { it.id == messageId } ?: return null
        if (message.source == CaptureSource.RECEIPT || message.state is MessageState.Reading) return null
        session.update { it.copy(editingMessageId = messageId) }
        return message.text
    }

    fun cancelEditing() = session.update { it.copy(editingMessageId = null) }

    // ------------------------------------------------------------------ rows

    fun edit(key: String, edit: RowEdit) {
        if (session.value.saving) return
        session.update { s -> s.copy(rows = BatchReview.edit(s.rows, key, edit, state.value.categories, separator), saveError = null) }
    }

    fun remove(key: String) {
        if (session.value.saving) return
        session.update { s -> s.copy(rows = BatchReview.remove(s.rows, key)) }
    }

    fun discardAll() = session.update { Session(defaultWalletId = it.defaultWalletId, typeHint = it.typeHint, transfersEnabled = it.transfersEnabled) }

    fun dismissError() = session.update { it.copy(saveError = null) }

    // ------------------------------------------------------------------ saving

    /**
     * Saves every row that is ready, in one database transaction. Rows that still need attention stay in the composer
     * (the save button says so before it is tapped). [onSaved] runs only after the write has committed.
     */
    fun save(onSaved: (count: Int) -> Unit) {
        val current = state.value
        if (session.value.saving || !current.loaded) return
        val ready = current.rows.filter { current.statuses[it.key]?.ready == true }
        if (ready.isEmpty()) return
        val entries = ready.map { row ->
            val money = current.statuses.getValue(row.key).money!!
            if (row.type.isTransfer) {
                TransactionRepository.BatchEntry.Move(
                    TransactionRepository.TransferInput(null, row.key, row.walletId!!, row.toWalletId!!, money, row.date, row.description)
                )
            } else {
                TransactionRepository.BatchEntry.Single(
                    TransactionRepository.Input(null, row.key, row.type, money, row.walletId!!, row.categoryId, row.date, row.description)
                )
            }
        }
        session.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = transactions.saveBatchOrNull(entries)) {
                is TransactionRepository.BatchResult.Saved -> {
                    val savedKeys = ready.map { it.key }.toSet()
                    session.update { s ->
                        val savedPerMessage = s.rows.filter { it.key in savedKeys }.groupingBy { it.messageId }.eachCount()
                        s.copy(
                            rows = s.rows.filterNot { it.key in savedKeys },
                            messages = s.messages.map { m ->
                                val st = m.state
                                val n = savedPerMessage[m.id] ?: 0
                                if (st is MessageState.Read && n > 0) m.copy(state = st.copy(saved = st.saved + n)) else m
                            },
                            saving = false,
                        )
                    }
                    onSaved(ready.size)
                }
                is TransactionRepository.BatchResult.Rejected -> {
                    val row = ready.getOrNull(result.index)
                    val name = row?.description?.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: "One row"
                    session.update {
                        it.copy(saving = false, saveError = "Nothing was saved. $name no longer matches its account or category — check it and try again.")
                    }
                }
                null -> session.update {
                    it.copy(saving = false, saveError = "Nothing was saved — the phone couldn’t write it. Try again; nothing will be saved twice.")
                }
            }
        }
    }

    companion object {
        val Factory = containerFactory {
            ComposerViewModel(it.transactions, it.wallets, it.categories, LocalTransactionInterpreter())
        }
    }
}
