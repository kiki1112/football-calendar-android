package com.footballcalendar.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.footballcalendar.app.FootballCalendarApplication
import com.footballcalendar.app.data.preferences.UserPreferences
import com.footballcalendar.app.model.DataSourceMode
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.ScheduleChange
import com.footballcalendar.app.model.ScheduleChangeType
import com.footballcalendar.app.util.IcsExporter
import com.footballcalendar.app.util.MatchTimeFormatter
import com.footballcalendar.app.widget.NextMatchWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isMonthExpanded: Boolean = true,
    val onlyFavorites: Boolean = false,
    val selectedLeagueFilter: String? = null,
    val allMatches: List<MatchItem> = emptyList(),
    val filteredMatches: List<MatchItem> = emptyList(),
    val matchesByDate: Map<LocalDate, List<MatchItem>> = emptyMap(),
    val selectedDayMatches: List<MatchItem> = emptyList(),
    val prefs: UserPreferences = UserPreferences(),
    val isSyncing: Boolean = false,
    val recentChanges: List<ScheduleChange> = emptyList(),
    val bannerMessage: String? = null,
    val selectedMatchDetail: MatchItem? = null,
)

private data class CalendarViewFilterState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isMonthExpanded: Boolean = true,
    val onlyFavorites: Boolean = false,
    val selectedLeagueFilter: String? = null,
    val isSyncing: Boolean = false,
    val recentChanges: List<ScheduleChange> = emptyList(),
    val bannerMessage: String? = null,
    val selectedMatchDetail: MatchItem? = null,
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as FootballCalendarApplication).container
    private val fixtureRepo = container.fixtureRepository
    private val prefsRepo = container.prefsRepository
    private val syncScheduler = container.syncScheduler
    private val calendarManager = container.systemCalendarSyncManager
    private val notificationHelper = container.notificationHelper

    private val filterState = MutableStateFlow(CalendarViewFilterState())

    val uiState: StateFlow<CalendarUiState> = combine(
        fixtureRepo.observeAllMatches(),
        prefsRepo.preferencesFlow,
        filterState,
    ) { allMatches, prefs, ui ->
        val zoneId = ZoneId.systemDefault()
        val filtered = allMatches.filter { match ->
            val leagueAllowed = match.competitionCode in prefs.followedCompetitions
            val chipAllowed = ui.selectedLeagueFilter == null || match.competitionCode == ui.selectedLeagueFilter
            val favAllowed = !ui.onlyFavorites || match.involvesAnyTeam(prefs.favoriteTeams)
            leagueAllowed && chipAllowed && favAllowed
        }

        val grouped = filtered.groupBy { match ->
            MatchTimeFormatter.displayDateFor(
                utcIso = match.utcDate,
                zoneId = zoneId,
                watchNightGrouping = prefs.watchNightGrouping,
            ) ?: LocalDate.now(zoneId)
        }

        val dayMatches = grouped[ui.selectedDate].orEmpty().sortedBy { it.utcDate }

        CalendarUiState(
            currentMonth = ui.currentMonth,
            selectedDate = ui.selectedDate,
            isMonthExpanded = ui.isMonthExpanded,
            onlyFavorites = ui.onlyFavorites,
            selectedLeagueFilter = ui.selectedLeagueFilter,
            allMatches = allMatches,
            filteredMatches = filtered,
            matchesByDate = grouped,
            selectedDayMatches = dayMatches,
            prefs = prefs,
            isSyncing = ui.isSyncing,
            recentChanges = ui.recentChanges,
            bannerMessage = ui.bannerMessage,
            selectedMatchDetail = ui.selectedMatchDetail,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    fun selectDate(date: LocalDate) {
        filterState.update {
            it.copy(
                selectedDate = date,
                currentMonth = YearMonth.from(date),
            )
        }
    }

    fun previousMonth() {
        filterState.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) }
    }

    fun nextMonth() {
        filterState.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) }
    }

    fun jumpToToday() {
        val today = LocalDate.now()
        filterState.update {
            it.copy(
                selectedDate = today,
                currentMonth = YearMonth.from(today),
            )
        }
    }

    fun toggleMonthExpanded() {
        filterState.update { it.copy(isMonthExpanded = !it.isMonthExpanded) }
    }

    fun toggleOnlyFavorites() {
        filterState.update { it.copy(onlyFavorites = !it.onlyFavorites) }
    }

    fun selectLeagueFilter(code: String?) {
        filterState.update {
            val next = if (it.selectedLeagueFilter == code) null else code
            it.copy(selectedLeagueFilter = next)
        }
    }

    fun openMatchDetail(match: MatchItem) {
        filterState.update { it.copy(selectedMatchDetail = match) }
    }

    fun closeMatchDetail() {
        filterState.update { it.copy(selectedMatchDetail = null) }
    }

    fun clearBanner() {
        filterState.update { it.copy(bannerMessage = null) }
    }

    fun dismissChangesBanner() {
        filterState.update { it.copy(recentChanges = emptyList()) }
    }

    fun syncNow(simulateDemoChange: Boolean = false) {
        if (filterState.value.isSyncing) return
        viewModelScope.launch {
            filterState.update { it.copy(isSyncing = true) }
            if (simulateDemoChange) {
                prefsRepo.bumpDemoMutationStep()
            }
            val result = withContext(Dispatchers.IO) {
                fixtureRepo.syncFixtures()
            }
            result.onSuccess { summary ->
                val prefs = prefsRepo.currentPreferences()
                val meaningful = summary.changes.filter {
                    it.type == ScheduleChangeType.RESCHEDULED ||
                        it.type == ScheduleChangeType.POSTPONED ||
                        it.type == ScheduleChangeType.ADDED
                }
                if (prefs.notifyOnScheduleChange && meaningful.isNotEmpty()) {
                    notificationHelper.notifyScheduleChanges(meaningful)
                }
                if (prefs.autoSyncSystemCalendar) {
                    withContext(Dispatchers.IO) {
                        val toSync = uiState.value.filteredMatches
                        calendarManager.syncMatchesToDeviceCalendar(toSync)
                    }
                }
                NextMatchWidgetProvider.updateAllWidgets(getApplication())
                filterState.update {
                    it.copy(
                        isSyncing = false,
                        recentChanges = summary.changes,
                        bannerMessage = summary.message,
                    )
                }
            }.onFailure { err ->
                filterState.update {
                    it.copy(
                        isSyncing = false,
                        bannerMessage = "同步失败：${err.message ?: "请检查网络或订阅地址"}",
                    )
                }
            }
        }
    }

    fun toggleCompetition(code: String) {
        viewModelScope.launch {
            prefsRepo.toggleCompetition(code)
            NextMatchWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun toggleFavoriteTeam(teamName: String) {
        viewModelScope.launch {
            prefsRepo.toggleFavoriteTeam(teamName)
            NextMatchWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun saveDataSource(mode: DataSourceMode, githubPagesUrl: String, apiKey: String) {
        viewModelScope.launch {
            prefsRepo.updateDataSource(mode, githubPagesUrl, apiKey)
            syncNow(simulateDemoChange = false)
        }
    }

    fun updateSyncSettings(enabled: Boolean, intervalHours: Int, wifiOnly: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateSyncSettings(enabled, intervalHours, wifiOnly)
            syncScheduler.configurePeriodicSync(enabled, intervalHours, wifiOnly)
            filterState.update {
                it.copy(
                    bannerMessage = if (enabled) {
                        "已开启后台自动同步（每 ${intervalHours} 小时）"
                    } else {
                        "已暂停后台自动同步"
                    },
                )
            }
        }
    }

    fun updateDisplayAndNotificationSettings(
        watchNightGrouping: Boolean,
        notifyOnScheduleChange: Boolean,
        notifyBeforeMinutes: Int,
        autoSyncSystemCalendar: Boolean,
    ) {
        viewModelScope.launch {
            prefsRepo.updateDisplayAndNotificationSettings(
                watchNightGrouping = watchNightGrouping,
                notifyOnScheduleChange = notifyOnScheduleChange,
                notifyBeforeMinutes = notifyBeforeMinutes,
                autoSyncSystemCalendar = autoSyncSystemCalendar,
            )
            if (autoSyncSystemCalendar) {
                syncMatchesToSystemCalendarNow()
            }
        }
    }

    fun syncMatchesToSystemCalendarNow() {
        viewModelScope.launch {
            if (!calendarManager.hasCalendarPermissions()) {
                filterState.update { it.copy(bannerMessage = "请先授予手机日历读写权限") }
                return@launch
            }
            val count = withContext(Dispatchers.IO) {
                calendarManager.syncMatchesToDeviceCalendar(uiState.value.filteredMatches)
            }
            filterState.update {
                it.copy(bannerMessage = "已同步 $count 场关注赛事到手机系统日历")
            }
        }
    }

    fun buildAddToCalendarIntent(match: MatchItem): Intent =
        calendarManager.buildAddToCalendarIntent(match)

    fun buildShareIcsIntent(matches: List<MatchItem>, fileName: String = "football_schedule.ics"): Intent? {
        if (matches.isEmpty()) return null
        val app = getApplication<Application>()
        val icsDir = File(app.cacheDir, "ics").apply { mkdirs() }
        val file = File(icsDir, fileName)
        file.writeText(IcsExporter.buildIcsContent(matches), Charsets.UTF_8)
        val uri: Uri = FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "足球赛事日历 (.ics)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun showBanner(message: String) {
        filterState.update { it.copy(bannerMessage = message) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                CalendarViewModel(app)
            }
        }
    }
}
