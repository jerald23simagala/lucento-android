package com.lucento.app.data.repository

/**
 * Runs a block atomically. Production wires this to Room's `withTransaction`, so multi-row writes
 * (both legs of a transfer, paying a recurring occurrence) either all happen or none do.
 */
interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R

    companion object {
        /** For tests and in-memory fakes, where writes cannot partially fail. */
        val Direct: TransactionRunner = object : TransactionRunner {
            override suspend fun <R> run(block: suspend () -> R): R = block()
        }
    }
}
