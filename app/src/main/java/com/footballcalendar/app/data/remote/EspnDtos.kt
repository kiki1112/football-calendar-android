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
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.util.TeamAssetCatalog
import com.footballcalendar.app.util.TeamLocalization
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object EspnLeagueCatalog {
    val slugByCode = mapOf(
        "PL" to "eng.1",
        "PD" to "esp.1",
        "BL1" to "ger.1",
        "SA" to "ita.1",
        "FL1" to "fra.1",
        "CL" to "uefa.champions",
        "UEL" to "uefa.europa",
        "UECL" to "uefa.europa.conf",
        "ACL" to "afc.champions",
        "J1" to "jpn.1",
        "AL" to "aus.1",
        "MLS" to "usa.1",
        "ELC" to "eng.2",
        "DED" to "ned.1",
        "PPL" to "por.1",
        "BSA" to "bra.1",
        "CLI" to "conmebol.libertadores",
        "WC" to "fifa.world",
        "EC" to "uefa.euro",
        "UNL" to "uefa.nations",
        "U17WC" to "fifa.world.u17",
    )

    private val codeBySlug = slugByCode.entries.associate { (k, v) -> v to k }

    val standingsCodes = listOf("UEL", "UECL", "ACL", "J1", "AL", "MLS")

    fun slugForCode(code: String): String? = slugByCode[code.uppercase()]

    fun codeForSlug(slug: String?): String? = slug?.let { codeBySlug[it.trim()] }
}

@Serializable
data class EspnScoreboardResponse(
    val events: List<EspnEventDto> = emptyList(),
)

@Serializable
data class EspnEventDto(
    val id: String = "",
    val date: String = "",
    val league: EspnEventLeagueDto? = null,
    val status: EspnStatusDto? = null,
    val competitions: List<EspnCompetitionDto> = emptyList(),
) {
    fun toMatchItem(fallbackSlug: String = ""): MatchItem? {
        if (id.isBlank() || date.isBlank()) return null
        val slug = fallbackSlug.ifBlank { league?.slug.orEmpty() }.trim()
        val compCode = EspnLeagueCatalog.codeForSlug(slug) ?: return null
        val meta = CompetitionCatalog.findByCode(compCode)
        val comp = competitions.firstOrNull() ?: return null
        val homeC = comp.competitors.firstOrNull { it.homeAway == "home" } ?: comp.competitors.getOrNull(0)
        val awayC = comp.competitors.firstOrNull { it.homeAway == "away" } ?: comp.competitors.getOrNull(1)
        val homeName = homeC?.team?.displayName?.ifBlank { homeC.team.name } ?: "TBD"
        val awayName = awayC?.team?.displayName?.ifBlank { awayC.team.name } ?: "TBD"
        val (statusEnum, elapsed) = parseEspnStatus(comp.status ?: status)
        val showScore = statusEnum.isLive || statusEnum == MatchStatus.FINISHED
        val homeScore = if (showScore) homeC?.score?.toIntOrNull() else null
        val awayScore = if (showScore) awayC?.score?.toIntOrNull() else null
        val homeLogo = homeC?.team?.logo?.ifBlank { null } ?: homeC?.team?.logos?.firstOrNull()?.href.orEmpty()
        val awayLogo = awayC?.team?.logo?.ifBlank { null } ?: awayC?.team?.logos?.firstOrNull()?.href.orEmpty()

        return MatchItem(
            id = "espn-$id",
            competitionCode = compCode,
            competitionName = meta?.enName ?: league?.name ?: compCode,
            competitionZhName = meta?.zhName ?: compCode,
            matchday = null,
            stage = if (compCode in CompetitionCatalog.internationalCodes) "INTERNATIONAL" else "REGULAR_SEASON",
            utcDate = date,
            status = statusEnum,
            homeTeamId = homeC?.team?.id?.toIntOrNull() ?: 0,
            homeTeamName = homeName,
            homeTeamShortName = homeC?.team?.shortDisplayName?.ifBlank { homeName } ?: homeName,
            homeTeamZhName = TeamLocalization.localizeTeam(homeName, homeC?.team?.shortDisplayName),
            awayTeamId = awayC?.team?.id?.toIntOrNull() ?: 0,
            awayTeamName = awayName,
            awayTeamShortName = awayC?.team?.shortDisplayName?.ifBlank { awayName } ?: awayName,
            awayTeamZhName = TeamLocalization.localizeTeam(awayName, awayC?.team?.shortDisplayName),
            homeScore = homeScore,
            awayScore = awayScore,
            venue = comp.venue?.fullName.orEmpty(),
            lastUpdatedUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            homeTeamCrest = TeamAssetCatalog.resolveTeamLogo(homeName, homeLogo),
            awayTeamCrest = TeamAssetCatalog.resolveTeamLogo(awayName, awayLogo),
            elapsedMinutes = elapsed,
            espnSlug = slug,
            espnEventId = id,
        )
    }

    companion object {
        fun parseEspnStatus(st: EspnStatusDto?): Pair<MatchStatus, Int?> {
            val state = st?.type?.state?.lowercase().orEmpty()
            val name = st?.type?.name?.uppercase().orEmpty()
            val elapsed = st?.displayClock
                ?.substringBefore("'")
                ?.replace("+", "")
                ?.trim()
                ?.toIntOrNull()
            return when {
                "HALFTIME" in name -> MatchStatus.PAUSED to elapsed
                "POSTPONED" in name -> MatchStatus.POSTPONED to null
                "CANCELED" in name || "CANCELLED" in name -> MatchStatus.CANCELLED to null
                "SUSPENDED" in name -> MatchStatus.SUSPENDED to elapsed
                state == "in" -> MatchStatus.LIVE to elapsed
                state == "post" -> MatchStatus.FINISHED to elapsed
                else -> MatchStatus.TIMED to null
            }
        }
    }
}

