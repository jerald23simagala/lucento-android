package com.lucento.app.domain.capture

import com.lucento.app.domain.Category
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import java.math.BigDecimal
import java.time.LocalDate

/** Where a message came from. All three go through the same interpreter and the same review. */
enum class CaptureSource { TYPED, SPOKEN, RECEIPT }

/** Something the interpreter could not settle on its own; the review row points at the field concerned. */
enum class CandidateFlag {
    /** No amount was written. The user types it; nothing is guessed. */
    AMOUNT_MISSING,
    /** Several numbers could be the amount; they are offered as one-tap choices, none is pre-filled. */
    AMOUNT_AMBIGUOUS,
    /** No category matched the words; the row can still be saved without one. */
    CATEGORY_UNMATCHED,
    /** The date was not found (receipt) or its day/month order is unclear. */
    DATE_UNCERTAIN,
    /** A receipt with no labelled total: the largest amounts are offered, none is pre-filled. */
    TOTAL_UNCONFIRMED,
    /** A receipt whose items don't add up to the printed total. */
    ITEMS_DONT_ADD_UP,
}

/** What the interpreter needs to know about the user's setup. Nothing here leaves the device. */
data class InterpretationContext(
    val wallets: List<Wallet>,
    val categories: List<Category>,
    val today: LocalDate,
    /** Account used when the words don't name one. */
    val defaultWalletId: Long?,
    /** Type to assume when nothing in a clause says income or expense (e.g. started from a wallet's "Income"). */
    val typeHint: TransactionType? = null,
)

/**
 * One proposed transaction. Every field is a suggestion shown in an editable row; nothing is saved without review.
 * [type] is EXPENSE, INCOME or TRANSFER_OUT (a transfer between two of the user's accounts, never spending).
 */
data class TransactionCandidate(
    val description: String,
    val type: TransactionType,
    /** Major units exactly as written; null when missing or ambiguous (see [flags]). */
    val amount: BigDecimal?,
    /** One-tap choices when the amount is ambiguous or a receipt total is unconfirmed, most likely first. */
    val amountOptions: List<BigDecimal> = emptyList(),
    val walletId: Long?,
    val toWalletId: Long? = null,
    val categoryId: Long?,
    val date: LocalDate,
    /** ISO code of a currency the text or receipt named; never converted. */
    val mentionedCurrency: String? = null,
    /** The words this row was read from, shown when the row is expanded. */
    val sourceText: String,
    val flags: Set<CandidateFlag> = emptySet(),
    /**
     * True when the words named the account ([walletId], the source of a transfer); false when it is only the
     * composer's active wallet, which may then be switched for this row.
     */
    val walletFromText: Boolean = false,
)

/** Who did the interpreting, so the UI never presents local rules as something else. */
data class InterpreterEngine(val label: String, val onDevice: Boolean)

sealed interface Interpretation {
    data class Candidates(val items: List<TransactionCandidate>, val engine: InterpreterEngine) : Interpretation
    /** Nothing that looks like a transaction; [message] says what would help. */
    data class NothingFound(val message: String) : Interpretation
}

/**
 * The boundary between capture (text, speech, receipt text) and review. The composer depends only on this.
 *
 * Lucento ships [LocalTransactionInterpreter]: deterministic and fully offline. A remote model could implement the
 * same interface later, but only behind an explicit opt-in (see docs/CAPTURE.md): the app has no INTERNET permission,
 * holds no API keys, and financial text must never leave the device without the user's consent.
 */
interface TransactionInterpreter {
    val engine: InterpreterEngine
    suspend fun interpretText(text: String, context: InterpretationContext): Interpretation
    suspend fun interpretReceipt(lines: List<String>, context: InterpretationContext): Interpretation
}

/** On-device interpretation with [BatchEntryParser] and [ReceiptCandidates]. */
class LocalTransactionInterpreter : TransactionInterpreter {
    override val engine = InterpreterEngine(label = "On this phone", onDevice = true)

    override suspend fun interpretText(text: String, context: InterpretationContext): Interpretation {
        val items = BatchEntryParser.parse(text, context)
        return if (items.isEmpty()) {
            Interpretation.NothingFound("Nothing to record was found. Include an amount, like “coffee 180”.")
        } else {
            Interpretation.Candidates(items, engine)
        }
    }

    override suspend fun interpretReceipt(lines: List<String>, context: InterpretationContext): Interpretation {
        val candidate = ReceiptCandidates.from(lines, context)
            ?: return Interpretation.NothingFound("No text was found. Try again with the whole receipt in view, flat and well lit.")
        return Interpretation.Candidates(listOf(candidate), engine)
    }
}
