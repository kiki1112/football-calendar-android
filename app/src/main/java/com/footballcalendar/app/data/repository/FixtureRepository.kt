package com.footballcalendar.app.data.repository

import com.footballcalendar.app.data.local.MatchDao
import com.footballcalendar.app.data.local.MatchEntity
import com.footballcalendar.app.data.local.StandingDao
import com.footballcalendar.app.data.local.StandingEntity
import com.footballcalendar.app.data.local.TeamDao
import com.footballcalendar.app.data.local.TeamEntity
import com.footballcalendar.app.data.preferences.UserPreferencesRepository
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
    private var lastLiveSweepMillis: Long = 0L

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
     * Fetches live scores worldwide in 1 API-Football request (throttled to min 30s)
     * and updates matching fixtures inside Room.
     */
    suspend fun refreshLiveScores(force: Boolean = false): Result<Int> {
        return runCatching {
            val prefs = prefsRepo.currentPreferences()
            val afKey = prefs.effectiveApiFootballKey()
            require(afKey.isNotEmpty()) { "未配置 API-Football 密钥，无法拉取实时比分" }

            val nowMs = System.currentTimeMillis()
            if (!force && nowMs - lastLiveSweepMillis < 30_000L) {
                return@runCatching 0
            }
            lastLiveSweepMillis = nowMs

            val resp = apiService.fetchApiFootballFixtures(
                url = "https://v3.football.api-sports.io/fixtures?live=all",
                apiKey = afKey,
            )
            val liveItems = resp.response.mapNotNull { it.toMatchItem() }
            if (liveItems.isEmpty()) return@runCatching 0

            val existing = matchDao.getAllMatchesOnce().map { it.toDomain() }.toMutableList()
            var updatedCount = 0
            for (live in liveItems) {
                val idx = existing.indexOfFirst {
                    it.apiFootballFixtureId == live.apiFootballFixtureId ||
                        it.id == live.id ||
                        (it.homeTeamZhName == live.homeTeamZhName && it.awayTeamZhName == live.awayTeamZhName)
                }
                if (idx >= 0) {
                    val cur = existing[idx]
                    existing[idx] = cur.copy(
                        status = live.status,
                        homeScore = live.homeScore,
                        awayScore = live.awayScore,
                        elapsedMinutes = live.elapsedMinutes,
                        apiFootballFixtureId = live.apiFootballFixtureId,
                        lastUpdatedUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                    )
                    updatedCount++
                } else if (live.competitionCode in prefs.followedCompetitions) {
                    existing += live
                    updatedCount++
                }
            }
            if (updatedCount > 0) {
                matchDao.upsertAll(existing.map { MatchEntity.fromDomain(it) })
            }
            updatedCount
        }
    }

    /**
     * Fetches real-time events (goals, assists, cards, substitutions), lineups, and statistics
     * for an opened match using API-Football `fixtures?id=...` (1 API call, cached 45s while live).
     */
    suspend fun fetchMatchLiveDetail(match: MatchItem, forceRefresh: Boolean = false): Result<LiveMatchDetail> {
        return runCatching {
            val cached = liveDetailCache[match.id]
            val nowMs = System.currentTimeMillis()
            if (!forceRefresh && cached != null) {
                val freshWindow = if (match.status == MatchStatus.FINISHED) 24 * 60 * 60 * 1000L else 45_000L
                if (nowMs - cached.syncedAtMillis < freshWindow) {
                    return@runCatching cached
                }
            }

            val prefs = prefsRepo.currentPreferences()
            val afKey = prefs.effectiveApiFootballKey()
            require(afKey.isNotEmpty()) { "未配置 API-Football 实时事件接口密钥" }

            var targetFixtureId = match.apiFootballFixtureId
                ?: match.id.removePrefix("af-").toIntOrNull()

            // If match came from Football-Data without apiFootballFixtureId, try matching today/match date once
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
                    val matched = dayResp.response.firstOrNull { row ->
                        val hZh = TeamLocalization.localizeTeam(row.teams.home.name)
                        val aZh = TeamLocalization.localizeTeam(row.teams.away.name)
                        hZh == match.homeTeamZhName && aZh == match.awayTeamZhName
                    }
                    targetFixtureId = matched?.fixture?.id
                }
            }

            require(targetFixtureId != null && targetFixtureId > 0) {
                "该场次暂无可用的实时事件编号（仅近期/进行中/已完赛赛事支持）"
            }

            val detailResp = apiService.fetchApiFootballFixtures(
                url = "https://v3.football.api-sports.io/fixtures?id=$targetFixtureId",
                apiKey = afKey,
            )
            val row = detailResp.response.firstOrNull()
                ?: error("未返回比赛过程详情")
            val detail = row.toLiveDetail(match.id)
            liveDetailCache[match.id] = detail

            // Also persist updated score/elapsed back into Room MatchEntity
            val liveMatch = row.toMatchItem()
            val all = matchDao.getAllMatchesOnce().map { it.toDomain() }
            val idx = all.indexOfFirst { it.id == match.id }
            if (idx >= 0) {
                val cur = all[idx]
                val updated = cur.copy(
                    status = liveMatch?.status ?: cur.status,
                    homeScore = detail.homeScore ?: cur.homeScore,
                    awayScore = detail.awayScore ?: cur.awayScore,
                    elapsedMinutes = detail.elapsedMinutes ?: cur.elapsedMinutes,
                    apiFootballFixtureId = targetFixtureId,
                )
                matchDao.upsertAll(listOf(MatchEntity.fromDomain(updated)))
            }
            detail
        }
    }

    suspend fun fetchTeamDetailOnDemand(
        teamName: String,
        teamId: Int,
        compCode: String,
        crest: String,
    ): Result<TeamDetailInfo?> {
        return runCatching {
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
