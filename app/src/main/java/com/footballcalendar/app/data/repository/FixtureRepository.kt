package com.footballcalendar.app.data.repository

import com.footballcalendar.app.data.local.MatchDao
import com.footballcalendar.app.data.local.MatchEntity
import com.footballcalendar.app.data.preferences.UserPreferencesRepository
import com.footballcalendar.app.data.remote.FixtureDataSource
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.SyncSummary
import com.footballcalendar.app.util.ScheduleDiffEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FixtureRepository(
    private val matchDao: MatchDao,
    private val prefsRepo: UserPreferencesRepository,
    private val dataSource: FixtureDataSource,
) {
    fun observeAllMatches(): Flow<List<MatchItem>> =
        matchDao.observeAllMatches().map { list -> list.map { it.toDomain() } }

    suspend fun getAllMatchesOnce(): List<MatchItem> =
        matchDao.getAllMatchesOnce().map { it.toDomain() }

    suspend fun ensureSeededIfEmpty() {
        if (matchDao.getAllMatchesOnce().isEmpty()) {
            runCatching { syncFixtures() }
        }
    }

    suspend fun syncFixtures(zoneId: ZoneId = ZoneId.systemDefault()): Result<SyncSummary> {
        return runCatching {
            val prefs = prefsRepo.currentPreferences()
            val existing = matchDao.getAllMatchesOnce().map { it.toDomain() }
            val incoming = dataSource.fetchMatches(prefs)

            val diffResult = ScheduleDiffEngine.diff(
                existing = existing,
                incoming = incoming,
                zoneId = zoneId,
            )

            matchDao.replaceAll(diffResult.mergedMatches.map { MatchEntity.fromDomain(it) })

            val nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            val summaryText = if (diffResult.changes.isEmpty()) {
                "已同步 ${diffResult.mergedMatches.size} 场（无赛程变更）"
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
}
