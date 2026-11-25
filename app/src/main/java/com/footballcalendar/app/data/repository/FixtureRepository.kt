package com.footballcalendar.app.data.repository

import com.footballcalendar.app.data.local.MatchDao
import com.footballcalendar.app.data.local.MatchEntity
import com.footballcalendar.app.data.local.StandingDao
import com.footballcalendar.app.data.local.StandingEntity
import com.footballcalendar.app.data.preferences.UserPreferencesRepository
import com.footballcalendar.app.data.remote.FeedApiService
import com.footballcalendar.app.data.remote.FixtureDataSource
import com.footballcalendar.app.model.DataSourceMode
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
                        shirtNumber = 10,
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

            // Resolve official Chinese Wikipedia title and description if available
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

            val displayZhName = zhSummary?.title?.ifBlank { null } ?: zhTitle ?: enSummary.title
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
                shirtNumber = 10,
                birthYear = 0,
                portraitUrl = portrait,
                bio = displayBio,
            )
            _starPlayers.value = (_starPlayers.value + created).distinctBy { it.id }
            created
        }
    }
}
