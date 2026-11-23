package com.lucento.app.domain.capture.merchant

import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import java.time.LocalDate

/**
 * What the person's own saved transactions say about a payee: the category and account they usually record it with
 * ("Shell" → Fuel, "Verizon" → the bank account it is always paid from).
 *
 * Built on the phone from the ledger each time a document is read, and never stored: editing or deleting those
 * transactions changes or removes the suggestion, and signing out or switching accounts — which wipes this phone's
 * ledger (docs/SYNC.md) — leaves nothing of one person's habits for the next. Suggestions only; every row is reviewed.
 */
class MerchantMemory internal constructor(private val habits: Map<String, Habit>) {

    /** How one payee is usually recorded: the most-chosen category and account, and how often each was chosen. */
    data class Habit(
        val times: Int,
        val categoryId: Long?,
        val categoryTimes: Int,
        val walletId: Long?,
        val walletTimes: Int,
    ) {
        /** Chosen at least twice, and for at least 60 % of this payee's transactions. */
        val usualCategory: Long? get() = categoryId?.takeIf { usual(categoryTimes) }
        val usualWallet: Long? get() = walletId?.takeIf { usual(walletTimes) }
        private fun usual(n: Int) = n >= 2 && n * 10 >= times * 6
    }

    val size: Int get() = habits.size

    /** The habit for a payee named [name] (a dictionary name or the document's words), for income or expense. */
    fun habit(name: String?, type: TransactionType, directory: MerchantDirectory): Habit? {
        if (name.isNullOrBlank()) return null
        return key(name, directory)?.let { habits["$it|${type.name}"] }
    }

    companion object {
        val EMPTY = MerchantMemory(emptyMap())

        /** About thirteen months: a habit should be current, and a yearly bill should still count. */
        const val LOOKBACK_DAYS = 400L
        const val MAX_TRANSACTIONS = 3_000

        fun build(history: List<Transaction>, directory: MerchantDirectory, today: LocalDate): MerchantMemory {
            val since = today.minusDays(LOOKBACK_DAYS)
            val recent = history.asSequence()
                .filter { (it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME) && !it.fxResult }
                .filter { it.note.isNotBlank() && !it.date.isBefore(since) && !it.date.isAfter(today) }
                .sortedByDescending { it.date }
                .take(MAX_TRANSACTIONS)
                .toList()
            if (recent.isEmpty()) return EMPTY
            val keys = HashMap<String, String?>()
            val grouped = recent.groupBy { t -> keys.getOrPut(t.note) { key(t.note, directory) }?.let { "$it|${t.type.name}" } }
            val habits = grouped.mapNotNull { (k, list) ->
                k ?: return@mapNotNull null
                val category = list.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().maxByOrNull { it.value }
                val wallet = list.groupingBy { it.walletId }.eachCount().maxByOrNull { it.value }
                k to Habit(list.size, category?.key, category?.value ?: 0, wallet?.key, wallet?.value ?: 0)
            }.toMap()
            return MerchantMemory(habits)
        }

        private val STOP = setOf(
            "the", "and", "at", "to", "for", "of", "on", "in", "my", "via", "from", "pos", "purchase", "payment", "pymt", "debit",
            "credit", "card", "online", "store", "inc", "llc", "ltd", "corp", "co", "branch", "receipt", "bill", "bills", "paid",
            "transfer", "recurring", "authorized", "www", "com",
        )

        /** One payee's key: its dictionary name when known, else the first two meaningful words of [text]. */
        internal fun key(text: String, directory: MerchantDirectory): String? {
            val parties = MerchantRecognizer.descriptor(text, directory)
            parties.payee?.let { return "e:" + MerchantDirectory.normalize(it.name) }
            val words = MerchantDirectory.normalize(parties.payeeName ?: text)
                .replace(Regex("[^\\p{L}\\s]"), " ")
                .split(' ')
                .filter { it.length >= 2 && it !in STOP }
                .take(2)
            return if (words.isEmpty()) null else "n:" + words.joinToString(" ")
        }
    }
}
