package com.footballcalendar.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.ScheduleChange
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.StarPlayer
import com.footballcalendar.app.ui.CalendarUiState
import com.footballcalendar.app.ui.components.AppleSegmentedControl
import com.footballcalendar.app.ui.components.PlayerPortrait
import com.footballcalendar.app.ui.components.TeamCrest
import com.footballcalendar.app.ui.theme.AmberTint
import com.footballcalendar.app.ui.theme.AmberWarm
import com.footballcalendar.app.ui.theme.AppleCanvas
import com.footballcalendar.app.ui.theme.AppleDivider
import com.footballcalendar.app.ui.theme.AppleLiveRed
import com.footballcalendar.app.ui.theme.AppleRed
import com.footballcalendar.app.ui.theme.AppleRedBorder
import com.footballcalendar.app.ui.theme.AppleRedDeep
import com.footballcalendar.app.ui.theme.AppleRedTint
import com.footballcalendar.app.ui.theme.InkPrimary
import com.footballcalendar.app.ui.theme.InkSecondary
import com.footballcalendar.app.ui.theme.InkTertiary
import com.footballcalendar.app.util.MatchTimeFormatter
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToday: () -> Unit,
    onToggleExpanded: () -> Unit,
    onSelectFilterTab: (Int) -> Unit,
    onSelectLeagueFilter: (String?) -> Unit,
    onRefresh: () -> Unit,
    onDismissChanges: () -> Unit,
    onOpenMatchDetail: (MatchItem) -> Unit,
    onCloseMatchDetail: () -> Unit,
    onOpenPlayerDetail: (StarPlayer) -> Unit,
    onToggleFavoriteTeam: (String) -> Unit,
    onToggleWatchlist: (String) -> Unit,
    onBuildCalendarIntent: (MatchItem) -> Intent,
    onBuildShareIcsIntent: (List<MatchItem>, String) -> Intent?,
) {
    val context = LocalContext.current

    PullToRefreshBox(
        isRefreshing = state.isSyncing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(AppleCanvas),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Apple Large Title Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${MatchTimeFormatter.formatYearMonth(state.currentMonth.atDay(1))} · ${state.prefs.lastSyncSummary}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppleRed,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "赛事日历",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkPrimary,
                            letterSpacing = (-0.5).sp,
                        )
                    }
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 1.dp,
                        onClick = { if (!state.isSyncing) onRefresh() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = AppleRed,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "立即同步",
                                    tint = AppleRed,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 2. Hero Next Match Countdown Card
            if (state.heroMatch != null) {
                item {
                    HeroNextMatchCard(
                        match = state.heroMatch,
                        homeRank = state.standingFor(
                            state.heroMatch.competitionCode,
                            state.heroMatch.homeTeamZhName,
                            state.heroMatch.homeTeamName,
                        )?.rank,
                        awayRank = state.standingFor(
                            state.heroMatch.competitionCode,
                            state.heroMatch.awayTeamZhName,
                            state.heroMatch.awayTeamName,
                        )?.rank,
                        onClick = { onOpenMatchDetail(state.heroMatch) },
                    )
                }
            }

            // 3. Apple Segmented Control + League Filter Pills
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppleSegmentedControl(
                        options = listOf(
                            "全部关注",
                            "★ 主队",
                            "♥ 必看(${state.prefs.watchlistMatchIds.size})",
                        ),
                        selectedIndex = state.calendarFilterTab,
                        onSelect = onSelectFilterTab,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LeaguePill(
                            label = "全部",
                            selected = state.selectedLeagueFilter == null,
                            onClick = { onSelectLeagueFilter(null) },
                        )
                        LeaguePill(
                            label = "国家队",
                            selected = state.selectedLeagueFilter == "NAT",
                            onClick = { onSelectLeagueFilter("NAT") },
                        )
                        CompetitionCatalog.all
                            .filter { it.code in state.prefs.followedCompetitions && it.code !in setOf("WC", "EC", "INT") }
                            .forEach { comp ->
                                LeaguePill(
                                    label = comp.zhName,
                                    selected = state.selectedLeagueFilter == comp.code,
                                    onClick = { onSelectLeagueFilter(comp.code) },
                                )
                            }
                    }
                }
            }

            // 4. Apple Clean Calendar Card
            item {
                AppleCalendarCard(
                    currentMonth = state.currentMonth,
                    selectedDate = state.selectedDate,
                    isExpanded = state.isMonthExpanded,
                    matchesByDate = state.matchesByDate,
                    favoriteTeams = state.prefs.favoriteTeams,
                    onSelectDate = onSelectDate,
                    onPrevMonth = onPrevMonth,
                    onNextMonth = onNextMonth,
                    onJumpToday = onJumpToday,
                    onToggleExpanded = onToggleExpanded,
                )
            }

            // 5. Recent Schedule Diff Banner
            if (state.recentChanges.isNotEmpty()) {
                item {
                    ChangesAlertCard(
                        changes = state.recentChanges,
                        onDismiss = onDismissChanges,
                    )
                }
            }

            // 6. Schedule Clash Warning Banner
            if (state.dayClashWarnings.isNotEmpty()) {
                item {
                    ClashWarningBanner(warnings = state.dayClashWarnings)
                }
            }

            // 7. Day Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${MatchTimeFormatter.formatDateTitle(state.selectedDate)} · ${state.selectedDayMatches.size} 场",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary,
                    )
                    if (state.prefs.watchNightGrouping) {
                        Text(
                            text = "含次日凌晨场",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkTertiary,
                        )
                    }
                }
            }

            // 8. Selected Day Matches
            if (state.selectedDayMatches.isEmpty()) {
                item {
                    EmptyDayWhiteCard(
                        onRefresh = onRefresh,
                        upcomingMatches = state.filteredMatches
                            .filter { !it.status.isCancelledOrPostponed }
                            .take(3),
                        onJumpMatchDate = { m ->
                            MatchTimeFormatter.displayDateFor(
                                utcIso = m.utcDate,
                                watchNightGrouping = state.prefs.watchNightGrouping,
                            )?.let(onSelectDate)
                        },
                    )
                }
            } else {
                items(state.selectedDayMatches, key = { it.id }) { match ->
                    AppleMatchCard(
                        match = match,
                        isFavoriteTeamMatch = match.involvesAnyTeam(state.prefs.favoriteTeams),
                        isWatchlisted = match.id in state.prefs.watchlistMatchIds,
                        homeRank = state.standingFor(match.competitionCode, match.homeTeamZhName, match.homeTeamName)?.rank,
                        awayRank = state.standingFor(match.competitionCode, match.awayTeamZhName, match.awayTeamName)?.rank,
                        onClick = { onOpenMatchDetail(match) },
                        onToggleWatchlist = { onToggleWatchlist(match.id) },
                    )
                }
            }
        }
    }

    val detail = state.selectedMatchDetail
    if (detail != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val relatedPlayers = remember(detail.id, state.starPlayers) {
            state.starPlayers.filter { p ->
                (p.clubZhName.isNotBlank() && detail.involvesTeam(p.clubZhName)) ||
                    (p.clubName.isNotBlank() && detail.involvesTeam(p.clubName)) ||
                    (p.nationalZhName.isNotBlank() && detail.involvesTeam(p.nationalZhName)) ||
                    (p.nationalName.isNotBlank() && detail.involvesTeam(p.nationalName))
            }
        }
        ModalBottomSheet(
            onDismissRequest = onCloseMatchDetail,
            sheetState = sheetState,
            containerColor = Color.White.copy(alpha = 0.94f),
            scrimColor = Color(0x4A1C1C1E),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            MatchDetailBottomSheet(
                match = detail,
                favoriteTeams = state.prefs.favoriteTeams,
                isWatchlisted = detail.id in state.prefs.watchlistMatchIds,
                homeStanding = state.standingFor(detail.competitionCode, detail.homeTeamZhName, detail.homeTeamName),
                awayStanding = state.standingFor(detail.competitionCode, detail.awayTeamZhName, detail.awayTeamName),
                relatedPlayers = relatedPlayers,
                onClickPlayer = onOpenPlayerDetail,
                onToggleFavorite = onToggleFavoriteTeam,
                onToggleWatchlist = { onToggleWatchlist(detail.id) },
                onAddToSystemCalendar = { context.startActivity(onBuildCalendarIntent(detail)) },
                onShareIcs = {
                    onBuildShareIcsIntent(listOf(detail), "match_${detail.id}.ics")?.let { intent ->
                        context.startActivity(Intent.createChooser(intent, "分享赛程日历 (.ics)"))
                    }
                },
            )
        }
    }
}

