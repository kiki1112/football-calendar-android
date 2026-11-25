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
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.DataSourceMode
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.ScheduleChange
import com.footballcalendar.app.model.ScheduleChangeType
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.StarPlayer
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isMonthExpanded: Boolean = true,
    val calendarFilterTab: Int = 0, // 0=全部关注, 1=只看主队, 2=必看清单
    val selectedLeagueFilter: String? = null, // null=全部, "NAT"=国家队合集, or code
    val allMatches: List<MatchItem> = emptyList(),
    val filteredMatches: List<MatchItem> = emptyList(),
    val matchesByDate: Map<LocalDate, List<MatchItem>> = emptyMap(),
    val selectedDayMatches: List<MatchItem> = emptyList(),
    val heroMatch: MatchItem? = null,
    val dayClashWarnings: List<String> = emptyList(),
    val standingsByLeague: Map<String, List<StandingEntry>> = emptyMap(),
    val teamRankByName: Map<String, StandingEntry> = emptyMap(),
    val teamRankByLeagueAndName: Map<Pair<String, String>, StandingEntry> = emptyMap(),
    val selectedStandingsLeague: String = "PL",
    val starPlayers: List<StarPlayer> = emptyList(),
    val selectedStarPlayer: StarPlayer? = null,
    val isSearchingPlayer: Boolean = false,
    val prefs: UserPreferences = UserPreferences(),
    val isSyncing: Boolean = false,
    val recentChanges: List<ScheduleChange> = emptyList(),
    val bannerMessage: String? = null,
    val selectedMatchDetail: MatchItem? = null,
) {
    fun standingFor(competitionCode: String, teamZh: String, teamEn: String): StandingEntry? {
        return teamRankByLeagueAndName[competitionCode to teamZh]
            ?: teamRankByLeagueAndName[competitionCode to teamEn]
    }
}

