package com.footballcalendar.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.DataSourceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "football_calendar_prefs",
)

data class UserPreferences(
    val dataSourceMode: DataSourceMode = DataSourceMode.DEMO,
    val githubPagesUrl: String = DEFAULT_GITHUB_PAGES_URL,
    val footballDataApiKey: String = "",
    val followedCompetitions: Set<String> = CompetitionCatalog.defaultCodes,
    val favoriteTeams: Set<String> = setOf("阿森纳", "利物浦", "皇家马德里"),
    val autoSyncEnabled: Boolean = true,
    val syncIntervalHours: Int = 6,
    val wifiOnlySync: Boolean = false,
    val watchNightGrouping: Boolean = true,
    val notifyOnScheduleChange: Boolean = true,
    val notifyBeforeMatchMinutes: Int = 30,
    val autoSyncSystemCalendar: Boolean = false,
    val lastSyncUtc: String = "",
    val lastSyncSummary: String = "尚未同步",
    val notifiedMatchIds: Set<String> = emptySet(),
    val demoMutationStep: Int = 0,
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val DATA_SOURCE_MODE = stringPreferencesKey("data_source_mode")
        val GITHUB_PAGES_URL = stringPreferencesKey("github_pages_url")
        val FOOTBALL_DATA_API_KEY = stringPreferencesKey("football_data_api_key")
        val FOLLOWED_COMPETITIONS = stringSetPreferencesKey("followed_competitions")
        val FAVORITE_TEAMS = stringSetPreferencesKey("favorite_teams")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        val WIFI_ONLY_SYNC = booleanPreferencesKey("wifi_only_sync")
        val WATCH_NIGHT_GROUPING = booleanPreferencesKey("watch_night_grouping")
        val NOTIFY_ON_SCHEDULE_CHANGE = booleanPreferencesKey("notify_on_schedule_change")
        val NOTIFY_BEFORE_MATCH_MINUTES = intPreferencesKey("notify_before_match_minutes")
        val AUTO_SYNC_SYSTEM_CALENDAR = booleanPreferencesKey("auto_sync_system_calendar")
        val LAST_SYNC_UTC = stringPreferencesKey("last_sync_utc")
        val LAST_SYNC_SUMMARY = stringPreferencesKey("last_sync_summary")
        val NOTIFIED_MATCH_IDS = stringSetPreferencesKey("notified_match_ids")
        val DEMO_MUTATION_STEP = intPreferencesKey("demo_mutation_step")
    }

    val preferencesFlow: Flow<UserPreferences> = context.userPrefsDataStore.data.map { prefs ->
        UserPreferences(
            dataSourceMode = DataSourceMode.fromName(prefs[Keys.DATA_SOURCE_MODE]),
            githubPagesUrl = prefs[Keys.GITHUB_PAGES_URL] ?: DEFAULT_GITHUB_PAGES_URL,
            footballDataApiKey = prefs[Keys.FOOTBALL_DATA_API_KEY] ?: "",
            followedCompetitions = prefs[Keys.FOLLOWED_COMPETITIONS] ?: CompetitionCatalog.defaultCodes,
            favoriteTeams = prefs[Keys.FAVORITE_TEAMS] ?: setOf("阿森纳", "利物浦", "皇家马德里"),
            autoSyncEnabled = prefs[Keys.AUTO_SYNC_ENABLED] ?: true,
            syncIntervalHours = prefs[Keys.SYNC_INTERVAL_HOURS] ?: 6,
            wifiOnlySync = prefs[Keys.WIFI_ONLY_SYNC] ?: false,
            watchNightGrouping = prefs[Keys.WATCH_NIGHT_GROUPING] ?: true,
            notifyOnScheduleChange = prefs[Keys.NOTIFY_ON_SCHEDULE_CHANGE] ?: true,
            notifyBeforeMatchMinutes = prefs[Keys.NOTIFY_BEFORE_MATCH_MINUTES] ?: 30,
            autoSyncSystemCalendar = prefs[Keys.AUTO_SYNC_SYSTEM_CALENDAR] ?: false,
            lastSyncUtc = prefs[Keys.LAST_SYNC_UTC] ?: "",
            lastSyncSummary = prefs[Keys.LAST_SYNC_SUMMARY] ?: "尚未同步",
            notifiedMatchIds = prefs[Keys.NOTIFIED_MATCH_IDS] ?: emptySet(),
            demoMutationStep = prefs[Keys.DEMO_MUTATION_STEP] ?: 0,
        )
    }

    suspend fun currentPreferences(): UserPreferences = preferencesFlow.first()

    suspend fun updateDataSource(mode: DataSourceMode, githubPagesUrl: String, apiKey: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.DATA_SOURCE_MODE] = mode.name
            prefs[Keys.GITHUB_PAGES_URL] = githubPagesUrl.trim()
            prefs[Keys.FOOTBALL_DATA_API_KEY] = apiKey.trim()
        }
    }

    suspend fun toggleCompetition(code: String) {
        context.userPrefsDataStore.edit { prefs ->
            val current = prefs[Keys.FOLLOWED_COMPETITIONS]?.toMutableSet()
                ?: CompetitionCatalog.defaultCodes.toMutableSet()
            if (code in current) {
                if (current.size > 1) current.remove(code)
            } else {
                current.add(code)
            }
            prefs[Keys.FOLLOWED_COMPETITIONS] = current
        }
    }

    suspend fun toggleFavoriteTeam(teamName: String) {
        val cleaned = teamName.trim()
        if (cleaned.isEmpty()) return
        context.userPrefsDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_TEAMS]?.toMutableSet()
                ?: mutableSetOf("阿森纳", "利物浦", "皇家马德里")
            if (cleaned in current) current.remove(cleaned) else current.add(cleaned)
            prefs[Keys.FAVORITE_TEAMS] = current
        }
    }

    suspend fun updateSyncSettings(autoSyncEnabled: Boolean, intervalHours: Int, wifiOnly: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC_ENABLED] = autoSyncEnabled
            prefs[Keys.SYNC_INTERVAL_HOURS] = intervalHours.coerceIn(6, 24)
            prefs[Keys.WIFI_ONLY_SYNC] = wifiOnly
        }
    }

    suspend fun updateDisplayAndNotificationSettings(
        watchNightGrouping: Boolean,
        notifyOnScheduleChange: Boolean,
        notifyBeforeMinutes: Int,
        autoSyncSystemCalendar: Boolean,
    ) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.WATCH_NIGHT_GROUPING] = watchNightGrouping
            prefs[Keys.NOTIFY_ON_SCHEDULE_CHANGE] = notifyOnScheduleChange
            prefs[Keys.NOTIFY_BEFORE_MATCH_MINUTES] = notifyBeforeMinutes
            prefs[Keys.AUTO_SYNC_SYSTEM_CALENDAR] = autoSyncSystemCalendar
        }
    }

    suspend fun recordSyncResult(syncedAtUtc: String, summary: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_UTC] = syncedAtUtc
            prefs[Keys.LAST_SYNC_SUMMARY] = summary
        }
    }

    suspend fun markMatchesNotified(matchIds: Set<String>) {
        if (matchIds.isEmpty()) return
        context.userPrefsDataStore.edit { prefs ->
            val existing = prefs[Keys.NOTIFIED_MATCH_IDS] ?: emptySet()
            prefs[Keys.NOTIFIED_MATCH_IDS] = (existing + matchIds).takeLastCompat(200)
        }
    }

    suspend fun bumpDemoMutationStep(): Int {
        var next = 0
        context.userPrefsDataStore.edit { prefs ->
            val cur = prefs[Keys.DEMO_MUTATION_STEP] ?: 0
            next = cur + 1
            prefs[Keys.DEMO_MUTATION_STEP] = next
        }
        return next
    }

    private fun Set<String>.takeLastCompat(limit: Int): Set<String> {
        if (size <= limit) return this
        return toList().takeLast(limit).toSet()
    }

    companion object {
        const val DEFAULT_GITHUB_PAGES_URL =
            "https://kiki1112.github.io/football-calendar-android/fixtures.json"
    }
}
