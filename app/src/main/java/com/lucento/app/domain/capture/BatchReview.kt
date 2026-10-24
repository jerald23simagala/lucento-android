package com.lucento.app.domain.capture

import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/** The fields of a review row the user can change. */
enum class RowField { DESCRIPTION, TYPE, AMOUNT, CATEGORY, DATE, WALLET, TO_WALLET }

/**
 * One proposed transaction in the composer, as the user sees and edits it. [key] is the idempotency key used when it is
 * saved, so a retry or double tap can never write it twice. [edited] records what the user changed, so a re-read of the
 * message never overwrites a correction.
 */
data class ReviewRow(
    val key: String,
    val messageId: Long,
    val source: CaptureSource,
    val description: String,
    /** EXPENSE, INCOME or TRANSFER_OUT. */
    val type: TransactionType,
    /** The amount as typed, with the locale's decimal separator; parsed with the account's currency on save. */
    val amountText: String,
    val amountOptions: List<BigDecimal>,
    val walletId: Long?,
    val toWalletId: Long?,
    val categoryId: Long?,
    val date: LocalDate,
    val mentionedCurrency: String?,
    val sourceText: String,
    val flags: Set<CandidateFlag>,
    val edited: Set<RowField> = emptySet(),
) {
    companion object {
        fun from(candidate: TransactionCandidate, key: String, messageId: Long, source: CaptureSource, separator: Char) = ReviewRow(
            key = key,
            messageId = messageId,
            source = source,
            description = candidate.description,
            type = candidate.type,
            amountText = candidate.amount?.let { plain(it, separator) }.orEmpty(),
            amountOptions = candidate.amountOptions,
            walletId = candidate.walletId,
            toWalletId = candidate.toWalletId,
            categoryId = candidate.categoryId,
            date = candidate.date,
            mentionedCurrency = candidate.mentionedCurrency,
            sourceText = candidate.sourceText,
            flags = candidate.flags,
        )

        fun plain(value: BigDecimal, separator: Char): String = value.toPlainString().replace('.', separator)
    }
}

/** A change to one row. */
sealed interface RowEdit {
    data class Description(val text: String) : RowEdit
    data class Amount(val text: String) : RowEdit
    data class ChooseAmount(val value: BigDecimal) : RowEdit
    data class Type(val type: TransactionType) : RowEdit
    data class CategoryChoice(val id: Long?) : RowEdit
    data class Date(val date: LocalDate) : RowEdit
    data class Account(val id: Long) : RowEdit
    data class ToAccount(val id: Long) : RowEdit
}

/** Something about a row. A [blocking] issue keeps the row out of the save until it is fixed. */
data class RowIssue(val field: RowField, val message: String, val blocking: Boolean)

data class RowStatus(val issues: List<RowIssue>, val money: Money?) {
    val ready: Boolean get() = money != null && issues.none { it.blocking }
    fun issueFor(field: RowField): RowIssue? = issues.firstOrNull { it.field == field && it.blocking } ?: issues.firstOrNull { it.field == field }
}

/** What the rows are checked against. */
data class ReviewSetup(
    val wallets: List<Wallet>,
    val categories: List<Category>,
    val separator: Char,
    /** Transfers need the Multiple wallets module and two accounts. */
    val transfersAllowed: Boolean,
    /** Saved transactions, for the gentle "already saved?" hint. */
    val history: List<Transaction> = emptyList(),
)

/** Totals of the rows that are ready, per currency (Lucento never adds different currencies together). */
data class BatchSummary(
    val total: Int,
    val ready: Int,
    val expenses: List<Money>,
    val income: List<Money>,
    val transfers: Int,
) {
    val needsAttention: Int get() = total - ready
}

object BatchReview {

    const val MAX_DESCRIPTION = 200

    // ------------------------------------------------------------------ editing

    fun edit(rows: List<ReviewRow>, key: String, edit: RowEdit, categories: List<Category>, separator: Char): List<ReviewRow> =
        rows.map { if (it.key == key) apply(it, edit, categories, separator) else it }

    fun remove(rows: List<ReviewRow>, key: String): List<ReviewRow> = rows.filterNot { it.key == key }

    fun apply(row: ReviewRow, edit: RowEdit, categories: List<Category>, separator: Char): ReviewRow = when (edit) {
        is RowEdit.Description -> row.copy(description = edit.text.take(MAX_DESCRIPTION), edited = row.edited + RowField.DESCRIPTION)
        is RowEdit.Amount -> row.copy(
            amountText = edit.text.take(24),
            flags = row.flags - AMOUNT_FLAGS,
            edited = row.edited + RowField.AMOUNT,
        )
        is RowEdit.ChooseAmount -> row.copy(
            amountText = ReviewRow.plain(edit.value, separator),
            flags = row.flags - AMOUNT_FLAGS,
            edited = row.edited + RowField.AMOUNT,
        )
        is RowEdit.Type -> retype(row, edit.type, categories)
        is RowEdit.CategoryChoice -> row.copy(
            categoryId = edit.id,
            flags = row.flags - CandidateFlag.CATEGORY_UNMATCHED,
            edited = row.edited + RowField.CATEGORY,
        )
        is RowEdit.Date -> row.copy(date = edit.date, flags = row.flags - CandidateFlag.DATE_UNCERTAIN, edited = row.edited + RowField.DATE)
        is RowEdit.Account -> row.copy(walletId = edit.id, edited = row.edited + RowField.WALLET)
        is RowEdit.ToAccount -> row.copy(toWalletId = edit.id, edited = row.edited + RowField.TO_WALLET)
    }

