package com.footballcalendar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.footballcalendar.app.ui.CalendarViewModel
import com.footballcalendar.app.ui.screens.CalendarScreen
import com.footballcalendar.app.ui.screens.MatchDetailBottomSheet
import com.footballcalendar.app.ui.screens.PlayerDetailSheet
import com.footballcalendar.app.ui.screens.SettingsScreen
import com.footballcalendar.app.ui.screens.StandingsScreen
import com.footballcalendar.app.ui.screens.TeamDetailBottomSheet
import com.footballcalendar.app.ui.screens.TeamsScreen
import com.footballcalendar.app.ui.theme.AppleCanvas
import com.footballcalendar.app.ui.theme.AppleDivider
import com.footballcalendar.app.ui.theme.AppleRed
import com.footballcalendar.app.ui.theme.AppleRedTint
import com.footballcalendar.app.ui.theme.FootballCalendarTheme
import com.footballcalendar.app.ui.theme.InkTertiary

private enum class AppTab(val route: String, val label: String, val icon: ImageVector) {
    CALENDAR("calendar", "日历", Icons.Filled.CalendarMonth),
    STANDINGS("standings", "积分榜", Icons.Filled.Leaderboard),
    TEAMS("teams", "球员榜", Icons.Filled.Star),
    SETTINGS("settings", "设置", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                AppleCanvas.toArgb(),
                AppleCanvas.toArgb(),
            ),
        )
        setContent {
            FootballCalendarTheme {
                FootballCalendarRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FootballCalendarRoot(
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: AppTab.CALENDAR.route
    val lifecycleOwner = context as? LifecycleOwner

    DisposableEffect(lifecycleOwner) {
        val owner = lifecycleOwner
        if (owner == null) {
            viewModel.setForegroundActive(true)
            onDispose { viewModel.setForegroundActive(false) }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> viewModel.setForegroundActive(true)
                    Lifecycle.Event.ON_PAUSE -> viewModel.setForegroundActive(false)
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                viewModel.setForegroundActive(true)
            }
            onDispose {
                owner.lifecycle.removeObserver(observer)
                viewModel.setForegroundActive(false)
            }
        }
    }

    LaunchedEffect(state.bannerMessage) {
        val msg = state.bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val hasModalOpen = state.selectedMatchDetail != null || state.selectedStarPlayer != null || state.selectedTeamDetail != null
    val backdropBlur by animateDpAsState(
        targetValue = if (hasModalOpen) 18.dp else 0.dp,
        label = "backdropBlur",
    )

    Scaffold(
        modifier = if (backdropBlur > 0.dp) Modifier.blur(backdropBlur) else Modifier,
        containerColor = AppleCanvas,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column {
                HorizontalDivider(color = AppleDivider)
                NavigationBar(containerColor = Color.White) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(AppTab.CALENDAR.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppleRed,
                                selectedTextColor = AppleRed,
                                indicatorColor = AppleRedTint,
                                unselectedIconColor = InkTertiary,
                                unselectedTextColor = InkTertiary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppTab.CALENDAR.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppTab.CALENDAR.route) {
                CalendarScreen(
                    state = state,
                    onSelectDate = viewModel::selectDate,
                    onPrevMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                    onJumpToday = viewModel::jumpToToday,
                    onToggleExpanded = viewModel::toggleMonthExpanded,
                    onSelectFilterTab = viewModel::setCalendarFilterTab,
                    onSelectLeagueFilter = viewModel::selectLeagueFilter,
                    onRefresh = { viewModel.syncNow(simulateDemoChange = false) },
                    onDismissChanges = viewModel::dismissChangesBanner,
                    onOpenMatchDetail = { m -> viewModel.openMatchDetail(m) },
                    onOpenTeamDetail = { en, zh, cc, cr, tid -> viewModel.openTeamDetail(en, zh, cc, cr, tid) },
                    onToggleWatchlist = viewModel::toggleWatchlistMatch,
                )
            }
            composable(AppTab.STANDINGS.route) {
                StandingsScreen(
                    state = state,
                    onSelectLeague = viewModel::selectStandingsLeague,
                    onClickTeam = viewModel::openTeamDetailFromStanding,
                )
            }
            composable(AppTab.TEAMS.route) {
                TeamsScreen(
                    state = state,
                    onToggleCompetition = viewModel::toggleCompetition,
                    onToggleFavoriteTeam = viewModel::toggleFavoriteTeam,
                    onToggleFavoritePlayer = viewModel::toggleFavoritePlayer,
                    onOpenPlayerDetail = { p -> viewModel.openPlayerDetail(p) },
                    onOpenTeamDetail = { en, zh, cc, cr -> viewModel.openTeamDetail(en, zh, cc, cr) },
                    onSearchExternalPlayer = viewModel::searchExternalPlayer,
                    onOpenMatchDetail = { m -> viewModel.openMatchDetail(m) },
                )
            }
            composable(AppTab.SETTINGS.route) {
                SettingsScreen(
                    state = state,
                    onSaveDataSource = viewModel::saveDataSource,
                    onSimulateDemoUpdate = { viewModel.syncNow(simulateDemoChange = true) },
                    onUpdateSyncSettings = viewModel::updateSyncSettings,
                    onUpdateDisplayAndNotifications = viewModel::updateDisplayAndNotificationSettings,
                    onSyncSystemCalendarNow = viewModel::syncMatchesToSystemCalendarNow,
                    onOpenMatchDetail = { m -> viewModel.openMatchDetail(m) },
                    onOpenTeamDetail = { en, zh, cc, cr, tid -> viewModel.openTeamDetail(en, zh, cc, cr, tid) },
                    onBuildShareIcsIntent = viewModel::buildShareIcsIntent,
                    onShowMessage = viewModel::showBanner,
                )
            }
        }
    }

    val selectedMatch = state.selectedMatchDetail
    val selectedTeam = state.selectedTeamDetail
    val selectedPlayer = state.selectedStarPlayer

    if (selectedMatch != null) {
        androidx.compose.runtime.key(selectedMatch.id) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val relatedPlayers = remember(selectedMatch.id, state.starPlayers) {
                state.starPlayers.filter { p ->
                    (p.clubZhName.isNotBlank() && selectedMatch.involvesTeam(p.clubZhName)) ||
                        (p.clubName.isNotBlank() && selectedMatch.involvesTeam(p.clubName)) ||
                        (p.nationalZhName.isNotBlank() && selectedMatch.involvesTeam(p.nationalZhName)) ||
                        (p.nationalName.isNotBlank() && selectedMatch.involvesTeam(p.nationalName))
                }
            }
            ModalBottomSheet(
                onDismissRequest = viewModel::closeAllModals,
                sheetState = sheetState,
                containerColor = Color.White.copy(alpha = 0.94f),
                scrimColor = Color(0x4A1C1C1E),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                MatchDetailBottomSheet(
                    match = selectedMatch,
                    liveDetail = state.selectedMatchLiveDetail,
                    isLoadingLiveDetail = state.isLoadingMatchDetail,
                    liveDetailNote = state.matchDetailNote,
                    useTimelineView = state.useTimelineView,
                    onlyGoalsFilter = state.onlyGoalsFilter,
                    h2hBundle = state.selectedMatchH2H,
                    isLoadingH2H = state.isLoadingMatchH2H,
                    favoriteTeams = state.prefs.favoriteTeams,
                    isWatchlisted = selectedMatch.id in state.prefs.watchlistMatchIds,
                    homeStanding = state.standingFor(selectedMatch.competitionCode, selectedMatch.homeTeamZhName, selectedMatch.homeTeamName),
                    awayStanding = state.standingFor(selectedMatch.competitionCode, selectedMatch.awayTeamZhName, selectedMatch.awayTeamName),
                    relatedPlayers = relatedPlayers,
                    canNavigateBack = state.canNavigateBackModal,
                    onNavigateBack = viewModel::navigateBackModal,
                    onSetTimelineView = viewModel::setTimelineView,
                    onToggleOnlyGoals = viewModel::toggleOnlyGoals,
                    onClickTeam = { en, zh, cc, cr, tid -> viewModel.openTeamDetail(en, zh, cc, cr, tid) },
                    onClickMatch = { m -> viewModel.openMatchDetail(m) },
                    onClickPlayer = { p -> viewModel.openPlayerDetail(p) },
                    onClickLineupPlayer = { lp, side ->
                        viewModel.openLineupPlayerByName(
                            name = lp.name,
                            zhName = lp.zhName,
                            number = lp.number,
                            portraitUrl = lp.portraitUrl,
                            teamName = side.teamName,
                            teamZhName = side.teamZhName,
                            compCode = selectedMatch.competitionCode,
                        )
                    },
                    onToggleFavorite = viewModel::toggleFavoriteTeam,
                    onToggleWatchlist = { viewModel.toggleWatchlistMatch(selectedMatch.id) },
                    onAddToSystemCalendar = { context.startActivity(viewModel.buildAddToCalendarIntent(selectedMatch)) },
                    onShareIcs = {
                        viewModel.buildShareIcsIntent(listOf(selectedMatch), "match_${selectedMatch.id}.ics")?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "分享赛程日历 (.ics)"))
                        }
                    },
                )
            }
        }
    } else if (selectedTeam != null) {
        androidx.compose.runtime.key(selectedTeam.zhName, selectedTeam.name) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = viewModel::closeAllModals,
                sheetState = sheetState,
                containerColor = Color.White.copy(alpha = 0.94f),
                scrimColor = Color(0x4A1C1C1E),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                val teamStanding = state.standingFor(selectedTeam.competitionCode, selectedTeam.zhName, selectedTeam.name)
                    ?: state.teamRankByName[selectedTeam.zhName]
                    ?: state.teamRankByName[selectedTeam.name]
                val combinedTeamMatches = remember(selectedTeam.zhName, selectedTeam.name, state.selectedTeamHistory, state.allMatches) {
                    val base = state.selectedTeamHistory + state.allMatches.filter { m ->
                        m.involvesTeam(selectedTeam.zhName) || m.involvesTeam(selectedTeam.name)
                    }
                    base.distinctBy { "${it.utcDate.take(10)}|${it.homeTeamZhName}|${it.awayTeamZhName}" }
                }
                TeamDetailBottomSheet(
                    team = selectedTeam,
                    standing = teamStanding,
                    matches = combinedTeamMatches,
                    isLoading = state.isLoadingTeamDetail,
                    isLoadingHistory = state.isLoadingTeamHistory,
                    isFavorite = selectedTeam.zhName in state.prefs.favoriteTeams || selectedTeam.name in state.prefs.favoriteTeams,
                    canNavigateBack = state.canNavigateBackModal,
                    onNavigateBack = viewModel::navigateBackModal,
                    onToggleFavorite = { viewModel.toggleFavoriteTeam(selectedTeam.zhName) },
                    onClickSquadMember = { member -> viewModel.openSquadMemberAsPlayer(member, selectedTeam) },
                    onClickMatch = { m -> viewModel.openMatchDetail(m) },
                )
            }
        }
    } else if (selectedPlayer != null) {
        androidx.compose.runtime.key(selectedPlayer.id) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = viewModel::closeAllModals,
                sheetState = sheetState,
                containerColor = Color.White.copy(alpha = 0.94f),
                scrimColor = Color(0x4A1C1C1E),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                val playerMatches = state.allMatches.filter { m ->
                    (selectedPlayer.clubZhName.isNotBlank() && m.involvesTeam(selectedPlayer.clubZhName)) ||
                        (selectedPlayer.clubName.isNotBlank() && m.involvesTeam(selectedPlayer.clubName)) ||
                        (selectedPlayer.nationalZhName.isNotBlank() && m.involvesTeam(selectedPlayer.nationalZhName)) ||
                        (selectedPlayer.nationalName.isNotBlank() && m.involvesTeam(selectedPlayer.nationalName))
                }.sortedByDescending { it.utcDate }

                PlayerDetailSheet(
                    player = selectedPlayer,
                    isFavorite = selectedPlayer.id in state.prefs.favoritePlayers,
                    upcomingMatches = playerMatches,
                    canNavigateBack = state.canNavigateBackModal,
                    onNavigateBack = viewModel::navigateBackModal,
                    onToggleFavorite = { viewModel.toggleFavoritePlayer(selectedPlayer.id) },
                    onClickTeam = { en, zh, cc, cr -> viewModel.openTeamDetail(en, zh, cc, cr) },
                    onClickMatch = { m -> viewModel.openMatchDetail(m) },
                )
            }
        }
    }
}
