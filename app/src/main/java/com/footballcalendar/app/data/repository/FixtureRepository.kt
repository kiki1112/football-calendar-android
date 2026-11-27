package com.footballcalendar.app.data.repository

import android.content.Context
import android.util.Log
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
import com.footballcalendar.app.data.remote.GitHubFeedResponse
import com.footballcalendar.app.model.DataSourceMode
import com.footballcalendar.app.model.LiveBroadcastAlert
import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchH2HBundle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.URLEncoder
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

class FixtureRepository(
    private val appContext: Context,
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

    private val liveDetailCache = ConcurrentHashMap<String, LiveMatchDetail>()
    private val espnDirCache = ConcurrentHashMap<String, Pair<String, String>>()
    private val fetchedDirSlugs = Collections.synchronizedSet(mutableSetOf<String>())
    private val teamScheduleCache = ConcurrentHashMap<String, Pair<Long, List<MatchItem>>>()
    private val h2hCache = ConcurrentHashMap<String, Pair<Long, MatchH2HBundle>>()
    private val teamDecodeCache = ConcurrentHashMap<String, Pair<Int, TeamDetailInfo>>()
    private val knownLiveSnapshots = ConcurrentHashMap<String, Triple<MatchStatus, Int?, Int?>>()
    private val emittedBroadcastKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private var lastStandingsSignature: Int = 0
    private var lastTeamsSignature: Int = 0
    private val seedMutex = Mutex()
    private val syncMutex = Mutex()
    private val wikiPortraitCache = ConcurrentHashMap<String, String>().apply {
        TeamAssetCatalog.starPlayers.forEach { p ->
            if (p.portraitUrl.isNotBlank()) put(p.name.lowercase(), p.portraitUrl)
        }
    }
    private var lastAfLiveSweepMillis: Long = 0L

    fun getCachedLiveDetail(matchId: String): LiveMatchDetail? = liveDetailCache[matchId]

    fun getCachedH2H(matchId: String): MatchH2HBundle? = h2hCache[matchId]?.second

    fun observeAllMatches(): Flow<List<MatchItem>> =
        matchDao.observeAllMatches()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    fun observeAllStandings(): Flow<List<StandingEntry>> =
        standingDao.observeAllStandings()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    fun observeAllTeams(): Flow<List<TeamDetailInfo>> =
        teamDao.observeAllTeams()
            .map { list ->
                list.map { te ->
                    val sig = ((te.squadJson.hashCode() * 31) + te.espnTeamId.hashCode()) * 31 + te.crest.hashCode()
                    val cached = teamDecodeCache[te.keyName]
                    if (cached != null && cached.first == sig) {
                        cached.second
                    } else {
                        te.toDomain(json).also { teamDecodeCache[te.keyName] = sig to it }
                    }
                }
            }
            .flowOn(Dispatchers.IO)

    suspend fun getAllMatchesOnce(): List<MatchItem> =
        matchDao.getAllMatchesOnce().map { it.toDomain() }

    suspend fun ensureSeededIfEmpty() {
        var shouldRemoteSync = false
        seedMutex.withLock {
            val prefs = prefsRepo.currentPreferences()
            val hasMatches = matchDao.getAllMatchesOnce().isNotEmpty()
            val hasStandings = standingDao.getAllStandingsOnce().isNotEmpty()
            val hasTeams = teamDao.getAllTeamsOnce().isNotEmpty()
            if (!hasMatches || !hasStandings || !hasTeams) {
                seedFromBundledAsset(prefs.followedCompetitions, prefs.dataSourceMode, populateDb = true)
                shouldRemoteSync = true
            } else if (_starPlayers.value.size <= 25) {
                // Fast 0ms warm-start hydration of ranked players & pre-cached match details from bundled snapshot
                seedFromBundledAsset(prefs.followedCompetitions, prefs.dataSourceMode, populateDb = false)
            }
        }
        if (shouldRemoteSync) {
            runCatching { syncFixtures() }
        }
    }

    private fun readBundledSeedRawText(): String {
        val candidates = listOf("seed_fixtures.json", "seed_fixtures.json.gz")
        var lastError: Throwable? = null
        for (assetName in candidates) {
            try {
                return appContext.assets.open(assetName).use { decodeSeedStream(it) }
            } catch (err: Throwable) {
                lastError = err
            }
        }
        throw lastError ?: IllegalStateException("Bundled seed asset not found")
    }

    private suspend fun seedFromBundledAsset(
        followedCompetitions: Set<String>,
        sourceMode: DataSourceMode,
        populateDb: Boolean,
    ) {
        runCatching {
            val t0 = System.currentTimeMillis()
            val rawText = readBundledSeedRawText()
            val parsed = json.decodeFromString(GitHubFeedResponse.serializer(), rawText)
            val payload = parsed.toSyncPayload(followedCompetitions)
            Log.i(
                "FixtureRepository",
                "seed decoded in ${System.currentTimeMillis() - t0}ms (matches=${payload.matches.size}, standings=${payload.standings.size}, teams=${payload.teams.size})",
            )
            if (payload.players.isNotEmpty()) {
                _starPlayers.value = payload.players
                payload.players.forEach { p ->
                    if (p.portraitUrl.isNotBlank()) wikiPortraitCache[p.name.lowercase()] = p.portraitUrl
                }
            }
            if (payload.liveDetails.isNotEmpty()) {
                liveDetailCache.putAll(payload.liveDetails)
            }
            payload.teams.forEach { td ->
                if (td.espnTeamId.isNotBlank() && td.espnSlug.isNotBlank()) {
                    indexEspnTeamRef(td.name, td.zhName, td.espnSlug, td.espnTeamId)
                }
                if (td.coachName.isNotBlank() && td.coachPortraitUrl.isNotBlank()) {
                    wikiPortraitCache.putIfAbsent(td.coachName.lowercase(), td.coachPortraitUrl)
                }
                td.squad.forEach { sq ->
                    if (sq.portraitUrl.isNotBlank()) {
                        wikiPortraitCache.putIfAbsent(sq.name.lowercase(), sq.portraitUrl)
                    }
                }
            }
            if (populateDb) {
                if (payload.matches.isNotEmpty()) {
                    matchDao.replaceAll(payload.matches.map { MatchEntity.fromDomain(it) })
                }
                if (payload.standings.isNotEmpty()) {
                    standingDao.replaceAll(payload.standings.map { StandingEntity.fromDomain(it) })
                }
                if (prefsRepo.currentPreferences().lastSyncUtc.isBlank()) {
                    val stamp = parsed.generatedAtUtc.ifBlank {
                        DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                    }
                    prefsRepo.recordSyncResult(
                        syncedAtUtc = stamp,
                        summary = "已加载 ${payload.matches.size} 场赛事快照",
                        sourceMode = sourceMode,
                        standingsSyncedAtMillis = System.currentTimeMillis(),
                    )
                }
                if (payload.teams.isNotEmpty()) {
                    teamDao.replaceAll(payload.teams.map { TeamEntity.fromDomain(it, json) })
                }
                Log.i("FixtureRepository", "seed DB populated in ${System.currentTimeMillis() - t0}ms")
            }
        }.onFailure { err ->
            Log.e("FixtureRepository", "seedFromBundledAsset failed (populateDb=$populateDb)", err)
        }
    }

    suspend fun syncFixtures(zoneId: ZoneId = ZoneId.systemDefault()): Result<SyncSummary> {
        return runCatching { syncMutex.withLock {
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
                val sig = payload.standings.size * 31 + payload.standings.take(20).sumOf { it.points * 7 + it.rank }
                if (modeChanged || sig != lastStandingsSignature || standingDao.getAllStandingsOnce().isEmpty()) {
                    standingDao.replaceAll(payload.standings.map { StandingEntity.fromDomain(it) })
                    lastStandingsSignature = sig
                }
                standingsSyncedMillis = System.currentTimeMillis()
            } else if (modeChanged) {
                standingDao.clearAll()
            }

            if (payload.teams.isNotEmpty()) {
                val tSig = payload.teams.size * 31 + payload.teams.sumOf { it.squad.size * 13 + it.zhName.hashCode() }
                if (modeChanged || tSig != lastTeamsSignature || teamDao.getAllTeamsOnce().isEmpty()) {
                    teamDao.replaceAll(payload.teams.map { TeamEntity.fromDomain(it, json) })
                    lastTeamsSignature = tSig
                }
                payload.teams.forEach { td ->
                    if (td.espnTeamId.isNotBlank() && td.espnSlug.isNotBlank()) {
                        indexEspnTeamRef(td.name, td.zhName, td.espnSlug, td.espnTeamId)
                    }
                    if (td.coachName.isNotBlank() && td.coachPortraitUrl.isNotBlank()) {
                        wikiPortraitCache[td.coachName.lowercase()] = td.coachPortraitUrl
                    }
                    td.squad.forEach { sq ->
                        if (sq.portraitUrl.isNotBlank()) {
                            wikiPortraitCache[sq.name.lowercase()] = sq.portraitUrl
                        }
                    }
                }
            } else if (modeChanged) {
                teamDao.clearAll()
            }

            if (payload.players.isNotEmpty()) {
                _starPlayers.value = payload.players
                payload.players.forEach { p ->
                    if (p.portraitUrl.isNotBlank()) wikiPortraitCache[p.name.lowercase()] = p.portraitUrl
                }
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
        } }
    }

    /**
     * Zero-quota, near-zero-latency live score refresh using ESPN `all/scoreboard` first,
     * returning real-time broadcast alerts (kickoffs, goals with scorers, full-time results).
     */
    suspend fun pollLiveUpdatesWithBroadcasts(): Result<List<LiveBroadcastAlert>> {
        return runCatching {
            if (syncMutex.isLocked) return@runCatching emptyList()
            val prefs = prefsRepo.currentPreferences()
            if (prefs.dataSourceMode == DataSourceMode.DEMO) return@runCatching emptyList()

            val existing = matchDao.getAllMatchesOnce().map { it.toDomain() }.toMutableList()
            if (knownLiveSnapshots.isEmpty()) {
                existing.forEach { m ->
                    knownLiveSnapshots[m.id] = Triple(m.status, m.homeScore, m.awayScore)
                }
            }
            val changedItems = mutableListOf<MatchItem>()

            // 1. Free ESPN global live scoreboard (0 quota, fast real-time updates) + host fallback
            val espnResp = runCatching {
                apiService.fetchEspnScoreboard(
                    "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard",
                )
            }.getOrNull() ?: runCatching {
                apiService.fetchEspnScoreboard(
                    "https://site.web.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard",
                )
            }.getOrNull()
            espnResp?.events?.mapNotNull { it.toMatchItem(allowUnmappedComp = true) }?.forEach { live ->
                applyLiveUpdate(live, existing, prefs.followedCompetitions, changedItems)
            }

            // 1b. Also check active league-specific scoreboards in the live window (max 4 leagues in parallel)
            val nowUtc = Instant.now()
            val activeSlugs = existing.filter { m ->
                m.status.isLive || MatchTimeFormatter.parseInstant(m.utcDate)?.let { ko ->
                    Duration.between(ko, nowUtc).toMinutes() in -20..180
                } == true
            }.mapNotNull { m -> m.espnSlug ?: EspnLeagueCatalog.slugForCode(m.competitionCode) }
                .distinct()
                .take(4)
            if (activeSlugs.isNotEmpty()) {
                coroutineScope {
                    activeSlugs.map { slug ->
                        async {
                            runCatching {
                                apiService.fetchEspnScoreboard(
                                    "https://site.api.espn.com/apis/site/v2/sports/soccer/$slug/scoreboard",
                                ).events.mapNotNull { it.toMatchItem(fallbackSlug = slug) }
                            }.getOrDefault(emptyList())
                        }
                    }.forEach { def ->
                        def.await().forEach { live ->
                            applyLiveUpdate(live, existing, prefs.followedCompetitions, changedItems)
                        }
                    }
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

            val alerts = mutableListOf<LiveBroadcastAlert>()
            for (updated in changedItems.distinctBy { it.id }) {
                val isRelevant = updated.competitionCode in prefs.followedCompetitions ||
                    updated.id in prefs.watchlistMatchIds ||
                    updated.involvesAnyTeam(prefs.favoriteTeams)
                if (!isRelevant) continue

                val prev = knownLiveSnapshots[updated.id]
                knownLiveSnapshots[updated.id] = Triple(updated.status, updated.homeScore, updated.awayScore)
                if (prev == null) continue

                val (prevStatus, prevH, prevA) = prev
                val curH = updated.homeScore
                val curA = updated.awayScore
                val scoreChanged = (curH != null && prevH != null && curH > prevH) ||
                    (curA != null && prevA != null && curA > prevA) ||
                    (curH != null && curA != null && prevH == null && prevA == null && (curH > 0 || curA > 0))

                if (scoreChanged) {
                    var scorerDetail = "${updated.competitionZhName} · 实时比分 ${curH ?: 0}:${curA ?: 0}"
                    if (updated.espnSlug != null && updated.espnEventId != null) {
                        val det = fetchMatchLiveDetail(updated, forceRefresh = true).getOrNull()
                        val lastGoal = det?.events?.lastOrNull { it.type.equals("Goal", true) }
                        if (lastGoal != null) {
                            val minTag = if (lastGoal.extra != null) "${lastGoal.elapsed}+${lastGoal.extra}'" else "${lastGoal.elapsed}'"
                            scorerDetail = "$minTag ${lastGoal.playerZhName} 破门 · ${updated.competitionZhName}"
                        }
                    }
                    val alertKey = "${updated.id}|score|${curH ?: 0}:${curA ?: 0}"
                    if (emittedBroadcastKeys.add(alertKey)) {
                        alerts += LiveBroadcastAlert(
                            id = alertKey,
                            match = updated,
                            headline = "⚽ 进球播报！${updated.homeTeamZhName} ${curH ?: 0}:${curA ?: 0} ${updated.awayTeamZhName}",
                            detail = scorerDetail,
                        )
                    }
                } else if (!prevStatus.isLive && updated.status.isLive) {
                    val alertKey = "${updated.id}|kickoff"
                    if (emittedBroadcastKeys.add(alertKey)) {
                        alerts += LiveBroadcastAlert(
                            id = alertKey,
                            match = updated,
                            headline = "🏁 开赛播报！${updated.homeTeamZhName} vs ${updated.awayTeamZhName}",
                            detail = "${updated.competitionZhName} 已开球 · 正在同步实时图文事件",
                        )
                    }
                } else if (prevStatus.isLive && updated.status == MatchStatus.FINISHED) {
                    val alertKey = "${updated.id}|fulltime"
                    if (emittedBroadcastKeys.add(alertKey)) {
                        alerts += LiveBroadcastAlert(
                            id = alertKey,
                            match = updated,
                            headline = "⏹ 全场战报：${updated.homeTeamZhName} ${curH ?: 0}:${curA ?: 0} ${updated.awayTeamZhName}",
                            detail = "${updated.competitionZhName} 全场比赛结束",
                        )
                    }
                }
            }
            alerts
        }
    }

    suspend fun refreshLiveScoresEspnFirst(): Result<Int> =
        pollLiveUpdatesWithBroadcasts().map { it.size }

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
                ((live.competitionCode == "OTHER" || it.competitionCode == live.competitionCode) &&
                    matchesSamePair(it, live))
        }
        if (idx >= 0) {
            val cur = existing[idx]
            val newStatus = if (live.status.isLive || live.status == MatchStatus.FINISHED) live.status else cur.status
            val newH = live.homeScore ?: cur.homeScore
            val newA = live.awayScore ?: cur.awayScore
            val newElapsed = live.elapsedMinutes ?: cur.elapsedMinutes
            val newEspnSlug = live.espnSlug?.takeIf { it.isNotBlank() } ?: cur.espnSlug
            val newEspnEventId = live.espnEventId?.takeIf { it.isNotBlank() } ?: cur.espnEventId
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
        } else if (live.competitionCode != "OTHER" && live.competitionCode in followedCodes && (live.status.isLive || live.status == MatchStatus.FINISHED)) {
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

            // 1. Try ESPN Summary (Free, zero quota, works for BOTH live and historical matches)
            var targetSlug = match.espnSlug ?: EspnLeagueCatalog.slugForCode(match.competitionCode)
            var targetEventId = match.espnEventId ?: match.id.removePrefix("espn-").takeIf { match.id.startsWith("espn-") }
            if (targetEventId == null) {
                val utcDate = MatchTimeFormatter.parseInstant(match.utcDate)
                    ?.atZone(ZoneOffset.UTC)
                    ?.toLocalDate()
                if (utcDate != null) {
                    val dateCandidates = listOf(
                        DateTimeFormatter.BASIC_ISO_DATE.format(utcDate),
                        DateTimeFormatter.BASIC_ISO_DATE.format(utcDate.minusDays(1)),
                    )
                    val querySlug = targetSlug ?: "all"
                    for (ds in dateCandidates) {
                        val sb = runCatching {
                            apiService.fetchEspnScoreboard(
                                "https://site.api.espn.com/apis/site/v2/sports/soccer/$querySlug/scoreboard?dates=$ds",
                            )
                        }.getOrNull() ?: runCatching {
                            apiService.fetchEspnScoreboard(
                                "https://site.web.api.espn.com/apis/site/v2/sports/soccer/$querySlug/scoreboard?dates=$ds",
                            )
                        }.getOrNull()
                        val matched = sb?.events?.mapNotNull {
                            it.toMatchItem(fallbackSlug = targetSlug.orEmpty(), allowUnmappedComp = true)
                        }?.firstOrNull { em -> matchesSamePair(em, match) }
                        if (matched?.espnEventId != null) {
                            targetEventId = matched.espnEventId
                            if (targetSlug.isNullOrBlank() && !matched.espnSlug.isNullOrBlank()) {
                                targetSlug = matched.espnSlug
                            }
                            break
                        }
                    }
                }
                if (targetEventId == null) {
                    val homeRef = resolveEspnTeamRef(match.homeTeamName, match.homeTeamZhName, match.competitionCode)
                    if (homeRef != null) {
                        val schMatches = fetchEspnTeamMatches(homeRef.first, homeRef.second)
                        val hit = schMatches.firstOrNull { em ->
                            matchesSamePair(em, match) &&
                                (utcDate == null || MatchTimeFormatter.parseInstant(em.utcDate)?.atZone(ZoneOffset.UTC)?.toLocalDate()?.let { d ->
                                    kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(d, utcDate)) <= 1
                                } == true)
                        }
                        if (hit?.espnEventId != null) {
                            targetSlug = hit.espnSlug ?: homeRef.first
                            targetEventId = hit.espnEventId
                        }
                    }
                }
            }

            if (targetSlug != null && targetEventId != null) {
                val rawEspnDetail = runCatching {
                    val sm = apiService.fetchEspnSummary(
                        "https://site.api.espn.com/apis/site/v2/sports/soccer/$targetSlug/summary?event=$targetEventId",
                    )
                    sm.toLiveDetail(match.id, targetEventId)
                }.getOrNull() ?: runCatching {
                    val sm = apiService.fetchEspnSummary(
                        "https://site.web.api.espn.com/apis/site/v2/sports/soccer/$targetSlug/summary?event=$targetEventId",
                    )
                    sm.toLiveDetail(match.id, targetEventId)
                }.getOrNull()
                if (rawEspnDetail != null) {
                    val espnDetail = enrichDetailLineupPortraits(rawEspnDetail)
                    liveDetailCache[match.id] = espnDetail
                    persistDetailOntoMatch(match.id, espnDetail, espnSlug = targetSlug, espnEventId = targetEventId)
                    return@runCatching espnDetail
                }
            }

            // Never wipe out an existing cached live detail if live polling fails transiently
            if (cached != null) {
                return@runCatching cached
            }

            // 2. Fallback to API-Football (e.g. J2 / K League 1 / U20 Asian Cup)
            val prefs = prefsRepo.currentPreferences()
            val afKey = prefs.effectiveApiFootballKey()
            require(afKey.isNotEmpty()) { "实时赛况网络超时，请点击右上角或下拉刷新重试" }

            var targetFixtureId = match.apiFootballFixtureId
                ?: match.id.removePrefix("af-").toIntOrNull()

            if (targetFixtureId == null) {
                val utcDateStr = MatchTimeFormatter.parseInstant(match.utcDate)
                    ?.atZone(ZoneOffset.UTC)
                    ?.toLocalDate()
                    ?.toString()
                if (utcDateStr != null) {
                    val dayResp = runCatching {
                        apiService.fetchApiFootballFixtures(
                            url = "https://v3.football.api-sports.io/fixtures?date=$utcDateStr",
                            apiKey = afKey,
                        )
                    }.getOrNull()
                    targetFixtureId = dayResp?.response?.firstOrNull { row ->
                        TeamLocalization.localizeTeam(row.teams.home.name) == match.homeTeamZhName &&
                            TeamLocalization.localizeTeam(row.teams.away.name) == match.awayTeamZhName
                    }?.fixture?.id
                }
            }

            require(targetFixtureId != null && targetFixtureId > 0) {
                "实时赛况连接暂时受阻，或比赛尚未开赛（可稍候下拉刷新赛程获取预缓存详情）"
            }

            val detailResp = apiService.fetchApiFootballFixtures(
                url = "https://v3.football.api-sports.io/fixtures?id=$targetFixtureId",
                apiKey = afKey,
            )
            val row = detailResp.response.firstOrNull() ?: error("暂无比赛过程详情")
            val detail = enrichDetailLineupPortraits(row.toLiveDetail(match.id))
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

    suspend fun fetchTeamHistoryAndDetail(
        teamName: String,
        teamZhName: String,
        teamId: Int,
        compCode: String,
        crest: String,
    ): Result<Pair<TeamDetailInfo?, List<MatchItem>>> {
        return runCatching {
            seedPortraitAndEspnCachesFromDb()
            val allStoredTeams = teamDao.getAllTeamsOnce().map { it.toDomain(json) }
            val cachedTeam = findMatchingTeam(allStoredTeams, teamName, teamZhName)
            val espnRef = cachedTeam?.let {
                if (it.espnSlug.isNotBlank() && it.espnTeamId.isNotBlank()) it.espnSlug to it.espnTeamId else null
            } ?: resolveEspnTeamRef(teamName, teamZhName, compCode)

            val localMatches = matchDao.getAllMatchesOnce().map { it.toDomain() }.filter { m ->
                matchesTeamIdent(m.homeTeamName, m.homeTeamZhName, teamName, teamZhName) ||
                    matchesTeamIdent(m.awayTeamName, m.awayTeamZhName, teamName, teamZhName)
            }

            coroutineScope {
                val historyDeferred = async {
                    val fetched = mutableListOf<MatchItem>()
                    if (espnRef != null) {
                        fetched += fetchEspnTeamMatches(espnRef.first, espnRef.second)
                    } else if (teamId > 0) {
                        val prefs = prefsRepo.currentPreferences()
                        val fdKey = prefs.effectiveFootballDataKey()
                        if (fdKey.isNotEmpty() && compCode !in setOf("J2", "KL1", "U20AC")) {
                            runCatching {
                                apiService.fetchFootballDataTeamMatches(fdKey, teamId, status = "FINISHED", limit = 20)
                                    .matches.mapNotNull { it.toDomain() }
                            }.getOrNull()?.let { fetched += it }
                        }
                    }
                    dedupeAndSortTeamMatches(localMatches + fetched)
                }

                val detailDeferred = async {
                    var resolved = cachedTeam
                    if (resolved == null || resolved.squad.isEmpty()) {
                        resolved = fetchTeamDetailOnDemand(
                            teamName = cachedTeam?.name ?: teamName,
                            teamId = espnRef?.second?.toIntOrNull() ?: teamId,
                            compCode = cachedTeam?.competitionCode?.ifBlank { compCode } ?: compCode,
                            crest = cachedTeam?.crest?.ifBlank { crest } ?: crest,
                            espnRefOverride = espnRef,
                        ).getOrNull() ?: cachedTeam
                    }
                    if (resolved != null) {
                        if (espnRef != null && (resolved.espnTeamId.isBlank() || resolved.espnSlug.isBlank())) {
                            resolved = resolved.copy(espnSlug = espnRef.first, espnTeamId = espnRef.second)
                        }
                        resolved = enrichTeamSquadPortraits(resolved)
                    }
                    resolved
                }

                detailDeferred.await() to historyDeferred.await()
            }
        }
    }

    suspend fun fetchMatchHeadToHeadAndForm(match: MatchItem): Result<MatchH2HBundle> {
        return runCatching {
            val cached = h2hCache[match.id]
            val nowMs = System.currentTimeMillis()
            if (cached != null && (nowMs - cached.first) < 600_000L) {
                return@runCatching cached.second
            }
            seedPortraitAndEspnCachesFromDb()

            val localMatches = matchDao.getAllMatchesOnce().map { it.toDomain() }
            val homeRef = resolveEspnTeamRef(match.homeTeamName, match.homeTeamZhName, match.competitionCode)
            val awayRef = resolveEspnTeamRef(match.awayTeamName, match.awayTeamZhName, match.competitionCode)

            val (homeEspn, awayEspn, fdH2h) = coroutineScope {
                val hDef = async {
                    if (homeRef != null) fetchEspnTeamMatches(homeRef.first, homeRef.second) else emptyList()
                }
                val aDef = async {
                    if (awayRef != null) fetchEspnTeamMatches(awayRef.first, awayRef.second) else emptyList()
                }
                val fdDef = async {
                    val fdId = match.id.removePrefix("fd-").toLongOrNull()
                    val fdKey = prefsRepo.currentPreferences().effectiveFootballDataKey()
                    if (fdId != null && fdKey.isNotEmpty()) {
                        runCatching {
                            apiService.fetchFootballDataHead2Head(fdKey, fdId, limit = 15)
                                .matches.mapNotNull { it.toDomain() }
                        }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }
                Triple(hDef.await(), aDef.await(), fdDef.await())
            }

            val combinedAll = dedupeAndSortTeamMatches(localMatches + homeEspn + awayEspn + fdH2h)
            val h2h = combinedAll.filter { m ->
                m.id != match.id && m.status == MatchStatus.FINISHED && matchesSamePair(m, match)
            }.sortedByDescending { it.utcDate }

            val homeRecent = dedupeAndSortTeamMatches(
                (homeEspn + localMatches).filter { m ->
                    m.id != match.id && m.status == MatchStatus.FINISHED &&
                        (matchesTeamIdent(m.homeTeamName, m.homeTeamZhName, match.homeTeamName, match.homeTeamZhName) ||
                            matchesTeamIdent(m.awayTeamName, m.awayTeamZhName, match.homeTeamName, match.homeTeamZhName))
                },
            ).filter { it.status == MatchStatus.FINISHED }.sortedByDescending { it.utcDate }.take(10)

            val awayRecent = dedupeAndSortTeamMatches(
                (awayEspn + localMatches).filter { m ->
                    m.id != match.id && m.status == MatchStatus.FINISHED &&
                        (matchesTeamIdent(m.homeTeamName, m.homeTeamZhName, match.awayTeamName, match.awayTeamZhName) ||
                            matchesTeamIdent(m.awayTeamName, m.awayTeamZhName, match.awayTeamName, match.awayTeamZhName))
                },
            ).filter { it.status == MatchStatus.FINISHED }.sortedByDescending { it.utcDate }.take(10)

            val bundle = MatchH2HBundle(
                h2hMatches = h2h,
                homeRecentMatches = homeRecent,
                awayRecentMatches = awayRecent,
            )
            h2hCache[match.id] = nowMs to bundle
            bundle
        }
    }

    suspend fun enrichSinglePlayerPortrait(player: StarPlayer): StarPlayer {
        val needPortrait = player.portraitUrl.isBlank()
        val needBio = player.bio.isBlank()
        if (!needPortrait && !needBio) return player

        var newPortrait = player.portraitUrl
        var newBio = player.bio
        if (needPortrait) {
            newPortrait = enrichWikipediaPortraitsBatch(listOf(player.name))[player.name]
                ?: wikiPortraitCache[player.name.lowercase()]
                ?: ""
        }
        if (needPortrait || needBio) {
            val wikiCandidate = runCatching { fetchWikipediaBioAndPortrait(player.name) }.getOrNull()
            if (newPortrait.isBlank() && !wikiCandidate?.first.isNullOrBlank()) {
                newPortrait = wikiCandidate!!.first
            }
            if (newBio.isBlank() && !wikiCandidate?.second.isNullOrBlank()) {
                newBio = wikiCandidate!!.second
            }
        }
        if (newBio.isBlank()) {
            newBio = TeamAssetCatalog.buildStructuredPlayerBio(player.copy(portraitUrl = newPortrait))
        }
        if (newPortrait == player.portraitUrl && newBio == player.bio) return player
        val updated = player.copy(portraitUrl = newPortrait, bio = newBio)
        _starPlayers.value = _starPlayers.value.map { if (it.id == player.id) updated else it }
        return updated
    }

    private suspend fun fetchWikipediaBioAndPortrait(enName: String): Pair<String, String> {
        val cleaned = enName.trim()
        if (cleaned.isBlank()) return "" to ""
        val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=" +
            URLEncoder.encode("$cleaned footballer", "UTF-8") +
            "&gsrlimit=4&prop=pageimages&pithumbsize=300&format=json"
        val pages = runCatching {
            apiService.fetchWikipediaPageImages(searchUrl).query?.pages?.values.orEmpty()
        }.getOrDefault(emptyList())
        val bestPage = pages.firstOrNull {
            !it.thumbnail?.source.isNullOrBlank() && !it.title.contains("disambiguation", true)
        } ?: pages.firstOrNull { !it.title.contains("disambiguation", true) } ?: pages.firstOrNull()
        val resolvedTitle = bestPage?.title?.ifBlank { cleaned } ?: cleaned
        var portrait = bestPage?.thumbnail?.source.orEmpty()

        val encodedEn = URLEncoder.encode(resolvedTitle.replace(' ', '_'), "UTF-8")
        val enSum = runCatching {
            apiService.fetchWikipediaSummary("https://en.wikipedia.org/api/rest_v1/page/summary/$encodedEn")
        }.getOrNull()
        if (portrait.isBlank()) {
            portrait = enSum?.thumbnail?.source.orEmpty()
        }
        val zhTitle = runCatching {
            val langUrl = "https://en.wikipedia.org/w/api.php?action=query&prop=langlinks&lllang=zh&format=json&titles=" +
                URLEncoder.encode(resolvedTitle, "UTF-8")
            apiService.fetchWikipediaLanglinks(langUrl).query?.pages?.values?.firstOrNull()?.langlinks?.firstOrNull()?.title
        }.getOrNull()
        val zhSum = if (!zhTitle.isNullOrBlank()) {
            runCatching {
                apiService.fetchWikipediaSummary(
                    "https://zh.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(zhTitle.replace(' ', '_'), "UTF-8")}",
                )
            }.getOrNull()
        } else {
            null
        }
        val bestBio = zhSum?.extract?.ifBlank { null } ?: enSum?.extract.orEmpty()
        if (portrait.isNotBlank()) wikiPortraitCache[cleaned.lowercase()] = portrait
        return portrait to bestBio
    }

    suspend fun fetchTeamDetailOnDemand(
        teamName: String,
        teamId: Int,
        compCode: String,
        crest: String,
        espnRefOverride: Pair<String, String>? = null,
    ): Result<TeamDetailInfo?> {
        return runCatching {
            val espnRef = espnRefOverride ?: resolveEspnTeamRef(teamName, TeamLocalization.localizeTeam(teamName), compCode)
            val targetSlug = espnRef?.first ?: EspnLeagueCatalog.slugForCode(compCode)
            val targetEspnId = espnRef?.second?.toIntOrNull() ?: teamId
            if (targetSlug != null && targetEspnId > 0) {
                val roster = runCatching {
                    apiService.fetchEspnRoster(
                        "https://site.api.espn.com/apis/site/v2/sports/soccer/$targetSlug/teams/$targetEspnId/roster",
                    )
                }.getOrNull()
                if (roster != null && roster.athletes.isNotEmpty()) {
                    val base = roster.toTeamDetail(
                        teamId = targetEspnId,
                        compCode = compCode,
                        teamName = teamName,
                        shortName = teamName,
                        crest = crest,
                        espnTeamId = targetEspnId.toString(),
                        espnSlug = targetSlug,
                    )
                    val enriched = enrichTeamSquadPortraits(base)
                    teamDao.upsertAll(listOf(TeamEntity.fromDomain(enriched, json)))
                    return@runCatching enriched
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

            val detail = enrichTeamSquadPortraits(
                TeamDetailInfo(
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
                    espnTeamId = espnRef?.second.orEmpty(),
                    espnSlug = espnRef?.first.orEmpty(),
                ),
            )
            teamDao.upsertAll(listOf(TeamEntity.fromDomain(detail, json)))
            detail
        }
    }

    private suspend fun enrichTeamSquadPortraits(team: TeamDetailInfo): TeamDetailInfo {
        val missingSquad = team.squad.filter { it.portraitUrl.isBlank() && it.name.isNotBlank() }.map { it.name }
        val needCoach = team.coachName.isNotBlank() && team.coachPortraitUrl.isBlank()
        val queryNames = (missingSquad + listOfNotNull(team.coachName.takeIf { needCoach })).distinct()
        val resolved = if (queryNames.isNotEmpty()) enrichWikipediaPortraitsBatch(queryNames) else emptyMap()
        val resolvedCoachPhoto = team.coachPortraitUrl.ifBlank {
            resolved[team.coachName] ?: wikiPortraitCache[team.coachName.lowercase()].orEmpty()
        }
        val updatedSquad = team.squad.map { sq ->
            if (sq.portraitUrl.isBlank()) {
                val hit = resolved[sq.name] ?: wikiPortraitCache[sq.name.lowercase()]
                if (!hit.isNullOrBlank()) sq.copy(portraitUrl = hit) else sq
            } else {
                sq
            }
        }
        val updatedTeam = team.copy(squad = updatedSquad, coachPortraitUrl = resolvedCoachPhoto)
        if (updatedTeam != team) {
            runCatching { teamDao.upsertAll(listOf(TeamEntity.fromDomain(updatedTeam, json))) }
        }
        return updatedTeam
    }

    private suspend fun enrichDetailLineupPortraits(detail: LiveMatchDetail): LiveMatchDetail {
        if (detail.lineups.isEmpty()) return detail
        val storedTeams = runCatching { teamDao.getAllTeamsOnce().map { it.toDomain(json) } }.getOrDefault(emptyList())
        storedTeams.forEach { t ->
            t.squad.forEach { sq ->
                if (sq.portraitUrl.isNotBlank()) wikiPortraitCache.putIfAbsent(sq.name.lowercase(), sq.portraitUrl)
            }
            if (t.coachName.isNotBlank() && t.coachPortraitUrl.isNotBlank()) {
                wikiPortraitCache.putIfAbsent(t.coachName.lowercase(), t.coachPortraitUrl)
            }
        }
        val allNames = buildList {
            detail.lineups.forEach { side ->
                (side.startXI + side.substitutes).forEach { if (it.name.isNotBlank()) add(it.name) }
                val matchedTeam = findMatchingTeam(storedTeams, side.teamName, side.teamZhName)
                val coachEn = side.coachName.ifBlank { matchedTeam?.coachName.orEmpty() }
                if (coachEn.isNotBlank()) add(coachEn)
            }
        }.distinct()
        val resolved = enrichWikipediaPortraitsBatch(allNames)
        return detail.copy(
            lineups = detail.lineups.map { side ->
                val matchedTeam = findMatchingTeam(storedTeams, side.teamName, side.teamZhName)
                val coachEn = side.coachName.ifBlank { matchedTeam?.coachName.orEmpty() }
                val coachZh = side.coachZhName.ifBlank { matchedTeam?.coachZhName?.ifBlank { TeamLocalization.localizeCoach(coachEn) }.orEmpty() }
                val coachPhoto = side.coachPortraitUrl.ifBlank {
                    matchedTeam?.coachPortraitUrl.orEmpty().ifBlank {
                        resolved[coachEn] ?: wikiPortraitCache[coachEn.lowercase()].orEmpty()
                    }
                }
                side.copy(
                    coachName = coachEn,
                    coachZhName = coachZh,
                    coachPortraitUrl = coachPhoto,
                    startXI = side.startXI.map { p ->
                        p.copy(portraitUrl = p.portraitUrl.ifBlank { resolved[p.name] ?: wikiPortraitCache[p.name.lowercase()] ?: "" })
                    },
                    substitutes = side.substitutes.map { p ->
                        p.copy(portraitUrl = p.portraitUrl.ifBlank { resolved[p.name] ?: wikiPortraitCache[p.name.lowercase()] ?: "" })
                    },
                )
            },
        )
    }

    private suspend fun enrichWikipediaPortraitsBatch(names: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val toQuery = mutableListOf<String>()
        for (raw in names.distinct()) {
            val cached = wikiPortraitCache[raw.lowercase()]
            if (!cached.isNullOrBlank()) {
                result[raw] = cached
            } else if (raw.isNotBlank()) {
                toQuery += raw
            }
        }
        if (toQuery.isEmpty()) return result

        val chunks = toQuery.take(120).chunked(40)
        coroutineScope {
            chunks.map { chunk ->
                async {
                    runCatching {
                        val titlesParam = URLEncoder.encode(chunk.joinToString("|"), "UTF-8")
                        val resp = apiService.fetchWikipediaPageImages(
                            "https://en.wikipedia.org/w/api.php?action=query&titles=$titlesParam&prop=pageimages&pithumbsize=300&redirects=1&format=json",
                        )
                        val q = resp.query ?: return@runCatching emptyMap()
                        val norm = q.normalized.associate { it.from to it.to }
                        val redir = q.redirects.associate { it.from to it.to }
                        val imgByTitle = q.pages.values.mapNotNull { pg ->
                            val src = pg.thumbnail?.source.orEmpty()
                            if (pg.title.isNotBlank() && src.isNotBlank()) pg.title to src else null
                        }.toMap()
                        buildMap {
                            chunk.forEach { rawName ->
                                val s1 = norm[rawName] ?: rawName
                                val s2 = redir[s1] ?: s1
                                val hit = imgByTitle[s2] ?: imgByTitle[s1] ?: imgByTitle[rawName]
                                if (!hit.isNullOrBlank()) {
                                    wikiPortraitCache[rawName.lowercase()] = hit
                                    put(rawName, hit)
                                }
                            }
                        }
                    }.getOrDefault(emptyMap())
                }
            }.forEach { deferred ->
                result.putAll(deferred.await())
            }
        }
        val missingForWikidata = toQuery.filter { !result.containsKey(it) && it.isNotBlank() }.take(40)
        if (missingForWikidata.isNotEmpty()) {
            result.putAll(fetchWikidataPortraitsBatch(missingForWikidata))
        }
        val missingForSearch = toQuery.filter { !result.containsKey(it) && it.isNotBlank() }.take(10)
        if (missingForSearch.isNotEmpty()) {
            coroutineScope {
                missingForSearch.map { rawName ->
                    async {
                        val sUrl = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=" +
                            URLEncoder.encode("$rawName footballer", "UTF-8") +
                            "&gsrlimit=4&prop=pageimages&pithumbsize=300&format=json"
                        val pages = runCatching {
                            apiService.fetchWikipediaPageImages(sUrl).query?.pages?.values.orEmpty()
                        }.getOrDefault(emptyList())
                        val thumb = pages.firstOrNull {
                            !it.thumbnail?.source.isNullOrBlank() && !it.title.contains("disambiguation", true)
                        }?.thumbnail?.source
                        if (!thumb.isNullOrBlank()) {
                            wikiPortraitCache[rawName.lowercase()] = thumb
                            rawName to thumb
                        } else {
                            null
                        }
                    }
                }.forEach { def ->
                    def.await()?.let { (k, v) -> result[k] = v }
                }
            }
        }
        return result
    }

    private suspend fun fetchWikidataPortraitsBatch(names: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val variantToRaw = mutableMapOf<String, String>()
        names.distinct().forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.length >= 2) {
                variantToRaw[trimmed] = raw
                val ascii = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                    .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                if (ascii != trimmed && ascii.length >= 2) {
                    variantToRaw.putIfAbsent(ascii, raw)
                }
            }
        }
        val chunks = variantToRaw.keys.chunked(20).take(3)
        for (chunk in chunks) {
            val valuesClause = chunk.joinToString(" ") { v ->
                val escaped = v.replace("\\", "\\\\").replace("\"", "\\\"")
                "\"$escaped\"@en"
            }
            val sparql = "SELECT ?label ?img WHERE { VALUES ?label { $valuesClause } " +
                "?item wdt:P106 wd:Q937857 ; wdt:P18 ?img . " +
                "{ ?item rdfs:label ?label . } UNION { ?item skos:altLabel ?label . } }"
            val encoded = URLEncoder.encode(sparql, "UTF-8")
            val resp = runCatching {
                apiService.fetchWikidataSparql("https://query.wikidata.org/sparql?query=$encoded&format=json")
            }.getOrNull() ?: continue
            resp.results?.bindings.orEmpty().forEach { row ->
                val label = row["label"]?.value.orEmpty()
                val rawImg = row["img"]?.value.orEmpty()
                val rawName = variantToRaw[label] ?: return@forEach
                if (rawImg.isNotBlank() && !result.containsKey(rawName)) {
                    var httpsUrl = if (rawImg.startsWith("http://commons.wikimedia.org/")) {
                        "https://" + rawImg.removePrefix("http://")
                    } else {
                        rawImg
                    }
                    if (httpsUrl.contains("Special:FilePath") && !httpsUrl.contains("?")) {
                        httpsUrl += "?width=300"
                    }
                    wikiPortraitCache[rawName.lowercase()] = httpsUrl
                    result[rawName] = httpsUrl
                }
            }
        }
        return result
    }

    private suspend fun seedPortraitAndEspnCachesFromDb() {
        teamDao.getAllTeamsOnce().forEach { te ->
            val t = te.toDomain(json)
            if (t.espnTeamId.isNotBlank() && t.espnSlug.isNotBlank()) {
                indexEspnTeamRef(t.name, t.zhName, t.espnSlug, t.espnTeamId)
            }
            t.squad.forEach { sq ->
                if (sq.portraitUrl.isNotBlank()) {
                    wikiPortraitCache.putIfAbsent(sq.name.lowercase(), sq.portraitUrl)
                }
            }
        }
        if (espnDirCache.isEmpty()) {
            matchDao.getAllMatchesOnce().map { it.toDomain() }.forEach { m ->
                val slug = m.espnSlug
                if (!slug.isNullOrBlank() && m.id.startsWith("espn-")) {
                    if (m.homeTeamId > 0) indexEspnTeamRef(m.homeTeamName, m.homeTeamZhName, slug, m.homeTeamId.toString())
                    if (m.awayTeamId > 0) indexEspnTeamRef(m.awayTeamName, m.awayTeamZhName, slug, m.awayTeamId.toString())
                }
            }
        }
    }

    private fun indexEspnTeamRef(enName: String, zhName: String, slug: String, espnTeamId: String) {
        val pair = slug to espnTeamId
        val nk = normalizeTeamLookup(enName)
        if (nk.isNotBlank()) espnDirCache.putIfAbsent(nk, pair)
        if (zhName.isNotBlank()) espnDirCache.putIfAbsent(zhName.trim(), pair)
    }

    private suspend fun resolveEspnTeamRef(
        teamName: String,
        teamZhName: String,
        compCode: String,
    ): Pair<String, String>? {
        val nk = normalizeTeamLookup(teamName)
        val localized = TeamLocalization.localizeTeam(teamName).ifBlank { teamZhName }
        espnDirCache[nk]?.let { return it }
        espnDirCache[localized]?.let { return it }
        espnDirCache[teamZhName.trim()]?.let { return it }

        val candidates = linkedSetOf<String>()
        EspnLeagueCatalog.slugForCode(compCode)?.let { candidates += it }
        if (compCode in setOf("WC", "EC", "UNL", "U17WC", "U20AC", "INT") || localized.endsWith("国家队")) {
            candidates += listOf("uefa.nations", "fifa.world", "uefa.euro", "afc.cup")
        } else {
            candidates += listOf("eng.1", "esp.1", "ger.1", "ita.1", "fra.1", "uefa.champions", "uefa.europa", "jpn.1", "usa.1", "afc.champions", "aus.1")
        }

        for (slug in candidates) {
            if (!fetchedDirSlugs.add(slug)) continue
            val teamsResp = runCatching {
                apiService.fetchEspnTeams("https://site.api.espn.com/apis/site/v2/sports/soccer/$slug/teams")
            }.getOrNull()
            teamsResp?.sports?.flatMap { it.leagues }?.flatMap { it.teams }?.mapNotNull { it.team }?.forEach { t ->
                val dn = t.displayName.ifBlank { t.name }
                if (t.id.isNotBlank() && dn.isNotBlank()) {
                    val zh = TeamLocalization.localizeTeam(dn, t.shortDisplayName)
                    indexEspnTeamRef(dn, zh, slug, t.id)
                }
            }
            espnDirCache[nk]?.let { return it }
            espnDirCache[localized]?.let { return it }
        }
        return espnDirCache[nk] ?: espnDirCache[localized] ?: espnDirCache[teamZhName.trim()]
    }

    private suspend fun fetchEspnTeamMatches(slug: String, espnTeamId: String): List<MatchItem> {
        val cacheKey = "$slug:$espnTeamId"
        val cached = teamScheduleCache[cacheKey]
        val nowMs = System.currentTimeMillis()
        if (cached != null && (nowMs - cached.first) < 600_000L) {
            return cached.second
        }
        val combined = coroutineScope {
            val curDef = async {
                runCatching {
                    apiService.fetchEspnTeamSchedule(
                        "https://site.api.espn.com/apis/site/v2/sports/soccer/$slug/teams/$espnTeamId/schedule",
                    ).events.mapNotNull { it.toMatchItem(slug) }
                }.getOrDefault(emptyList())
            }
            val prevDef = async {
                runCatching {
                    apiService.fetchEspnTeamSchedule(
                        "https://site.api.espn.com/apis/site/v2/sports/soccer/$slug/teams/$espnTeamId/schedule?season=2025",
                    ).events.mapNotNull { it.toMatchItem(slug) }
                }.getOrDefault(emptyList())
            }
            dedupeAndSortTeamMatches(curDef.await() + prevDef.await())
        }
        teamScheduleCache[cacheKey] = nowMs to combined
        return combined
    }

    private fun findMatchingTeam(
        teams: List<TeamDetailInfo>,
        enName: String,
        zhName: String,
    ): TeamDetailInfo? {
        val nk = normalizeTeamLookup(enName)
        return teams.firstOrNull { t ->
            t.zhName == zhName ||
                t.name.equals(enName, true) ||
                (nk.isNotBlank() && normalizeTeamLookup(t.name) == nk)
        }
    }

    private fun matchesTeamIdent(
        candEn: String,
        candZh: String,
        targetEn: String,
        targetZh: String,
    ): Boolean {
        if (targetZh.isNotBlank() && candZh == targetZh) return true
        if (targetEn.isNotBlank() && candEn.equals(targetEn, true)) return true
        val n1 = normalizeTeamLookup(candEn)
        val n2 = normalizeTeamLookup(targetEn)
        return n1.isNotBlank() && n1 == n2
    }

    private fun matchesSamePair(a: MatchItem, b: MatchItem): Boolean {
        val straight = matchesTeamIdent(a.homeTeamName, a.homeTeamZhName, b.homeTeamName, b.homeTeamZhName) &&
            matchesTeamIdent(a.awayTeamName, a.awayTeamZhName, b.awayTeamName, b.awayTeamZhName)
        val swapped = matchesTeamIdent(a.homeTeamName, a.homeTeamZhName, b.awayTeamName, b.awayTeamZhName) &&
            matchesTeamIdent(a.awayTeamName, a.awayTeamZhName, b.homeTeamName, b.homeTeamZhName)
        return straight || swapped
    }

    private fun dedupeAndSortTeamMatches(list: List<MatchItem>): List<MatchItem> {
        val map = linkedMapOf<String, MatchItem>()
        for (m in list) {
            val day = m.utcDate.take(10)
            val h = normalizeTeamLookup(m.homeTeamName).ifBlank { m.homeTeamZhName }
            val a = normalizeTeamLookup(m.awayTeamName).ifBlank { m.awayTeamZhName }
            val key = "$day|$h|$a"
            val cur = map[key]
            if (cur == null) {
                map[key] = m
            } else {
                map[key] = cur.copy(
                    status = if (m.status == MatchStatus.FINISHED || m.status.isLive) m.status else cur.status,
                    homeScore = m.homeScore ?: cur.homeScore,
                    awayScore = m.awayScore ?: cur.awayScore,
                    espnSlug = cur.espnSlug ?: m.espnSlug,
                    espnEventId = cur.espnEventId ?: m.espnEventId,
                    apiFootballFixtureId = cur.apiFootballFixtureId ?: m.apiFootballFixtureId,
                )
            }
        }
        return map.values.sortedByDescending { it.utcDate }
    }

    private fun normalizeTeamLookup(raw: String): String =
        raw.lowercase()
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fff]+"), " ")
            .replace(Regex("\\b(fc|cf|afc|kv|sc|sv|sk|fk|ssc|ac|as|us|rc|vfl|vfb|tsg|fsv)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

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

    companion object {
        /**
         * Decodes either a gzip-compressed seed asset stream or an already-decompressed
         * UTF-8 JSON stream (which Android AAPT2 produces when stripping `.gz` during packaging).
         */
        internal fun decodeSeedStream(input: InputStream): String {
            val buffered = BufferedInputStream(input, 8192)
            buffered.mark(2)
            val b0 = buffered.read()
            val b1 = buffered.read()
            buffered.reset()
            val stream: InputStream = if (b0 == 0x1f && b1 == 0x8b) {
                GZIPInputStream(buffered)
            } else {
                buffered
            }
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
}
