package com.footballcalendar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.footballcalendar.app.ui.CalendarViewModel
import com.footballcalendar.app.ui.screens.CalendarScreen
import com.footballcalendar.app.ui.screens.SettingsScreen
import com.footballcalendar.app.ui.screens.TeamsScreen
import com.footballcalendar.app.ui.theme.FootballCalendarTheme

private enum class AppTab(val route: String, val label: String, val icon: ImageVector) {
    CALENDAR("calendar", "赛事日历", Icons.Filled.CalendarMonth),
    TEAMS("teams", "关注球队", Icons.Filled.Star),
    SETTINGS("settings", "同步设置", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
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
                    )
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
                    onToggleOnlyFavorites = viewModel::toggleOnlyFavorites,
                    onSelectLeagueFilter = viewModel::selectLeagueFilter,
                    onRefresh = { viewModel.syncNow(simulateDemoChange = false) },
                    onDismissChanges = viewModel::dismissChangesBanner,
                    onOpenMatchDetail = viewModel::openMatchDetail,
                    onCloseMatchDetail = viewModel::closeMatchDetail,
                    onToggleFavoriteTeam = viewModel::toggleFavoriteTeam,
                    onBuildCalendarIntent = viewModel::buildAddToCalendarIntent,
                    onBuildShareIcsIntent = viewModel::buildShareIcsIntent,
                )
            }
            composable(AppTab.TEAMS.route) {
                TeamsScreen(
                    state = state,
                    onToggleCompetition = viewModel::toggleCompetition,
                    onToggleFavoriteTeam = viewModel::toggleFavoriteTeam,
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
                    onBuildShareIcsIntent = viewModel::buildShareIcsIntent,
                    onShowMessage = viewModel::showBanner,
                )
            }
        }
    }
}
