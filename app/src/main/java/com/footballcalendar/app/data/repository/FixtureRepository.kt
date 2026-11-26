package com.footballcalendar.app.data.repository

import com.footballcalendar.app.data.local.MatchDao
import com.footballcalendar.app.data.local.MatchEntity
import com.footballcalendar.app.data.local.StandingDao
import com.footballcalendar.app.data.local.StandingEntity
import com.footballcalendar.app.data.local.TeamDao
import com.footballcalendar.app.data.local.TeamEntity
import com.footballcalendar.app.data.preferences.UserPreferencesRepository
import com.footballcalendar.app.data.remote.EspnLeagueCatalog
import com.footballcalendar.app.data.remote.FeedApiService
import com.footballcalendar.app.data.remote.FixtureDataSource
import com.footballcalendar.app.model.DataSourceMode
import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.SquadMember
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.StarPlayer
import com.footballcalendar.app.model.SyncSummary
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.util.MatchTimeFormatter
import com.footballcalendar.app.util.ScheduleDiffEngine
import com.footballcalendar.app.util.TeamAssetCatalog
import com.footballcalendar.app.util.TeamLocalization
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class FixtureRepository(
    private val matchDao: MatchDao,
    private val standingDao: StandingDao,
    private val teamDao: TeamDao,
    private val prefsRepo: UserPreferencesRepository,
    private val dataSource: FixtureDataSource,
    private val apiService: FeedApiService,
    private val json: Json,
) {
    private val _starPlayers = MutableStateFlow(TeamAssetCatalog.starPlayers)
    val starPlayersFlow: Flow<List<StarPlayer>> = _starPlayers.asStateFlow()

    private val liveDetailCache = linkedMapOf<String, LiveMatchDetail>()
    private var lastAfLiveSweepMillis: Long = 0L

    fun getCachedLiveDetail(matchId: String): LiveMatchDetail? = liveDetailCache[matchId]

    fun observeAllMatches(): Flow<List<MatchItem>> =
        matchDao.observeAllMatches().map { list -> list.map { it.toDomain() } }

    fun observeAllStandings(): Flow<List<StandingEntry>> =
        standingDao.observeAllStandings().map { list -> list.map { it.toDomain() } }

    fun observeAllTeams(): Flow<List<TeamDetailInfo>> =
        teamDao.observeAllTeams().map { list -> list.map { it.toDomain(json) } }

    suspend fun getAllMatchesOnce(): List<MatchItem> =
        matchDao.getAllMatchesOnce().map { it.toDomain() }

    suspend fun ensureSeededIfEmpty() {
        if (matchDao.getAllMatchesOnce().isEmpty() || standingDao.getAllStandingsOnce().isEmpty()) {
            runCatching { syncFixtures() }
        }
    }

    suspend fun syncFixtures(zoneId: ZoneId = ZoneId.systemDefault()): Result<SyncSummary> {
        return runCatching {
            val prefs = prefsRepo.currentPreferences()
            val modeChanged = prefs.lastSyncedMode.isNotBlank() &&
                prefs.lastSyncedMode != prefs.dataSourceMode.name

            val rawExisting = matchDao.getAllMatchesOnce().map { it.toDomain() }
            val existing = when {
                modeChanged -> emptyList()
                prefs.dataSourceMode != DataSourceMode.DEMO ->
                    rawExisting.filter { !it.id.startsWith("demo-") }
                else -> rawExisting
            }

            val payload = dataSource.fetchAll(prefs)

            val diffResult = ScheduleDiffEngine.diff(
                existing = existing,
                incoming = payload.matches,
                zoneId = zoneId,
            )

            matchDao.replaceAll(diffResult.mergedMatches.map { MatchEntity.fromDomain(it) })

            var standingsSyncedMillis: Long? = null
            if (payload.standings.isNotEmpty()) {
                standingDao.replaceAll(payload.standings.map { StandingEntity.fromDomain(it) })
                standingsSyncedMillis = System.currentTimeMillis()
            } else if (modeChanged) {
                standingDao.clearAll()
            }

            if (payload.teams.isNotEmpty()) {
                teamDao.replaceAll(payload.teams.map { TeamEntity.fromDomain(it, json) })
            } else if (modeChanged) {
                teamDao.clearAll()
            }

            if (payload.players.isNotEmpty()) {
                _starPlayers.value = payload.players
            }
            if (payload.liveDetails.isNotEmpty()) {
                liveDetailCache.putAll(payload.liveDetails)
            }

            val nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            val summaryText = if (diffResult.changes.isEmpty()) {
                "已同步 ${diffResult.mergedMatches.size} 场赛程"
            } else {
                "已同步 ${diffResult.mergedMatches.size} 场 · ${diffResult.changes.size} 项更新"
            }
            prefsRepo.recordSyncResult(
                syncedAtUtc = nowIso,
                summary = summaryText,
                sourceMode = prefs.dataSourceMode,
                standingsSyncedAtMillis = standingsSyncedMillis,
            )

            SyncSummary(
                syncedAtUtc = nowIso,
                totalMatches = diffResult.mergedMatches.size,
                changes = diffResult.changes,
                sourceMode = prefs.dataSourceMode,
                message = summaryText,
            )
        }
    }

    /**
     * Zero-quota, near-zero-latency live score refresh using ESPN `all/scoreboard` first,
     * supplementing with API-Football `fixtures?live=all` only when non-ESPN leagues (J2/KL1/U20AC)
     * have active windows (throttled to >= 180s to protect the 100/day Free plan quota).
     */
    suspend fun refreshLiveScoresEspnFirst(): Result<Int> {
        return runCatching {
            val prefs = prefsRepo.currentPreferences()
            if (prefs.dataSourceMode == DataSourceMode.DEMO) return@runCatching 0

            val existing = matchDao.getAllMatchesOnce().map { it.toDomain() }.toMutableList()
            val changedItems = mutableListOf<MatchItem>()

            // 1. Free ESPN global live scoreboard (0 quota, fast real-time updates)
            runCatching {
                val espnResp = apiService.fetchEspnScoreboard(
                    "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard",
                )
                val espnItems = espnResp.events.mapNotNull { it.toMatchItem() }
                for (live in espnItems) {
                    applyLiveUpdate(live, existing, prefs.followedCompetitions, changedItems)
                }
            }

            // 2. Smart quota-safe API-Football supplement ONLY if non-ESPN leagues have live/kickoff windows
            val afOnlyCodes = setOf("J2", "KL1", "U20AC", "INT")
            val nowInstant = Instant.now()
            val hasActiveAfWindow = existing.any { m ->
                m.competitionCode in afOnlyCodes && (
                    m.status.isLive || MatchTimeFormatter.parseInstant(m.utcDate)?.let { ko ->
                        val mins = Duration.between(ko, nowInstant).toMinutes()
                        mins in -15..165
                    } == true
                )
            }
            val afKey = prefs.effectiveApiFootballKey()
            val nowMs = System.currentTimeMillis()
            if (afKey.isNotEmpty() && hasActiveAfWindow && (nowMs - lastAfLiveSweepMillis) >= 180_000L) {
                lastAfLiveSweepMillis = nowMs
                runCatching {
                    val afResp = apiService.fetchApiFootballFixtures(
                        url = "https://v3.football.api-sports.io/fixtures?live=all",
                        apiKey = afKey,
                    )
                    afResp.response.mapNotNull { it.toMatchItem() }.forEach { live ->
                        applyLiveUpdate(live, existing, prefs.followedCompetitions, changedItems)
                    }
                }
            }

            if (changedItems.isNotEmpty()) {
                matchDao.upsertAll(changedItems.distinctBy { it.id }.map { MatchEntity.fromDomain(it) })
            }
            changedItems.size
        }
    }

    private fun applyLiveUpdate(
        live: MatchItem,
        existing: MutableList<MatchItem>,
        followedCodes: Set<String>,
        changedOut: MutableList<MatchItem>,
    ) {
        val idx = existing.indexOfFirst {
            (live.espnEventId != null && it.espnEventId == live.espnEventId) ||
                (live.apiFootballFixtureId != null && it.apiFootballFixtureId == live.apiFootballFixtureId) ||
                it.id == live.id ||
                (it.competitionCode == live.competitionCode &&
                    it.homeTeamZhName == live.homeTeamZhName &&
                    it.awayTeamZhName == live.awayTeamZhName)
        }
        if (idx >= 0) {
            val cur = existing[idx]
            val newStatus = if (live.status.isLive || live.status == MatchStatus.FINISHED) live.status else cur.status
            val newH = live.homeScore ?: cur.homeScore
            val newA = live.awayScore ?: cur.awayScore
            val newElapsed = live.elapsedMinutes ?: cur.elapsedMinutes
            val newEspnSlug = live.espnSlug ?: cur.espnSlug
            val newEspnEventId = live.espnEventId ?: cur.espnEventId
            val newAfId = live.apiFootballFixtureId ?: cur.apiFootballFixtureId
            if (newStatus != cur.status || newH != cur.homeScore || newA != cur.awayScore ||
                newElapsed != cur.elapsedMinutes || newEspnSlug != cur.espnSlug ||
                newEspnEventId != cur.espnEventId || newAfId != cur.apiFootballFixtureId
            ) {
                val updated = cur.copy(
                    status = newStatus,
                    homeScore = newH,
                    awayScore = newA,
                    elapsedMinutes = newElapsed,
                    espnSlug = newEspnSlug,
                    espnEventId = newEspnEventId,
                    apiFootballFixtureId = newAfId,
                    lastUpdatedUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                )
                existing[idx] = updated
                changedOut += updated
            }
        } else if (live.competitionCode in followedCodes && (live.status.isLive || live.status == MatchStatus.FINISHED)) {
            existing += live
            changedOut += live
        }
    }

    /**
     * Fetches match events, lineups & stats via ESPN `summary` FIRST (free, unlimited, fast),
     * falling back automatically to API-Football `fixtures?id=...` if needed.
     */
    suspend fun fetchMatchLiveDetail(match: MatchItem, forceRefresh: Boolean = false): Result<LiveMatchDetail> {
        return runCatching {
            val cached = liveDetailCache[match.id]
            val nowMs = System.currentTimeMillis()
            if (!forceRefresh && cached != null) {
                val isFinished = cached.statusShort == MatchStatus.FINISHED.name || match.status == MatchStatus.FINISHED
                if (isFinished || (nowMs - cached.syncedAtMillis) < 8_000L) {
                    return@runCatching cached
                }
            }

            // 1. Try ESPN Summary (Free, zero quota)
            val targetSlug = match.espnSlug ?: EspnLeagueCatalog.slugForCode(match.competitionCode)
            var targetEventId = match.espnEventId ?: match.id.removePrefix("espn-").takeIf { match.id.startsWith("espn-") }
            if (targetSlug != null && targetEventId == null) {
                val utcDate = MatchTimeFormatter.parseInstant(match.utcDate)
                    ?.atZone(ZoneOffset.UTC)
                    ?.toLocalDate()
                if (utcDate != null) {
                    val ds = DateTimeFormatter.BASIC_ISO_DATE.format(utcDate)
                    val sb = runCatching {
                        apiService.fetchEspnScoreboard(
                            "https://site.api.espn.com/apis/site/v2/sports/soccer/$targetSlug/scoreboard?dates=$ds",
                        )
                    }.getOrNull()
                    targetEventId = sb?.events?.mapNotNull { it.toMatchItem(targetSlug) }?.firstOrNull { em ->
                        em.homeTeamZhName == match.homeTeamZhName && em.awayTeamZhName == match.awayTeamZhName
                    }?.espnEventId
                }
            }

            if (targetSlug != null && targetEventId != null) {
                val espnDetail = runCatching {
                    val sm = apiService.fetchEspnSummary(
                        "https://site.api.espn.com/apis/site/v2/sports/soccer/$targetSlug/summary?event=$targetEventId",
                    )
                    sm.toLiveDetail(match.id, targetEventId)
                }.getOrNull()
                if (espnDetail != null) {
                    liveDetailCache[match.id] = espnDetail
                    persistDetailOntoMatch(match.id, espnDetail, espnSlug = targetSlug, espnEventId = targetEventId)
                    return@runCatching espnDetail
                }
            }

            // 2. Fallback to API-Football (e.g. J2 / K League 1 / U20 Asian Cup)
            if (cached != null && (nowMs - cached.syncedAtMillis) < 45_000L) {
                return@runCatching cached
            }
            val prefs = prefsRepo.currentPreferences()
            val afKey = prefs.effectiveApiFootballKey()
            require(afKey.isNotEmpty()) { "该赛事暂不支持细节回放" }

            var targetFixtureId = match.apiFootballFixtureId
                ?: match.id.removePrefix("af-").toIntOrNull()

            if (targetFixtureId == null) {
                val utcDateStr = MatchTimeFormatter.parseInstant(match.utcDate)
                    ?.atZone(ZoneOffset.UTC)
                    ?.toLocalDate()
                    ?.toString()
                if (utcDateStr != null) {
                    val dayResp = apiService.fetchApiFootballFixtures(
                        url = "https://v3.football.api-sports.io/fixtures?date=$utcDateStr",
                        apiKey = afKey,
                    )
                    targetFixtureId = dayResp.response.firstOrNull { row ->
                        TeamLocalization.localizeTeam(row.teams.home.name) == match.homeTeamZhName &&
                            TeamLocalization.localizeTeam(row.teams.away.name) == match.awayTeamZhName
                    }?.fixture?.id
                }
            }

            require(targetFixtureId != null && targetFixtureId > 0) {
                "比赛未开赛或暂无实时事件记录"
            }

            val detailResp = apiService.fetchApiFootballFixtures(
                url = "https://v3.football.api-sports.io/fixtures?id=$targetFixtureId",
                apiKey = afKey,
            )
            val row = detailResp.response.firstOrNull() ?: error("暂无比赛过程详情")
            val detail = row.toLiveDetail(match.id)
            liveDetailCache[match.id] = detail
            persistDetailOntoMatch(match.id, detail, afFixtureId = targetFixtureId)
            detail
        }
    }

    private suspend fun persistDetailOntoMatch(
        matchId: String,
        detail: LiveMatchDetail,
        espnSlug: String? = null,
        espnEventId: String? = null,
        afFixtureId: Int? = null,
    ) {
        val all = matchDao.getAllMatchesOnce().map { it.toDomain() }
        val cur = all.firstOrNull { it.id == matchId } ?: return
        val newStatus = MatchStatus.fromRaw(detail.statusShort).takeIf { it != MatchStatus.UNKNOWN } ?: cur.status
        val updated = cur.copy(
            status = newStatus,
            homeScore = detail.homeScore ?: cur.homeScore,
            awayScore = detail.awayScore ?: cur.awayScore,
            elapsedMinutes = detail.elapsedMinutes ?: cur.elapsedMinutes,
            espnSlug = espnSlug ?: cur.espnSlug,
            espnEventId = espnEventId ?: cur.espnEventId,
            apiFootballFixtureId = afFixtureId ?: cur.apiFootballFixtureId,
        )
        matchDao.upsertAll(listOf(MatchEntity.fromDomain(updated)))
    }

    suspend fun fetchTeamDetailOnDemand(
        teamName: String,
        teamId: Int,
        compCode: String,
        crest: String,
    ): Result<TeamDetailInfo?> {
        return runCatching {
            val espnSlug = EspnLeagueCatalog.slugForCode(compCode)
            if (espnSlug != null && teamId > 0) {
                val roster = runCatching {
                    apiService.fetchEspnRoster(
                        "https://site.api.espn.com/apis/site/v2/sports/soccer/$espnSlug/teams/$teamId/roster",
                    )
                }.getOrNull()
                if (roster != null && roster.athletes.isNotEmpty()) {
                    val detail = roster.toTeamDetail(
                        teamId = teamId,
                        compCode = compCode,
                        teamName = teamName,
                        shortName = teamName,
                        crest = crest,
                    )
                    teamDao.upsertAll(listOf(TeamEntity.fromDomain(detail, json)))
                    return@runCatching detail
                }
            }

            val prefs = prefsRepo.currentPreferences()
            val afKey = prefs.effectiveApiFootballKey()
            if (afKey.isBlank() || teamId <= 0) return@runCatching null

            val infoResp = runCatching {
                apiService.fetchApiFootballTeamInfo("https://v3.football.api-sports.io/teams?id=$teamId", afKey)
            }.getOrNull()?.response?.firstOrNull()
            val coachName = runCatching {
                apiService.fetchApiFootballCoaches("https://v3.football.api-sports.io/coachs?team=$teamId", afKey)
            }.getOrNull()?.response?.firstOrNull()?.name ?: ""
            val squadResp = runCatching {
                apiService.fetchApiFootballSquad("https://v3.football.api-sports.io/players/squads?team=$teamId", afKey)
            }.getOrNull()?.response?.firstOrNull()?.players.orEmpty()

            val detail = TeamDetailInfo(
                teamId = teamId,
                competitionCode = compCode,
                name = infoResp?.team?.name?.ifBlank { teamName } ?: teamName,
                shortName = teamName,
                zhName = TeamLocalization.localizeTeam(teamName),
                crest = infoResp?.team?.logo?.ifBlank { crest } ?: crest,
                venue = infoResp?.venue?.name ?: "",
                founded = infoResp?.team?.founded ?: 0,
                clubColors = "",
                coachName = coachName,
                coachZhName = TeamLocalization.localizeCoach(coachName),
                coachNationality = "",
                squad = squadResp.map { p ->
                    val (zh, tr) = TeamLocalization.localizePlayer(p.name)
                    SquadMember(
                        id = "afp-${p.id}",
                        name = p.name,
                        zhName = zh,
                        isTransliterated = tr,
                        position = p.position ?: "",
                        positionZh = TeamLocalization.localizePosition(p.position),
                        nationality = "",
                        birthYear = p.age?.let { 2026 - it } ?: 0,
                        shirtNumber = p.number ?: 0,
                        portraitUrl = p.photo ?: "",
                    )
                },
            )
            teamDao.upsertAll(listOf(TeamEntity.fromDomain(detail, json)))
            detail
        }
    }

    suspend fun searchExternalPlayer(query: String): Result<StarPlayer?> {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return Result.success(null)

        val localHit = _starPlayers.value.firstOrNull {
            it.zhName.contains(cleaned, ignoreCase = true) ||
                it.name.contains(cleaned, ignoreCase = true)
        }
        if (localHit != null) return Result.success(localHit)

        return runCatching {
            val hasChinese = cleaned.any { it.code in 0x4E00..0x9FFF }
            val encoded = URLEncoder.encode(cleaned.replace(' ', '_'), "UTF-8")

            if (hasChinese) {
                val zhSummary = apiService.fetchWikipediaSummary(
                    "https://zh.wikipedia.org/api/rest_v1/page/summary/$encoded",
                )
                if (zhSummary.title.isNotBlank()) {
                    return@runCatching StarPlayer(
                        id = "wiki-${zhSummary.title.hashCode()}",
                        name = zhSummary.title,
                        zhName = zhSummary.title,
                        clubName = "",
                        clubZhName = "公开百科",
                        clubCrestUrl = "",
                        nationalName = "",
                        nationalZhName = zhSummary.description.ifBlank { "职业足球运动员" },
                        nationalCrestUrl = "",
                        positionZh = zhSummary.description.ifBlank { "足球运动员" },
                        nationalityZh = "国际",
                        shirtNumber = 0,
                        birthYear = 0,
                        portraitUrl = zhSummary.thumbnail?.source ?: "",
                        bio = zhSummary.extract,
                    ).also { created ->
                        _starPlayers.value = (_starPlayers.value + created).distinctBy { it.id }
                    }
                }
            }

            val enSummary = apiService.fetchWikipediaSummary(
                "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded",
            )
            if (enSummary.title.isBlank()) return@runCatching null

            val enTitleEncoded = URLEncoder.encode(enSummary.title, "UTF-8")
            val zhTitle = runCatching {
                val langResp = apiService.fetchWikipediaLanglinks(
                    "https://en.wikipedia.org/w/api.php?action=query&prop=langlinks&lllang=zh&format=json&titles=$enTitleEncoded",
                )
                langResp.query?.pages?.values?.firstOrNull()?.langlinks?.firstOrNull()?.title
            }.getOrNull()

            val zhSummary = if (!zhTitle.isNullOrBlank()) {
                runCatching {
                    val zhSlug = URLEncoder.encode(zhTitle.replace(' ', '_'), "UTF-8")
                    apiService.fetchWikipediaSummary(
                        "https://zh.wikipedia.org/api/rest_v1/page/summary/$zhSlug",
                    )
                }.getOrNull()
            } else {
                null
            }

            val (fallbackZh, tr) = TeamLocalization.localizePlayer(enSummary.title)
            val displayZhName = zhSummary?.title?.ifBlank { null } ?: zhTitle ?: fallbackZh
            val displayDesc = zhSummary?.description?.ifBlank { null } ?: "职业足球运动员"
            val displayBio = zhSummary?.extract?.ifBlank { null } ?: enSummary.extract
            val portrait = enSummary.thumbnail?.source?.ifBlank { null }
                ?: zhSummary?.thumbnail?.source ?: ""

            val created = StarPlayer(
                id = "wiki-${enSummary.title.lowercase().replace(' ', '-')}",
                name = enSummary.title,
                zhName = displayZhName,
                clubName = "",
                clubZhName = "公开百科",
                clubCrestUrl = "",
                nationalName = "",
                nationalZhName = displayDesc,
                nationalCrestUrl = "",
                positionZh = displayDesc,
                nationalityZh = "国际",
                shirtNumber = 0,
                birthYear = 0,
                portraitUrl = portrait,
                bio = displayBio,
                isTransliterated = tr && zhTitle.isNullOrBlank(),
            )
            _starPlayers.value = (_starPlayers.value + created).distinctBy { it.id }
            created
        }
    }
}
