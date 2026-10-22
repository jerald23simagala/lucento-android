package com.lucento.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A contribution joined with its goal's currency (contributions always use the goal's currency). */
data class GoalContributionRow(
    val id: Long,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "occurred_on") val occurredOn: Long,
    val note: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
)

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY created_at ASC, id ASC")
    fun observeActive(): Flow<List<GoalEntity>>

    @Query(
        "SELECT c.id AS id, c.goal_id AS goal_id, c.amount_minor AS amount_minor, c.occurred_on AS occurred_on, " +
            "c.note AS note, g.currency_code AS currency_code " +
            "FROM goal_contributions c INNER JOIN goals g ON g.id = c.goal_id " +
            "ORDER BY c.occurred_on DESC, c.id DESC"
    )
    fun observeContributions(): Flow<List<GoalContributionRow>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: Long): GoalEntity?

    @Query("SELECT * FROM goal_contributions WHERE client_uuid = :clientUuid LIMIT 1")
    suspend fun findContributionByClientUuid(clientUuid: String): GoalContributionEntity?

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM goal_contributions WHERE goal_id = :goalId")
    suspend fun savedMinor(goalId: Long): Long

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertContribution(contribution: GoalContributionEntity): Long

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteContribution(id: Long)
}