private data class UiInteractionState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isMonthExpanded: Boolean = true,
    val calendarFilterTab: Int = 0,
    val selectedLeagueFilter: String? = null,
    val selectedStandingsLeague: String = "PL",
    val selectedStarPlayer: StarPlayer? = null,
    val isSearchingPlayer: Boolean = false,
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

    private val uiInteraction = MutableStateFlow(UiInteractionState())

    val uiState: StateFlow<CalendarUiState> = combine(
        fixtureRepo.observeAllMatches(),
        fixtureRepo.observeAllStandings(),
        fixtureRepo.starPlayersFlow,
        prefsRepo.preferencesFlow,
        uiInteraction,
    ) { allMatches, allStandings, players, prefs, ui ->
        val zoneId = MatchTimeFormatter.BEIJING_ZONE
        val filtered = allMatches.filter { match ->
            val leagueAllowed = match.competitionCode in prefs.followedCompetitions
            val chipAllowed = when (ui.selectedLeagueFilter) {
                null -> true
                "NAT" -> match.isInternational
                else -> match.competitionCode == ui.selectedLeagueFilter
            }
            val tabAllowed = when (ui.calendarFilterTab) {
                1 -> match.involvesAnyTeam(prefs.favoriteTeams)
                2 -> match.id in prefs.watchlistMatchIds
                else -> true
            }
            leagueAllowed && chipAllowed && tabAllowed
        }

        val grouped = filtered.groupBy { match ->
            MatchTimeFormatter.displayDateFor(
                utcIso = match.utcDate,
                zoneId = zoneId,
                watchNightGrouping = prefs.watchNightGrouping,
            ) ?: LocalDate.now(zoneId)
        }

        val dayMatches = grouped[ui.selectedDate].orEmpty().sortedBy { it.utcDate }
        val clashWarnings = detectScheduleClashes(dayMatches, zoneId)

        val now = Instant.now().minusSeconds(1800)
        val upcomingFollowed = allMatches.filter { match ->
            !match.status.isCancelledOrPostponed &&
                match.competitionCode in prefs.followedCompetitions &&
                (MatchTimeFormatter.parseInstant(match.utcDate)?.isAfter(now) == true)
        }.sortedBy { it.utcDate }

        val hero = upcomingFollowed.firstOrNull { it.id in prefs.watchlistMatchIds }
            ?: upcomingFollowed.firstOrNull { it.involvesAnyTeam(prefs.favoriteTeams) }
            ?: upcomingFollowed.firstOrNull()

        val standingsGrouped = allStandings.groupBy { it.competitionCode }
        val rankMap = buildMap<String, StandingEntry> {
            allStandings.forEach { entry ->
                putIfAbsent(entry.teamName, entry)
                putIfAbsent(entry.teamZhName, entry)
            }
        }
        val leagueRankMap = buildMap<Pair<String, String>, StandingEntry> {
            allStandings.forEach { entry ->
                put(entry.competitionCode to entry.teamName, entry)
                put(entry.competitionCode to entry.teamZhName, entry)
            }
        }

        CalendarUiState(
            currentMonth = ui.currentMonth,
            selectedDate = ui.selectedDate,
            isMonthExpanded = ui.isMonthExpanded,
            calendarFilterTab = ui.calendarFilterTab,
            selectedLeagueFilter = ui.selectedLeagueFilter,
            allMatches = allMatches,
            filteredMatches = filtered,
            matchesByDate = grouped,
            selectedDayMatches = dayMatches,
            heroMatch = hero,
            dayClashWarnings = clashWarnings,
            standingsByLeague = standingsGrouped,
            teamRankByName = rankMap,
            teamRankByLeagueAndName = leagueRankMap,
            selectedStandingsLeague = ui.selectedStandingsLeague,
            starPlayers = players,
            selectedStarPlayer = ui.selectedStarPlayer,
            isSearchingPlayer = ui.isSearchingPlayer,
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

    private fun detectScheduleClashes(dayMatches: List<MatchItem>, zoneId: ZoneId): List<String> {
        if (dayMatches.size < 2) return emptyList()
        val warnings = mutableListOf<String>()
        for (i in 0 until dayMatches.size - 1) {
            val a = dayMatches[i]
            val b = dayMatches[i + 1]
            val ta = MatchTimeFormatter.parseInstant(a.utcDate) ?: continue
            val tb = MatchTimeFormatter.parseInstant(b.utcDate) ?: continue
            val gapMins = Duration.between(ta, tb).abs().toMinutes()
            if (gapMins <= 75) {
                val timeStr = MatchTimeFormatter.formatKickoffTime(a.utcDate, zoneId)
                warnings += "$timeStr 附近「${a.homeTeamZhName} vs ${a.awayTeamZhName}」与「${b.homeTeamZhName} vs ${b.awayTeamZhName}」时间重叠"
            }
        }
        return warnings
    }

    fun selectDate(date: LocalDate) {
        uiInteraction.update {
            it.copy(selectedDate = date, currentMonth = YearMonth.from(date))
        }
    }

    fun previousMonth() {
        uiInteraction.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) }
    }

    fun nextMonth() {
        uiInteraction.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) }
    }

    fun jumpToToday() {
        val today = LocalDate.now()
        uiInteraction.update { it.copy(selectedDate = today, currentMonth = YearMonth.from(today)) }
    }

    fun toggleMonthExpanded() {
        uiInteraction.update { it.copy(isMonthExpanded = !it.isMonthExpanded) }
    }

    fun setCalendarFilterTab(tab: Int) {
        uiInteraction.update { it.copy(calendarFilterTab = tab) }
    }

    fun selectLeagueFilter(code: String?) {
        uiInteraction.update {
            val next = if (it.selectedLeagueFilter == code) null else code
            it.copy(selectedLeagueFilter = next)
        }
    }

    fun selectStandingsLeague(code: String) {
        uiInteraction.update { it.copy(selectedStandingsLeague = code) }
    }

    fun openMatchDetail(match: MatchItem) {
        uiInteraction.update { it.copy(selectedMatchDetail = match) }
    }

    fun closeMatchDetail() {
        uiInteraction.update { it.copy(selectedMatchDetail = null) }
    }

    fun openPlayerDetail(player: StarPlayer) {
        uiInteraction.update { it.copy(selectedStarPlayer = player) }
    }

    fun closePlayerDetail() {
        uiInteraction.update { it.copy(selectedStarPlayer = null) }
    }

    fun clearBanner() {
        uiInteraction.update { it.copy(bannerMessage = null) }
    }

    fun dismissChangesBanner() {
        uiInteraction.update { it.copy(recentChanges = emptyList()) }
    }

    fun toggleWatchlistMatch(matchId: String) {
        viewModelScope.launch {
            prefsRepo.toggleWatchlistMatch(matchId)
        }
    }

    fun toggleFavoritePlayer(playerId: String) {
        viewModelScope.launch {
            prefsRepo.toggleFavoritePlayer(playerId)
        }
    }

    fun searchExternalPlayer(query: String) {
        if (query.isBlank() || uiInteraction.value.isSearchingPlayer) return
        viewModelScope.launch {
            uiInteraction.update { it.copy(isSearchingPlayer = true) }
            val result = withContext(Dispatchers.IO) {
                fixtureRepo.searchExternalPlayer(query)
            }
            result.onSuccess { player ->
                uiInteraction.update {
                    if (player != null) {
                        it.copy(isSearchingPlayer = false, selectedStarPlayer = player)
                    } else {
                        it.copy(isSearchingPlayer = false, bannerMessage = "未找到「$query」的公开资料，可尝试输入英文全名")
                    }
                }
            }.onFailure {
                uiInteraction.update {
                    it.copy(isSearchingPlayer = false, bannerMessage = "检索球星失败，请检查网络连接或使用英文名")
                }
            }
        }
    }

    fun syncNow(simulateDemoChange: Boolean = false) {
        if (uiInteraction.value.isSyncing) return
        viewModelScope.launch {
            uiInteraction.update { it.copy(isSyncing = true) }
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
                        calendarManager.syncMatchesToDeviceCalendar(uiState.value.filteredMatches)
                    }
                }
                NextMatchWidgetProvider.updateAllWidgets(getApplication())
                uiInteraction.update {
                    it.copy(
                        isSyncing = false,
                        recentChanges = summary.changes,
                        bannerMessage = summary.message,
                    )
                }
            }.onFailure { err ->
                uiInteraction.update {
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
            uiInteraction.update {
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
                uiInteraction.update { it.copy(bannerMessage = "请先授予手机日历读写权限") }
                return@launch
            }
            val count = withContext(Dispatchers.IO) {
                calendarManager.syncMatchesToDeviceCalendar(uiState.value.filteredMatches)
            }
            uiInteraction.update {
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
        uiInteraction.update { it.copy(bannerMessage = message) }
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
