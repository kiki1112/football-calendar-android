package com.footballcalendar.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.footballcalendar.app.model.LineupPlayerItem
import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchCommentaryItem
import com.footballcalendar.app.model.MatchEventItem
import com.footballcalendar.app.model.MatchLineupSide
import com.footballcalendar.app.model.MatchStatPair
import com.footballcalendar.app.util.LineupSanitizer
import com.footballcalendar.app.util.TeamLocalization
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MatchEventJson(
    val elapsed: Int = 0,
    val extra: Int? = null,
    val teamName: String = "",
    val teamZhName: String = "",
    val isHomeTeam: Boolean = true,
    val playerName: String = "",
    val playerZhName: String = "",
    val assistName: String? = null,
    val assistZhName: String? = null,
    val type: String = "",
    val detail: String = "",
)

@Serializable
data class LineupPlayerJson(
    val playerId: String = "",
    val name: String = "",
    val zhName: String = "",
    val number: Int = 0,
    val pos: String = "",
    val portraitUrl: String = "",
    val formationPlace: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val shots: Int = 0,
    val shotsOnTarget: Int = 0,
    val passes: Int = 0,
    val tackles: Int = 0,
    val saves: Int = 0,
    val foulsCommitted: Int = 0,
    val subbedIn: Boolean = false,
    val subbedOut: Boolean = false,
    val rating: String = "",
)

@Serializable
data class MatchLineupSideJson(
    val teamName: String = "",
    val teamZhName: String = "",
    val formation: String = "",
    val coachName: String = "",
    val coachZhName: String = "",
    val startXI: List<LineupPlayerJson> = emptyList(),
    val substitutes: List<LineupPlayerJson> = emptyList(),
    val coachPortraitUrl: String = "",
)

@Serializable
data class MatchStatPairJson(
    val labelZh: String = "",
    val homeValue: String = "",
    val awayValue: String = "",
    val group: String = "综合",
)

@Serializable
data class MatchCommentaryJson(
    val sequence: Int = 0,
    val clockDisplay: String = "",
    val elapsed: Int = 0,
    val textEn: String = "",
    val textZh: String = "",
    val typeSlug: String = "",
    val teamName: String = "",
    val teamZhName: String = "",
    val isHomeTeam: Boolean? = null,
)

@Serializable
data class LiveMatchDetailPayloadJson(
    val events: List<MatchEventJson> = emptyList(),
    val lineups: List<MatchLineupSideJson> = emptyList(),
    val stats: List<MatchStatPairJson> = emptyList(),
    val commentary: List<MatchCommentaryJson> = emptyList(),
    val venueName: String = "",
    val attendance: Int = 0,
    val refereeName: String = "",
    val weatherText: String = "",
    val temperatureText: String = "",
)