    private val AMOUNT_FLAGS = setOf(CandidateFlag.AMOUNT_MISSING, CandidateFlag.AMOUNT_AMBIGUOUS, CandidateFlag.TOTAL_UNCONFIRMED)

    /**
     * Changing income ↔ expense keeps a category that still fits, otherwise finds one from the row's words again.
     * A transfer has no category.
     */
    private fun retype(row: ReviewRow, type: TransactionType, categories: List<Category>): ReviewRow {
        if (type == row.type) return row
        val edited = row.edited + RowField.TYPE
        if (type.isTransfer) {
            return row.copy(type = type, categoryId = null, flags = row.flags - CandidateFlag.CATEGORY_UNMATCHED, edited = edited - RowField.CATEGORY)
        }
        val kind = kindOf(type)
        val current = categories.firstOrNull { it.id == row.categoryId }
        if (current != null && current.kind == kind) return row.copy(type = type, edited = edited)
        val words = " " + (row.description + " " + row.sourceText).lowercase(Locale.ROOT) + " "
        val found = QuickEntryParser.detectCategory(words, type, categories)
        return row.copy(
            type = type,
            categoryId = found,
            flags = if (found == null) row.flags + CandidateFlag.CATEGORY_UNMATCHED else row.flags - CandidateFlag.CATEGORY_UNMATCHED,
            edited = edited - RowField.CATEGORY,
        )
    }

    private fun kindOf(type: TransactionType) = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

    /**
     * When the user edits a message and sends it again, rows read from the new text keep the corrections made to the
     * old rows: matched by description, or by position when the number of rows is unchanged.
     */
    fun carryOver(previous: List<ReviewRow>, fresh: List<ReviewRow>, categories: List<Category>): List<ReviewRow> {
        if (previous.isEmpty()) return fresh
        val unused = previous.toMutableList()
        return fresh.mapIndexed { index, row ->
            val old = unused.firstOrNull { it.description.isNotBlank() && it.description.equals(row.description, ignoreCase = true) }
                ?: previous.getOrNull(index)?.takeIf { previous.size == fresh.size && it in unused }
                ?: return@mapIndexed row
            unused.remove(old)
            merge(old, row, categories)
        }
    }

    private fun merge(old: ReviewRow, row: ReviewRow, categories: List<Category>): ReviewRow {
        var merged = row.copy(key = old.key, edited = old.edited)
        if (RowField.DESCRIPTION in old.edited) merged = merged.copy(description = old.description)
        if (RowField.TYPE in old.edited) merged = merged.copy(type = old.type, toWalletId = old.toWalletId)
        if (RowField.AMOUNT in old.edited) merged = merged.copy(amountText = old.amountText, flags = merged.flags - AMOUNT_FLAGS)
        if (RowField.CATEGORY in old.edited) {
            val kept = categories.firstOrNull { it.id == old.categoryId }
            if (old.categoryId == null || (kept != null && !merged.type.isTransfer && kept.kind == kindOf(merged.type))) {
                merged = merged.copy(categoryId = old.categoryId, flags = merged.flags - CandidateFlag.CATEGORY_UNMATCHED)
            }
        }
        if (RowField.DATE in old.edited) merged = merged.copy(date = old.date, flags = merged.flags - CandidateFlag.DATE_UNCERTAIN)
        if (RowField.WALLET in old.edited) merged = merged.copy(walletId = old.walletId)
        if (RowField.TO_WALLET in old.edited) merged = merged.copy(toWalletId = old.toWalletId)
        return merged
    }

    // ------------------------------------------------------------------ checking

