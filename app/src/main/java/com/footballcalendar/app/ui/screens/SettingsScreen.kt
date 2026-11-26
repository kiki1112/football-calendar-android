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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.footballcalendar.app.util.MatchTimeFormatter

@Composable
fun SettingsScreen(
    state: CalendarUiState,
    onSaveDataSource: (DataSourceMode, String, String, String) -> Unit,
    onSimulateDemoUpdate: () -> Unit,
    onUpdateSyncSettings: (Boolean, Int, Boolean) -> Unit,
    onUpdateDisplayAndNotifications: (Boolean, Boolean, Int, Boolean) -> Unit,
    onSyncSystemCalendarNow: () -> Unit,
    onOpenMatchDetail: (MatchItem) -> Unit,
    onOpenTeamDetail: (enName: String, zhName: String, compCode: String, crest: String, teamId: Int) -> Unit,
    onBuildShareIcsIntent: (List<MatchItem>, String) -> Intent?,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current

    var selectedMode by remember(state.prefs.dataSourceMode) { mutableStateOf(state.prefs.dataSourceMode) }
    var githubPagesUrl by remember(state.prefs.githubPagesUrl) { mutableStateOf(state.prefs.githubPagesUrl) }
    var customFdKey by remember(state.prefs.footballDataApiKey) { mutableStateOf(state.prefs.footballDataApiKey) }
    var customAfKey by remember(state.prefs.apiFootballKey) { mutableStateOf(state.prefs.apiFootballKey) }

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
        customFdKey = state.prefs.footballDataApiKey
        customAfKey = state.prefs.apiFootballKey
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
            onUpdateDisplayAndNotifications(watchNightGrouping, notifyOnChange, notifyBeforeMins, true)
        } else {
            autoSyncCalendar = false
            onShowMessage("需要日历读写权限才能自动托管同步到系统日历")
        }
    }

    val watchlistMatches = state.allMatches.filter { it.id in state.prefs.watchlistMatchIds }.sortedBy { it.utcDate }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleCanvas),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            ) {
                Text(
                    text = "看球闹钟与加密内置订阅",
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

        // Data Source Group (Keys built into BuildConfig & GitHub Secrets - never exposed in UI)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("数据源与实时接口配置", style = MaterialTheme.typography.labelLarge, color = InkSecondary, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = AppleRedTint, shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val fdStatus = if (state.prefs.hasBuiltInFootballDataKey()) "已内置加密部署（无需填写）" else "仅通过 GitHub Actions 同步"
                                val afStatus = if (state.prefs.hasBuiltInApiFootballKey()) "已内置加密部署（Free 100次/日 · 支持实时事件）" else "仅通过 GitHub Actions 同步"
                                Text("Football-Data：$fdStatus", style = MaterialTheme.typography.labelSmall, color = AppleRed, fontWeight = FontWeight.Bold)
                                Text("API-Football：$afStatus", style = MaterialTheme.typography.labelSmall, color = InkPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        DataSourceMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMode = mode }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selectedMode == mode, onClick = { selectedMode = mode })
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(mode.displayName, fontWeight = FontWeight.SemiBold, color = InkPrimary)
                                    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                                }
                            }
                        }

                        if (selectedMode == DataSourceMode.GITHUB_PAGES) {
                            OutlinedTextField(
                                value = githubPagesUrl,
                                onValueChange = { githubPagesUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("GitHub Pages 订阅地址") },
                                singleLine = true,
                            )
                        }

                        if (selectedMode == DataSourceMode.FOOTBALL_DATA_API) {
                            OutlinedTextField(
                                value = customFdKey,
                                onValueChange = { customFdKey = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Football-Data Key（留空即用内置 Key）") },
                                placeholder = { Text("已安全内置部署，无需重复输入") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = customAfKey,
                                onValueChange = { customAfKey = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("API-Football Key（留空即用内置 Key）") },
                                placeholder = { Text("已安全内置部署，无需重复输入") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                            )
                        }

                        Button(
                            onClick = { onSaveDataSource(selectedMode, githubPagesUrl, customFdKey, customAfKey) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppleRed),
                            enabled = !state.isSyncing,
                        ) {
                            Text(if (state.isSyncing) "正在同步..." else "保存并立即全量同步", fontWeight = FontWeight.Bold)
                        }

                        Surface(
                            color = AppleRedTint,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "● 已默认开启 ESPN + API-Football 零延迟实时同步：进行中比分每 15 秒、比赛详情页事件/阵容每 10 秒全自动更新，无需任何手动按钮",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppleRed,
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
                                onUpdateDisplayAndNotifications(checked, notifyOnChange, notifyBeforeMins, autoSyncCalendar)
                            },
                        )
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
                                onUpdateDisplayAndNotifications(watchNightGrouping, checked, notifyBeforeMins, autoSyncCalendar)
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
                                    onUpdateDisplayAndNotifications(watchNightGrouping, notifyOnChange, notifyBeforeMins, false)
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
                checkedThumbColor = Color.White,
                checkedTrackColor = AppleRed,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE5E5EA),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
