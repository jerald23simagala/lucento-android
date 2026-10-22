package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.WalletDao
import com.lucento.app.data.local.WalletEntity
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WalletRepository(
    private val dao: WalletDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object BlankName : SaveResult
        data object DuplicateName : SaveResult
        data object NegativeBalance : SaveResult
    }

    fun observeWallets(): Flow<List<Wallet>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun create(name: String, type: WalletType, balance: Money): SaveResult {
        val trimmed = name.trim()
        validate(trimmed, balance, excludeId = 0)?.let { return it }
        val now = clock()
        val id = dao.insert(
            WalletEntity(
                name = trimmed,
                type = type.name,
                currencyCode = balance.currencyCode,
                openingBalanceMinor = balance.amountMinor,
                sortOrder = dao.maxSortOrder() + 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        return SaveResult.Saved(id)
    }

    suspend fun update(id: Long, name: String, type: WalletType, balance: Money): SaveResult {
        val existing = dao.getById(id) ?: return SaveResult.BlankName
        val trimmed = name.trim()
        validate(trimmed, balance, excludeId = id)?.let { return it }
        dao.update(
            existing.copy(
                name = trimmed,
                type = type.name,
                currencyCode = balance.currencyCode,
                openingBalanceMinor = balance.amountMinor,
                updatedAt = clock(),
            )
        )
        return SaveResult.Saved(id)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    private suspend fun validate(name: String, balance: Money, excludeId: Long): SaveResult? = when {
        name.isEmpty() -> SaveResult.BlankName
        balance.isNegative -> SaveResult.NegativeBalance
        dao.countActiveWithName(name, excludeId) > 0 -> SaveResult.DuplicateName
        else -> null
    }
}

internal fun WalletEntity.toDomain(): Wallet = Wallet(
    id = id,
    name = name,
    type = runCatching { WalletType.valueOf(type) }.getOrDefault(WalletType.CASH),
    balance = Money(openingBalanceMinor, currencyCode),
    createdAtEpochMillis = createdAt,
)