    fun check(row: ReviewRow, setup: ReviewSetup): RowStatus {
        val issues = ArrayList<RowIssue>()
        val wallets = setup.wallets.associateBy { it.id }
        val wallet = row.walletId?.let { wallets[it] }
        if (wallet == null) {
            issues += RowIssue(
                RowField.WALLET,
                if (setup.wallets.isEmpty()) "Add an account first" else if (row.type.isTransfer) "Choose where the money came from" else "Choose an account",
                blocking = true,
            )
        }

        val money = amountOf(row, wallet, setup.separator, issues)

        if (wallet != null && row.mentionedCurrency != null && row.mentionedCurrency != wallet.currencyCode &&
            RowField.AMOUNT !in row.edited && RowField.WALLET !in row.edited
        ) {
            issues += RowIssue(
                RowField.WALLET,
                "You mentioned ${row.mentionedCurrency}, but ${wallet.name} uses ${wallet.currencyCode}. Lucento won’t convert it — choose another account or confirm the amount.",
                blocking = true,
            )
        }

        if (row.type.isTransfer) {
            val to = row.toWalletId?.let { wallets[it] }
            when {
                !setup.transfersAllowed -> issues += RowIssue(RowField.TYPE, "Transfers need two accounts. Record it as an expense or income instead.", blocking = true)
                to == null -> issues += RowIssue(RowField.TO_WALLET, "Choose where the money went", blocking = true)
                wallet != null && to.id == wallet.id -> issues += RowIssue(RowField.TO_WALLET, "Choose two different accounts", blocking = true)
                wallet != null && to.currencyCode != wallet.currencyCode ->
                    issues += RowIssue(RowField.TO_WALLET, "Both accounts must use the same currency. Lucento never converts.", blocking = true)
            }
        } else {
            val category = row.categoryId?.let { id -> setup.categories.firstOrNull { it.id == id } }
            when {
                row.categoryId == null -> issues += RowIssue(RowField.CATEGORY, "No category — it will be saved as uncategorized", blocking = false)
                category == null || category.archived || category.kind != kindOf(row.type) ->
                    issues += RowIssue(RowField.CATEGORY, "Choose a category", blocking = true)
            }
        }

        if (CandidateFlag.DATE_UNCERTAIN in row.flags) {
            issues += RowIssue(RowField.DATE, "Check the date — the receipt’s date was missing or unclear", blocking = false)
        }
        if (CandidateFlag.ITEMS_DONT_ADD_UP in row.flags && RowField.AMOUNT !in row.edited) {
            issues += RowIssue(RowField.AMOUNT, "The items don’t add up to this total — check it", blocking = false)
        }
        if (money != null && wallet != null && looksSaved(row, money, setup.history)) {
            issues += RowIssue(RowField.DESCRIPTION, "A matching transaction is already saved for this day", blocking = false)
        }
        return RowStatus(issues, money)
    }

    private fun amountOf(row: ReviewRow, wallet: Wallet?, separator: Char, issues: MutableList<RowIssue>): Money? {
        if (row.amountText.isBlank()) {
            val message = when {
                CandidateFlag.TOTAL_UNCONFIRMED in row.flags -> "Which is the total? No line was labelled “Total”."
                CandidateFlag.AMOUNT_AMBIGUOUS in row.flags -> "Which amount?"
                else -> "Enter the amount"
            }
            issues += RowIssue(RowField.AMOUNT, message, blocking = true)
            return null
        }
        if (wallet == null) return null
        return when (val parsed = MoneyParser.parse(row.amountText, wallet.currencyCode, separator)) {
            is MoneyParser.Result.Success -> if (parsed.money.amountMinor <= 0L) {
                issues += RowIssue(RowField.AMOUNT, "The amount must be more than zero", blocking = true)
                null
            } else {
                parsed.money
            }
            is MoneyParser.Result.TooManyDecimals -> {
                issues += RowIssue(
                    RowField.AMOUNT,
                    if (parsed.allowed == 0) "${wallet.currencyCode} has no decimal places" else "${wallet.currencyCode} uses ${parsed.allowed} decimal places",
                    blocking = true,
                )
                null
            }
            MoneyParser.Result.TooLarge -> {
                issues += RowIssue(RowField.AMOUNT, "That amount is too large", blocking = true)
                null
            }
            MoneyParser.Result.Empty, MoneyParser.Result.Invalid -> {
                issues += RowIssue(RowField.AMOUNT, "Enter a valid amount", blocking = true)
                null
            }
        }
    }

    private fun looksSaved(row: ReviewRow, money: Money, history: List<Transaction>): Boolean = history.any { t ->
        t.walletId == row.walletId && t.date == row.date && t.amount == money && t.type == row.type &&
            (row.type.isTransfer || t.note.isBlank() || row.description.isBlank() || t.note.trim().equals(row.description.trim(), ignoreCase = true))
    }

    fun summarize(rows: List<ReviewRow>, statuses: Map<String, RowStatus>): BatchSummary {
        val ready = rows.filter { statuses[it.key]?.ready == true }
        fun totals(type: TransactionType) = ready.filter { it.type == type }
            .mapNotNull { statuses[it.key]?.money }
            .groupBy { it.currencyCode }
            .map { (code, list) -> list.fold(Money(0, code)) { acc, m -> acc + m } }
        return BatchSummary(
            total = rows.size,
            ready = ready.size,
            expenses = totals(TransactionType.EXPENSE),
            income = totals(TransactionType.INCOME),
            transfers = ready.count { it.type.isTransfer },
        )
    }
}
