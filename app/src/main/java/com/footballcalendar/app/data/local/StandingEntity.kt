package com.footballcalendar.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.util.TeamAssetCatalog
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "standings")
data class StandingEntity(
    @PrimaryKey val id: String,
    val competitionCode: String,
    val rank: Int,
    val teamName: String,
    val teamZhName: String,
    val teamCrest: String,
    val played: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val goalDifference: Int,
    val points: Int,
    val form: String,
) {
    fun toDomain(): StandingEntry = StandingEntry(
        competitionCode = competitionCode,
        rank = rank,
        teamName = teamName,
        teamZhName = teamZhName,
        teamCrest = teamCrest.ifBlank { TeamAssetCatalog.resolveTeamLogo(teamName) },
        played = played,
        won = won,
        draw = draw,
        lost = lost,
        goalDifference = goalDifference,
        points = points,
        form = form,
    )

    companion object {
        fun fromDomain(entry: StandingEntry): StandingEntity = StandingEntity(
            id = "${entry.competitionCode}_${entry.rank}_${entry.teamName}",
            competitionCode = entry.competitionCode,
            rank = entry.rank,
            teamName = entry.teamName,
            teamZhName = entry.teamZhName,
            teamCrest = entry.teamCrest,
            played = entry.played,
            won = entry.won,
            draw = entry.draw,
            lost = entry.lost,
            goalDifference = entry.goalDifference,
            points = entry.points,
            form = entry.form,
        )
    }
}

@Dao
interface StandingDao {
    @Query("SELECT * FROM standings ORDER BY competitionCode ASC, rank ASC")
    fun observeAllStandings(): Flow<List<StandingEntity>>

    @Query("SELECT * FROM standings ORDER BY competitionCode ASC, rank ASC")
    suspend fun getAllStandingsOnce(): List<StandingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StandingEntity>)

    @Query("DELETE FROM standings")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<StandingEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}
