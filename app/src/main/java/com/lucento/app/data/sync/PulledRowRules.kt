package com.lucento.app.data.sync

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The same rules as the server's CHECK constraints (supabase/migrations/20261101120000_financial_check_constraints.sql),
 * applied to every row the app pulls (P2-9, N-06). A row that breaks them is skipped and counted — never reinterpreted:
 * the local readers would otherwise turn an unknown transaction type into an expense, or an unknown wallet type into
 * cash. The row stays on the server untouched and is pulled again if it is ever corrected.
 */
internal object PulledRowRules {
    private const val MAX_MINOR = 1_000_000_000_000_000L
    private val CURRENCY = Regex("^[A-Z]{3}$")
    private val WALLET_TYPES = setOf("CASH", "BANK", "E_WALLET", "SAVINGS", "CREDIT_CARD", "LOAN", "OTHER_ASSET")
    private val TRANSACTION_TYPES = setOf("EXPENSE", "INCOME", "TRANSFER_OUT", "TRANSFER_IN", "ADJUSTMENT_IN", "ADJUSTMENT_OUT")
    private val FREQUENCIES = setOf("WEEKLY", "MONTHLY", "YEARLY")

    /** Why [row] of [table] is not a valid Lucento record, or null when it is. Soft-deleted rows are never checked. */
    fun problem(table: String, row: JsonObject): String? {
        if (row.isSet("deleted_at")) return null
        return when (table) {
            "wallets" -> firstOf(
                currency(row),
                oneOf(row, "type", WALLET_TYPES),
                range(row, "opening_balance_minor", -MAX_MINOR, MAX_MINOR),
                optionalOneOf(row, "repayment", setOf("ONE_TIME", "MONTHLY")),
                optionalRange(row, "interest_rate_bps", 0, 10_000),
                optionalRange(row, "term_months", 1, Long.MAX_VALUE),
                optionalRange(row, "installment_minor", 1, MAX_MINOR),
                optionalRange(row, "credit_limit_minor", 1, MAX_MINOR),
                optionalRange(row, "statement_day", 1, 31),
                optionalRange(row, "due_day", 1, 31),
            )
            "categories" -> oneOf(row, "kind", setOf("EXPENSE", "INCOME"))
            "transactions" -> firstOf(currency(row), oneOf(row, "type", TRANSACTION_TYPES), range(row, "amount_minor", 0, MAX_MINOR))
            "budgets" -> firstOf(
                currency(row),
                range(row, "limit_minor", 0, MAX_MINOR),
                optionalMatch(row, "month", YEAR_MONTH),
                optionalMatch(row, "effective_from", YEAR_MONTH),
                // A row is one month or a run from a month, never both (server check budgets_single_scope).
                if (row.isSet("month") && row.isSet("effective_from")) "effective_from" else null,
            )
            "goals" -> firstOf(currency(row), range(row, "target_minor", 0, MAX_MINOR))
            "recurring" -> firstOf(
                currency(row),
                oneOf(row, "type", setOf("EXPENSE", "INCOME")),
                oneOf(row, "frequency", FREQUENCIES),
                range(row, "interval_count", 1, Long.MAX_VALUE),
                range(row, "amount_minor", 0, MAX_MINOR),
                optionalRange(row, "interest_minor", 0, MAX_MINOR),
                optionalRange(row, "next_index", 0, Long.MAX_VALUE),
            )
            else -> null
        }
    }

    private fun firstOf(vararg problems: String?): String? = problems.firstOrNull { it != null }

    private fun JsonObject.isSet(key: String) = this[key].let { it != null && it !is JsonNull }

    private fun text(row: JsonObject, key: String): String? = (row[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun currency(row: JsonObject): String? =
        text(row, "currency_code").let { if (it != null && CURRENCY.matches(it)) null else "currency_code" }

    private fun oneOf(row: JsonObject, key: String, allowed: Set<String>): String? = if (text(row, key) in allowed) null else key

    private fun optionalOneOf(row: JsonObject, key: String, allowed: Set<String>): String? =
        if (!row.isSet(key)) null else oneOf(row, key, allowed)

    private fun range(row: JsonObject, key: String, min: Long, max: Long): String? {
        val value = (row[key] as? JsonPrimitive)?.longOrNull ?: return key
        return if (value in min..max) null else key
    }

    private fun optionalRange(row: JsonObject, key: String, min: Long, max: Long): String? =
        if (!row.isSet(key)) null else range(row, key, min, max)

    private fun optionalMatch(row: JsonObject, key: String, pattern: Regex): String? =
        if (!row.isSet(key)) null else if (text(row, key)?.let(pattern::matches) == true) null else key

    /** "yyyy-MM" with a real month (budget lifecycle, v10). */
    private val YEAR_MONTH = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
}
