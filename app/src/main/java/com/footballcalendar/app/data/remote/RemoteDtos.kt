package com.footballcalendar.app.data.remote

import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.StarPlayer
import com.footballcalendar.app.util.TeamAssetCatalog
import com.footballcalendar.app.util.TeamLocalization
import kotlinx.serialization.Serializable

@Serializable
data class GitHubFeedResponse(
    val schemaVersion: Int = 2,
    val generatedAtUtc: String = "",
    val mode: String = "demo_fallback",
    val matches: List<GitHubFeedMatchDto> = emptyList(),
    val standings: List<GitHubFeedStandingDto> = emptyList(),
    val players: List<GitHubFeedPlayerDto> = emptyList(),
)

@Serializable
data class GitHubFeedMatchDto(
    val id: String,
    val competitionCode: String = "OTHER",
    val competitionName: String = "",
    val competitionZhName: String = "",
    val matchday: Int? = null,
    val stage: String = "REGULAR_SEASON",
    val utcDate: String,
    val status: String = "SCHEDULED",
    val homeTeamId: Int = 0,
    val homeTeamName: String = "TBD",
    val homeTeamShortName: String = "TBD",
    val homeTeamZhName: String = "待定",
    val homeTeamCrest: String = "",
    val awayTeamId: Int = 0,
    val awayTeamName: String = "TBD",
    val awayTeamShortName: String = "TBD",
    val awayTeamZhName: String = "待定",
    val awayTeamCrest: String = "",
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val venue: String = "",
    val lastUpdatedUtc: String = "",
) {
    fun toDomain(): MatchItem = MatchItem(
        id = id,
        competitionCode = competitionCode,
        competitionName = competitionName.ifBlank { competitionCode },
        competitionZhName = competitionZhName.ifBlank {
            TeamLocalization.localizeCompetition(competitionCode, competitionName)
        },
        matchday = matchday,
        stage = stage,
        utcDate = utcDate,
        status = MatchStatus.fromRaw(status),
        homeTeamId = homeTeamId,
        homeTeamName = homeTeamName,
        homeTeamShortName = homeTeamShortName.ifBlank { homeTeamName },
        homeTeamZhName = homeTeamZhName.ifBlank {
            TeamLocalization.localizeTeam(homeTeamName, homeTeamShortName)
        },
        awayTeamId = awayTeamId,
        awayTeamName = awayTeamName,
        awayTeamShortName = awayTeamShortName.ifBlank { awayTeamName },
        awayTeamZhName = awayTeamZhName.ifBlank {
            TeamLocalization.localizeTeam(awayTeamName, awayTeamShortName)
        },
        homeScore = homeScore,
        awayScore = awayScore,
        venue = venue,
        lastUpdatedUtc = lastUpdatedUtc.ifBlank { utcDate },
        homeTeamCrest = TeamAssetCatalog.resolveTeamLogo(homeTeamName, homeTeamCrest),
        awayTeamCrest = TeamAssetCatalog.resolveTeamLogo(awayTeamName, awayTeamCrest),
    )
}

@Serializable
data class GitHubFeedStandingDto(
    val competitionCode: String,
    val rank: Int,
    val teamName: String,
    val teamZhName: String = "",
    val teamCrest: String = "",
    val played: Int = 0,
    val won: Int = 0,
    val draw: Int = 0,
    val lost: Int = 0,
    val goalDifference: Int = 0,
    val points: Int = 0,
    val form: String = "",
) {
    fun toDomain(): StandingEntry = StandingEntry(
        competitionCode = competitionCode,
        rank = rank,
        teamName = teamName,
        teamZhName = teamZhName.ifBlank { TeamLocalization.localizeTeam(teamName) },
        teamCrest = TeamAssetCatalog.resolveTeamLogo(teamName, teamCrest),
        played = played,
        won = won,
        draw = draw,
        lost = lost,
        goalDifference = goalDifference,
        points = points,
        form = form,
    )
}

