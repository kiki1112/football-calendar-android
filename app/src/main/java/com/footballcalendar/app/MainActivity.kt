package com.footballcalendar.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.footballcalendar.app.sync.NotificationHelper
import com.footballcalendar.app.ui.CalendarViewModel
import com.footballcalendar.app.ui.components.LiquidGlassSurface
import com.footballcalendar.app.ui.components.LocalHazeState
import com.footballcalendar.app.ui.components.schemeCSpringSpec
import com.footballcalendar.app.ui.screens.CalendarScreen
import com.footballcalendar.app.ui.screens.MatchDetailBottomSheet
import com.footballcalendar.app.ui.screens.PlayerDetailSheet
import com.footballcalendar.app.ui.screens.SettingsScreen
import com.footballcalendar.app.ui.screens.StandingsScreen
import com.footballcalendar.app.ui.screens.TeamDetailBottomSheet
import com.footballcalendar.app.ui.screens.TeamsScreen
import com.footballcalendar.app.ui.theme.FootballCalendarTheme
import com.footballcalendar.app.ui.theme.ThemeTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

private enum class AppTab(val route: String, val label: String, val icon: ImageVector) {
    CALENDAR("calendar", "日历", Icons.Filled.CalendarMonth),
    STANDINGS("standings", "积分榜", Icons.Filled.Leaderboard),
    TEAMS("teams", "球员榜", Icons.Filled.Star),
    SETTINGS("settings", "设置", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    private val pendingOpenMatchId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenMatchId.value = intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_MATCH_ID)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                ThemeTokens.CanvasPearl.toArgb(),
                ThemeTokens.CanvasPearl.toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.light(
                ThemeTokens.CanvasPearl.toArgb(),
                ThemeTokens.CanvasPearl.toArgb(),
            ),
        )
        setContent {
            FootballCalendarTheme {
                val pendingMatchId by pendingOpenMatchId.collectAsStateWithLifecycle()
                FootballCalendarRoot(
                    pendingMatchId = pendingMatchId,
                    onConsumePendingMatchId = { pendingOpenMatchId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(NotificationHelper.EXTRA_OPEN_MATCH_ID)?.let { mid ->
            pendingOpenMatchId.value = mid
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FootballCalendarRoot(
    pendingMatchId: String? = null,
    onConsumePendingMatchId: () -> Unit = {},
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: AppTab.CALENDAR.route
    val lifecycleOwner = context as? LifecycleOwner

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(state.prefs.notifyOnLiveBroadcast) {
        if (state.prefs.notifyOnLiveBroadcast && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(pendingMatchId, state.allMatches.size) {
        val mid = pendingMatchId
        if (!mid.isNullOrBlank() && state.allMatches.isNotEmpty()) {
            viewModel.openMatchById(mid)
            onConsumePendingMatchId()
        }
    }

    LaunchedEffect(state.liveBroadcastAlert?.id) {
        if (state.liveBroadcastAlert != null) {
            delay(9_000L)
            viewModel.dismissLiveBroadcast()
        }
    }

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

    val selectedMatch = state.selectedMatchDetail
    val selectedTeam = state.selectedTeamDetail
    val selectedPlayer = state.selectedStarPlayer
    val hasPopupModalOpen = selectedMatch != null && !state.canNavigateBackModal && currentRoute == AppTab.CALENDAR.route
    val backdropBlur by animateDpAsState(
        targetValue = if (hasPopupModalOpen) 18.dp else 0.dp,
        label = "backdropBlur",
    )

    BackHandler(enabled = selectedMatch != null || selectedTeam != null || selectedPlayer != null) {
        viewModel.navigateBackModal()
    }

    val hazeState = remember { HazeState() }
    val haptic = LocalHapticFeedback.current
    val selectedTabIndex = remember(currentRoute) {
        AppTab.entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ThemeTokens.CanvasPearl),
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .haze(state = hazeState)
                    .then(if (backdropBlur > 0.dp) Modifier.blur(backdropBlur) else Modifier),
                containerColor = ThemeTokens.CanvasPearl,
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 86.dp),
                    )
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = AppTab.CALENDAR.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding()),
                ) {
                    composable(AppTab.CALENDAR.route) {
                        CalendarScreen(
                            state = state,
                            onSelectDate = viewModel::selectDate,
                            onPrevMonth = viewModel::previousMonth,
                            onNextMonth = viewModel::nextMonth,
                            onJumpToday = viewModel::jumpToToday,
                            onToggleExpanded = viewModel::toggleMonthExpanded,
                            onSelectLotteryFilter = viewModel::setLotteryFilterOnly,
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
                    onTestLiveBroadcast = viewModel::sendTestLiveBroadcast,
                    onSyncSystemCalendarNow = viewModel::syncMatchesToSystemCalendarNow,
                    onOpenMatchDetail = { m -> viewModel.openMatchDetail(m) },
                    onOpenTeamDetail = { en, zh, cc, cr, tid -> viewModel.openTeamDetail(en, zh, cc, cr, tid) },
                    onBuildShareIcsIntent = viewModel::buildShareIcsIntent,
                    onShowMessage = viewModel::showBanner,
                )
            }
        }
    }

    if (selectedMatch != null) {
        androidx.compose.runtime.key(selectedMatch.id) {
            val relatedPlayers = remember(selectedMatch.id, state.starPlayers) {
                state.starPlayers.filter { p ->
                    (p.clubZhName.isNotBlank() && selectedMatch.involvesTeam(p.clubZhName)) ||
                        (p.clubName.isNotBlank() && selectedMatch.involvesTeam(p.clubName)) ||
                        (p.nationalZhName.isNotBlank() && selectedMatch.involvesTeam(p.nationalZhName)) ||
                        (p.nationalName.isNotBlank() && selectedMatch.involvesTeam(p.nationalName))
                }
            }
            val openMatchPopupOnHome = !state.canNavigateBackModal && currentRoute == AppTab.CALENDAR.route
            if (openMatchPopupOnHome) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = viewModel::closeAllModals,
                    sheetState = sheetState,
                    containerColor = ThemeTokens.SurfacePure,
                    scrimColor = ThemeTokens.ModalScrim,
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
                        isFullScreen = false,
                        canNavigateBack = state.canNavigateBackModal,
                        onNavigateBack = viewModel::navigateBackModal,
                        onCloseAll = viewModel::closeAllModals,
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
            } else {
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
                    isFullScreen = true,
                    canNavigateBack = state.canNavigateBackModal,
                    onNavigateBack = viewModel::navigateBackModal,
                    onCloseAll = viewModel::closeAllModals,
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
                onCloseAll = viewModel::closeAllModals,
                onToggleFavorite = { viewModel.toggleFavoriteTeam(selectedTeam.zhName) },
                onClickSquadMember = { member -> viewModel.openSquadMemberAsPlayer(member, selectedTeam) },
                onClickMatch = { m -> viewModel.openMatchDetail(m) },
            )
        }
    } else if (selectedPlayer != null) {
        androidx.compose.runtime.key(selectedPlayer.id) {
            val playerMatches = remember(
                selectedPlayer.id,
                state.selectedPlayerMatches,
                state.allMatches,
            ) {
                val fallbackLocal = state.allMatches.filter { m ->
                    (selectedPlayer.clubZhName.isNotBlank() && m.involvesTeam(selectedPlayer.clubZhName)) ||
                        (selectedPlayer.clubName.isNotBlank() && m.involvesTeam(selectedPlayer.clubName)) ||
                        (selectedPlayer.nationalZhName.isNotBlank() && m.involvesTeam(selectedPlayer.nationalZhName)) ||
                        (selectedPlayer.nationalName.isNotBlank() && m.involvesTeam(selectedPlayer.nationalName))
                }
                (state.selectedPlayerMatches + fallbackLocal)
                    .distinctBy { "${it.utcDate.take(10)}|${it.homeTeamZhName}|${it.awayTeamZhName}" }
                    .sortedByDescending { it.utcDate }
            }

            PlayerDetailSheet(
                player = selectedPlayer,
                isFavorite = selectedPlayer.id in state.prefs.favoritePlayers,
                upcomingMatches = playerMatches,
                isLoadingHistory = state.isLoadingPlayerHistory,
                canNavigateBack = state.canNavigateBackModal,
                onNavigateBack = viewModel::navigateBackModal,
                onCloseAll = viewModel::closeAllModals,
                onToggleFavorite = { viewModel.toggleFavoritePlayer(selectedPlayer.id) },
                onClickTeam = { en, zh, cc, cr -> viewModel.openTeamDetail(en, zh, cc, cr) },
                onClickMatch = { m -> viewModel.openMatchDetail(m) },
            )
        }
    }

        // Floating Scheme C Liquid Glass 4-Tab Pill Dock (shown on top-level tabs only)
        AnimatedVisibility(
            visible = selectedMatch == null && selectedTeam == null && selectedPlayer == null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            LiquidGlassSurface(
                hazeState = hazeState,
                shape = RoundedCornerShape(999.dp),
                elevation = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
            ) {
                val tabWidth = maxWidth / AppTab.entries.size
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * selectedTabIndex,
                    animationSpec = schemeCSpringSpec(),
                    label = "floatingDockIndicatorOffset",
                )
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(ThemeTokens.SurfacePure)
                        .border(
                            width = 0.5.dp,
                            color = ThemeTokens.Hairline,
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        val tint by animateColorAsState(
                            targetValue = if (selected) ThemeTokens.AccentFallback else ThemeTokens.InkMuted,
                            animationSpec = schemeCSpringSpec(),
                            label = "floatingDockTabTint",
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (!selected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        navController.navigate(tab.route) {
                                            popUpTo(AppTab.CALENDAR.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(21.dp),
                            )
                            Text(
                                text = tab.label,
                                color = tint,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        }

        // In-App Real-Time Match Broadcast Floating Banner (top overlay across all screens)
        val liveAlert = state.liveBroadcastAlert
        AnimatedVisibility(
            visible = liveAlert != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            if (liveAlert != null) {
                Surface(
                    onClick = {
                        viewModel.dismissLiveBroadcast()
                        viewModel.openMatchDetail(liveAlert.match)
                    },
                    color = ThemeTokens.SurfacePure,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, ThemeTokens.LivePulseBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ThemeTokens.LivePulse),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = liveAlert.headline,
                                color = ThemeTokens.LivePulse,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = liveAlert.detail,
                                color = ThemeTokens.InkPrimary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = "看直播 ›",
                            color = ThemeTokens.AccentFallback,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = "✕",
                            color = ThemeTokens.InkMuted,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { viewModel.dismissLiveBroadcast() }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        }
    }
}
