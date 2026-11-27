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
import com.footballcalendar.app.model.LiveBroadcastAlert
import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchH2HBundle
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.ScheduleChange
import com.footballcalendar.app.model.ScheduleChangeType
import com.footballcalendar.app.model.SquadMember
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.StarPlayer
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.util.CoachResolver
import com.footballcalendar.app.util.DetailPollingPolicy
import com.footballcalendar.app.util.DetailStreamController
import com.footballcalendar.app.util.IcsExporter
import com.footballcalendar.app.util.MatchTimeFormatter
import com.footballcalendar.app.util.TeamAssetCatalog
import com.footballcalendar.app.util.TeamLocalization
import com.footballcalendar.app.widget.NextMatchWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    val selectedTeamHistory: List<MatchItem> = emptyList(),
    val isLoadingTeamDetail: Boolean = false,
    val isLoadingTeamHistory: Boolean = false,
    val starPlayers: List<StarPlayer> = emptyList(),
    val selectedStarPlayer: StarPlayer? = null,
    val selectedPlayerMatches: List<MatchItem> = emptyList(),
    val isLoadingPlayerHistory: Boolean = false,
    val isSearchingPlayer: Boolean = false,
    val prefs: UserPreferences = UserPreferences(),
    val isSyncing: Boolean = false,
    val recentChanges: List<ScheduleChange> = emptyList(),
    val bannerMessage: String? = null,
    val liveBroadcastAlert: LiveBroadcastAlert? = null,
    val selectedMatchDetail: MatchItem? = null,
    val selectedMatchLiveDetail: LiveMatchDetail? = null,
    val selectedMatchH2H: MatchH2HBundle? = null,
    val isLoadingMatchDetail: Boolean = false,
    val isLoadingMatchH2H: Boolean = false,
    val matchDetailNote: String? = null,
    val useTimelineView: Boolean = true,
    val onlyGoalsFilter: Boolean = false,
    val canNavigateBackModal: Boolean = false,
) {
    fun standingFor(competitionCode: String, teamZh: String, teamEn: String): StandingEntry? {
        val localizedZh = TeamLocalization.localizeTeam(teamEn).ifBlank { teamZh }
        return teamRankByLeagueAndName[competitionCode to teamZh]
            ?: teamRankByLeagueAndName[competitionCode to localizedZh]
            ?: teamRankByLeagueAndName[competitionCode to teamEn]
            ?: standingsByLeague[competitionCode]?.firstOrNull { st ->
                st.teamZhName == teamZh || st.teamZhName == localizedZh || st.teamName.equals(teamEn, true)
            }
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
    val selectedPlayerMatches: List<MatchItem> = emptyList(),
    val isLoadingPlayerHistory: Boolean = false,
    val selectedTeamDetail: TeamDetailInfo? = null,
    val selectedTeamHistory: List<MatchItem> = emptyList(),
    val isLoadingTeamDetail: Boolean = false,
    val isLoadingTeamHistory: Boolean = false,
    val isSearchingPlayer: Boolean = false,
    val isSyncing: Boolean = false,
    val recentChanges: List<ScheduleChange> = emptyList(),
    val bannerMessage: String? = null,
    val liveBroadcastAlert: LiveBroadcastAlert? = null,
    val selectedMatchDetail: MatchItem? = null,
    val selectedMatchLiveDetail: LiveMatchDetail? = null,
    val selectedMatchH2H: MatchH2HBundle? = null,
    val isLoadingMatchDetail: Boolean = false,
    val isLoadingMatchH2H: Boolean = false,
    val matchDetailNote: String? = null,
    val useTimelineView: Boolean = true,
    val onlyGoalsFilter: Boolean = false,
    val canNavigateBackModal: Boolean = false,
)

private sealed interface ModalSnapshot {
    data class Match(val match: MatchItem) : ModalSnapshot
    data class Team(
        val teamName: String,
        val teamZhName: String,
        val teamId: Int,
        val compCode: String,
        val crest: String,
    ) : ModalSnapshot
    data class Player(val player: StarPlayer) : ModalSnapshot
}

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as FootballCalendarApplication).container
    private val fixtureRepo = container.fixtureRepository
    private val prefsRepo = container.prefsRepository
    private val syncScheduler = container.syncScheduler
    private val calendarManager = container.systemCalendarSyncManager
    private val notificationHelper = container.notificationHelper

    private val uiInteraction = MutableStateFlow(UiInteractionState())
    private val modalStack = mutableListOf<ModalSnapshot>()
    private var liveTickerJob: Job? = null
    private var detailPollingJob: Job? = null
    private var h2hJob: Job? = null
    private var teamHistoryJob: Job? = null

    private val precomputedCoreFlow = combine(
        fixtureRepo.observeAllMatches(),
        fixtureRepo.observeAllStandings(),
        fixtureRepo.observeAllTeams(),
        fixtureRepo.starPlayersFlow,
    ) { allMatches, allStandings, allTeams, basePlayers ->
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
        // Merge Scorer Leaderboard + All Squad Players across all teams on worker thread
        val mergedPlayers = buildList {
            val seenKey = HashSet<String>(basePlayers.size + allTeams.size * 24)
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
        PrecomputedCore(
            matches = allMatches,
            standingsGrouped = standingsGrouped,
            rankMap = rankMap,
            leagueRankMap = leagueRankMap,
            teamsMap = teamsMap,
            allTeams = allTeams,
            mergedPlayers = mergedPlayers,
        )
    }.flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedDayRoomFlow = combine(
        uiInteraction.map { it.selectedDate }.distinctUntilChanged(),
        prefsRepo.preferencesFlow.map { it.watchNightGrouping }.distinctUntilChanged(),
    ) { date, watchNightGrouping ->
        date.toString() to watchNightGrouping
    }.distinctUntilChanged().flatMapLatest { (dateKey, watchNightGrouping) ->
        fixtureRepo.observeMatchesByDate(
            dateKey = dateKey,
            isLotteryOnly = false,
            watchNightGrouping = watchNightGrouping,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedMatchDetailRoomFlow = uiInteraction
        .map { it.selectedMatchDetail?.id }
        .distinctUntilChanged()
        .flatMapLatest { matchId ->
            if (matchId == null) flowOf(null) else fixtureRepo.observeMatchDetail(matchId)
        }

    val uiState: StateFlow<CalendarUiState> = combine(
        precomputedCoreFlow,
        selectedDayRoomFlow,
        selectedMatchDetailRoomFlow,
        prefsRepo.preferencesFlow,
        uiInteraction,
    ) { core, roomDayMatches, roomMatchDetail, prefs, ui ->
        val allMatches = core.matches
        val zoneId = MatchTimeFormatter.BEIJING_ZONE
        fun matchAllowed(match: MatchItem): Boolean {
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
            return leagueAllowed && chipAllowed && tabAllowed
        }

        val filtered = allMatches.filter(::matchAllowed)

        val grouped = filtered.groupBy { match ->
            MatchTimeFormatter.displayDateFor(
                utcIso = match.utcDate,
                zoneId = zoneId,
                watchNightGrouping = prefs.watchNightGrouping,
            ) ?: LocalDate.now(zoneId)
        }

        val dayMatches = roomDayMatches
            .filter(::matchAllowed)
            .ifEmpty { grouped[ui.selectedDate].orEmpty() }
            .sortedBy { it.utcDate }

        val now = Instant.now().minusSeconds(1800)
        val upcomingFollowed = allMatches.filter { match ->
            !match.phase.isPostponed &&
                match.competitionCode in prefs.followedCompetitions &&
                (MatchTimeFormatter.parseInstant(match.utcDate)?.isAfter(now) == true)
        }.sortedBy { it.utcDate }

        val hero = upcomingFollowed.firstOrNull { it.id in prefs.watchlistMatchIds }
            ?: upcomingFollowed.firstOrNull { it.involvesAnyTeam(prefs.favoriteTeams) }
            ?: upcomingFollowed.firstOrNull()

        // Sync selectedMatchDetail if scores/finalization were refreshed in Room
        val latestSelectedMatch = ui.selectedMatchDetail?.let { sel ->
            allMatches.firstOrNull { it.id == sel.id } ?: sel
        }
        val latestLiveDetail = roomMatchDetail ?: ui.selectedMatchLiveDetail
        val latestSelectedTeam = ui.selectedTeamDetail?.let { sel ->
            val roomTeam = core.teamsMap[sel.name.lowercase()] ?: core.teamsMap[sel.zhName]
            if (roomTeam != null && (roomTeam.squad.isNotEmpty() || sel.squad.isEmpty())) {
                CoachResolver.applyAuthoritativeCoach(
                    team = roomTeam,
                    matches = allMatches,
                    detailsByMatchId = latestLiveDetail?.let { mapOf(it.matchId to it) }.orEmpty(),
                )
            } else {
                sel
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
            standingsByLeague = core.standingsGrouped,
            teamRankByName = core.rankMap,
            teamRankByLeagueAndName = core.leagueRankMap,
            teamsByName = core.teamsMap,
            allTeams = core.allTeams,
            selectedStandingsLeague = ui.selectedStandingsLeague,
            selectedTeamDetail = latestSelectedTeam,
            selectedTeamHistory = ui.selectedTeamHistory,
            isLoadingTeamDetail = ui.isLoadingTeamDetail,
            isLoadingTeamHistory = ui.isLoadingTeamHistory,
            starPlayers = core.mergedPlayers,
            selectedStarPlayer = ui.selectedStarPlayer,
            selectedPlayerMatches = ui.selectedPlayerMatches,
            isLoadingPlayerHistory = ui.isLoadingPlayerHistory,
            isSearchingPlayer = ui.isSearchingPlayer,
            prefs = prefs,
            isSyncing = ui.isSyncing,
            recentChanges = ui.recentChanges,
            bannerMessage = ui.bannerMessage,
            liveBroadcastAlert = ui.liveBroadcastAlert,
            selectedMatchDetail = latestSelectedMatch,
            selectedMatchLiveDetail = latestLiveDetail,
            selectedMatchH2H = ui.selectedMatchH2H,
            isLoadingMatchDetail = ui.isLoadingMatchDetail,
            isLoadingMatchH2H = ui.isLoadingMatchH2H,
            matchDetailNote = ui.matchDetailNote,
            useTimelineView = ui.useTimelineView,
            onlyGoalsFilter = ui.onlyGoalsFilter,
            canNavigateBackModal = ui.canNavigateBackModal,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    fun setTimelineView(enabled: Boolean) {
        uiInteraction.update { it.copy(useTimelineView = enabled) }
    }

    fun toggleOnlyGoals() {
        uiInteraction.update { it.copy(onlyGoalsFilter = !it.onlyGoalsFilter) }
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

    fun openMatchDetail(match: MatchItem, pushToStack: Boolean = true) {
        if (pushToStack) {
            val top = modalStack.lastOrNull()
            if (top !is ModalSnapshot.Match || top.match.id != match.id) {
                modalStack.add(ModalSnapshot.Match(match))
            }
        }
        detailPollingJob?.cancel()
        detailPollingJob = null
        h2hJob?.cancel()
        h2hJob = null
        teamHistoryJob?.cancel()
        teamHistoryJob = null

        val cached = fixtureRepo.getCachedLiveDetail(match.id)
        val cachedH2H = fixtureRepo.getCachedH2H(match.id)
        val shouldSync = shouldAutoSyncDetail(match, hasLocalSnapshot = cached != null)
        uiInteraction.update {
            it.copy(
                selectedTeamDetail = null,
                selectedTeamHistory = emptyList(),
                isLoadingTeamDetail = false,
                isLoadingTeamHistory = false,
                selectedStarPlayer = null,
                selectedMatchDetail = match,
                selectedMatchLiveDetail = cached,
                selectedMatchH2H = cachedH2H,
                isLoadingMatchDetail = cached == null && shouldSync,
                isLoadingMatchH2H = cachedH2H == null,
                canNavigateBackModal = modalStack.size > 1,
                matchDetailNote = when {
                    match.isFinalized || (match.phase.isFinished && !shouldSync) -> {
                        if (cached != null && cached.commentary.isEmpty()) "本场暂无文字直播记录" else null
                    }
                    cached == null && !shouldSync -> {
                        "已接入 ESPN 与 API-Football 零延迟引擎 · 开赛前约 45 分钟自动同步首发阵容与实时事件"
                    }
                    else -> null
                },
            )
        }
        if (shouldSync) {
            startDetailStream(match, hasLocalSnapshot = cached != null)
        } else if (cached == null) {
            viewModelScope.launch {
                val stored = withContext(Dispatchers.IO) {
                    fixtureRepo.getStoredLiveDetail(match.id)
                }
                uiInteraction.update { st ->
                    if (st.selectedMatchDetail?.id != match.id) return@update st
                    val resolvedDetail = stored ?: st.selectedMatchLiveDetail
                    st.copy(
                        selectedMatchLiveDetail = resolvedDetail,
                        isLoadingMatchDetail = false,
                        matchDetailNote = when {
                            match.isFinalized || match.phase.isFinished -> {
                                if (resolvedDetail == null || resolvedDetail.commentary.isEmpty()) {
                                    "本场暂无文字直播记录"
                                } else {
                                    null
                                }
                            }
                            resolvedDetail == null -> {
                                "已接入 ESPN 与 API-Football 零延迟引擎 · 开赛前约 45 分钟自动同步首发阵容与实时事件"
                            }
                            else -> null
                        },
                    )
                }
            }
        }
        startMatchH2HLoad(match)
    }

    private fun startMatchH2HLoad(match: MatchItem) {
        h2hJob?.cancel()
        h2hJob = viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                fixtureRepo.fetchMatchHeadToHeadAndForm(match)
            }
            val bundle = res.getOrNull()
            uiInteraction.update { st ->
                if (st.selectedMatchDetail?.id != match.id) return@update st
                st.copy(
                    selectedMatchH2H = bundle ?: st.selectedMatchH2H ?: MatchH2HBundle(),
                    isLoadingMatchH2H = false,
                )
            }
        }
    }

    private fun shouldAutoSyncDetail(match: MatchItem, hasLocalSnapshot: Boolean = false): Boolean =
        DetailPollingPolicy.shouldStartPolling(
            match = match,
            hasLocalSnapshot = hasLocalSnapshot,
        )

    private fun startDetailStream(initialMatch: MatchItem, hasLocalSnapshot: Boolean = false) {
        detailPollingJob?.cancel()
        detailPollingJob = DetailStreamController.launchIfNeeded(
            scope = viewModelScope,
            initialMatch = initialMatch,
            hasLocalSnapshot = hasLocalSnapshot,
            currentMatchProvider = { uiState.value.selectedMatchDetail },
            currentDetailProvider = { uiInteraction.value.selectedMatchLiveDetail },
            fetchAndApply = { target, force -> fetchAndApplyMatchDetail(target, force) },
        )
    }

    private suspend fun fetchAndApplyMatchDetail(match: MatchItem, force: Boolean) {
        val result = withContext(Dispatchers.IO) {
            fixtureRepo.fetchMatchLiveDetail(match, forceRefresh = force)
        }
        result.onSuccess { detail ->
            uiInteraction.update {
                if (it.selectedMatchDetail?.id != match.id) return@update it
                val curMatch = uiState.value.selectedMatchDetail ?: match
                val noLongerPolling = !DetailPollingPolicy.shouldContinuePolling(curMatch, detail)
                it.copy(
                    selectedMatchLiveDetail = detail,
                    isLoadingMatchDetail = false,
                    matchDetailNote = when {
                        (curMatch.isFinalized || curMatch.phase.isFinished || noLongerPolling) && detail.commentary.isEmpty() -> {
                            "本场暂无文字直播记录"
                        }
                        detail.events.isEmpty() &&
                            detail.lineups.isEmpty() &&
                            detail.stats.isEmpty() &&
                            detail.commentary.isEmpty() -> {
                            "已连接实时数据流：当前暂无事件记录（赛前约 45 分钟公布首发）"
                        }
                        else -> null
                    },
                )
            }
        }.onFailure { err ->
            uiInteraction.update {
                if (it.selectedMatchDetail?.id != match.id) return@update it
                val curMatch = uiState.value.selectedMatchDetail ?: match
                it.copy(
                    isLoadingMatchDetail = false,
                    matchDetailNote = if (it.selectedMatchLiveDetail == null) {
                        if (curMatch.isFinalized || curMatch.phase.isFinished) {
                            "本场暂无文字直播记录"
                        } else {
                            err.message ?: "暂无实时赛况详情"
                        }
                    } else {
                        it.matchDetailNote
                    },
                )
            }
        }
    }

    fun closeMatchDetail() = closeAllModals()

    fun openTeamDetailFromStanding(entry: StandingEntry) {
        openTeamDetail(
            teamName = entry.teamName,
            teamZhName = entry.teamZhName,
            compCode = entry.competitionCode,
            crest = entry.teamCrest,
        )
    }

    fun openTeamDetail(
        teamName: String,
        teamZhName: String = "",
        compCode: String = "",
        crest: String = "",
        teamId: Int = 0,
        pushToStack: Boolean = true,
    ) {
        val cleanEn = teamName.ifBlank { teamZhName }.trim()
        val resolvedZh = TeamLocalization.preferChinese(cleanEn, teamZhName.ifBlank { TeamLocalization.localizeTeam(cleanEn) })
        if (cleanEn.isBlank() && resolvedZh.isBlank()) return

        val currentState = uiState.value
        val cached = currentState.teamsByName[cleanEn.lowercase()]
            ?: currentState.teamsByName[resolvedZh]
            ?: currentState.allTeams.firstOrNull {
                it.zhName == resolvedZh || it.name.equals(cleanEn, true)
            }

        val resolvedTeamId = cached?.teamId?.takeIf { it > 0 }
            ?: teamId.takeIf { it > 0 }
            ?: currentState.allMatches.firstOrNull {
                it.homeTeamName.equals(cleanEn, true) || it.homeTeamZhName == resolvedZh
            }?.homeTeamId
            ?: currentState.allMatches.firstOrNull {
                it.awayTeamName.equals(cleanEn, true) || it.awayTeamZhName == resolvedZh
            }?.awayTeamId
            ?: 0

        val resolvedCode = cached?.competitionCode?.ifBlank { null }
            ?: compCode.ifBlank { null }
            ?: currentState.teamRankByName[resolvedZh]?.competitionCode
            ?: ""
        val resolvedCrest = cached?.crest?.ifBlank { null }
            ?: crest.ifBlank { null }
            ?: TeamAssetCatalog.resolveTeamLogo(cleanEn).ifBlank { TeamAssetCatalog.resolveTeamLogo(resolvedZh) }

        if (pushToStack) {
            val top = modalStack.lastOrNull()
            if (top !is ModalSnapshot.Team || (top.teamZhName != resolvedZh && !top.teamName.equals(cleanEn, true))) {
                modalStack.add(ModalSnapshot.Team(cleanEn, resolvedZh, resolvedTeamId, resolvedCode, resolvedCrest))
            }
        }

        detailPollingJob?.cancel()
        detailPollingJob = null
        h2hJob?.cancel()
        h2hJob = null
        teamHistoryJob?.cancel()
        teamHistoryJob = null

        val fallback = cached ?: TeamDetailInfo(
            teamId = resolvedTeamId,
            competitionCode = resolvedCode,
            name = cleanEn,
            shortName = cleanEn,
            zhName = resolvedZh,
            crest = resolvedCrest,
            venue = "",
            founded = 0,
            clubColors = "",
            coachName = "",
            coachZhName = "",
            coachNationality = "",
            squad = emptyList(),
        )
        val localTeamMatches = currentState.allMatches.filter { m ->
            m.involvesTeam(resolvedZh) || m.involvesTeam(cleanEn)
        }.sortedByDescending { it.utcDate }

        uiInteraction.update {
            it.copy(
                selectedMatchDetail = null,
                selectedMatchLiveDetail = null,
                selectedMatchH2H = null,
                isLoadingMatchDetail = false,
                isLoadingMatchH2H = false,
                selectedStarPlayer = null,
                selectedTeamDetail = fallback,
                selectedTeamHistory = localTeamMatches,
                isLoadingTeamDetail = cached == null || cached.squad.isEmpty() || cached.squad.any { sq -> sq.portraitUrl.isBlank() },
                isLoadingTeamHistory = true,
                canNavigateBackModal = modalStack.size > 1,
            )
        }

        teamHistoryJob = viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                fixtureRepo.fetchTeamHistoryAndDetail(
                    teamName = fallback.name,
                    teamZhName = fallback.zhName,
                    teamId = resolvedTeamId,
                    compCode = resolvedCode,
                    crest = resolvedCrest,
                )
            }
            val pair = res.getOrNull()
            uiInteraction.update { st ->
                val curTeam = st.selectedTeamDetail ?: return@update st
                if (curTeam.zhName != resolvedZh && !curTeam.name.equals(cleanEn, true)) return@update st
                st.copy(
                    selectedTeamDetail = pair?.first ?: curTeam,
                    selectedTeamHistory = pair?.second?.takeIf { it.isNotEmpty() } ?: st.selectedTeamHistory,
                    isLoadingTeamDetail = false,
                    isLoadingTeamHistory = false,
                )
            }
        }
    }

    fun closeTeamDetail() = closeAllModals()

    fun openPlayerDetail(player: StarPlayer, pushToStack: Boolean = true) {
        val withBio = if (player.bio.isNotBlank()) player else player.copy(
            bio = TeamAssetCatalog.buildStructuredPlayerBio(player),
        )
        val enrichedCrests = withBio.copy(
            clubCrestUrl = TeamAssetCatalog.resolveTeamLogo(withBio.clubName, withBio.clubCrestUrl)
                .ifBlank { TeamAssetCatalog.resolveTeamLogo(withBio.clubZhName) },
            nationalCrestUrl = TeamAssetCatalog.resolveTeamLogo(withBio.nationalName, withBio.nationalCrestUrl)
                .ifBlank { TeamAssetCatalog.resolveTeamLogo(withBio.nationalZhName) },
        )
        if (pushToStack) {
            val top = modalStack.lastOrNull()
            if (top !is ModalSnapshot.Player || top.player.id != enrichedCrests.id) {
                modalStack.add(ModalSnapshot.Player(enrichedCrests))
            }
        }
        detailPollingJob?.cancel()
        detailPollingJob = null
        h2hJob?.cancel()
        h2hJob = null
        teamHistoryJob?.cancel()
        teamHistoryJob = null

        val localMatches = uiState.value.allMatches.filter { m ->
            (enrichedCrests.clubZhName.isNotBlank() && m.involvesTeam(enrichedCrests.clubZhName)) ||
                (enrichedCrests.clubName.isNotBlank() && m.involvesTeam(enrichedCrests.clubName)) ||
                (enrichedCrests.nationalZhName.isNotBlank() && m.involvesTeam(enrichedCrests.nationalZhName)) ||
                (enrichedCrests.nationalName.isNotBlank() && m.involvesTeam(enrichedCrests.nationalName))
        }.sortedByDescending { it.utcDate }

        uiInteraction.update {
            it.copy(
                selectedMatchDetail = null,
                selectedMatchLiveDetail = null,
                selectedMatchH2H = null,
                selectedTeamDetail = null,
                selectedTeamHistory = emptyList(),
                isLoadingTeamDetail = false,
                isLoadingTeamHistory = false,
                selectedStarPlayer = enrichedCrests,
                selectedPlayerMatches = localMatches,
                isLoadingPlayerHistory = enrichedCrests.clubName.isNotBlank() || enrichedCrests.nationalName.isNotBlank(),
                canNavigateBackModal = modalStack.size > 1,
            )
        }
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                fixtureRepo.enrichSinglePlayerPortrait(enrichedCrests)
            }
            uiInteraction.update { st ->
                if (st.selectedStarPlayer?.id == enrichedCrests.id) {
                    st.copy(selectedStarPlayer = updated)
                } else {
                    st
                }
            }
        }
        if (enrichedCrests.clubName.isNotBlank() || enrichedCrests.nationalName.isNotBlank()) {
            teamHistoryJob = viewModelScope.launch {
                val extraList = withContext(Dispatchers.IO) {
                    val out = mutableListOf<MatchItem>()
                    if (enrichedCrests.clubName.isNotBlank()) {
                        fixtureRepo.fetchTeamHistoryAndDetail(
                            teamName = enrichedCrests.clubName,
                            teamZhName = enrichedCrests.clubZhName,
                            teamId = 0,
                            compCode = enrichedCrests.competitionCode,
                            crest = enrichedCrests.clubCrestUrl,
                        ).getOrNull()?.second?.let { out.addAll(it) }
                    }
                    if (enrichedCrests.nationalName.isNotBlank()) {
                        fixtureRepo.fetchTeamHistoryAndDetail(
                            teamName = enrichedCrests.nationalName,
                            teamZhName = enrichedCrests.nationalZhName,
                            teamId = 0,
                            compCode = "INT",
                            crest = enrichedCrests.nationalCrestUrl,
                        ).getOrNull()?.second?.let { out.addAll(it) }
                    }
                    out
                }
                uiInteraction.update { st ->
                    if (st.selectedStarPlayer?.id != enrichedCrests.id) return@update st
                    val merged = (st.selectedPlayerMatches + extraList)
                        .distinctBy { "${it.utcDate.take(10)}|${it.homeTeamZhName}|${it.awayTeamZhName}" }
                        .sortedByDescending { it.utcDate }
                    st.copy(
                        selectedPlayerMatches = merged,
                        isLoadingPlayerHistory = false,
                    )
                }
            }
        }
    }

    fun openLineupPlayerByName(
        name: String,
        zhName: String,
        number: Int,
        portraitUrl: String,
        teamName: String,
        teamZhName: String,
        compCode: String,
    ) {
        val existing = uiState.value.starPlayers.firstOrNull {
            it.name.equals(name, true) || (zhName.isNotBlank() && it.zhName == zhName)
        }
        val target = existing ?: StarPlayer(
            id = "lp-${name.lowercase().replace(' ', '-')}",
            name = name,
            zhName = zhName.ifBlank { TeamLocalization.localizePlayer(name).first },
            clubName = teamName,
            clubZhName = teamZhName,
            clubCrestUrl = TeamAssetCatalog.resolveTeamLogo(teamName),
            nationalName = "",
            nationalZhName = "",
            nationalCrestUrl = "",
            positionZh = "一线队球员",
            nationalityZh = "国际",
            shirtNumber = number,
            birthYear = 0,
            portraitUrl = portraitUrl,
            competitionCode = compCode,
        )
        openPlayerDetail(target, pushToStack = true)
    }

    fun openSquadMemberAsPlayer(member: SquadMember, team: TeamDetailInfo) {
        val isNatTeam = team.competitionCode in CompetitionCatalog.internationalCodes || team.zhName.endsWith("国家队")
        val player = StarPlayer(
            id = member.id,
            name = member.name,
            zhName = member.zhName,
            clubName = if (isNatTeam) "" else team.name,
            clubZhName = if (isNatTeam) "" else team.zhName,
            clubCrestUrl = if (isNatTeam) "" else team.crest,
            nationalName = if (isNatTeam) team.name else member.nationality,
            nationalZhName = if (isNatTeam) team.zhName else TeamLocalization.localizeTeam(member.nationality).ifBlank { member.nationality },
            nationalCrestUrl = if (isNatTeam) team.crest else TeamAssetCatalog.resolveTeamLogo(member.nationality),
            positionZh = member.positionZh,
            nationalityZh = member.nationality,
            shirtNumber = member.shirtNumber,
            birthYear = member.birthYear,
            portraitUrl = member.portraitUrl,
            competitionCode = team.competitionCode,
            isTransliterated = member.isTransliterated,
        )
        openPlayerDetail(player, pushToStack = true)
    }

    fun navigateBackModal() {
        if (modalStack.size <= 1) {
            closeAllModals()
            return
        }
        modalStack.removeAt(modalStack.lastIndex)
        when (val prev = modalStack.last()) {
            is ModalSnapshot.Match -> openMatchDetail(prev.match, pushToStack = false)
            is ModalSnapshot.Team -> openTeamDetail(prev.teamName, prev.teamZhName, prev.compCode, prev.crest, prev.teamId, pushToStack = false)
            is ModalSnapshot.Player -> openPlayerDetail(prev.player, pushToStack = false)
        }
    }

    fun closePlayerDetail() = closeAllModals()

    fun closeAllModals() {
        modalStack.clear()
        detailPollingJob?.cancel()
        detailPollingJob = null
        h2hJob?.cancel()
        h2hJob = null
        teamHistoryJob?.cancel()
        teamHistoryJob = null
        uiInteraction.update {
            it.copy(
                selectedMatchDetail = null,
                selectedMatchLiveDetail = null,
                selectedMatchH2H = null,
                isLoadingMatchDetail = false,
                isLoadingMatchH2H = false,
                matchDetailNote = null,
                selectedTeamDetail = null,
                selectedTeamHistory = emptyList(),
                isLoadingTeamDetail = false,
                isLoadingTeamHistory = false,
                selectedStarPlayer = null,
                selectedPlayerMatches = emptyList(),
                isLoadingPlayerHistory = false,
                canNavigateBackModal = false,
            )
        }
    }

    fun dismissLiveBroadcast() {
        uiInteraction.update { it.copy(liveBroadcastAlert = null) }
    }

    fun openMatchById(matchId: String) {
        val m = uiState.value.allMatches.firstOrNull { it.id == matchId } ?: return
        openMatchDetail(m)
    }

    fun sendTestLiveBroadcast() {
        val target = uiState.value.heroMatch ?: uiState.value.allMatches.firstOrNull()
        val sent = notificationHelper.notifyTestBroadcast(target)
        val preview = LiveBroadcastAlert(
            id = "test-${System.currentTimeMillis()}",
            match = target ?: MatchItem(
                id = "test-0", competitionCode = "PL", competitionName = "Premier League", competitionZhName = "英超",
                matchday = 1, stage = "REGULAR_SEASON", utcDate = Instant.now().toString(),
                status = MatchStatus.LIVE, homeTeamId = 0, homeTeamName = "Liverpool FC", homeTeamShortName = "Liverpool",
                homeTeamZhName = "利物浦", awayTeamId = 0, awayTeamName = "Manchester City FC", awayTeamShortName = "Man City",
                awayTeamZhName = "曼城", homeScore = 2, awayScore = 1, venue = "Anfield",
                lastUpdatedUtc = Instant.now().toString(),
            ),
            headline = "⚽ 实时播报推送测试：${target?.let { "${it.homeTeamZhName} vs ${it.awayTeamZhName}" } ?: "足球赛事日历"}",
            detail = if (sent) "系统通知栏与顶部实时横幅已触发（点击可直接进入赛况）" else "已在应用内触发实时横幅（如需系统通知栏推送请授予通知权限）",
        )
        uiInteraction.update { it.copy(liveBroadcastAlert = preview) }
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
                    val alerts = withContext(Dispatchers.IO) {
                        fixtureRepo.pollLiveUpdatesWithBroadcasts().getOrDefault(emptyList())
                    }
                    if (alerts.isNotEmpty()) {
                        uiInteraction.update { it.copy(liveBroadcastAlert = alerts.first()) }
                        if (prefsRepo.currentPreferences().notifyOnLiveBroadcast) {
                            notificationHelper.notifyLiveBroadcasts(alerts)
                        }
                        NextMatchWidgetProvider.updateAllWidgets(getApplication())
                    }
                    val hasLive = uiState.value.filteredMatches.any { it.phase.isLiveOrHalfTime }
                    delay(if (hasLive) 8_000L else 15_000L)
                }
            }
            uiState.value.selectedMatchDetail?.let { openMatch ->
                val hasSnap = uiState.value.selectedMatchLiveDetail != null ||
                    fixtureRepo.getCachedLiveDetail(openMatch.id) != null
                if (detailPollingJob?.isActive != true && shouldAutoSyncDetail(openMatch, hasLocalSnapshot = hasSnap)) {
                    startDetailStream(openMatch, hasLocalSnapshot = hasSnap)
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
        notifyOnLiveBroadcast: Boolean = true,
    ) {
        viewModelScope.launch {
            prefsRepo.updateDisplayAndNotificationSettings(
                watchNightGrouping = watchNightGrouping,
                notifyOnScheduleChange = notifyOnScheduleChange,
                notifyBeforeMinutes = notifyBeforeMinutes,
                autoSyncSystemCalendar = autoSyncSystemCalendar,
                notifyOnLiveBroadcast = notifyOnLiveBroadcast,
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

    private data class PrecomputedCore(
        val matches: List<MatchItem>,
        val standingsGrouped: Map<String, List<StandingEntry>>,
        val rankMap: Map<String, StandingEntry>,
        val leagueRankMap: Map<Pair<String, String>, StandingEntry>,
        val teamsMap: Map<String, TeamDetailInfo>,
        val allTeams: List<TeamDetailInfo>,
        val mergedPlayers: List<StarPlayer>,
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
