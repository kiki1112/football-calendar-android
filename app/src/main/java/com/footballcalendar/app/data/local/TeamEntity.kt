package com.footballcalendar.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.footballcalendar.app.model.SquadMember
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.util.TeamLocalization
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SquadMemberJson(
    val id: String = "",
    val name: String = "",
    val zhName: String = "",
    val isTransliterated: Boolean = false,
    val position: String = "",
    val positionZh: String = "",
    val nationality: String = "",
    val birthYear: Int = 0,
    val shirtNumber: Int = 0,
    val portraitUrl: String = "",
)

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val keyName: String,
    val teamId: Int,
    val competitionCode: String,
    val name: String,
    val shortName: String,
    val zhName: String,
    val crest: String,
    val venue: String,
    val founded: Int,
    val clubColors: String,
    val coachName: String,
    val coachZhName: String,
    val coachNationality: String,
    val squadJson: String,
    val espnTeamId: String = "",
    val espnSlug: String = "",
    val coachPortraitUrl: String = "",
) {
    fun toDomain(json: Json): TeamDetailInfo {
        val parsed = runCatching {
            json.decodeFromString<List<SquadMemberJson>>(squadJson)
        }.getOrDefault(emptyList())
        return TeamDetailInfo(
            teamId = teamId,
            competitionCode = competitionCode,
            name = name,
            shortName = shortName,
            zhName = TeamLocalization.preferChinese(name, zhName),
            crest = crest,
            venue = venue,
            founded = founded,
            clubColors = clubColors,
            coachName = coachName,
            coachZhName = TeamLocalization.preferChineseCoach(coachName, coachZhName),
            coachNationality = coachNationality,
            squad = parsed.map { m ->
                val (pZh, tr) = if (m.zhName.isNotBlank() && TeamLocalization.containsCjk(m.zhName)) {
                    m.zhName to m.isTransliterated
                } else {
                    TeamLocalization.localizePlayer(m.name)
                }
                SquadMember(
                    id = m.id,
                    name = m.name,
                    zhName = pZh,
                    isTransliterated = tr,
                    position = m.position,
                    positionZh = m.positionZh.ifBlank { TeamLocalization.localizePosition(m.position) },
                    nationality = m.nationality,
                    birthYear = m.birthYear,
                    shirtNumber = m.shirtNumber,
                    portraitUrl = m.portraitUrl,
                )
            },
            espnTeamId = espnTeamId,
            espnSlug = espnSlug,
            coachPortraitUrl = coachPortraitUrl,
        )
    }

    companion object {
        fun fromDomain(info: TeamDetailInfo, json: Json): TeamEntity = TeamEntity(
            keyName = info.name.lowercase().trim(),
            teamId = info.teamId,
            competitionCode = info.competitionCode,
            name = info.name,
            shortName = info.shortName,
            zhName = info.zhName,
            crest = info.crest,
            venue = info.venue,
            founded = info.founded,
            clubColors = info.clubColors,
            coachName = info.coachName,
            coachZhName = TeamLocalization.preferChineseCoach(info.coachName, info.coachZhName),
            coachNationality = info.coachNationality,
            squadJson = json.encodeToString(
                info.squad.map {
                    SquadMemberJson(
                        id = it.id,
                        name = it.name,
                        zhName = it.zhName,
                        isTransliterated = it.isTransliterated,
                        position = it.position,
                        positionZh = it.positionZh,
                        nationality = it.nationality,
                        birthYear = it.birthYear,
                        shirtNumber = it.shirtNumber,
                        portraitUrl = it.portraitUrl,
                    )
                },
            ),
            espnTeamId = info.espnTeamId,
            espnSlug = info.espnSlug,
            coachPortraitUrl = info.coachPortraitUrl,
        )
    }
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY competitionCode ASC, zhName ASC")
    fun observeAllTeams(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams")
    suspend fun getAllTeamsOnce(): List<TeamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TeamEntity>)

    @Query("DELETE FROM teams")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<TeamEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}