@Entity(tableName = "match_details")
data class MatchDetailEntity(
    @PrimaryKey val matchId: String,
    val fixtureId: Int,
    val elapsedMinutes: Int?,
    val statusShort: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val payloadJson: String,
    val syncedAtMillis: Long,
) {
    fun toDomain(json: Json): LiveMatchDetail {
        val decoded = runCatching {
            json.decodeFromString(LiveMatchDetailPayloadJson.serializer(), payloadJson)
        }.getOrDefault(LiveMatchDetailPayloadJson())
        return LiveMatchDetail(
            matchId = matchId,
            fixtureId = fixtureId,
            elapsedMinutes = elapsedMinutes,
            statusShort = statusShort,
            homeScore = homeScore,
            awayScore = awayScore,
            events = decoded.events.map {
                MatchEventItem(
                    elapsed = it.elapsed,
                    extra = it.extra,
                    teamName = it.teamName,
                    teamZhName = it.teamZhName,
                    isHomeTeam = it.isHomeTeam,
                    playerName = it.playerName,
                    playerZhName = it.playerZhName,
                    assistName = it.assistName,
                    assistZhName = it.assistZhName,
                    type = it.type,
                    detail = it.detail,
                )
            },
            lineups = decoded.lineups.map { side ->
                val rawStarters = side.startXI.map { it.toDomain() }
                MatchLineupSide(
                    teamName = side.teamName,
                    teamZhName = TeamLocalization.preferChinese(side.teamName, side.teamZhName),
                    formation = side.formation,
                    coachName = side.coachName,
                    coachZhName = TeamLocalization.preferChineseCoach(side.coachName, side.coachZhName),
                    startXI = LineupSanitizer.dedupeStarters(rawStarters),
                    substitutes = side.substitutes.map { it.toDomain() },
                    coachPortraitUrl = side.coachPortraitUrl,
                )
            },
            stats = decoded.stats.map {
                MatchStatPair(
                    labelZh = it.labelZh,
                    homeValue = it.homeValue,
                    awayValue = it.awayValue,
                    group = it.group,
                )
            },
            syncedAtMillis = syncedAtMillis,
            commentary = decoded.commentary.map {
                MatchCommentaryItem(
                    sequence = it.sequence,
                    clockDisplay = it.clockDisplay,
                    elapsed = it.elapsed,
                    textEn = it.textEn,
                    textZh = it.textZh,
                    typeSlug = it.typeSlug,
                    teamName = it.teamName,
                    teamZhName = it.teamZhName,
                    isHomeTeam = it.isHomeTeam,
                )
            },
            venueName = decoded.venueName,
            attendance = decoded.attendance,
            refereeName = decoded.refereeName,
            weatherText = decoded.weatherText,
            temperatureText = decoded.temperatureText,
        )
    }

    companion object {
        fun fromDomain(detail: LiveMatchDetail, json: Json): MatchDetailEntity {
            val payload = LiveMatchDetailPayloadJson(
                events = detail.events.map {
                    MatchEventJson(
                        elapsed = it.elapsed,
                        extra = it.extra,
                        teamName = it.teamName,
                        teamZhName = it.teamZhName,
                        isHomeTeam = it.isHomeTeam,
                        playerName = it.playerName,
                        playerZhName = it.playerZhName,
                        assistName = it.assistName,
                        assistZhName = it.assistZhName,
                        type = it.type,
                        detail = it.detail,
                    )
                },
                lineups = detail.lineups.map { side ->
                    MatchLineupSideJson(
                        teamName = side.teamName,
                        teamZhName = side.teamZhName,
                        formation = side.formation,
                        coachName = side.coachName,
                        coachZhName = TeamLocalization.preferChineseCoach(side.coachName, side.coachZhName),
                        startXI = LineupSanitizer.dedupeStarters(side.startXI).map { it.toJson() },
                        substitutes = side.substitutes.map { it.toJson() },
                        coachPortraitUrl = side.coachPortraitUrl,
                    )
                },
                stats = detail.stats.map {
                    MatchStatPairJson(
                        labelZh = it.labelZh,
                        homeValue = it.homeValue,
                        awayValue = it.awayValue,
                        group = it.group,
                    )
                },
                commentary = detail.commentary.map {
                    MatchCommentaryJson(
                        sequence = it.sequence,
                        clockDisplay = it.clockDisplay,
                        elapsed = it.elapsed,
                        textEn = it.textEn,
                        textZh = it.textZh,
                        typeSlug = it.typeSlug,
                        teamName = it.teamName,
                        teamZhName = it.teamZhName,
                        isHomeTeam = it.isHomeTeam,
                    )
                },
                venueName = detail.venueName,
                attendance = detail.attendance,
                refereeName = detail.refereeName,
                weatherText = detail.weatherText,
                temperatureText = detail.temperatureText,
            )
            return MatchDetailEntity(
                matchId = detail.matchId,
                fixtureId = detail.fixtureId,
                elapsedMinutes = detail.elapsedMinutes,
                statusShort = detail.statusShort,
                homeScore = detail.homeScore,
                awayScore = detail.awayScore,
                payloadJson = json.encodeToString(payload),
                syncedAtMillis = detail.syncedAtMillis,
            )
        }
    }
}

private fun LineupPlayerJson.toDomain(): LineupPlayerItem = LineupPlayerItem(
    name = name,
    zhName = zhName,
    number = number,
    pos = pos,
    portraitUrl = portraitUrl,
    formationPlace = formationPlace,
    goals = goals,
    assists = assists,
    yellowCards = yellowCards,
    redCards = redCards,
    shots = shots,
    shotsOnTarget = shotsOnTarget,
    passes = passes,
    tackles = tackles,
    saves = saves,
    foulsCommitted = foulsCommitted,
    subbedIn = subbedIn,
    subbedOut = subbedOut,
    rating = rating,
    playerId = playerId,
)

private fun LineupPlayerItem.toJson(): LineupPlayerJson = LineupPlayerJson(
    playerId = effectivePlayerId,
    name = name,
    zhName = zhName,
    number = number,
    pos = pos,
    portraitUrl = portraitUrl,
    formationPlace = formationPlace,
    goals = goals,
    assists = assists,
    yellowCards = yellowCards,
    redCards = redCards,
    shots = shots,
    shotsOnTarget = shotsOnTarget,
    passes = passes,
    tackles = tackles,
    saves = saves,
    foulsCommitted = foulsCommitted,
    subbedIn = subbedIn,
    subbedOut = subbedOut,
    rating = rating,
)

@Dao
interface MatchDetailDao {
    @Query("SELECT * FROM match_details WHERE matchId = :matchId LIMIT 1")
    fun observeByMatchId(matchId: String): Flow<MatchDetailEntity?>

    @Query("SELECT * FROM match_details")
    fun observeAllDetails(): Flow<List<MatchDetailEntity>>

    @Query("SELECT * FROM match_details WHERE matchId = :matchId LIMIT 1")
    suspend fun getByMatchIdOnce(matchId: String): MatchDetailEntity?

    @Query("SELECT * FROM match_details")
    suspend fun getAllOnce(): List<MatchDetailEntity>

    @Query("SELECT matchId FROM match_details")
    suspend fun getAllMatchIdsOnce(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MatchDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MatchDetailEntity>)
}
