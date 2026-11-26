package com.footballcalendar.app.data.remote

import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.LineupPlayerItem
import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchEventItem
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchLineupSide
import com.footballcalendar.app.model.MatchStatPair
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.SquadMember
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.StarPlayer
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.util.TeamAssetCatalog
import com.footballcalendar.app.util.TeamLocalization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubFeedResponse(
    val schemaVersion: Int = 2,
    val generatedAtUtc: String = "",
    val mode: String = "demo_fallback",
    val matches: List<GitHubFeedMatchDto> = emptyList(),
    val standings: List<GitHubFeedStandingDto> = emptyList(),
    val players: List<GitHubFeedPlayerDto> = emptyList(),
    val teams: List<GitHubFeedTeamDto> = emptyList(),
    val liveDetails: Map<String, GitHubFeedLiveDetailDto> = emptyMap(),
)

@Serializable
data class GitHubFeedLiveDetailDto(
    val matchId: String = "",
    val fixtureId: Int = 0,
    val elapsedMinutes: Int? = null,
    val statusShort: String = "",
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val events: List<GitHubFeedEventDto> = emptyList(),
    val lineups: List<GitHubFeedLineupDto> = emptyList(),
    val stats: List<GitHubFeedStatPairDto> = emptyList(),
) {
    fun toDomain(nowMillis: Long): LiveMatchDetail = LiveMatchDetail(
        matchId = matchId,
        fixtureId = fixtureId,
        elapsedMinutes = elapsedMinutes,
        statusShort = statusShort,
        homeScore = homeScore,
        awayScore = awayScore,
        events = events.map {
            MatchEventItem(
                elapsed = it.elapsed,
                extra = it.extra,
                teamName = it.teamName,
                teamZhName = TeamLocalization.preferChinese(it.teamName, it.teamZhName),
                isHomeTeam = it.isHomeTeam,
                playerName = it.playerName,
                playerZhName = it.playerZhName.ifBlank { TeamLocalization.localizePlayer(it.playerName).first },
                assistName = it.assistName,
                assistZhName = it.assistName?.let { an -> it.assistZhName?.ifBlank { null } ?: TeamLocalization.localizePlayer(an).first },
                type = it.type,
                detail = it.detail,
            )
        },
        lineups = lineups.map { l ->
            MatchLineupSide(
                teamName = l.teamName,
                teamZhName = TeamLocalization.preferChinese(l.teamName, l.teamZhName),
                formation = l.formation,
                coachName = l.coachName,
                coachZhName = l.coachZhName.ifBlank { TeamLocalization.localizeCoach(l.coachName) },
                startXI = l.startXI.map { s ->
                    LineupPlayerItem(
                        name = s.name,
                        zhName = s.zhName.ifBlank { TeamLocalization.localizePlayer(s.name).first },
                        number = s.number,
                        pos = s.pos,
                    )
                },
                substitutes = l.substitutes.map { s ->
                    LineupPlayerItem(
                        name = s.name,
                        zhName = s.zhName.ifBlank { TeamLocalization.localizePlayer(s.name).first },
                        number = s.number,
                        pos = s.pos,
                    )
                },
            )
        },
        stats = stats.map { MatchStatPair(it.labelZh, it.homeValue, it.awayValue) },
        syncedAtMillis = nowMillis,
    )
}

