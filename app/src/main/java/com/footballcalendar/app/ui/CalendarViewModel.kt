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
import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.ScheduleChange
import com.footballcalendar.app.model.ScheduleChangeType
import com.footballcalendar.app.model.SquadMember
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.StarPlayer
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.util.IcsExporter
import com.footballcalendar.app.util.MatchTimeFormatter
import com.footballcalendar.app.util.TeamAssetCatalog
import com.footballcalendar.app.util.TeamLocalization
import com.footballcalendar.app.widget.NextMatchWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

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
    val standingsByLeague: Map<String, List<StandingEntry>> = emptyMap(),
    val teamRankByName: Map<String, StandingEntry> = emptyMap(),
    val teamRankByLeagueAndName: Map<Pair<String, String>, StandingEntry> = emptyMap(),
    val teamsByName: Map<String, TeamDetailInfo> = emptyMap(),
    val allTeams: List<TeamDetailInfo> = emptyList(),
    val selectedStandingsLeague: String = "PL",
    val selectedTeamDetail: TeamDetailInfo? = null,
    val isLoadingTeamDetail: Boolean = false,
    val starPlayers: List<StarPlayer> = emptyList(),
    val selectedStarPlayer: StarPlayer? = null,
    val isSearchingPlayer: Boolean = false,
    val prefs: UserPreferences = UserPreferences(),
    val isSyncing: Boolean = false,
    val recentChanges: List<ScheduleChange> = emptyList(),
    val bannerMessage: String? = null,
    val selectedMatchDetail: MatchItem? = null,
    val selectedMatchLiveDetail: LiveMatchDetail? = null,
    val isLoadingMatchDetail: Boolean = false,
    val matchDetailNote: String? = null,
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
    val selectedTeamDetail: TeamDetailInfo? = null,
    val isLoadingTeamDetail: Boolean = false,
    val isSearchingPlayer: Boolean = false,
    val isSyncing: Boolean = false,
    val recentChanges: List<ScheduleChange> = emptyList(),
    val bannerMessage: String? = null,
    val selectedMatchDetail: MatchItem? = null,
    val selectedMatchLiveDetail: LiveMatchDetail? = null,
    val isLoadingMatchDetail: Boolean = false,
    val matchDetailNote: String? = null,
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as FootballCalendarApplication).container
    private val fixtureRepo = container.fixtureRepository
    private val prefsRepo = container.prefsRepository
    private val syncScheduler = container.syncScheduler
    private val calendarManager = container.systemCalendarSyncManager
    private val notificationHelper = container.notificationHelper

    private val uiInteraction = MutableStateFlow(UiInteractionState())
    private var liveTickerJob: Job? = null
    private var detailPollingJob: Job? = null

    private val coreDataFlow = combine(
        fixtureRepo.observeAllMatches(),
        fixtureRepo.observeAllStandings(),
        fixtureRepo.observeAllTeams(),
        fixtureRepo.starPlayersFlow,
    ) { matches, standings, teams, players ->
        QuadData(matches, standings, teams, players)
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        coreDataFlow,
        prefsRepo.preferencesFlow,
        uiInteraction,
    ) { core, prefs, ui ->
        val allMatches = core.matches
        val allStandings = core.standings
        val allTeams = core.teams
        val basePlayers = core.players

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
        val teamsMap = buildMap<String, TeamDetailInfo> {
            allTeams.forEach { t ->
                put(t.name.lowercase(), t)
                put(t.zhName, t)
                put(t.shortName.lowercase(), t)
            }
        }

        // Merge Scorer Leaderboard + All Squad Players across all teams cleanly
        val mergedPlayers = buildList {
            val seenKey = HashSet<String>()
            basePlayers.forEach { p ->
                val key = "${p.name.lowercase()}|${p.clubName.lowercase()}"
                if (seenKey.add(key)) add(p)
            }
            allTeams.forEach { team ->
                team.squad.forEach { sq ->
                    val key = "${sq.name.lowercase()}|${team.name.lowercase()}"
                    if (seenKey.add(key)) {
                        add(
                            StarPlayer(
                                id = sq.id,
                                name = sq.name,
                                zhName = sq.zhName,
                                clubName = team.name,
                                clubZhName = team.zhName,
                                clubCrestUrl = team.crest,
                                nationalName = sq.nationality,
                                nationalZhName = TeamLocalization.localizeTeam(sq.nationality).ifBlank { sq.nationality },
                                nationalCrestUrl = TeamAssetCatalog.resolveTeamLogo(sq.nationality),
                                positionZh = sq.positionZh,
                                nationalityZh = sq.nationality,
                                shirtNumber = sq.shirtNumber,
                                birthYear = sq.birthYear,
                                portraitUrl = sq.portraitUrl,
                                competitionCode = team.competitionCode,
                                isTransliterated = sq.isTransliterated,
                            ),
                        )
                    }
                }
            }
        }

        // Sync selectedMatchDetail if scores were refreshed in Room
        val latestSelectedMatch = ui.selectedMatchDetail?.let { sel ->
            allMatches.firstOrNull { it.id == sel.id } ?: sel
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
            standingsByLeague = standingsGrouped,
            teamRankByName = rankMap,
            teamRankByLeagueAndName = leagueRankMap,
            teamsByName = teamsMap,
            allTeams = allTeams,
            selectedStandingsLeague = ui.selectedStandingsLeague,
            selectedTeamDetail = ui.selectedTeamDetail,
            isLoadingTeamDetail = ui.isLoadingTeamDetail,
            starPlayers = mergedPlayers,
            selectedStarPlayer = ui.selectedStarPlayer,
            isSearchingPlayer = ui.isSearchingPlayer,
            prefs = prefs,
            isSyncing = ui.isSyncing,
            recentChanges = ui.recentChanges,
            bannerMessage = ui.bannerMessage,
            selectedMatchDetail = latestSelectedMatch,
            selectedMatchLiveDetail = ui.selectedMatchLiveDetail,
            isLoadingMatchDetail = ui.isLoadingMatchDetail,
            matchDetailNote = ui.matchDetailNote,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

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
        detailPollingJob?.cancel()
        detailPollingJob = null
        val cached = fixtureRepo.getCachedLiveDetail(match.id)
        val shouldSync = shouldAutoSyncDetail(match)
        uiInteraction.update {
            it.copy(
                selectedMatchDetail = match,
                selectedMatchLiveDetail = cached,
                isLoadingMatchDetail = cached == null && shouldSync,
                matchDetailNote = if (cached == null && !shouldSync) {
                    "已接入 ESPN 与 API-Football 零延迟引擎 · 开赛前约 45 分钟自动同步首发阵容与实时事件"
                } else {
                    null
                },
            )
        }
        if (shouldSync) {
            startDetailStream(match)
        }
    }

    private fun shouldAutoSyncDetail(match: MatchItem): Boolean {
        if (match.status.isLive || match.status == MatchStatus.FINISHED ||
            match.espnEventId != null || match.apiFootballFixtureId != null
        ) {
            return true
        }
        val ko = MatchTimeFormatter.parseInstant(match.utcDate) ?: return false
        val mins = Duration.between(ko, Instant.now()).toMinutes()
        return mins in -75..210
    }

    private fun startDetailStream(initialMatch: MatchItem) {
        detailPollingJob?.cancel()
        detailPollingJob = viewModelScope.launch {
            fetchAndApplyMatchDetail(initialMatch, force = false)
            while (isActive) {
                val currentMatch = uiState.value.selectedMatchDetail ?: break
                val currentDetail = uiInteraction.value.selectedMatchLiveDetail
                val hasCompleteFinal = currentMatch.status == MatchStatus.FINISHED &&
                    currentDetail != null &&
                    (currentDetail.events.isNotEmpty() || currentDetail.lineups.isNotEmpty() || currentDetail.stats.isNotEmpty())
                if (hasCompleteFinal) break

                val ko = MatchTimeFormatter.parseInstant(currentMatch.utcDate)
                val inLiveOrLineupWindow = currentMatch.status.isLive ||
                    (ko != null && Duration.between(ko, Instant.now()).toMinutes() in -60..195)
                if (!inLiveOrLineupWindow) break

                delay(10_000L)
                val targetMatch = uiState.value.selectedMatchDetail ?: break
                fetchAndApplyMatchDetail(targetMatch, force = true)
            }
        }
    }

    private suspend fun fetchAndApplyMatchDetail(match: MatchItem, force: Boolean) {
        val result = withContext(Dispatchers.IO) {
            fixtureRepo.fetchMatchLiveDetail(match, forceRefresh = force)
        }
        result.onSuccess { detail ->
            uiInteraction.update {
                if (it.selectedMatchDetail?.id != match.id) return@update it
                it.copy(
                    selectedMatchLiveDetail = detail,
                    isLoadingMatchDetail = false,
                    matchDetailNote = if (detail.events.isEmpty() && detail.lineups.isEmpty() && detail.stats.isEmpty()) {
                        "已零延迟连接实时数据流：当前暂无事件记录（赛前约 45 分钟公布首发）"
                    } else {
                        null
                    },
                )
            }
        }.onFailure { err ->
            uiInteraction.update {
                if (it.selectedMatchDetail?.id != match.id) return@update it
                it.copy(
                    isLoadingMatchDetail = false,
                    matchDetailNote = if (it.selectedMatchLiveDetail == null) {
                        err.message ?: "暂无实时赛况详情"
                    } else {
                        it.matchDetailNote
                    },
                )
            }
        }
    }

    fun closeMatchDetail() {
        detailPollingJob?.cancel()
        detailPollingJob = null
        uiInteraction.update {
            it.copy(
                selectedMatchDetail = null,
                selectedMatchLiveDetail = null,
                isLoadingMatchDetail = false,
                matchDetailNote = null,
            )
        }
    }

    fun openTeamDetailFromStanding(entry: StandingEntry) {
        val currentState = uiState.value
        val cached = currentState.teamsByName[entry.teamName.lowercase()]
            ?: currentState.teamsByName[entry.teamZhName]

        val fallback = cached ?: TeamDetailInfo(
            teamId = currentState.allMatches.firstOrNull {
                it.homeTeamName.equals(entry.teamName, true)
            }?.homeTeamId ?: currentState.allMatches.firstOrNull {
                it.awayTeamName.equals(entry.teamName, true)
            }?.awayTeamId ?: 0,
            competitionCode = entry.competitionCode,
            name = entry.teamName,
            shortName = entry.teamName,
            zhName = entry.teamZhName,
            crest = entry.teamCrest,
            venue = "",
            founded = 0,
            clubColors = "",
            coachName = "",
            coachZhName = "",
            coachNationality = "",
            squad = emptyList(),
        )

        uiInteraction.update {
            it.copy(
                selectedTeamDetail = fallback,
                isLoadingTeamDetail = cached == null && fallback.teamId > 0,
            )
        }

        if (cached == null && fallback.teamId > 0) {
            viewModelScope.launch {
                val res = withContext(Dispatchers.IO) {
                    fixtureRepo.fetchTeamDetailOnDemand(
                        teamName = entry.teamName,
                        teamId = fallback.teamId,
                        compCode = entry.competitionCode,
                        crest = entry.teamCrest,
                    )
                }
                uiInteraction.update { st ->
                    val loaded = res.getOrNull()
                    if (st.selectedTeamDetail?.name == entry.teamName && loaded != null) {
                        st.copy(selectedTeamDetail = loaded, isLoadingTeamDetail = false)
                    } else {
                        st.copy(isLoadingTeamDetail = false)
                    }
                }
            }
        }
    }

    fun closeTeamDetail() {
        uiInteraction.update { it.copy(selectedTeamDetail = null, isLoadingTeamDetail = false) }
    }

    fun openPlayerDetail(player: StarPlayer) {
        uiInteraction.update { it.copy(selectedStarPlayer = player) }
    }

    fun openSquadMemberAsPlayer(member: SquadMember, team: TeamDetailInfo) {
        val player = StarPlayer(
            id = member.id,
            name = member.name,
            zhName = member.zhName,
            clubName = team.name,
            clubZhName = team.zhName,
            clubCrestUrl = team.crest,
            nationalName = member.nationality,
            nationalZhName = TeamLocalization.localizeTeam(member.nationality).ifBlank { member.nationality },
            nationalCrestUrl = TeamAssetCatalog.resolveTeamLogo(member.nationality),
            positionZh = member.positionZh,
            nationalityZh = member.nationality,
            shirtNumber = member.shirtNumber,
            birthYear = member.birthYear,
            portraitUrl = member.portraitUrl,
            competitionCode = team.competitionCode,
            isTransliterated = member.isTransliterated,
        )
        uiInteraction.update { it.copy(selectedTeamDetail = null, selectedStarPlayer = player) }
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
                    it.copy(isSearchingPlayer = false, bannerMessage = "检索球员失败，请检查网络连接或使用英文名")
                }
            }
        }
    }

    fun setForegroundActive(active: Boolean) {
        if (active) {
            if (liveTickerJob?.isActive == true) return
            liveTickerJob = viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    fixtureRepo.ensureSeededIfEmpty()
                }
                while (isActive) {
                    val updatedCount = withContext(Dispatchers.IO) {
                        fixtureRepo.refreshLiveScoresEspnFirst().getOrDefault(0)
                    }
                    if (updatedCount > 0) {
                        NextMatchWidgetProvider.updateAllWidgets(getApplication())
                    }
                    delay(15_000L)
                }
            }
            uiState.value.selectedMatchDetail?.let { openMatch ->
                if (detailPollingJob?.isActive != true && shouldAutoSyncDetail(openMatch)) {
                    startDetailStream(openMatch)
                }
            }
        } else {
            liveTickerJob?.cancel()
            liveTickerJob = null
            detailPollingJob?.cancel()
            detailPollingJob = null
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

    fun saveDataSource(mode: DataSourceMode, githubPagesUrl: String, apiKey: String, apiFootballKey: String = "") {
        viewModelScope.launch {
            prefsRepo.updateDataSource(mode, githubPagesUrl, apiKey, apiFootballKey)
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

    private data class QuadData(
        val matches: List<MatchItem>,
        val standings: List<StandingEntry>,
        val teams: List<TeamDetailInfo>,
        val players: List<StarPlayer>,
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                CalendarViewModel(app)
            }
        }
    }
}
