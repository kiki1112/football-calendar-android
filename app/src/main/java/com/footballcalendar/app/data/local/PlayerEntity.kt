package com.footballcalendar.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.footballcalendar.app.model.StarPlayer
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val zhName: String,
    val clubName: String,
    val clubZhName: String,
    val clubCrestUrl: String,
    val nationalName: String,
    val nationalZhName: String,
    val nationalCrestUrl: String,
    val positionZh: String,
    val nationalityZh: String,
    val shirtNumber: Int,
    val birthYear: Int,
    val portraitUrl: String,
    val bio: String,
    val competitionCode: String,
    val rank: Int,
    val goals: Int,
    val assists: Int,
    val penalties: Int,
    val playedMatches: Int,
    val isTransliterated: Boolean,
    val sortOrder: Int,
) {
    fun toDomain(): StarPlayer = StarPlayer(
        id = id,
        name = name,
        zhName = zhName,
        clubName = clubName,
        clubZhName = clubZhName,
        clubCrestUrl = clubCrestUrl,
        nationalName = nationalName,
        nationalZhName = nationalZhName,
        nationalCrestUrl = nationalCrestUrl,
        positionZh = positionZh,
        nationalityZh = nationalityZh,
        shirtNumber = shirtNumber,
        birthYear = birthYear,
        portraitUrl = portraitUrl,
        bio = bio,
        competitionCode = competitionCode,
        rank = rank,
        goals = goals,
        assists = assists,
        penalties = penalties,
        playedMatches = playedMatches,
        isTransliterated = isTransliterated,
    )

    companion object {
        fun fromDomain(player: StarPlayer, sortOrder: Int): PlayerEntity = PlayerEntity(
            id = player.id,
            name = player.name,
            zhName = player.zhName,
            clubName = player.clubName,
            clubZhName = player.clubZhName,
            clubCrestUrl = player.clubCrestUrl,
            nationalName = player.nationalName,
            nationalZhName = player.nationalZhName,
            nationalCrestUrl = player.nationalCrestUrl,
            positionZh = player.positionZh,
            nationalityZh = player.nationalityZh,
            shirtNumber = player.shirtNumber,
            birthYear = player.birthYear,
            portraitUrl = player.portraitUrl,
            bio = player.bio,
            competitionCode = player.competitionCode,
            rank = player.rank,
            goals = player.goals,
            assists = player.assists,
            penalties = player.penalties,
            playedMatches = player.playedMatches,
            isTransliterated = player.isTransliterated,
            sortOrder = sortOrder,
        )
    }
}

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY sortOrder ASC, rank ASC, goals DESC")
    fun observeAllPlayers(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players ORDER BY sortOrder ASC, rank ASC, goals DESC")
    suspend fun getAllPlayersOnce(): List<PlayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PlayerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PlayerEntity>)

    @Query("DELETE FROM players")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<PlayerEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}
