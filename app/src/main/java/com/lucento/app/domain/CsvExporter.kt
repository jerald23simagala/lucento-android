package com.lucento.app.domain

import java.time.format.DateTimeFormatter

/**
 * RFC 4180 CSV of transactions: ISO dates, exact decimal amounts (from minor units, never floating point),
 * one row per stored movement. Transfer legs are exported as such and share a transfer id, so a
 * spreadsheet can reconcile them; they are clearly typed and never look like income or spending.
 */
object CsvExporter {

    val HEADER = listOf("date", "type", "amount", "currency", "account", "category", "note", "transfer_id")

    fun export(
        transactions: List<Transaction>,
        walletNames: Map<Long, String>,
        categoryNames: Map<Long, String>,
    ): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append("\r\n")
        transactions
            .sortedWith(compareBy<Transaction> { it.date }.thenBy { it.id })
            .forEach { t ->
                val row = listOf(
                    t.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    when (t.type) {
                        TransactionType.EXPENSE -> "expense"
                        TransactionType.INCOME -> "income"
                        TransactionType.TRANSFER_OUT -> "transfer_out"
                        TransactionType.TRANSFER_IN -> "transfer_in"
                    },
                    t.signedAmount.toBigDecimal().toPlainString(),
                    t.currencyCode,
                    walletNames[t.walletId].orEmpty(),
                    t.categoryId?.let { categoryNames[it] }.orEmpty(),
                    t.note,
                    t.transferGroup.orEmpty(),
                )
                sb.append(row.joinToString(",") { escape(it) }).append("\r\n")
            }
        return sb.toString()
    }

    /**
     * Quotes fields containing separators, quotes or line breaks. Fields starting with a formula trigger
     * (=, +, -, @) are prefixed with an apostrophe so spreadsheets don't execute them; amounts are exempt
     * because they are generated numbers, not user text.
     */
    fun escape(field: String): String {
        val isNumber = field.matches(Regex("-?\\d+(\\.\\d+)?"))
        val safe = if (!isNumber && field.isNotEmpty() && field[0] in "=+-@\t\r") "'$field" else field
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
