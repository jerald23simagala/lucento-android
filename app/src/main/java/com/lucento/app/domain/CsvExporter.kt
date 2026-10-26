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
                // Generated values: ISO date, fixed type words, exact decimal amount, ISO currency, transfer UUID.
                val generated = listOf(
                    t.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    when (t.type) {
                        TransactionType.EXPENSE -> "expense"
                        TransactionType.INCOME -> "income"
                        TransactionType.TRANSFER_OUT -> "transfer_out"
                        TransactionType.TRANSFER_IN -> "transfer_in"
                        TransactionType.ADJUSTMENT_IN -> "adjustment_in"
                        TransactionType.ADJUSTMENT_OUT -> "adjustment_out"
                    },
                    t.signedAmount.toBigDecimal().toPlainString(),
                    t.currencyCode,
                ).map { quote(it) }
                // Free text typed by the user (or read from a receipt): neutralised before quoting.
                val text = listOf(
                    walletNames[t.walletId].orEmpty(),
                    t.categoryId?.let { categoryNames[it] }.orEmpty(),
                    t.note,
                ).map { escape(it) }
                sb.append((generated + text + quote(t.transferGroup.orEmpty())).joinToString(",")).append("\r\n")
            }
        return sb.toString()
    }

    /**
     * Escapes one user-entered text field: [neutralizeFormula] first, then RFC 4180 [quote]ing.
     *
     * Protection provided (and its limits): spreadsheet apps (Excel, LibreOffice Calc, Google Sheets, Numbers)
     * may evaluate a cell whose text starts with `=`, `+`, `-` or `@`, and some also act on a leading tab or
     * carriage return, or skip leading spaces/control characters before deciding. Such fields get a leading
     * apostrophe, which these apps treat as "this is text". This follows the OWASP CSV-injection guidance; it is
     * not a guarantee for every application or import setting (e.g. an importer told to strip apostrophes).
     * Amounts are never passed through here — they are generated numbers, so negative values stay numeric.
     */
    fun escape(field: String): String = quote(neutralizeFormula(field))

    /** Prefixes an apostrophe when the first meaningful character could start a formula. Other text is untouched. */
    fun neutralizeFormula(field: String): String {
        val first = field.firstOrNull { !it.isWhitespace() && !it.isISOControl() && it != '\uFEFF' } ?: return field
        val leadingControl = field.first().let { it == '\t' || it == '\r' || it == '\n' }
        return if (first in FORMULA_TRIGGERS || leadingControl) "'$field" else field
    }

    /** RFC 4180: quotes fields containing separators, quotes or line breaks, doubling embedded quotes. */
    fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    /** ASCII triggers plus their full-width forms, which some apps normalise before evaluating. */
    private const val FORMULA_TRIGGERS = "=+-@\uFF1D\uFF0B\uFF0D\uFF20"
}
