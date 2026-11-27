package com.footballcalendar.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchPhaseResolver
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.util.MatchFinalizationPolicy
import com.footballcalendar.app.util.MatchTimeFormatter
import com.footballcalendar.app.util.TeamAssetCatalog
import com.footballcalendar.app.util.TeamLocalization

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: String,
    val competitionCode: String,
    val competitionName: String,
    val competitionZhName: String,
    val matchday: Int?,
    val stage: String,
    val utcDate: String,
    val status: String,
    val homeTeamId: Int,
    val homeTeamName: String,
    val homeTeamShortName: String,
    val homeTeamZhName: String,
    val awayTeamId: Int,
    val awayTeamName: String,
    val awayTeamShortName: String,
    val awayTeamZhName: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val venue: String,
    val lastUpdatedUtc: String,
    val homeTeamCrest: String = "",
    val awayTeamCrest: String = "",
    val elapsedMinutes: Int? = null,
    val apiFootballFixtureId: Int? = null,
    val espnSlug: String? = null,
    val espnEventId: String? = null,
    val lotteryCode: String? = null,
    val lotteryHandicap: Int? = null,
    val lotteryDeadlineUtc: Long? = null,
    val isFinalized: Boolean = false,
    val watchNightDateKey: String = "",
    val localDateKey: String = "",
) {
    fun toDomain(): MatchItem {
        val localizedHome = TeamLocalization.preferChinese(homeTeamName, homeTeamZhName)
        val localizedAway = TeamLocalization.preferChinese(awayTeamName, awayTeamZhName)
        val statusEnum = MatchStatus.fromRaw(status)
        val phase = MatchPhaseResolver.resolve(statusEnum, utcDate, elapsedMinutes)
        val finalized = isFinalized || MatchFinalizationPolicy.isFinalized(
            phase = phase,
            utcDate = utcDate,
            elapsedMinutes = elapsedMinutes,
            alreadyFinalized = isFinalized,
        )
        return MatchItem(
            id = id,
            competitionCode = competitionCode,
            competitionName = competitionName,
            competitionZhName = TeamLocalization.localizeCompetition(competitionCode, competitionZhName),
            matchday = matchday,
            stage = stage,
            utcDate = utcDate,
            status = statusEnum,
            homeTeamId = homeTeamId,
            homeTeamName = homeTeamName,
            homeTeamShortName = homeTeamShortName,
            homeTeamZhName = localizedHome,
            awayTeamId = awayTeamId,
            awayTeamName = awayTeamName,
            awayTeamShortName = awayTeamShortName,
            awayTeamZhName = localizedAway,
            homeScore = homeScore,
            awayScore = awayScore,
            venue = venue,
            lastUpdatedUtc = lastUpdatedUtc,
            homeTeamCrest = homeTeamCrest.ifBlank { TeamAssetCatalog.resolveTeamLogo(homeTeamName) },
            awayTeamCrest = awayTeamCrest.ifBlank { TeamAssetCatalog.resolveTeamLogo(awayTeamName) },
            elapsedMinutes = elapsedMinutes,
            apiFootballFixtureId = apiFootballFixtureId,
            espnSlug = espnSlug,
            espnEventId = espnEventId,
            lotteryCode = lotteryCode,
            lotteryHandicap = lotteryHandicap,
            lotteryDeadlineUtc = lotteryDeadlineUtc,
            isFinalized = finalized,
        )
    }

    companion object {
        fun fromDomain(item: MatchItem, hasLocalSnapshot: Boolean = false): MatchEntity {
            val finalized = item.isFinalized || MatchFinalizationPolicy.isFinalized(
                phase = item.phase,
                utcDate = item.utcDate,
                elapsedMinutes = item.elapsedMinutes,
                hasLocalSnapshot = hasLocalSnapshot,
                alreadyFinalized = item.isFinalized,
            )
            return MatchEntity(
                id = item.id,
                competitionCode = item.competitionCode,
                competitionName = item.competitionName,
                competitionZhName = item.competitionZhName,
                matchday = item.matchday,
                stage = item.stage,
                utcDate = item.utcDate,
                status = item.status.name,
                homeTeamId = item.homeTeamId,
                homeTeamName = item.homeTeamName,
                homeTeamShortName = item.homeTeamShortName,
                homeTeamZhName = item.homeTeamZhName,
                awayTeamId = item.awayTeamId,
                awayTeamName = item.awayTeamName,
                awayTeamShortName = item.awayTeamShortName,
                awayTeamZhName = item.awayTeamZhName,
                homeScore = item.homeScore,
                awayScore = item.awayScore,
                venue = item.venue,
                lastUpdatedUtc = item.lastUpdatedUtc,
                homeTeamCrest = item.homeTeamCrest,
                awayTeamCrest = item.awayTeamCrest,
                elapsedMinutes = item.elapsedMinutes,
                apiFootballFixtureId = item.apiFootballFixtureId,
                espnSlug = item.espnSlug,
                espnEventId = item.espnEventId,
                lotteryCode = item.lotteryCode,
                lotteryHandicap = item.lotteryHandicap,
                lotteryDeadlineUtc = item.lotteryDeadlineUtc,
                isFinalized = finalized,
                watchNightDateKey = MatchTimeFormatter.dateKeyFor(item.utcDate, watchNightGrouping = true),
                localDateKey = MatchTimeFormatter.dateKeyFor(item.utcDate, watchNightGrouping = false),
            )
        }
    }
}