@Serializable
data class GitHubFeedEventDto(
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
data class GitHubFeedLineupPlayerDto(
    val name: String = "",
    val zhName: String = "",
    val number: Int = 0,
    val pos: String = "",
)

@Serializable
data class GitHubFeedLineupDto(
    val teamName: String = "",
    val teamZhName: String = "",
    val formation: String = "",
    val coachName: String = "",
    val coachZhName: String = "",
    val startXI: List<GitHubFeedLineupPlayerDto> = emptyList(),
    val substitutes: List<GitHubFeedLineupPlayerDto> = emptyList(),
)

@Serializable
data class GitHubFeedStatPairDto(
    val labelZh: String = "",
    val homeValue: String = "",
    val awayValue: String = "",
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
    val elapsedMinutes: Int? = null,
    val apiFootballFixtureId: Int? = null,
    val espnSlug: String? = null,
    val espnEventId: String? = null,
) {
    fun toDomain(): MatchItem = MatchItem(
        id = id,
        competitionCode = competitionCode,
        competitionName = competitionName.ifBlank { competitionCode },
        competitionZhName = TeamLocalization.localizeCompetition(competitionCode, competitionZhName.ifBlank { competitionName }),
        matchday = matchday,
        stage = stage,
        utcDate = utcDate,
        status = MatchStatus.fromRaw(status),
        homeTeamId = homeTeamId,
        homeTeamName = homeTeamName,
        homeTeamShortName = homeTeamShortName.ifBlank { homeTeamName },
        homeTeamZhName = TeamLocalization.preferChinese(homeTeamName, homeTeamZhName),
        awayTeamId = awayTeamId,
        awayTeamName = awayTeamName,
        awayTeamShortName = awayTeamShortName.ifBlank { awayTeamName },
        awayTeamZhName = TeamLocalization.preferChinese(awayTeamName, awayTeamZhName),
        homeScore = homeScore,
        awayScore = awayScore,
        venue = venue,
        lastUpdatedUtc = lastUpdatedUtc.ifBlank { utcDate },
        homeTeamCrest = TeamAssetCatalog.resolveTeamLogo(homeTeamName, homeTeamCrest),
        awayTeamCrest = TeamAssetCatalog.resolveTeamLogo(awayTeamName, awayTeamCrest),
        elapsedMinutes = elapsedMinutes,
        apiFootballFixtureId = apiFootballFixtureId,
        espnSlug = espnSlug,
        espnEventId = espnEventId,
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
    val zoneType: String = "NONE",
    val zoneLabel: String = "",
    val groupName: String = "",
) {
    fun toDomain(totalRows: Int): StandingEntry {
        val (resolvedType, resolvedLabel) = if (zoneType != "NONE" && zoneLabel.isNotBlank()) {
            zoneType to zoneLabel
        } else {
            TeamLocalization.determineStandingZone(competitionCode, rank, totalRows)
        }
        return StandingEntry(
            competitionCode = competitionCode,
            rank = rank,
            teamName = teamName,
            teamZhName = TeamLocalization.preferChinese(teamName, teamZhName),
            teamCrest = TeamAssetCatalog.resolveTeamLogo(teamName, teamCrest),
            played = played,
            won = won,
            draw = draw,
            lost = lost,
            goalDifference = goalDifference,
            points = points,
            form = form,
            zoneType = resolvedType,
            zoneLabel = resolvedLabel,
            groupName = groupName,
        )
    }
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
    val shirtNumber: Int = 0,
    val birthYear: Int = 2000,
    val portraitUrl: String = "",
    val competitionCode: String = "",
    val rank: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val penalties: Int = 0,
    val playedMatches: Int = 0,
    val isTransliterated: Boolean = false,
) {
    fun toDomain(): StarPlayer {
        val (resolvedZh, tr) = if (zhName.isNotBlank() && TeamLocalization.containsCjk(zhName)) {
            zhName to isTransliterated
        } else {
            TeamLocalization.localizePlayer(name)
        }
        return StarPlayer(
            id = id,
            name = name,
            zhName = resolvedZh,
            clubName = clubName,
            clubZhName = TeamLocalization.preferChinese(clubName, clubZhName),
            clubCrestUrl = TeamAssetCatalog.resolveTeamLogo(clubName, clubCrestUrl),
            nationalName = nationalName,
            nationalZhName = TeamLocalization.preferChinese(nationalName, nationalZhName),
            nationalCrestUrl = TeamAssetCatalog.resolveTeamLogo(nationalName, nationalCrestUrl),
            positionZh = TeamLocalization.localizePosition(positionZh),
            nationalityZh = nationalityZh,
            shirtNumber = shirtNumber,
            birthYear = birthYear,
            portraitUrl = portraitUrl,
            competitionCode = competitionCode,
            rank = rank,
            goals = goals,
            assists = assists,
            penalties = penalties,
            playedMatches = playedMatches,
            isTransliterated = tr,
        )
    }
}

@Serializable
data class GitHubFeedSquadMemberDto(
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

@Serializable
data class GitHubFeedTeamDto(
    val teamId: Int = 0,
    val competitionCode: String = "",
    val name: String = "",
    val shortName: String = "",
    val zhName: String = "",
    val crest: String = "",
    val venue: String = "",
    val founded: Int = 0,
    val clubColors: String = "",
    val coachName: String = "",
    val coachZhName: String = "",
    val coachNationality: String = "",
    val squad: List<GitHubFeedSquadMemberDto> = emptyList(),
) {
    fun toDomain(): TeamDetailInfo = TeamDetailInfo(
        teamId = teamId,
        competitionCode = competitionCode,
        name = name,
        shortName = shortName.ifBlank { name },
        zhName = TeamLocalization.preferChinese(name, zhName),
        crest = TeamAssetCatalog.resolveTeamLogo(name, crest),
        venue = venue,
        founded = founded,
        clubColors = clubColors,
        coachName = coachName,
        coachZhName = coachZhName.ifBlank { TeamLocalization.localizeCoach(coachName) },
        coachNationality = coachNationality,
        squad = squad.map { m ->
            val (pZh, tr) = if (m.zhName.isNotBlank() && TeamLocalization.containsCjk(m.zhName)) {
                m.zhName to m.isTransliterated
            } else {
                TeamLocalization.localizePlayer(m.name)
            }
            SquadMember(
                id = m.id.ifBlank { "sq-${m.name.hashCode()}" },
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
data class FootballDataTeamsResponse(
    val teams: List<FootballDataTeamFullDto> = emptyList(),
)

@Serializable
data class FootballDataTeamFullDto(
    val id: Int = 0,
    val name: String = "",
    val shortName: String? = null,
    val crest: String? = null,
    val venue: String? = null,
    val founded: Int? = null,
    val clubColors: String? = null,
    val coach: FootballDataCoachDto? = null,
    val squad: List<FootballDataSquadPlayerDto> = emptyList(),
) {
    fun toDomain(compCode: String): TeamDetailInfo {
        val coachName = coach?.name ?: ""
        return TeamDetailInfo(
            teamId = id,
            competitionCode = compCode,
            name = name,
            shortName = shortName ?: name,
            zhName = TeamLocalization.localizeTeam(name, shortName),
            crest = TeamAssetCatalog.resolveTeamLogo(name, crest),
            venue = venue ?: "",
            founded = founded ?: 0,
            clubColors = clubColors ?: "",
            coachName = coachName,
            coachZhName = TeamLocalization.localizeCoach(coachName),
            coachNationality = coach?.nationality ?: "",
            squad = squad.map { s ->
                val (zh, tr) = TeamLocalization.localizePlayer(s.name)
                val by = s.dateOfBirth?.take(4)?.toIntOrNull() ?: 2000
                SquadMember(
                    id = "fdp-${s.id}",
                    name = s.name,
                    zhName = zh,
                    isTransliterated = tr,
                    position = s.position ?: "",
                    positionZh = TeamLocalization.localizePosition(s.position),
                    nationality = s.nationality ?: "",
                    birthYear = by,
                    shirtNumber = s.shirtNumber ?: 0,
                )
            },
        )
    }
}

@Serializable
data class FootballDataCoachDto(
    val name: String? = null,
    val nationality: String? = null,
)

@Serializable
data class FootballDataSquadPlayerDto(
    val id: Int = 0,
    val name: String = "",
    val position: String? = null,
    val dateOfBirth: String? = null,
    val nationality: String? = null,
    val shirtNumber: Int? = null,
)

@Serializable
data class FootballDataScorersResponse(
    val scorers: List<FootballDataScorerRowDto> = emptyList(),
)

@Serializable
data class FootballDataScorerRowDto(
    val player: FootballDataSquadPlayerDto? = null,
    val team: FootballDataTeamDto? = null,
    val playedMatches: Int? = null,
    val goals: Int? = null,
    val assists: Int? = null,
    val penalties: Int? = null,
)

// ---------- API-Football v3 DTOs ----------

@Serializable
data class ApiFootballFixturesResponse(
    val results: Int = 0,
    val response: List<ApiFootballFixtureItemDto> = emptyList(),
)

@Serializable
data class ApiFootballFixtureItemDto(
    val fixture: ApiFootballFixtureMetaDto = ApiFootballFixtureMetaDto(),
    val league: ApiFootballLeagueMetaDto = ApiFootballLeagueMetaDto(),
    val teams: ApiFootballTeamsPairDto = ApiFootballTeamsPairDto(),
    val goals: ApiFootballGoalsDto = ApiFootballGoalsDto(),
    val events: List<ApiFootballEventDto>? = null,
    val lineups: List<ApiFootballLineupDto>? = null,
    val statistics: List<ApiFootballStatsTeamDto>? = null,
) {
    fun toMatchItem(): MatchItem? {
        val code = ApiFootballLeagueCatalog.mapLeagueId(league.id) ?: return null
        if (fixture.id == 0 || fixture.date.isBlank()) return null
        val meta = CompetitionCatalog.findByCode(code)
        val homeName = teams.home.name
        val awayName = teams.away.name
        return MatchItem(
            id = "af-${fixture.id}",
            competitionCode = code,
            competitionName = league.name.ifBlank { meta?.enName ?: code },
            competitionZhName = meta?.zhName ?: league.name.ifBlank { code },
            matchday = league.round.split(" ").lastOrNull()?.toIntOrNull(),
            stage = league.round.ifBlank { "REGULAR_SEASON" },
            utcDate = fixture.date,
            status = mapAfStatus(fixture.status.short),
            homeTeamId = teams.home.id,
            homeTeamName = homeName,
            homeTeamShortName = homeName,
            homeTeamZhName = TeamLocalization.localizeTeam(homeName),
            awayTeamId = teams.away.id,
            awayTeamName = awayName,
            awayTeamShortName = awayName,
            awayTeamZhName = TeamLocalization.localizeTeam(awayName),
            homeScore = goals.home,
            awayScore = goals.away,
            venue = fixture.venue?.name ?: "",
            lastUpdatedUtc = fixture.date,
            homeTeamCrest = TeamAssetCatalog.resolveTeamLogo(homeName, teams.home.logo),
            awayTeamCrest = TeamAssetCatalog.resolveTeamLogo(awayName, teams.away.logo),
            elapsedMinutes = fixture.status.elapsed,
            apiFootballFixtureId = fixture.id,
        )
    }

    fun toLiveDetail(matchId: String): LiveMatchDetail {
        val homeName = teams.home.name
        val parsedEvents = events.orEmpty().map { e ->
            val tName = e.team?.name ?: ""
            val pName = e.player?.name ?: ""
            val aName = e.assist?.name?.takeIf { it.isNotBlank() }
            MatchEventItem(
                elapsed = e.time?.elapsed ?: 0,
                extra = e.time?.extra,
                teamName = tName,
                teamZhName = TeamLocalization.localizeTeam(tName),
                isHomeTeam = tName.equals(homeName, ignoreCase = true) || e.team?.id == teams.home.id,
                playerName = pName,
                playerZhName = TeamLocalization.localizePlayer(pName).first,
                assistName = aName,
                assistZhName = aName?.let { TeamLocalization.localizePlayer(it).first },
                type = e.type ?: "",
                detail = e.detail ?: "",
            )
        }
        val parsedLineups = lineups.orEmpty().map { l ->
            val tName = l.team?.name ?: ""
            val cName = l.coach?.name ?: ""
            MatchLineupSide(
                teamName = tName,
                teamZhName = TeamLocalization.localizeTeam(tName),
                formation = l.formation ?: "",
                coachName = cName,
                coachZhName = TeamLocalization.localizeCoach(cName),
                startXI = l.startXI.orEmpty().mapNotNull { slot ->
                    val pn = slot.player?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    LineupPlayerItem(
                        name = pn,
                        zhName = TeamLocalization.localizePlayer(pn).first,
                        number = slot.player.number ?: 0,
                        pos = slot.player.pos ?: "",
                    )
                },
                substitutes = l.substitutes.orEmpty().mapNotNull { slot ->
                    val pn = slot.player?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    LineupPlayerItem(
                        name = pn,
                        zhName = TeamLocalization.localizePlayer(pn).first,
                        number = slot.player.number ?: 0,
                        pos = slot.player.pos ?: "",
                    )
                },
            )
        }
        val parsedStats = buildStatPairs(statistics.orEmpty())
        return LiveMatchDetail(
            matchId = matchId,
            fixtureId = fixture.id,
            elapsedMinutes = fixture.status.elapsed,
            statusShort = fixture.status.short,
            homeScore = goals.home,
            awayScore = goals.away,
            events = parsedEvents,
            lineups = parsedLineups,
            stats = parsedStats,
            syncedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun buildStatPairs(groups: List<ApiFootballStatsTeamDto>): List<MatchStatPair> {
        if (groups.size < 2) return emptyList()
        val homeMap = groups[0].statistics.orEmpty().associate { (it.type ?: "") to (it.value ?: "-") }
        val awayMap = groups[1].statistics.orEmpty().associate { (it.type ?: "") to (it.value ?: "-") }
        val keys = listOf(
            "Ball Possession" to "控球率",
            "Total Shots" to "射门次数",
            "Shots on Goal" to "射正次数",
            "Corner Kicks" to "角球",
            "Passes %" to "传球成功率",
            "Fouls" to "犯规",
            "Yellow Cards" to "黄牌",
            "expected_goals" to "预期进球 (xG)",
        )
        return keys.mapNotNull { (rawKey, zhLabel) ->
            val hv = (homeMap[rawKey] as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
                ?.content
            val av = (awayMap[rawKey] as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
                ?.content
            if (hv != null || av != null) {
                MatchStatPair(zhLabel, hv ?: "0", av ?: "0")
            } else {
                null
            }
        }
    }

    companion object {
        fun mapAfStatus(shortStatus: String): MatchStatus = when (shortStatus.uppercase()) {
            "FT", "AET", "PEN" -> MatchStatus.FINISHED
            "1H", "2H", "ET", "P", "LIVE" -> MatchStatus.LIVE
            "HT" -> MatchStatus.PAUSED
            "PST", "CANC", "ABD", "AWD", "WO" -> MatchStatus.POSTPONED
            "SUSP" -> MatchStatus.SUSPENDED
            "TBD" -> MatchStatus.SCHEDULED
            else -> MatchStatus.TIMED
        }
    }
}

object ApiFootballLeagueCatalog {
    private val map = mapOf(
        3 to "UEL", 848 to "UECL", 17 to "ACL", 98 to "J1", 99 to "J2", 292 to "KL1",
        188 to "AL", 253 to "MLS", 5 to "UNL", 587 to "U17WC", 965 to "U20AC", 1153 to "U20AC",
        1 to "WC", 30 to "WC", 31 to "WC", 32 to "WC", 33 to "WC", 34 to "WC", 37 to "WC",
        4 to "EC", 960 to "EC", 1012 to "INT", 10 to "INT",
        39 to "PL", 140 to "PD", 78 to "BL1", 135 to "SA", 61 to "FL1", 2 to "CL", 40 to "ELC", 88 to "DED", 94 to "PPL", 71 to "BSA",
    )

    fun mapLeagueId(id: Int): String? = map[id]
}

@Serializable
data class ApiFootballFixtureMetaDto(
    val id: Int = 0,
    val date: String = "",
    val status: ApiFootballStatusDto = ApiFootballStatusDto(),
    val venue: ApiFootballVenueDto? = null,
)

@Serializable
data class ApiFootballStatusDto(
    val short: String = "NS",
    val elapsed: Int? = null,
)

@Serializable
data class ApiFootballVenueDto(
    val name: String? = null,
)

@Serializable
data class ApiFootballLeagueMetaDto(
    val id: Int = 0,
    val name: String = "",
    val round: String = "",
)

@Serializable
data class ApiFootballTeamsPairDto(
    val home: ApiFootballTeamStubDto = ApiFootballTeamStubDto(),
    val away: ApiFootballTeamStubDto = ApiFootballTeamStubDto(),
)

@Serializable
data class ApiFootballTeamStubDto(
    val id: Int = 0,
    val name: String = "TBD",
    val logo: String? = null,
)

@Serializable
data class ApiFootballGoalsDto(
    val home: Int? = null,
    val away: Int? = null,
)

@Serializable
data class ApiFootballEventDto(
    val time: ApiFootballTimeDto? = null,
    val team: ApiFootballTeamStubDto? = null,
    val player: ApiFootballNamedIdDto? = null,
    val assist: ApiFootballNamedIdDto? = null,
    val type: String? = null,
    val detail: String? = null,
)

@Serializable
data class ApiFootballTimeDto(
    val elapsed: Int? = null,
    val extra: Int? = null,
)

@Serializable
data class ApiFootballNamedIdDto(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
data class ApiFootballLineupDto(
    val team: ApiFootballTeamStubDto? = null,
    val formation: String? = null,
    val coach: ApiFootballNamedIdDto? = null,
    val startXI: List<ApiFootballLineupSlotDto>? = null,
    val substitutes: List<ApiFootballLineupSlotDto>? = null,
)

@Serializable
data class ApiFootballLineupSlotDto(
    val player: ApiFootballLineupPlayerDto? = null,
)

@Serializable
data class ApiFootballLineupPlayerDto(
    val id: Int? = null,
    val name: String? = null,
    val number: Int? = null,
    val pos: String? = null,
)

@Serializable
data class ApiFootballStatsTeamDto(
    val team: ApiFootballTeamStubDto? = null,
    val statistics: List<ApiFootballStatEntryDto>? = null,
)

@Serializable
data class ApiFootballStatEntryDto(
    val type: String? = null,
    val value: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class ApiFootballSquadResponse(
    val response: List<ApiFootballSquadTeamRowDto> = emptyList(),
)

@Serializable
data class ApiFootballSquadTeamRowDto(
    val team: ApiFootballTeamStubDto? = null,
    val players: List<ApiFootballSquadPlayerDto> = emptyList(),
)

@Serializable
data class ApiFootballSquadPlayerDto(
    val id: Int = 0,
    val name: String = "",
    val age: Int? = null,
    val number: Int? = null,
    val position: String? = null,
    val photo: String? = null,
)

@Serializable
data class ApiFootballTeamInfoResponse(
    val response: List<ApiFootballTeamVenueRowDto> = emptyList(),
)

@Serializable
data class ApiFootballTeamVenueRowDto(
    val team: ApiFootballTeamExtraDto? = null,
    val venue: ApiFootballVenueDto? = null,
)

@Serializable
data class ApiFootballTeamExtraDto(
    val id: Int = 0,
    val name: String = "",
    val logo: String? = null,
    val founded: Int? = null,
)

@Serializable
data class ApiFootballCoachesResponse(
    val response: List<ApiFootballNamedIdDto> = emptyList(),
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

@Serializable
data class WikipediaLanglinksApiResponse(
    val query: WikipediaLanglinksQueryDto? = null,
)

@Serializable
data class WikipediaLanglinksQueryDto(
    val pages: Map<String, WikipediaLanglinksPageDto> = emptyMap(),
)

@Serializable
data class WikipediaLanglinksPageDto(
    val langlinks: List<WikipediaLanglinkDto> = emptyList(),
)

@Serializable
data class WikipediaLanglinkDto(
    val lang: String = "",
    @SerialName("*") val title: String = "",
)
