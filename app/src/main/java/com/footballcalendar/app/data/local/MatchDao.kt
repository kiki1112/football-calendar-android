package com.footballcalendar.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches ORDER BY utcDate ASC")
    fun observeAllMatches(): Flow<List<MatchEntity>>

    @Query(
        """
        SELECT * FROM matches
        WHERE watchNightDateKey = :dateKey
          AND (:isLotteryOnly = 0 OR lotteryCode IS NOT NULL)
        ORDER BY utcDate ASC
        """,
    )
    fun observeMatchesByDate(dateKey: String, isLotteryOnly: Boolean): Flow<List<MatchEntity>>

    @Query(
        """
        SELECT * FROM matches
        WHERE ((:watchNightGrouping = 1 AND watchNightDateKey = :dateKey)
            OR (:watchNightGrouping = 0 AND localDateKey = :dateKey))
          AND (:isLotteryOnly = 0 OR lotteryCode IS NOT NULL)
        ORDER BY utcDate ASC
        """,
    )
    fun observeMatchesByDateMode(
        dateKey: String,
        isLotteryOnly: Boolean,
        watchNightGrouping: Boolean,
    ): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :matchId LIMIT 1")
    fun observeMatchById(matchId: String): Flow<MatchEntity?>

    @Query("SELECT * FROM matches ORDER BY utcDate ASC")
    suspend fun getAllMatchesOnce(): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE id = :matchId LIMIT 1")
    suspend fun getMatchByIdOnce(matchId: String): MatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MatchEntity>)

    @Query("UPDATE matches SET isFinalized = 1 WHERE id = :matchId")
    suspend fun markFinalized(matchId: String)

    @Query("DELETE FROM matches")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<MatchEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}
