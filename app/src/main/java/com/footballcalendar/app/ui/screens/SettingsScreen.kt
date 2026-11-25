package com.footballcalendar.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footballcalendar.app.model.DataSourceMode
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.ui.CalendarUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: CalendarUiState,
    onSaveDataSource: (DataSourceMode, String, String) -> Unit,
    onSimulateDemoUpdate: () -> Unit,
    onUpdateSyncSettings: (Boolean, Int, Boolean) -> Unit,
    onUpdateDisplayAndNotifications: (Boolean, Boolean, Int, Boolean) -> Unit,
    onSyncSystemCalendarNow: () -> Unit,
    onBuildShareIcsIntent: (List<MatchItem>, String) -> Intent?,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current

    var selectedMode by remember(state.prefs.dataSourceMode) { mutableStateOf(state.prefs.dataSourceMode) }
    var githubPagesUrl by remember(state.prefs.githubPagesUrl) { mutableStateOf(state.prefs.githubPagesUrl) }
    var apiKey by remember(state.prefs.footballDataApiKey) { mutableStateOf(state.prefs.footballDataApiKey) }

    var autoSyncEnabled by remember(state.prefs.autoSyncEnabled) { mutableStateOf(state.prefs.autoSyncEnabled) }
    var intervalHours by remember(state.prefs.syncIntervalHours) { mutableIntStateOf(state.prefs.syncIntervalHours) }
    var wifiOnly by remember(state.prefs.wifiOnlySync) { mutableStateOf(state.prefs.wifiOnlySync) }

    var watchNightGrouping by remember(state.prefs.watchNightGrouping) { mutableStateOf(state.prefs.watchNightGrouping) }
    var notifyOnChange by remember(state.prefs.notifyOnScheduleChange) { mutableStateOf(state.prefs.notifyOnScheduleChange) }
    var notifyBeforeMins by remember(state.prefs.notifyBeforeMatchMinutes) { mutableIntStateOf(state.prefs.notifyBeforeMatchMinutes) }
    var autoSyncCalendar by remember(state.prefs.autoSyncSystemCalendar) { mutableStateOf(state.prefs.autoSyncSystemCalendar) }

    LaunchedEffect(state.prefs) {
        selectedMode = state.prefs.dataSourceMode
        githubPagesUrl = state.prefs.githubPagesUrl
        apiKey = state.prefs.footballDataApiKey
        autoSyncEnabled = state.prefs.autoSyncEnabled
        intervalHours = state.prefs.syncIntervalHours
        wifiOnly = state.prefs.wifiOnlySync
        watchNightGrouping = state.prefs.watchNightGrouping
        notifyOnChange = state.prefs.notifyOnScheduleChange
        notifyBeforeMins = state.prefs.notifyBeforeMatchMinutes
        autoSyncCalendar = state.prefs.autoSyncSystemCalendar
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            onShowMessage("未授予通知权限，后台赛程提醒可能无法弹出")
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            autoSyncCalendar = true
            onUpdateDisplayAndNotifications(
                watchNightGrouping,
                notifyOnChange,
                notifyBeforeMins,
                true,
            )
        } else {
            autoSyncCalendar = false
            onShowMessage("需要日历读写权限才能自动托管同步到系统日历")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = "同步源与系统设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Data source configuration
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "赛程数据源",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        DataSourceMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMode = mode }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedMode == mode,
                                    onClick = { selectedMode = mode },
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(mode.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (selectedMode == DataSourceMode.GITHUB_PAGES) {
                            OutlinedTextField(
                                value = githubPagesUrl,
                                onValueChange = { githubPagesUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("GitHub Pages 订阅地址") },
                                placeholder = { Text("https://用户名.github.io/仓库名") },
                                singleLine = true,
                                supportingText = {
                                    Text("支持填仓库 Pages 根路径或完整 fixtures.json 链接")
                                },
                            )
                        }

                        if (selectedMode == DataSourceMode.FOOTBALL_DATA_API) {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Football-Data.org X-Auth-Token") },
                                singleLine = true,
                                supportingText = {
                                    Text("可在 football-data.org 免费注册获取")
                                },
                            )
                        }

                        Button(
                            onClick = { onSaveDataSource(selectedMode, githubPagesUrl, apiKey) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSyncing,
                        ) {
                            Text(if (state.isSyncing) "正在同步..." else "保存并立即同步")
                        }

                        if (selectedMode == DataSourceMode.DEMO) {
                            OutlinedButton(
                                onClick = onSimulateDemoUpdate,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isSyncing,
                            ) {
                                Text("模拟赛程改期变更（测试自动 Diff 与通知）")
                            }
                        }
                    }
                }
            }

            // 2. WorkManager Background Sync Settings
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "自动后台更新 (WorkManager)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        SettingSwitchRow(
                            title = "定时自动拉取最新赛程",
                            subtitle = "在后台静默检查比赛改期、延期和比分更新",
                            checked = autoSyncEnabled,
                            onCheckedChange = { checked ->
                                autoSyncEnabled = checked
                                onUpdateSyncSettings(checked, intervalHours, wifiOnly)
                            },
                        )

                        Text(
                            text = "同步周期",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(6, 12, 24).forEach { hrs ->
                                FilterChip(
                                    selected = intervalHours == hrs,
                                    onClick = {
                                        intervalHours = hrs
                                        onUpdateSyncSettings(autoSyncEnabled, hrs, wifiOnly)
                                    },
                                    label = { Text("每 ${hrs} 小时") },
                                )
                            }
                        }

                        SettingSwitchRow(
                            title = "仅在 Wi-Fi 下后台更新",
                            subtitle = "节省移动网络流量",
                            checked = wifiOnly,
                            onCheckedChange = { checked ->
                                wifiOnly = checked
                                onUpdateSyncSettings(autoSyncEnabled, intervalHours, checked)
                            },
                        )
                    }
                }
            }

            // 3. Display & Notification Preferences
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "观赛显示与提醒",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        SettingSwitchRow(
                            title = "观赛夜分组（凌晨场归入前夜）",
                            subtitle = "次日 00:00–05:59 开球的欧洲赛事自动显示在前一天日历中",
                            checked = watchNightGrouping,
                            onCheckedChange = { checked ->
                                watchNightGrouping = checked
                                onUpdateDisplayAndNotifications(
                                    checked,
                                    notifyOnChange,
                                    notifyBeforeMins,
                                    autoSyncCalendar,
                                )
                            },
                        )

                        SettingSwitchRow(
                            title = "赛程改期 / 延期即时通知",
                            subtitle = "后台发现关注球队比赛时间变化时推送提醒",
                            checked = notifyOnChange,
                            onCheckedChange = { checked ->
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                notifyOnChange = checked
                                onUpdateDisplayAndNotifications(
                                    watchNightGrouping,
                                    checked,
                                    notifyBeforeMins,
                                    autoSyncCalendar,
                                )
                            },
                        )

                        Text(
                            text = "开球前提醒窗口",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "关闭", 15 to "15分", 30 to "30分", 60 to "60分").forEach { (mins, label) ->
                                FilterChip(
                                    selected = notifyBeforeMins == mins,
                                    onClick = {
                                        if (mins > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        notifyBeforeMins = mins
                                        onUpdateDisplayAndNotifications(
                                            watchNightGrouping,
                                            notifyOnChange,
                                            mins,
                                            autoSyncCalendar,
                                        )
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }

            // 4. Device System Calendar & ICS Export
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "系统日历托管与 .ics 导出",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        SettingSwitchRow(
                            title = "自动同步到手机系统日历",
                            subtitle = "自动维护独立的「足球赛事日历」账户，改期自动修正时间",
                            checked = autoSyncCalendar,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    calendarPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR,
                                        ),
                                    )
                                } else {
                                    autoSyncCalendar = false
                                    onUpdateDisplayAndNotifications(
                                        watchNightGrouping,
                                        notifyOnChange,
                                        notifyBeforeMins,
                                        false,
                                    )
                                }
                            },
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = onSyncSystemCalendarNow,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("立即写入系统日历")
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = onBuildShareIcsIntent(
                                        state.filteredMatches,
                                        "football_followed.ics",
                                    )
                                    if (intent != null) {
                                        context.startActivity(Intent.createChooser(intent, "分享关注赛程 (.ics)"))
                                    } else {
                                        onShowMessage("当前没有可导出的赛程")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("导出全部 .ics")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
