package com.footballcalendar.app.data.repository

import com.footballcalendar.app.data.local.MatchDao
import com.footballcalendar.app.data.local.MatchEntity
import com.footballcalendar.app.data.local.StandingDao
import com.footballcalendar.app.data.local.StandingEntity
import com.footballcalendar.app.data.preferences.UserPreferencesRepository
import com.footballcalendar.app.data.remote.FeedApiService
import com.footballcalendar.app.data.remote.FixtureDataSource
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.StarPlayer
import com.footballcalendar.app.model.SyncSummary
import com.footballcalendar.app.util.ScheduleDiffEngine
import com.footballcalendar.app.util.TeamAssetCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FixtureRepository(
    private val matchDao: MatchDao,
    private val standingDao: StandingDao,
    private val prefsRepo: UserPreferencesRepository,
    private val dataSource: FixtureDataSource,
    private val apiService: FeedApiService,
) {
    private val _starPlayers = MutableStateFlow(TeamAssetCatalog.starPlayers)
    val starPlayersFlow: Flow<List<StarPlayer>> = _starPlayers.asStateFlow()

    fun observeAllMatches(): Flow<List<MatchItem>> =
        matchDao.observeAllMatches().map { list -> list.map { it.toDomain() } }

    fun observeAllStandings(): Flow<List<StandingEntry>> =
        standingDao.observeAllStandings().map { list -> list.map { it.toDomain() } }

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
            val existing = matchDao.getAllMatchesOnce().map { it.toDomain() }
            val payload = dataSource.fetchAll(prefs)

            val diffResult = ScheduleDiffEngine.diff(
                existing = existing,
                incoming = payload.matches,
                zoneId = zoneId,
            )

            matchDao.replaceAll(diffResult.mergedMatches.map { MatchEntity.fromDomain(it) })
            if (payload.standings.isNotEmpty()) {
                standingDao.replaceAll(payload.standings.map { StandingEntity.fromDomain(it) })
            }
            if (payload.players.isNotEmpty()) {
                _starPlayers.value = payload.players
            }

            val nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            val summaryText = if (diffResult.changes.isEmpty()) {
                "已同步 ${diffResult.mergedMatches.size} 场 · ${payload.standings.size} 条积分"
            } else {
                "已同步 ${diffResult.mergedMatches.size} 场 · ${diffResult.changes.size} 项赛程更新"
            }
            prefsRepo.recordSyncResult(nowIso, summaryText)

            SyncSummary(
                syncedAtUtc = nowIso,
                totalMatches = diffResult.mergedMatches.size,
                changes = diffResult.changes,
                sourceMode = prefs.dataSourceMode,
                message = summaryText,
            )
        }
    }

    suspend fun searchExternalPlayer(query: String): Result<StarPlayer?> {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return Result.success(null)

        // Check built-in/synced players first
        val localHit = _starPlayers.value.firstOrNull {
            it.zhName.contains(cleaned, ignoreCase = true) ||
                it.name.contains(cleaned, ignoreCase = true)
        }
        if (localHit != null) return Result.success(localHit)

        return runCatching {
            val slug = URLEncoder.encode(cleaned.replace(' ', '_'), "UTF-8")
            val summary = apiService.fetchWikipediaSummary(
                "https://en.wikipedia.org/api/rest_v1/page/summary/$slug",
            )
            val portrait = summary.thumbnail?.source ?: ""
            if (summary.title.isBlank()) return@runCatching null
            val created = StarPlayer(
                id = "wiki-${summary.title.lowercase().replace(' ', '-')}",
                name = summary.title,
                zhName = summary.title,
                clubName = "",
                clubZhName = "实时检索",
                clubCrestUrl = "",
                nationalName = "",
                nationalZhName = summary.description.ifBlank { "职业球员" },
                nationalCrestUrl = "",
                positionZh = summary.description.ifBlank { "足球运动员" },
                nationalityZh = "全球",
                shirtNumber = 10,
                birthYear = 0,
                portraitUrl = portrait,
                bio = summary.extract,
            )
            _starPlayers.value = (_starPlayers.value + created).distinctBy { it.id }
            created
        }
    }
}