@Serializable
data class EspnEventLeagueDto(
    val slug: String = "",
    val name: String = "",
)

@Serializable
data class EspnStatusDto(
    val displayClock: String = "",
    val type: EspnStatusTypeDto? = null,
)

@Serializable
data class EspnStatusTypeDto(
    val state: String = "",
    val name: String = "",
)

@Serializable
data class EspnCompetitionDto(
    val status: EspnStatusDto? = null,
    val venue: EspnVenueDto? = null,
    val competitors: List<EspnCompetitorDto> = emptyList(),
)

@Serializable
data class EspnVenueDto(
    val fullName: String = "",
)

@Serializable
data class EspnCompetitorDto(
    val homeAway: String = "",
    val score: String = "",
    val team: EspnTeamStubDto? = null,
)

@Serializable
data class EspnTeamStubDto(
    val id: String = "",
    val name: String = "",
    val displayName: String = "",
    val shortDisplayName: String = "",
    val logo: String = "",
    val logos: List<EspnLogoDto> = emptyList(),
)

@Serializable
data class EspnLogoDto(
    val href: String = "",
)

@Serializable
data class EspnSummaryResponse(
    val header: EspnSummaryHeaderDto? = null,
    val keyEvents: List<EspnKeyEventDto> = emptyList(),
    val rosters: List<EspnRosterSideDto> = emptyList(),
    val boxscore: EspnBoxscoreDto? = null,
) {
    fun toLiveDetail(matchId: String, eventId: String): LiveMatchDetail {
        val comp = header?.competitions?.firstOrNull()
        val homeC = comp?.competitors?.firstOrNull { it.homeAway == "home" } ?: comp?.competitors?.getOrNull(0)
        val awayC = comp?.competitors?.firstOrNull { it.homeAway == "away" } ?: comp?.competitors?.getOrNull(1)
        val homeId = homeC?.team?.id.orEmpty()
        val homeName = homeC?.team?.displayName.orEmpty()
        val (statusEnum, elapsed) = EspnEventDto.parseEspnStatus(comp?.status)
        val showScore = statusEnum.isLive || statusEnum == MatchStatus.FINISHED
        val hScore = if (showScore) homeC?.score?.toIntOrNull() else null
        val aScore = if (showScore) awayC?.score?.toIntOrNull() else null

        val parsedEvents = keyEvents.mapNotNull { ke ->
            val rawType = ke.type?.text?.trim().orEmpty()
            val low = rawType.lowercase()
            if (listOf("goal", "card", "substitution", "penalty").none { it in low }) return@mapNotNull null

            val clockStr = ke.clock?.displayValue.orEmpty().trim()
            var mins = 0
            var extra: Int? = null
            if ("'" in clockStr) {
                val partsC = clockStr.replace("'", "").split("+")
                mins = partsC.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
                extra = partsC.getOrNull(1)?.trim()?.toIntOrNull()
            }
            val tName = ke.team?.displayName?.ifBlank { ke.team.name }.orEmpty()
            val pName = ke.participants.getOrNull(0)?.athlete?.displayName.orEmpty()
            if (pName.isBlank()) return@mapNotNull null
            val aName = ke.participants.getOrNull(1)?.athlete?.displayName?.takeIf { it.isNotBlank() }

            val evType: String
            val evDetail: String
            when {
                "substitution" in low -> {
                    evType = "subst"
                    evDetail = "换人调整"
                }
                "red" in low -> {
                    evType = "Card"
                    evDetail = "红牌"
                }
                "yellow" in low -> {
                    evType = "Card"
                    evDetail = "黄牌"
                }
                else -> {
                    evType = "Goal"
                    evDetail = when {
                        "penalty" in low -> "点球"
                        "own" in low -> "乌龙球"
                        else -> "进球"
                    }
                }
            }

            MatchEventItem(
                elapsed = mins,
                extra = extra,
                teamName = tName,
                teamZhName = TeamLocalization.localizeTeam(tName),
                isHomeTeam = (homeId.isNotBlank() && ke.team?.id == homeId) || tName.equals(homeName, true),
                playerName = pName,
                playerZhName = TeamLocalization.localizePlayer(pName).first,
                assistName = aName,
                assistZhName = aName?.let { TeamLocalization.localizePlayer(it).first },
                type = evType,
                detail = evDetail,
            )
        }

        val parsedLineups = rosters.map { r ->
            val tName = r.team?.displayName?.ifBlank { r.team.name }.orEmpty()
            val starters = mutableListOf<LineupPlayerItem>()
            val subs = mutableListOf<LineupPlayerItem>()
            r.roster.forEach { slot ->
                val pn = slot.athlete?.displayName?.trim().orEmpty()
                if (pn.isNotBlank()) {
                    val item = LineupPlayerItem(
                        name = pn,
                        zhName = TeamLocalization.localizePlayer(pn).first,
                        number = slot.jersey.toIntOrNull() ?: 0,
                        pos = slot.position?.abbreviation.orEmpty(),
                    )
                    if (slot.starter) starters += item else subs += item
                }
            }
            MatchLineupSide(
                teamName = tName,
                teamZhName = TeamLocalization.localizeTeam(tName),
                formation = r.formation.orEmpty(),
                coachName = "",
                coachZhName = "",
                startXI = starters,
                substitutes = subs,
            )
        }

        val statTeams = boxscore?.teams.orEmpty()
        val parsedStats = if (statTeams.size >= 2) {
            val firstIsHome = statTeams[0].team?.displayName.equals(homeName, true) || statTeams[0].team?.id == homeId
            val homeTeamStats = (if (firstIsHome) statTeams[0] else statTeams[1]).statistics.associate { it.name to it.displayValue }
            val awayTeamStats = (if (firstIsHome) statTeams[1] else statTeams[0]).statistics.associate { it.name to it.displayValue }
            listOf(
                "possessionPct" to "控球率",
                "totalShots" to "射门次数",
                "shotsOnTarget" to "射正次数",
                "wonCorners" to "角球",
                "passPct" to "传球成功率",
                "foulsCommitted" to "犯规",
                "yellowCards" to "黄牌",
                "offsides" to "越位",
            ).mapNotNull { (key, zhLabel) ->
                val rawH = homeTeamStats[key]
                val rawA = awayTeamStats[key]
                if (rawH != null && rawA != null) {
                    MatchStatPair(
                        labelZh = zhLabel,
                        homeValue = formatStat(key, rawH),
                        awayValue = formatStat(key, rawA),
                    )
                } else {
                    null
                }
            }
        } else {
            emptyList()
        }

        return LiveMatchDetail(
            matchId = matchId,
            fixtureId = eventId.toIntOrNull() ?: 0,
            elapsedMinutes = elapsed,
            statusShort = statusEnum.name,
            homeScore = hScore,
            awayScore = aScore,
            events = parsedEvents,
            lineups = parsedLineups,
            stats = parsedStats,
            syncedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun formatStat(key: String, raw: String): String {
        if (key == "possessionPct") return if (raw.endsWith("%")) raw else "$raw%"
        if (key == "passPct") {
            val d = raw.removeSuffix("%").toDoubleOrNull() ?: return raw
            return if (d <= 1.0) "${(d * 100).roundToInt()}%" else "${d.roundToInt()}%"
        }
        return raw
    }
}

@Serializable
data class EspnSummaryHeaderDto(
    val competitions: List<EspnCompetitionDto> = emptyList(),
)

@Serializable
data class EspnKeyEventDto(
    val type: EspnKeyEventTypeDto? = null,
    val clock: EspnClockDto? = null,
    val team: EspnTeamStubDto? = null,
    val participants: List<EspnParticipantDto> = emptyList(),
)

@Serializable
data class EspnKeyEventTypeDto(
    val text: String = "",
)

@Serializable
data class EspnClockDto(
    val displayValue: String = "",
)

@Serializable
data class EspnParticipantDto(
    val athlete: EspnAthleteStubDto? = null,
)

@Serializable
data class EspnAthleteStubDto(
    val displayName: String = "",
)

@Serializable
data class EspnRosterSideDto(
    val team: EspnTeamStubDto? = null,
    val formation: String? = null,
    val roster: List<EspnRosterSlotDto> = emptyList(),
)

@Serializable
data class EspnRosterSlotDto(
    val starter: Boolean = false,
    val jersey: String = "",
    val athlete: EspnAthleteStubDto? = null,
    val position: EspnPositionDto? = null,
)

@Serializable
data class EspnPositionDto(
    val abbreviation: String = "",
    val displayName: String = "",
    val name: String = "",
)

@Serializable
data class EspnBoxscoreDto(
    val teams: List<EspnBoxscoreTeamDto> = emptyList(),
)

@Serializable
data class EspnBoxscoreTeamDto(
    val team: EspnTeamStubDto? = null,
    val statistics: List<EspnBoxscoreStatDto> = emptyList(),
)

@Serializable
data class EspnBoxscoreStatDto(
    val name: String = "",
    val displayValue: String = "",
)

@Serializable
data class EspnStandingsResponse(
    val children: List<EspnStandingChildDto> = emptyList(),
    val standings: EspnStandingTableDto? = null,
) {
    fun toStandingEntries(compCode: String): List<StandingEntry> {
        val groups = children.ifEmpty { listOf(EspnStandingChildDto(name = "", standings = standings)) }
        val rows = mutableListOf<StandingEntry>()
        for (grp in groups) {
            val grpZh = TeamLocalization.localizeEspnGroup(grp.name)
            val entries = grp.standings?.entries.orEmpty()
            val total = entries.size
            entries.forEachIndexed { idx, entry ->
                val tName = entry.team?.displayName?.ifBlank { entry.team.name } ?: "Unknown"
                val tShort = entry.team?.shortDisplayName?.ifBlank { tName } ?: tName
                val crest = entry.team?.logos?.firstOrNull()?.href.orEmpty()
                val statsMap = entry.stats.associateBy { it.name }
                fun sInt(k: String, def: Int = 0): Int {
                    val obj = statsMap[k] ?: return def
                    return obj.value?.roundToInt()
                        ?: obj.displayValue.replace("+", "").trim().toIntOrNull()
                        ?: def
                }
                val rank = sInt("rank", idx + 1)
                val (zType, zLabel) = TeamLocalization.localizeEspnZone(entry.note?.description, compCode, rank, total)
                rows += StandingEntry(
                    competitionCode = compCode,
                    rank = rank,
                    teamName = tName,
                    teamZhName = TeamLocalization.localizeTeam(tName, tShort),
                    teamCrest = TeamAssetCatalog.resolveTeamLogo(tName, crest),
                    played = sInt("gamesPlayed"),
                    won = sInt("wins"),
                    draw = sInt("ties"),
                    lost = sInt("losses"),
                    goalDifference = sInt("pointDifferential"),
                    points = sInt("points"),
                    form = "",
                    zoneType = zType,
                    zoneLabel = zLabel,
                    groupName = grpZh,
                )
            }
        }
        return rows
    }
}

@Serializable
data class EspnStandingChildDto(
    val name: String = "",
    val standings: EspnStandingTableDto? = null,
)

@Serializable
data class EspnStandingTableDto(
    val entries: List<EspnStandingEntryDto> = emptyList(),
)

@Serializable
data class EspnStandingEntryDto(
    val team: EspnTeamStubDto? = null,
    val note: EspnStandingNoteDto? = null,
    val stats: List<EspnStandingStatDto> = emptyList(),
)

@Serializable
data class EspnStandingNoteDto(
    val description: String = "",
)

@Serializable
data class EspnStandingStatDto(
    val name: String = "",
    val value: Double? = null,
    val displayValue: String = "",
)

@Serializable
data class EspnTeamsResponse(
    val sports: List<EspnSportDto> = emptyList(),
)

@Serializable
data class EspnSportDto(
    val leagues: List<EspnSportLeagueDto> = emptyList(),
)

@Serializable
data class EspnSportLeagueDto(
    val teams: List<EspnTeamWrapperDto> = emptyList(),
)

@Serializable
data class EspnTeamWrapperDto(
    val team: EspnTeamStubDto? = null,
)

@Serializable
data class EspnTeamRosterResponse(
    val athletes: List<EspnRosterAthleteDto> = emptyList(),
) {
    fun toTeamDetail(
        teamId: Int,
        compCode: String,
        teamName: String,
        shortName: String,
        crest: String,
        espnTeamId: String = "",
        espnSlug: String = "",
    ): TeamDetailInfo = TeamDetailInfo(
        teamId = teamId,
        competitionCode = compCode,
        name = teamName,
        shortName = shortName.ifBlank { teamName },
        zhName = TeamLocalization.localizeTeam(teamName, shortName),
        crest = TeamAssetCatalog.resolveTeamLogo(teamName, crest),
        venue = "",
        founded = 0,
        clubColors = "",
        coachName = "",
        coachZhName = "",
        coachNationality = "",
        squad = athletes.mapNotNull { a ->
            val pn = a.displayName.ifBlank { a.fullName }.trim()
            if (pn.isBlank()) return@mapNotNull null
            val (pZh, tr) = TeamLocalization.localizePlayer(pn)
            val posEn = a.position?.displayName?.ifBlank { a.position.name }.orEmpty()
            SquadMember(
                id = "espnp-${a.id.ifBlank { pn.hashCode().toString() }}",
                name = pn,
                zhName = pZh,
                isTransliterated = tr,
                position = posEn,
                positionZh = TeamLocalization.localizePosition(posEn),
                nationality = a.citizenship,
                birthYear = if (a.age > 10) (2026 - a.age) else 0,
                shirtNumber = a.jersey.toIntOrNull() ?: 0,
                portraitUrl = a.headshot?.href.orEmpty(),
            )
        },
        espnTeamId = espnTeamId,
        espnSlug = espnSlug,
    )
}

@Serializable
data class EspnRosterAthleteDto(
    val id: String = "",
    val fullName: String = "",
    val displayName: String = "",
    val age: Int = 0,
    val jersey: String = "",
    val citizenship: String = "",
    val position: EspnPositionDto? = null,
    val headshot: EspnLogoDto? = null,
)

@Serializable
data class EspnTeamScheduleResponse(
    val events: List<EspnScheduleEventDto> = emptyList(),
)

@Serializable
data class EspnScheduleEventDto(
    val id: String = "",
    val date: String = "",
    val league: EspnEventLeagueDto? = null,
    val competitions: List<EspnScheduleCompetitionDto> = emptyList(),
) {
    fun toMatchItem(fallbackSlug: String = ""): MatchItem? {
        if (id.isBlank() || date.isBlank()) return null
        val slug = league?.slug?.ifBlank { null } ?: fallbackSlug
        val compCode = EspnLeagueCatalog.codeForSlug(slug) ?: CompetitionCatalog.findByNameOrAlias(league?.name)?.code ?: "OTHER"
        val meta = CompetitionCatalog.findByCode(compCode)
        val comp = competitions.firstOrNull() ?: return null
        val homeC = comp.competitors.firstOrNull { it.homeAway == "home" } ?: comp.competitors.getOrNull(0)
        val awayC = comp.competitors.firstOrNull { it.homeAway == "away" } ?: comp.competitors.getOrNull(1)
        val homeName = homeC?.team?.displayName?.ifBlank { homeC.team.name } ?: "TBD"
        val awayName = awayC?.team?.displayName?.ifBlank { awayC.team.name } ?: "TBD"
        val (statusEnum, elapsed) = EspnEventDto.parseEspnStatus(comp.status)
        val showScore = statusEnum.isLive || statusEnum == MatchStatus.FINISHED
        val homeScore = if (showScore) extractScore(homeC?.score) else null
        val awayScore = if (showScore) extractScore(awayC?.score) else null
        val homeLogo = homeC?.team?.logo?.ifBlank { null } ?: homeC?.team?.logos?.firstOrNull()?.href.orEmpty()
        val awayLogo = awayC?.team?.logo?.ifBlank { null } ?: awayC?.team?.logos?.firstOrNull()?.href.orEmpty()

        return MatchItem(
            id = "espn-$id",
            competitionCode = compCode,
            competitionName = meta?.enName ?: league?.name?.ifBlank { compCode } ?: compCode,
            competitionZhName = meta?.zhName ?: league?.name?.ifBlank { compCode } ?: compCode,
            matchday = null,
            stage = if (compCode in CompetitionCatalog.internationalCodes) "INTERNATIONAL" else "REGULAR_SEASON",
            utcDate = date,
            status = statusEnum,
            homeTeamId = homeC?.team?.id?.toIntOrNull() ?: 0,
            homeTeamName = homeName,
            homeTeamShortName = homeC?.team?.shortDisplayName?.ifBlank { homeName } ?: homeName,
            homeTeamZhName = TeamLocalization.localizeTeam(homeName, homeC?.team?.shortDisplayName),
            awayTeamId = awayC?.team?.id?.toIntOrNull() ?: 0,
            awayTeamName = awayName,
            awayTeamShortName = awayC?.team?.shortDisplayName?.ifBlank { awayName } ?: awayName,
            awayTeamZhName = TeamLocalization.localizeTeam(awayName, awayC?.team?.shortDisplayName),
            homeScore = homeScore,
            awayScore = awayScore,
            venue = comp.venue?.fullName.orEmpty(),
            lastUpdatedUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            homeTeamCrest = TeamAssetCatalog.resolveTeamLogo(homeName, homeLogo),
            awayTeamCrest = TeamAssetCatalog.resolveTeamLogo(awayName, awayLogo),
            elapsedMinutes = elapsed,
            espnSlug = slug.ifBlank { null },
            espnEventId = id,
        )
    }

    private fun extractScore(el: JsonElement?): Int? {
        if (el == null) return null
        return when (el) {
            is JsonPrimitive -> el.intOrNull ?: el.doubleOrNull?.toInt() ?: el.contentOrNull?.toIntOrNull()
            is JsonObject -> {
                val dv = (el["displayValue"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                val v = (el["value"] as? JsonPrimitive)?.doubleOrNull?.toInt()
                dv ?: v
            }
            else -> null
        }
    }
}

@Serializable
data class EspnScheduleCompetitionDto(
    val status: EspnStatusDto? = null,
    val venue: EspnVenueDto? = null,
    val competitors: List<EspnScheduleCompetitorDto> = emptyList(),
)

@Serializable
data class EspnScheduleCompetitorDto(
    val homeAway: String = "",
    val score: JsonElement? = null,
    val team: EspnTeamStubDto? = null,
)
