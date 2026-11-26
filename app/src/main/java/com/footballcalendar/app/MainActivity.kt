package com.footballcalendar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.footballcalendar.app.ui.CalendarViewModel
import com.footballcalendar.app.ui.screens.CalendarScreen
import com.footballcalendar.app.ui.screens.SettingsScreen
import com.footballcalendar.app.ui.screens.StandingsScreen
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

@Composable
private fun FootballCalendarRoot(
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: AppTab.CALENDAR.route

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
                    onRefreshLiveScores = viewModel::refreshLiveScoresNow,
                    onRefreshMatchLiveDetail = viewModel::refreshSelectedMatchLiveDetail,
                    onDismissChanges = viewModel::dismissChangesBanner,
                    onOpenMatchDetail = viewModel::openMatchDetail,
                    onCloseMatchDetail = viewModel::closeMatchDetail,
                    onOpenPlayerDetail = { player ->
                        viewModel.closeMatchDetail()
                        viewModel.openPlayerDetail(player)
                        navController.navigate(AppTab.TEAMS.route) {
                            popUpTo(AppTab.CALENDAR.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onToggleFavoriteTeam = viewModel::toggleFavoriteTeam,
                    onToggleWatchlist = viewModel::toggleWatchlistMatch,
                    onBuildCalendarIntent = viewModel::buildAddToCalendarIntent,
                    onBuildShareIcsIntent = viewModel::buildShareIcsIntent,
                )
            }
            composable(AppTab.STANDINGS.route) {
                StandingsScreen(
                    state = state,
                    onSelectLeague = viewModel::selectStandingsLeague,
                    onClickTeam = viewModel::openTeamDetailFromStanding,
                    onCloseTeamDetail = viewModel::closeTeamDetail,
                    onToggleFavoriteTeam = viewModel::toggleFavoriteTeam,
                    onClickSquadMember = { member, team ->
                        viewModel.openSquadMemberAsPlayer(member, team)
                        navController.navigate(AppTab.TEAMS.route) {
                            popUpTo(AppTab.CALENDAR.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenMatchDetail = { match ->
                        viewModel.openMatchDetail(match)
                        navController.navigate(AppTab.CALENDAR.route) {
                            popUpTo(AppTab.CALENDAR.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(AppTab.TEAMS.route) {
                TeamsScreen(
                    state = state,
                    onToggleCompetition = viewModel::toggleCompetition,
                    onToggleFavoriteTeam = viewModel::toggleFavoriteTeam,
                    onToggleFavoritePlayer = viewModel::toggleFavoritePlayer,
                    onOpenPlayerDetail = viewModel::openPlayerDetail,
                    onClosePlayerDetail = viewModel::closePlayerDetail,
                    onSearchExternalPlayer = viewModel::searchExternalPlayer,
                    onOpenMatchDetail = { match ->
                        viewModel.openMatchDetail(match)
                        navController.navigate(AppTab.CALENDAR.route) {
                            popUpTo(AppTab.CALENDAR.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(AppTab.SETTINGS.route) {
                SettingsScreen(
                    state = state,
                    onSaveDataSource = viewModel::saveDataSource,
                    onRefreshLiveScores = viewModel::refreshLiveScoresNow,
                    onSimulateDemoUpdate = { viewModel.syncNow(simulateDemoChange = true) },
                    onUpdateSyncSettings = viewModel::updateSyncSettings,
                    onUpdateDisplayAndNotifications = viewModel::updateDisplayAndNotificationSettings,
                    onSyncSystemCalendarNow = viewModel::syncMatchesToSystemCalendarNow,
                    onOpenMatchDetail = { match ->
                        viewModel.openMatchDetail(match)
                        navController.navigate(AppTab.CALENDAR.route) {
                            popUpTo(AppTab.CALENDAR.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onBuildShareIcsIntent = viewModel::buildShareIcsIntent,
                    onShowMessage = viewModel::showBanner,
                )
            }
        }
    }
}