@Composable
private fun HeroNextMatchCard(
    match: MatchItem,
    homeRank: Int?,
    awayRank: Int?,
    onClick: () -> Unit,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(match.id) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val kickoffInstant = MatchTimeFormatter.parseInstant(match.utcDate)
    val countdownText = remember(match.utcDate, nowMillis) {
        if (kickoffInstant == null) {
            "--:--:--"
        } else {
            val sec = Duration.between(Instant.ofEpochMilli(nowMillis), kickoffInstant).seconds
            when {
                sec in -6900..0 -> "LIVE 进行中"
                sec < -6900 -> "已结束"
                sec < 86400 -> {
                    val h = sec / 3600
                    val m = (sec % 3600) / 60
                    val s = sec % 60
                    "%02d:%02d:%02d".format(h, m, s)
                }
                else -> MatchTimeFormatter.formatMonthDayTime(match.utcDate)
            }
        }
    }

    val tagText = when {
        match.isInternational -> "国家队焦点"
        homeRank != null && awayRank != null -> "第${homeRank}名 vs 第${awayRank}名"
        else -> "下一场焦点大战"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(listOf(AppleRed, AppleRedDeep)),
            )
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "下一场焦点 · ${match.competitionZhName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = tagText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamCrest(url = match.homeTeamCrest, teamName = match.homeTeamZhName, size = 44.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = match.homeTeamZhName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "距开球",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        text = countdownText,
                        color = Color.White,
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                    Text(
                        text = "北京时间 ${MatchTimeFormatter.formatMonthDayTime(match.utcDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamCrest(url = match.awayTeamCrest, teamName = match.awayTeamZhName, size = 44.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = match.awayTeamZhName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaguePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) AppleRed else Color.White,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(0.75.dp, AppleDivider),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else InkPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun AppleCalendarCard(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    isExpanded: Boolean,
    matchesByDate: Map<LocalDate, List<MatchItem>>,
    favoriteTeams: Set<String>,
    onSelectDate: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToday: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val today = LocalDate.now()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevMonth, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月", tint = InkPrimary)
                    }
                    Text(
                        text = MatchTimeFormatter.formatYearMonth(currentMonth.atDay(1)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary,
                    )
                    IconButton(onClick = onNextMonth, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下个月", tint = InkPrimary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onJumpToday,
                        color = AppleRedTint,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = "今天",
                            color = AppleRed,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "展开/收起月历",
                            tint = InkSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkTertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val datesToShow = if (isExpanded) buildMonthDates(currentMonth) else buildWeekDates(selectedDate)
            datesToShow.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    week.forEach { date ->
                        val inCurrentMonth = date.month == currentMonth.month
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        val dayMatches = matchesByDate[date].orEmpty()
                        val hasFavorite = dayMatches.any { it.involvesAnyTeam(favoriteTeams) }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) AppleRed else Color.Transparent)
                                .then(
                                    if (isToday && !isSelected) {
                                        Modifier.border(1.2.dp, AppleRed, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { onSelectDate(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = when {
                                        isSelected -> Color.White
                                        isToday -> AppleRed
                                        !inCurrentMonth && isExpanded -> InkTertiary.copy(alpha = 0.45f)
                                        else -> InkPrimary
                                    },
                                )
                                if (dayMatches.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(4.5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> Color.White
                                                    hasFavorite -> AppleRed
                                                    else -> InkTertiary
                                                },
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangesAlertCard(
    changes: List<ScheduleChange>,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = AppleRedTint,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppleRedBorder),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "检测到 ${changes.size} 项赛程变化",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppleRedDeep,
                )
                Text(
                    text = changes.first().summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onDismiss) {
                Text("知道了", color = AppleRed)
            }
        }
    }
}

@Composable
private fun ClashWarningBanner(warnings: List<String>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = AmberTint,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = AmberWarm,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "开球时间撞车提醒",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF78350F),
                )
                Text(
                    text = warnings.first(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF92400E),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AppleMatchCard(
    match: MatchItem,
    isFavoriteTeamMatch: Boolean,
    isWatchlisted: Boolean,
    homeRank: Int?,
    awayRank: Int?,
    onClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
) {
    val kickoff = MatchTimeFormatter.formatKickoffTime(match.utcDate)
    val isEarly = MatchTimeFormatter.isEarlyMorning(match.utcDate)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = if (isFavoriteTeamMatch || isWatchlisted) {
            androidx.compose.foundation.BorderStroke(1.dp, AppleRedBorder)
        } else {
            androidx.compose.foundation.BorderStroke(0.5.dp, AppleDivider)
        },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = match.competitionZhName,
                        color = AppleRed,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (homeRank != null && awayRank != null) {
                        Text(
                            text = "· 第${homeRank} vs 第${awayRank}",
                            color = InkSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (isEarly) {
                        Surface(color = AmberTint, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "次日凌晨",
                                color = AmberWarm,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = when {
                            match.status.isLive -> AppleLiveRed
                            match.status.isCancelledOrPostponed -> AppleRedTint
                            else -> AppleCanvas
                        },
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = if (match.status.isLive) "● LIVE" else match.status.zhLabel,
                            color = when {
                                match.status.isLive -> Color.White
                                match.status.isCancelledOrPostponed -> AppleRed
                                else -> InkSecondary
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }

                    Icon(
                        imageVector = if (isWatchlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "标记必看",
                        tint = if (isWatchlisted) AppleRed else InkTertiary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(onClick = onToggleWatchlist),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TeamCrest(url = match.homeTeamCrest, teamName = match.homeTeamZhName, size = 32.dp)
                    Text(
                        text = match.homeTeamZhName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (match.homeScore != null && match.awayScore != null) {
                        Surface(
                            color = if (match.status.isLive) AppleRedTint else AppleCanvas,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = "${match.homeScore} : ${match.awayScore}",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = if (match.status.isLive) AppleRed else InkPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    } else {
                        Text(
                            text = kickoff,
                            style = TextStyle(
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black,
                                fontFeatureSettings = "tnum",
                            ),
                            color = InkPrimary,
                        )
                        Text(
                            text = "北京时间",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkTertiary,
                        )
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = match.awayTeamZhName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TeamCrest(url = match.awayTeamCrest, teamName = match.awayTeamZhName, size = 32.dp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = AppleCanvas)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = match.venue.ifBlank { match.competitionName },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${MatchTimeFormatter.formatRelativeCountdown(match.utcDate)} ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppleRed,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EmptyDayWhiteCard(
    onRefresh: () -> Unit,
    upcomingMatches: List<MatchItem>,
    onJumpMatchDate: (MatchItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "当天暂无符合筛选条件的赛程",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
            )
            Text(
                text = "下拉可立即更新赛程，或点击跳转到近期焦点比赛日：",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary,
                textAlign = TextAlign.Center,
            )
            upcomingMatches.forEach { m ->
                Surface(
                    onClick = { onJumpMatchDate(m) },
                    color = AppleRedTint,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = "${MatchTimeFormatter.formatMonthDayTime(m.utcDate)} · ${m.homeTeamZhName} vs ${m.awayTeamZhName}",
                        color = AppleRed,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            OutlinedButton(onClick = onRefresh) {
                Text("立即同步最新赛程")
            }
        }
    }
}

@Composable
private fun MatchDetailBottomSheet(
    match: MatchItem,
    favoriteTeams: Set<String>,
    isWatchlisted: Boolean,
    homeStanding: StandingEntry?,
    awayStanding: StandingEntry?,
    relatedPlayers: List<StarPlayer>,
    onClickPlayer: (StarPlayer) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleWatchlist: () -> Unit,
    onAddToSystemCalendar: () -> Unit,
    onShareIcs: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.82f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = AppleRedTint, shape = RoundedCornerShape(999.dp)) {
                Text(
                    text = "${match.competitionZhName} · ${match.status.zhLabel}",
                    color = AppleRed,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
            FilterChip(
                selected = isWatchlisted,
                onClick = onToggleWatchlist,
                label = { Text(if (isWatchlisted) "已加必看" else "加入必看") },
                leadingIcon = {
                    Icon(
                        if (isWatchlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }

        // Expanded Hero Matchup Card
        Surface(
            color = AppleCanvas.copy(alpha = 0.85f),
            shape = RoundedCornerShape(22.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamCrest(url = match.homeTeamCrest, teamName = match.homeTeamZhName, size = 64.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        match.homeTeamZhName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = InkPrimary,
                    )
                    if (homeStanding != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "第 ${homeStanding.rank} 名 · ${homeStanding.points}分",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppleRed,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (match.homeScore != null && match.awayScore != null) {
                            "${match.homeScore} : ${match.awayScore}"
                        } else {
                            MatchTimeFormatter.formatKickoffTime(match.utcDate)
                        },
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            fontFeatureSettings = "tnum",
                        ),
                        color = InkPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "北京时间 ${MatchTimeFormatter.formatMonthDayTime(match.utcDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(color = AppleRedTint, shape = RoundedCornerShape(999.dp)) {
                        Text(
                            text = MatchTimeFormatter.formatRelativeCountdown(match.utcDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppleRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamCrest(url = match.awayTeamCrest, teamName = match.awayTeamZhName, size = 64.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        match.awayTeamZhName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = InkPrimary,
                    )
                    if (awayStanding != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "第 ${awayStanding.rank} 名 · ${awayStanding.points}分",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppleRed,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Grouped iOS Info Card
        Surface(
            color = AppleCanvas.copy(alpha = 0.85f),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                DetailInfoRow("开球时间", "北京时间 ${MatchTimeFormatter.formatMonthDayTime(match.utcDate)}")
                HorizontalDivider(color = AppleDivider)
                DetailInfoRow(
                    "赛事阶段",
                    if (match.matchday != null) "${match.competitionZhName} · 第 ${match.matchday} 轮" else match.competitionZhName,
                )
                HorizontalDivider(color = AppleDivider)
                DetailInfoRow("比赛场地", match.venue.ifBlank { "待官方公布" })
                HorizontalDivider(color = AppleDivider)
                DetailInfoRow("当前状态", match.status.zhLabel)
            }
        }

        // Standings Comparison if available
        if (homeStanding != null || awayStanding != null) {
            Surface(
                color = AppleCanvas.copy(alpha = 0.85f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "积分榜实力对比",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary,
                    )
                    listOfNotNull(homeStanding, awayStanding).forEach { st ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TeamCrest(url = st.teamCrest, teamName = st.teamZhName, size = 22.dp)
                                Text(st.teamZhName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = "第${st.rank}名 · ${st.won}胜${st.draw}平${st.lost}负 · 净胜${st.goalDifference} · ${st.points}分",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkSecondary,
                            )
                        }
                    }
                }
            }
        }

        // Related Star Players
        if (relatedPlayers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "本场看点球星（点击查看资料）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    relatedPlayers.forEach { player ->
                        Surface(
                            onClick = { onClickPlayer(player) },
                            color = AppleCanvas.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PlayerPortrait(url = player.portraitUrl, playerName = player.zhName, size = 36.dp)
                                Column {
                                    Text(player.zhName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("#${player.shirtNumber} · ${player.positionZh}", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val homeFav = match.homeTeamZhName in favoriteTeams
            val awayFav = match.awayTeamZhName in favoriteTeams
            FilterChip(
                selected = homeFav,
                onClick = { onToggleFavorite(match.homeTeamZhName) },
                label = { Text("主队 ${match.homeTeamZhName}") },
                leadingIcon = {
                    Icon(
                        if (homeFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            FilterChip(
                selected = awayFav,
                onClick = { onToggleFavorite(match.awayTeamZhName) },
                label = { Text("主队 ${match.awayTeamZhName}") },
                leadingIcon = {
                    Icon(
                        if (awayFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onAddToSystemCalendar,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppleRed),
            ) {
                Icon(Icons.Filled.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加到手机系统日历", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onShareIcs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("导出标准 .ics 文件分享")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = InkPrimary)
    }
}

private fun buildMonthDates(month: YearMonth): List<LocalDate> {
    val firstOfMonth = month.atDay(1)
    val start = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0 until 42).map { start.plusDays(it.toLong()) }
}

private fun buildWeekDates(anchor: LocalDate): List<LocalDate> {
    val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0 until 7).map { start.plusDays(it.toLong()) }
}
