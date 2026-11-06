package com.lucento.app.data.repository

/**
 * Lucento Shared: whether this phone may change the financial structure — wallets, categories, budgets, goals,
 * schedules and debts. False on a Partner's phone; the database refuses those writes from a Partner anyway, so this only
 * keeps the phone from holding changes it could never upload. Transactions are never gated.
 */
fun interface StructureAccess {
    suspend fun canManageStructure(): Boolean

    companion object {
        val Always = StructureAccess { true }
    }
}
