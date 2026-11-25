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

    @Query("SELECT * FROM matches ORDER BY utcDate ASC")
    suspend fun getAllMatchesOnce(): List<MatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MatchEntity>)

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
