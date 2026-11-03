package com.lucento.app.data.repository

import com.lucento.app.data.local.TransactionDao
import com.lucento.app.data.local.TransactionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** P2-8 (N-12): screens share one whole-table query; scoped reads give the same numbers as the full list. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedTransactionsTest {

    private class CountingDao(val inner: FakeTransactionDao = FakeTransactionDao()) : TransactionDao by inner {
        var wholeTableQueries = 0
        override fun observeAll(): Flow<List<TransactionEntity>> = inner.observeAll().onStart { wholeTableQueries++ }
    }

    private fun row(id: Long, type: String, group: String? = null) = TransactionEntity(
        id = id, type = type, amountMinor = 100, currencyCode = "USD", walletId = 1, categoryId = null, occurredOn = 20_000,
        note = "", transferGroup = group, clientUuid = "u$id", createdAt = 0, updatedAt = 0,
    )

    @Test fun `several screens listening share one query, and a returning screen gets fresh rows`() = runTest {
        val dao = CountingDao()
        dao.inner.rows.value = listOf(row(1, "EXPENSE"))
        val repo = TransactionRepository(dao, FakeWalletDao(), FakeCategoryDao(), shareScope = backgroundScope)

        val a = launch { repo.observeTransactions().collect {} }
        val b = launch { repo.observeTransactions().collect {} }
        runCurrent()
        assertEquals(1, dao.wholeTableQueries)

        a.cancel(); b.cancel()
        advanceTimeBy(6_000) // past the 5 s grace: the shared query stops and its cache is dropped
        dao.inner.rows.value = listOf(row(1, "EXPENSE"), row(2, "INCOME"))
        assertEquals(2, repo.observeTransactions().first().size)
    }

    @Test fun `scoped counts and the detail read agree with the full list`() = runTest {
        val dao = FakeTransactionDao()
        dao.rows.value = listOf(row(1, "EXPENSE"), row(2, "INCOME"), row(3, "TRANSFER_OUT", "g"), row(4, "TRANSFER_IN", "g"))
        val repo = TransactionRepository(dao, FakeWalletDao(), FakeCategoryDao())

        assertEquals(4, repo.observeCount().first())
        assertEquals(repo.observeTransactions().first().count { it.type.isCashFlow }, repo.observeCashFlowCount().first())
        assertEquals(setOf(3L, 4L), repo.observeWithTransferLegs(3).first().map { it.id }.toSet())
        assertEquals(listOf(1L), repo.observeWithTransferLegs(1).first().map { it.id })
    }
}
