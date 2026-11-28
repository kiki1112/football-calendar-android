package com.footballcalendar.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.footballcalendar.app.model.DataSourceMode
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.ui.CalendarUiState
import com.footballcalendar.app.ui.theme.AppleCanvas
import com.footballcalendar.app.ui.theme.AppleRed
import com.footballcalendar.app.ui.theme.AppleRedBorder
import com.footballcalendar.app.ui.theme.AppleRedTint
import com.footballcalendar.app.ui.theme.InkPrimary
import com.footballcalendar.app.ui.theme.InkSecondary
import com.footballcalendar.app.ui.theme.ThemeTokens
import com.footballcalendar.app.util.MatchTimeFormatter

@Composable
fun SettingsScreen(
    state: CalendarUiState,
    onSaveDataSource: (DataSourceMode, String) -> Unit,
    onSimulateDemoUpdate: () -> Unit,
    onUpdateSyncSettings: (Boolean, Int, Boolean) -> Unit,
    onUpdateDisplayAndNotifications: (Boolean, Boolean, Int, Boolean, Boolean) -> Unit,
    onTestLiveBroadcast: () -> Unit,
    onSyncSystemCalendarNow: () -> Unit,
    onOpenMatchDetail: (MatchItem) -> Unit,
    onOpenTeamDetail: (enName: String, zhName: String, compCode: String, crest: String, teamId: Int) -> Unit,
    onBuildShareIcsIntent: (List<MatchItem>, String) -> Intent?,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current

    var selectedMode by remember(state.prefs.dataSourceMode) { mutableStateOf(state.prefs.dataSourceMode) }
    var githubPagesUrl by remember(state.prefs.githubPagesUrl) { mutableStateOf(state.prefs.githubPagesUrl) }

    var autoSyncEnabled by remember(state.prefs.autoSyncEnabled) { mutableStateOf(state.prefs.autoSyncEnabled) }
    var intervalHours by remember(state.prefs.syncIntervalHours) { mutableIntStateOf(state.prefs.syncIntervalHours) }
    var wifiOnly by remember(state.prefs.wifiOnlySync) { mutableStateOf(state.prefs.wifiOnlySync) }

    var watchNightGrouping by remember(state.prefs.watchNightGrouping) { mutableStateOf(state.prefs.watchNightGrouping) }
    var notifyOnChange by remember(state.prefs.notifyOnScheduleChange) { mutableStateOf(state.prefs.notifyOnScheduleChange) }
    var notifyOnLiveBroadcast by remember(state.prefs.notifyOnLiveBroadcast) { mutableStateOf(state.prefs.notifyOnLiveBroadcast) }
    var notifyBeforeMins by remember(state.prefs.notifyBeforeMatchMinutes) { mutableIntStateOf(state.prefs.notifyBeforeMatchMinutes) }
    var autoSyncCalendar by remember(state.prefs.autoSyncSystemCalendar) { mutableStateOf(state.prefs.autoSyncSystemCalendar) }

    LaunchedEffect(state.prefs) {
        selectedMode = state.prefs.dataSourceMode
        githubPagesUrl = state.prefs.githubPagesUrl
        autoSyncEnabled = state.prefs.autoSyncEnabled
        intervalHours = state.prefs.syncIntervalHours
        wifiOnly = state.prefs.wifiOnlySync
        watchNightGrouping = state.prefs.watchNightGrouping
        notifyOnChange = state.prefs.notifyOnScheduleChange
        notifyOnLiveBroadcast = state.prefs.notifyOnLiveBroadcast
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
            onUpdateDisplayAndNotifications(watchNightGrouping, notifyOnChange, notifyBeforeMins, true, notifyOnLiveBroadcast)
        } else {
            autoSyncCalendar = false
            onShowMessage("需要日历读写权限才能自动托管同步到系统日历")
        }
    }

    val watchlistMatches = state.allMatches.filter { it.id in state.prefs.watchlistMatchIds }.sortedBy { it.utcDate }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeTokens.CanvasPearl),
        contentPadding = PaddingValues(bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            ) {
                Text(
                    text = "看球闹钟与云端零密钥订阅",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppleRed,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "清单与设置",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkPrimary,
                    letterSpacing = (-0.5).sp,
                )
            }
        }

        // Watchlist Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppleRedBorder),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("♥ 我的必看清单（${watchlistMatches.size} 场）", fontWeight = FontWeight.Bold, color = AppleRed)
                        Text("开赛前 ${notifyBeforeMins} 分提醒", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                    }
                    if (watchlistMatches.isEmpty()) {
                        Text(
                            text = "在日历卡片右上角点一下 ♥，即可把想熬夜看的焦点战加入必看清单。",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSecondary,
                        )
                    } else {
                        watchlistMatches.take(5).forEach { m ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onOpenMatchDetail(m) }
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${m.homeTeamZhName} (${m.homeTeamName})",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppleRed,
                                            modifier = Modifier.clickable {
                                                onOpenTeamDetail(m.homeTeamName, m.homeTeamZhName, m.competitionCode, m.homeTeamCrest, m.homeTeamId)
                                            },
                                        )
                                        Text(" vs ", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                                        Text(
                                            text = "${m.awayTeamZhName} (${m.awayTeamName})",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppleRed,
                                            modifier = Modifier.clickable {
                                                onOpenTeamDetail(m.awayTeamName, m.awayTeamZhName, m.competitionCode, m.awayTeamCrest, m.awayTeamId)
                                            },
                                        )
                                    }
                                    Text(MatchTimeFormatter.formatMonthDayTime(m.utcDate), style = MaterialTheme.typography.labelMedium, color = AppleRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cloud Sync Status Group (0-Yuan Cloud Mid-Platform - Zero API Keys in Client)
        item {
            val totalMatchesCount = state.allMatches.size
            val lotteryMatchesCount = remember(state.allMatches) {
                state.allMatches.count { !it.lotteryCode.isNullOrBlank() }
            }
            val syncClockText = remember(state.prefs.lastSyncUtc) {
                state.prefs.lastSyncUtc.takeIf { it.isNotBlank() }?.let { raw ->
                    MatchTimeFormatter.formatMonthDayTime(raw).ifBlank { raw }
                } ?: "尚未手动触发（已启用本地快照）"
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "云端同步状态卡片",
                    style = MaterialTheme.typography.labelLarge,
                    color = ThemeTokens.InkSecondary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                )
                Surface(color = ThemeTokens.SurfacePure, shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = ThemeTokens.AccentSoftBg, shape = RoundedCornerShape(14.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = if (selectedMode == DataSourceMode.GITHUB_PAGES) {
                                        "● 云端 0 元聚合中台已连接（客户端零密钥）"
                                    } else {
                                        "● 离线精选缓存模式（弱网/无网保护）"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ThemeTokens.AccentFallback,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "上次全量同步：$syncClockText",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = ThemeTokens.InkPrimary,
                                )
                                Text(
                                    text = "已缓存 ${totalMatchesCount} 场赛程 · 体彩竞彩开售 ${lotteryMatchesCount} 场",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = ThemeTokens.InkSecondary,
                                )
                            }
                        }

                        DataSourceMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedMode = mode }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selectedMode == mode, onClick = { selectedMode = mode })
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(mode.displayName, fontWeight = FontWeight.SemiBold, color = ThemeTokens.InkPrimary)
                                    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = ThemeTokens.InkSecondary)
                                }
                            }
                        }

                        if (selectedMode == DataSourceMode.GITHUB_PAGES) {
                            OutlinedTextField(
                                value = githubPagesUrl,
                                onValueChange = { githubPagesUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("云端聚合订阅地址 (fixtures.json)") },
                                singleLine = true,
                            )
                        }

                        Button(
                            onClick = { onSaveDataSource(selectedMode, githubPagesUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeTokens.AccentFallback),
                            enabled = !state.isSyncing,
                        ) {
                            Text(
                                text = if (state.isSyncing) "正在同步云端全量与体彩赛程..." else "保存并立即全量同步",
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Surface(
                            color = ThemeTokens.SurfaceSubtle,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "● 每 30 分钟云端自动同步赛程、积分榜与体彩竞彩开售清单（09:00/15:00/21:00 加频刷新），客户端无需配置任何第三方 API Key",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = ThemeTokens.InkSecondary,
                            )
                        }

                        if (selectedMode == DataSourceMode.DEMO) {
                            OutlinedButton(
                                onClick = onSimulateDemoUpdate,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !state.isSyncing,
                            ) {
                                Text("模拟赛程改期变更（测试自动 Diff 与通知）")
                            }
                        }
                    }
                }
            }
        }

        // Background Sync & Display Group
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("后台更新与观赛习惯", style = MaterialTheme.typography.labelLarge, color = InkSecondary, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppleSwitchRow(
                            title = "定时自动检查改期与比分",
                            subtitle = "通过 WorkManager 后台静默更新赛程与积分榜",
                            checked = autoSyncEnabled,
                            onCheckedChange = { checked ->
                                autoSyncEnabled = checked
                                onUpdateSyncSettings(checked, intervalHours, wifiOnly)
                            },
                        )
                        val settingsChipColors = FilterChipDefaults.filterChipColors(
                            containerColor = ThemeTokens.SurfaceSubtle,
                            labelColor = ThemeTokens.InkSecondary,
                            selectedContainerColor = ThemeTokens.AccentFallback,
                            selectedLabelColor = ThemeTokens.PureWhite,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(6, 12, 24).forEach { hrs ->
                                FilterChip(
                                    selected = intervalHours == hrs,
                                    onClick = {
                                        intervalHours = hrs
                                        onUpdateSyncSettings(autoSyncEnabled, hrs, wifiOnly)
                                    },
                                    colors = settingsChipColors,
                                    border = null,
                                    label = { Text("每 ${hrs} 小时") },
                                )
                            }
                        }
                        HorizontalDivider(color = AppleCanvas)
                        AppleSwitchRow(
                            title = "仅在 Wi-Fi 下后台更新",
                            subtitle = "节省移动数据流量",
                            checked = wifiOnly,
                            onCheckedChange = { checked ->
                                wifiOnly = checked
                                onUpdateSyncSettings(autoSyncEnabled, intervalHours, checked)
                            },
                        )
                        HorizontalDivider(color = AppleCanvas)
                        AppleSwitchRow(
                            title = "观赛夜分组（凌晨场归入前夜）",
                            subtitle = "次日 00:00–05:59 欧洲赛事自动显示在前夜日历中",
                            checked = watchNightGrouping,
                            onCheckedChange = { checked ->
                                watchNightGrouping = checked
                                onUpdateDisplayAndNotifications(checked, notifyOnChange, notifyBeforeMins, autoSyncCalendar, notifyOnLiveBroadcast)
                            },
                        )
                        HorizontalDivider(color = AppleCanvas)
                        AppleSwitchRow(
                            title = "比赛实时进球与赛况播报推送",
                            subtitle = "开赛、实时进球破门与终场比分第一时间弹出消息通知",
                            checked = notifyOnLiveBroadcast,
                            onCheckedChange = { checked ->
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                notifyOnLiveBroadcast = checked
                                onUpdateDisplayAndNotifications(watchNightGrouping, notifyOnChange, notifyBeforeMins, autoSyncCalendar, checked)
                            },
                        )
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onTestLiveBroadcast()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("发送一条测试实时播报推送", fontWeight = FontWeight.SemiBold, color = AppleRed)
                        }
                        HorizontalDivider(color = AppleCanvas)
                        AppleSwitchRow(
                            title = "赛程改期 / 延期即时通知",
                            subtitle = "关注球队开球时间变化时第一时间通知",
                            checked = notifyOnChange,
                            onCheckedChange = { checked ->
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                notifyOnChange = checked
                                onUpdateDisplayAndNotifications(watchNightGrouping, checked, notifyBeforeMins, autoSyncCalendar, notifyOnLiveBroadcast)
                            },
                        )
                    }
                }
            }
        }

        // System Calendar & Export
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("系统日历托管与导出", style = MaterialTheme.typography.labelLarge, color = InkSecondary, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppleSwitchRow(
                            title = "自动同步到手机系统日历",
                            subtitle = "自动维护独立「足球赛事日历」，改期自动修正",
                            checked = autoSyncCalendar,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    calendarPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                                    )
                                } else {
                                    autoSyncCalendar = false
                                    onUpdateDisplayAndNotifications(watchNightGrouping, notifyOnChange, notifyBeforeMins, false, notifyOnLiveBroadcast)
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
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("写入系统日历")
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = onBuildShareIcsIntent(state.filteredMatches, "football_followed.ics")
                                    if (intent != null) {
                                        context.startActivity(Intent.createChooser(intent, "分享关注赛程 (.ics)"))
                                    } else {
                                        onShowMessage("当前没有可导出的赛程")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
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
private fun AppleSwitchRow(
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
            Text(title, fontWeight = FontWeight.SemiBold, color = InkPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ThemeTokens.PureWhite,
                checkedTrackColor = ThemeTokens.AccentFallback,
                uncheckedThumbColor = ThemeTokens.PureWhite,
                uncheckedTrackColor = ThemeTokens.SwitchUncheckedTrack,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