@Serializable
data class GitHubFeedPlayerDto(
    val id: String,
    val name: String,
    val zhName: String = "",
    val clubName: String = "",
    val clubZhName: String = "",
    val clubCrestUrl: String = "",
    val nationalName: String = "",
    val nationalZhName: String = "",
    val nationalCrestUrl: String = "",
    val positionZh: String = "",
    val nationalityZh: String = "",
    val shirtNumber: Int = 10,
    val birthYear: Int = 2000,
    val portraitUrl: String = "",
) {
    fun toDomain(): StarPlayer = StarPlayer(
        id = id,
        name = name,
        zhName = zhName.ifBlank { name },
        clubName = clubName,
        clubZhName = clubZhName.ifBlank { clubName },
        clubCrestUrl = TeamAssetCatalog.resolveTeamLogo(clubName, clubCrestUrl),
        nationalName = nationalName,
        nationalZhName = nationalZhName.ifBlank { nationalName },
        nationalCrestUrl = TeamAssetCatalog.resolveTeamLogo(nationalName, nationalCrestUrl),
        positionZh = positionZh,
        nationalityZh = nationalityZh,
        shirtNumber = shirtNumber,
        birthYear = birthYear,
        portraitUrl = portraitUrl,
    )
}

@Serializable
data class FootballDataMatchesResponse(
    val matches: List<FootballDataMatchDto> = emptyList(),
)

@Serializable
data class FootballDataMatchDto(
    val id: Long,
    val utcDate: String = "",
    val status: String = "SCHEDULED",
    val matchday: Int? = null,
    val stage: String? = null,
    val lastUpdated: String? = null,
    val venue: String? = null,
    val competition: FootballDataCompetitionDto? = null,
    val homeTeam: FootballDataTeamDto? = null,
    val awayTeam: FootballDataTeamDto? = null,
    val score: FootballDataScoreDto? = null,
) {
    fun toDomain(): MatchItem? {
        if (utcDate.isBlank()) return null
        val compCode = competition?.code?.uppercase() ?: "OTHER"
        val compName = competition?.name ?: compCode
        val homeName = homeTeam?.name ?: "TBD"
        val homeShort = homeTeam?.shortName ?: homeTeam?.tla ?: homeName
        val awayName = awayTeam?.name ?: "TBD"
        val awayShort = awayTeam?.shortName ?: awayTeam?.tla ?: awayName

        return MatchItem(
            id = "fd-$id",
            competitionCode = compCode,
            competitionName = compName,
            competitionZhName = TeamLocalization.localizeCompetition(compCode, compName),
            matchday = matchday,
            stage = stage ?: "REGULAR_SEASON",
            utcDate = utcDate,
            status = MatchStatus.fromRaw(status),
            homeTeamId = homeTeam?.id ?: 0,
            homeTeamName = homeName,
            homeTeamShortName = homeShort,
            homeTeamZhName = TeamLocalization.localizeTeam(homeName, homeShort),
            awayTeamId = awayTeam?.id ?: 0,
            awayTeamName = awayName,
            awayTeamShortName = awayShort,
            awayTeamZhName = TeamLocalization.localizeTeam(awayName, awayShort),
            homeScore = score?.fullTime?.home,
            awayScore = score?.fullTime?.away,
            venue = venue ?: "",
            lastUpdatedUtc = lastUpdated ?: utcDate,
            homeTeamCrest = TeamAssetCatalog.resolveTeamLogo(homeName, homeTeam?.crest),
            awayTeamCrest = TeamAssetCatalog.resolveTeamLogo(awayName, awayTeam?.crest),
        )
    }
}

@Serializable
data class FootballDataCompetitionDto(
    val id: Int? = null,
    val name: String? = null,
    val code: String? = null,
)

@Serializable
data class FootballDataTeamDto(
    val id: Int? = null,
    val name: String? = null,
    val shortName: String? = null,
    val tla: String? = null,
    val crest: String? = null,
)

@Serializable
data class FootballDataScoreDto(
    val fullTime: FootballDataScoreLineDto? = null,
)

@Serializable
data class FootballDataScoreLineDto(
    val home: Int? = null,
    val away: Int? = null,
)

@Serializable
data class FootballDataStandingsResponse(
    val standings: List<FootballDataStandingGroupDto> = emptyList(),
)

@Serializable
data class FootballDataStandingGroupDto(
    val type: String = "TOTAL",
    val table: List<FootballDataStandingRowDto> = emptyList(),
)

@Serializable
data class FootballDataStandingRowDto(
    val position: Int = 0,
    val team: FootballDataTeamDto? = null,
    val playedGames: Int = 0,
    val won: Int = 0,
    val draw: Int = 0,
    val lost: Int = 0,
    val points: Int = 0,
    val goalDifference: Int = 0,
    val form: String? = null,
)

@Serializable
data class WikipediaSummaryResponse(
    val title: String = "",
    val description: String = "",
    val extract: String = "",
    val thumbnail: WikipediaThumbnailDto? = null,
)

@Serializable
data class WikipediaThumbnailDto(
    val source: String = "",
)
