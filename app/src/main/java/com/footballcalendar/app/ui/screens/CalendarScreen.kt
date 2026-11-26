package com.footballcalendar.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.LineupPlayerItem
import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchCommentaryItem
import com.footballcalendar.app.model.MatchEventItem
import com.footballcalendar.app.model.MatchH2HBundle
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchLineupSide
import com.footballcalendar.app.model.MatchStatPair
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.ScheduleChange
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.StarPlayer
import com.footballcalendar.app.ui.CalendarUiState
import com.footballcalendar.app.ui.components.AppleSegmentedControl
import com.footballcalendar.app.ui.components.BilingualTextColumn
import com.footballcalendar.app.ui.components.PlayerPortrait
import com.footballcalendar.app.ui.components.TeamCrest
import com.footballcalendar.app.ui.theme.UclBlue
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
    onOpenTeamDetail: (enName: String, zhName: String, compCode: String, crest: String, teamId: Int) -> Unit,
    onToggleWatchlist: (String) -> Unit,
) {
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
                        onClickTeam = { en, zh, cc, cr, tid -> onOpenTeamDetail(en, zh, cc, cr, tid) },
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
                            .filter { it.code in state.prefs.followedCompetitions }
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

            // 6. Day Header
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
                itemsIndexed(state.selectedDayMatches, key = { idx, m -> "${m.id}_$idx" }) { _, match ->
                    AppleMatchCard(
                        match = match,
                        isFavoriteTeamMatch = match.involvesAnyTeam(state.prefs.favoriteTeams),
                        isWatchlisted = match.id in state.prefs.watchlistMatchIds,
                        homeRank = state.standingFor(match.competitionCode, match.homeTeamZhName, match.homeTeamName)?.rank,
                        awayRank = state.standingFor(match.competitionCode, match.awayTeamZhName, match.awayTeamName)?.rank,
                        onClick = { onOpenMatchDetail(match) },
                        onClickTeam = { en, zh, cc, cr, tid -> onOpenTeamDetail(en, zh, cc, cr, tid) },
                        onToggleWatchlist = { onToggleWatchlist(match.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroNextMatchCard(
    match: MatchItem,
    homeRank: Int?,
    awayRank: Int?,
    onClick: () -> Unit,
    onClickTeam: (enName: String, zhName: String, compCode: String, crest: String, teamId: Int) -> Unit,
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
        homeRank != null && awayRank != null -> "No.${homeRank} vs No.${awayRank}"
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
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onClickTeam(match.homeTeamName, match.homeTeamZhName, match.competitionCode, match.homeTeamCrest, match.homeTeamId)
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamCrest(url = match.homeTeamCrest, teamName = match.homeTeamZhName, size = 44.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    BilingualTextColumn(
                        zhName = match.homeTeamZhName,
                        enName = match.homeTeamName,
                        zhColor = Color.White,
                        enColor = Color.White.copy(alpha = 0.78f),
                        zhFontSize = 14.sp,
                        enFontSize = 10.sp,
                        alignment = Alignment.CenterHorizontally,
                        textAlign = TextAlign.Center,
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
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onClickTeam(match.awayTeamName, match.awayTeamZhName, match.competitionCode, match.awayTeamCrest, match.awayTeamId)
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamCrest(url = match.awayTeamCrest, teamName = match.awayTeamZhName, size = 44.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    BilingualTextColumn(
                        zhName = match.awayTeamZhName,
                        enName = match.awayTeamName,
                        zhColor = Color.White,
                        enColor = Color.White.copy(alpha = 0.78f),
                        zhFontSize = 14.sp,
                        enFontSize = 10.sp,
                        alignment = Alignment.CenterHorizontally,
                        textAlign = TextAlign.Center,
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
private fun AppleMatchCard(
    match: MatchItem,
    isFavoriteTeamMatch: Boolean,
    isWatchlisted: Boolean,
    homeRank: Int?,
    awayRank: Int?,
    onClick: () -> Unit,
    onClickTeam: (enName: String, zhName: String, compCode: String, crest: String, teamId: Int) -> Unit,
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
                            text = "· No.${homeRank} vs No.${awayRank}",
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
                        val liveBadge = if (match.status.isLive) {
                            match.elapsedMinutes?.let { "● LIVE ${it}'" } ?: "● LIVE"
                        } else {
                            match.status.zhLabel
                        }
                        Text(
                            text = liveBadge,
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
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onClickTeam(match.homeTeamName, match.homeTeamZhName, match.competitionCode, match.homeTeamCrest, match.homeTeamId)
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TeamCrest(url = match.homeTeamCrest, teamName = match.homeTeamZhName, size = 32.dp)
                    BilingualTextColumn(
                        zhName = match.homeTeamZhName,
                        enName = match.homeTeamName,
                        zhFontSize = 14.sp,
                        enFontSize = 10.sp,
                        alignment = Alignment.Start,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 6.dp),
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = MatchTimeFormatter.formatMonthDayTime(match.utcDate),
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum",
                            ),
                            color = InkSecondary,
                        )
                        Text(
                            text = "北京时间",
                            fontSize = 9.sp,
                            color = InkTertiary,
                        )
                    } else {
                        Text(
                            text = kickoff,
                            style = TextStyle(
                                fontSize = 18.sp,
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
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onClickTeam(match.awayTeamName, match.awayTeamZhName, match.competitionCode, match.awayTeamCrest, match.awayTeamId)
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    BilingualTextColumn(
                        zhName = match.awayTeamZhName,
                        enName = match.awayTeamName,
                        zhFontSize = 14.sp,
                        enFontSize = 10.sp,
                        alignment = Alignment.End,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailBottomSheet(
    match: MatchItem,
    liveDetail: LiveMatchDetail?,
    isLoadingLiveDetail: Boolean,
    liveDetailNote: String?,
    useTimelineView: Boolean = true,
    onlyGoalsFilter: Boolean = false,
    h2hBundle: MatchH2HBundle?,
    isLoadingH2H: Boolean,
    favoriteTeams: Set<String>,
    isWatchlisted: Boolean,
    homeStanding: StandingEntry?,
    awayStanding: StandingEntry?,
    relatedPlayers: List<StarPlayer>,
    isFullScreen: Boolean = false,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onCloseAll: () -> Unit = {},
    onSetTimelineView: (Boolean) -> Unit = {},
    onToggleOnlyGoals: () -> Unit = {},
    onClickTeam: (enName: String, zhName: String, compCode: String, crest: String, teamId: Int) -> Unit,
    onClickMatch: (MatchItem) -> Unit,
    onClickPlayer: (StarPlayer) -> Unit,
    onClickLineupPlayer: (LineupPlayerItem, MatchLineupSide) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleWatchlist: () -> Unit,
    onAddToSystemCalendar: () -> Unit,
    onShareIcs: () -> Unit,
) {
    // 0 = 赛况(事件+图文), 1 = 阵容(球场阵型+替补), 2 = 统计(球队+球员), 3 = 交锋与积分
    var selectedDetailTab by remember(match.id) { mutableIntStateOf(0) }

    val playerPortraitMap = remember(liveDetail, relatedPlayers) {
        buildMap<String, String> {
            relatedPlayers.forEach { p ->
                if (p.portraitUrl.isNotBlank()) {
                    put(p.name.lowercase(), p.portraitUrl)
                    put(p.zhName, p.portraitUrl)
                }
            }
            liveDetail?.lineups.orEmpty().forEach { side ->
                (side.startXI + side.substitutes).forEach { lp ->
                    if (lp.portraitUrl.isNotBlank()) {
                        put(lp.name.lowercase(), lp.portraitUrl)
                        put(lp.zhName, lp.portraitUrl)
                    }
                }
            }
        }
    }

    val lineupPlayerLookup = remember(liveDetail) {
        buildMap<String, Pair<LineupPlayerItem, MatchLineupSide>> {
            liveDetail?.lineups.orEmpty().forEach { side ->
                (side.startXI + side.substitutes).forEach { lp ->
                    put(lp.name.lowercase(), lp to side)
                    if (lp.zhName.isNotBlank()) put(lp.zhName, lp to side)
                }
            }
        }
    }

    val hScore = liveDetail?.homeScore ?: match.homeScore
    val aScore = liveDetail?.awayScore ?: match.awayScore
    val minSuffix = (liveDetail?.elapsedMinutes ?: match.elapsedMinutes)?.let { " $it'" } ?: ""
    val headerStatusBadge = when {
        match.status.isLive -> "进行中$minSuffix"
        match.status == MatchStatus.FINISHED -> "完"
        else -> "未开赛"
    }

    Column(
        modifier = if (isFullScreen) {
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .navigationBarsPadding()
        } else {
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
        },
    ) {
        // Dark Purple-Plum Reference Match Header (图一/图二/图三)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(listOf(Color(0xFF3E1944), Color(0xFF28163A))),
                    shape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                )
                .then(if (isFullScreen) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        onClick = if (canNavigateBack) onNavigateBack else onCloseAll,
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = if (canNavigateBack) "‹ 返回" else "‹ 返回",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }

                    // Center Matchup Bar with Both Crests & Live Score
                    Row(
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onClickTeam(match.homeTeamName, match.homeTeamZhName, match.competitionCode, match.homeTeamCrest, match.homeTeamId)
                                },
                        ) {
                            TeamCrest(url = match.homeTeamCrest, teamName = match.homeTeamZhName, size = 30.dp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = hScore?.toString() ?: "-",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (match.status.isLive) AppleRed else Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = headerStatusBadge,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = aScore?.toString() ?: "-",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onClickTeam(match.awayTeamName, match.awayTeamZhName, match.competitionCode, match.awayTeamCrest, match.awayTeamId)
                                },
                        ) {
                            TeamCrest(url = match.awayTeamCrest, teamName = match.awayTeamZhName, size = 30.dp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = onAddToSystemCalendar,
                            color = Color.White.copy(alpha = 0.16f),
                            shape = CircleShape,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EventAvailable,
                                contentDescription = "添加到日历",
                                tint = Color.White,
                                modifier = Modifier.padding(6.dp).size(16.dp),
                            )
                        }
                        Surface(
                            onClick = onCloseAll,
                            color = Color.White.copy(alpha = 0.16f),
                            shape = CircleShape,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = Color.White,
                                modifier = Modifier.padding(6.dp).size(16.dp),
                            )
                        }
                    }
                }

                // Team Names + Beijing Time Subline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = match.homeTeamZhName,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onClickTeam(match.homeTeamName, match.homeTeamZhName, match.competitionCode, match.homeTeamCrest, match.homeTeamId)
                            },
                    )
                    Text(
                        text = "${match.competitionZhName} · ${MatchTimeFormatter.formatMonthDayTime(match.utcDate)} 北京时间",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = match.awayTeamZhName,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onClickTeam(match.awayTeamName, match.awayTeamZhName, match.competitionCode, match.awayTeamCrest, match.awayTeamId)
                            },
                    )
                }
            }
        }

        // Sticky Sub-Tabs Strip (图一/图二/图三: 赛况 | 阵容 | 统计 | 交锋积分)
        Surface(color = Color.White) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val eventCountTag = liveDetail?.let { if (it.events.isNotEmpty()) " (${it.events.size})" else "" } ?: ""
                    listOf(
                        0 to "赛况$eventCountTag",
                        1 to "阵容",
                        2 to "统计",
                        3 to "交锋积分",
                    ).forEach { (tabIdx, title) ->
                        val active = selectedDetailTab == tabIdx
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedDetailTab = tabIdx }
                                .padding(top = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (active) AppleRed else InkSecondary,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.height(7.dp))
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (active) AppleRed else Color.Transparent),
                            )
                        }
                    }
                }
                HorizontalDivider(color = AppleDivider)
            }
        }

        // Main Tabbed Scrollable Content
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(AppleCanvas),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (selectedDetailTab) {
                0 -> {
                    // Tab 0: 图二 (赛况事件时间轴 + 图文直播 + 场地裁判 + 近期战绩)
                    item {
                        MatchLiveEventsTabContent(
                            match = match,
                            liveDetail = liveDetail,
                            isLoading = isLoadingLiveDetail,
                            note = liveDetailNote,
                            useTimelineView = useTimelineView,
                            onlyGoalsFilter = onlyGoalsFilter,
                            h2hBundle = h2hBundle,
                            playerPortraitMap = playerPortraitMap,
                            onSetTimelineView = onSetTimelineView,
                            onToggleOnlyGoals = onToggleOnlyGoals,
                            onClickEventPlayer = { pName, pZh ->
                                lineupPlayerLookup[pName.lowercase()]?.let { (lp, side) ->
                                    onClickLineupPlayer(lp, side)
                                } ?: lineupPlayerLookup[pZh]?.let { (lp, side) ->
                                    onClickLineupPlayer(lp, side)
                                } ?: relatedPlayers.firstOrNull {
                                    it.name.equals(pName, true) || it.zhName == pZh
                                }?.let(onClickPlayer)
                            },
                            onClickTeam = onClickTeam,
                        )
                    }
                }
                1 -> {
                    // Tab 1: 图一 (绿色足球场战术阵型图 + 替补阵容与主教练 + 图例)
                    item {
                        MatchTacticalLineupsTabContent(
                            match = match,
                            liveDetail = liveDetail,
                            isLoading = isLoadingLiveDetail,
                            note = liveDetailNote,
                            onClickLineupPlayer = onClickLineupPlayer,
                            onClickTeam = onClickTeam,
                        )
                    }
                }
                2 -> {
                    // Tab 2: 图三 (统计 -> 球队可视化对比 / 球员数据列表)
                    item {
                        MatchStatsTabContent(
                            match = match,
                            liveDetail = liveDetail,
                            isLoading = isLoadingLiveDetail,
                            note = liveDetailNote,
                            onClickLineupPlayer = onClickLineupPlayer,
                        )
                    }
                }
                else -> {
                    // Tab 3: 交锋记录 (H2H with TeamCrests) + 近期战绩 + 积分榜实力对比 + 关注球员 + 日历导出
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MatchHeroSummaryCard(
                                match = match,
                                hScore = hScore,
                                aScore = aScore,
                                homeStanding = homeStanding,
                                awayStanding = awayStanding,
                                isWatchlisted = isWatchlisted,
                                onToggleWatchlist = onToggleWatchlist,
                                onClickTeam = onClickTeam,
                            )

                            MatchH2HAndFormSection(
                                match = match,
                                h2hBundle = h2hBundle,
                                isLoadingH2H = isLoadingH2H,
                                onClickMatch = onClickMatch,
                                onClickTeam = onClickTeam,
                            )

                            if (homeStanding != null || awayStanding != null) {
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "积分榜实力对比（点击查看球队详情）",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = InkPrimary,
                                        )
                                        listOfNotNull(homeStanding, awayStanding).forEach { st ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        onClickTeam(st.teamName, st.teamZhName, st.competitionCode, st.teamCrest, 0)
                                                    }
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    TeamCrest(url = st.teamCrest, teamName = st.teamZhName, size = 24.dp)
                                                    BilingualTextColumn(zhName = st.teamZhName, enName = st.teamName, zhFontSize = 13.sp, enFontSize = 9.sp)
                                                }
                                                Text(
                                                    text = "No.${st.rank} · ${st.won}胜${st.draw}平${st.lost}负 · 净胜${st.goalDifference} · ${st.points}分 ›",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = InkSecondary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (relatedPlayers.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "本场关注球员（点击查看资料）",
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
                                        relatedPlayers.take(16).forEach { player ->
                                            Surface(
                                                onClick = { onClickPlayer(player) },
                                                color = Color.White,
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
                                                        Text(player.name, fontSize = 9.sp, color = InkTertiary)
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
                                    label = { Text("客队 ${match.awayTeamZhName}") },
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
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppleRed),
                                ) {
                                    Icon(Icons.Filled.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("添加到手机系统日历", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onShareIcs,
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("导出标准 .ics 文件分享")
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MatchHeroSummaryCard(
    match: MatchItem,
    hScore: Int?,
    aScore: Int?,
    homeStanding: StandingEntry?,
    awayStanding: StandingEntry?,
    isWatchlisted: Boolean,
    onToggleWatchlist: () -> Unit,
    onClickTeam: (enName: String, zhName: String, compCode: String, crest: String, teamId: Int) -> Unit,
) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onClickTeam(match.homeTeamName, match.homeTeamZhName, match.competitionCode, match.homeTeamCrest, match.homeTeamId)
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamCrest(url = match.homeTeamCrest, teamName = match.homeTeamZhName, size = 54.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    BilingualTextColumn(
                        zhName = match.homeTeamZhName,
                        enName = match.homeTeamName,
                        zhFontSize = 14.sp,
                        enFontSize = 10.sp,
                        alignment = Alignment.CenterHorizontally,
                        textAlign = TextAlign.Center,
                    )
                    if (homeStanding != null) {
                        Text("No.${homeStanding.rank} · ${homeStanding.points}分", fontSize = 11.sp, color = AppleRed, fontWeight = FontWeight.SemiBold)
                    }
                }
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (hScore != null && aScore != null) "$hScore : $aScore" else MatchTimeFormatter.formatKickoffTime(match.utcDate),
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum"),
                        color = InkPrimary,
                    )
                    Text("北京时间 ${MatchTimeFormatter.formatMonthDayTime(match.utcDate)}", fontSize = 10.sp, color = InkSecondary)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onClickTeam(match.awayTeamName, match.awayTeamZhName, match.competitionCode, match.awayTeamCrest, match.awayTeamId)
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamCrest(url = match.awayTeamCrest, teamName = match.awayTeamZhName, size = 54.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    BilingualTextColumn(
                        zhName = match.awayTeamZhName,
                        enName = match.awayTeamName,
                        zhFontSize = 14.sp,
                        enFontSize = 10.sp,
                        alignment = Alignment.CenterHorizontally,
                        textAlign = TextAlign.Center,
                    )
                    if (awayStanding != null) {
                        Text("No.${awayStanding.rank} · ${awayStanding.points}分", fontSize = 11.sp, color = AppleRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchLiveEventsTabContent(
    match: MatchItem,
    liveDetail: LiveMatchDetail?,
    isLoading: Boolean,
    note: String?,
    useTimelineView: Boolean,
    onlyGoalsFilter: Boolean,
    h2hBundle: MatchH2HBundle?,
    playerPortraitMap: Map<String, String>,
    onSetTimelineView: (Boolean) -> Unit,
    onToggleOnlyGoals: () -> Unit,
    onClickEventPlayer: (playerEn: String, playerZh: String) -> Unit,
    onClickTeam: (enName: String, zhName: String, compCode: String, crest: String, teamId: Int) -> Unit,
) {
    // 0 = 时间轴事件 (default), 1 = 图文直播
    var subMode by remember(match.id) { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Status & Mode Switch Card (图二: 事件 / 图文直播 + 只看进球)
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = AppleCanvas,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            listOf(
                                0 to "事件时间轴",
                                1 to "图文直播 (${liveDetail?.commentary?.size ?: 0})",
                            ).forEach { (idx, label) ->
                                val selected = subMode == idx
                                Surface(
                                    onClick = { subMode = idx },
                                    color = if (selected) AppleRed else Color.Transparent,
                                    shape = RoundedCornerShape(999.dp),
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color.White else InkSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = AppleRedTint,
                    ) {
                        Text(
                            text = when {
                                isLoading -> "同步中..."
                                match.status.isLive -> "● 4秒实时轮询"
                                else -> "● 官方事件流"
                            },
                            color = AppleRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                if (subMode == 0 && liveDetail?.events?.isNotEmpty() == true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = AppleCanvas,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                listOf(true to "对称时间轴", false to "经典列表").forEach { (isTl, lbl) ->
                                    val sel = useTimelineView == isTl
                                    Surface(
                                        onClick = { onSetTimelineView(isTl) },
                                        color = if (sel) InkPrimary else Color.Transparent,
                                        shape = RoundedCornerShape(999.dp),
                                    ) {
                                        Text(
                                            text = lbl,
                                            color = if (sel) Color.White else InkSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "只看进球",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (onlyGoalsFilter) AppleRed else InkSecondary,
                            )
                            Switch(
                                checked = onlyGoalsFilter,
                                onCheckedChange = { onToggleOnlyGoals() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppleRed,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFD1D1D6),
                                ),
                            )
                        }
                    }
                }

                if (isLoading && liveDetail == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AppleRed)
                        Text("正在高速拉取实时事件与图文直播流...", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                    }
                } else if (
                    !note.isNullOrBlank() &&
                    (liveDetail == null || (liveDetail.events.isEmpty() && liveDetail.commentary.isEmpty()))
                ) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                }

                if (liveDetail != null) {
                    if (subMode == 0) {
                        if (liveDetail.events.isEmpty()) {
                            Text(
                                text = if (match.status == MatchStatus.TIMED || match.status == MatchStatus.SCHEDULED) {
                                    "比赛尚未开始，开球后将自动实时推送进球、红黄牌与换人时间轴。"
                                } else {
                                    "暂无事件记录（可切换「图文直播」或下拉刷新获取）"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = InkTertiary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            val timelineNodes = remember(liveDetail.events, match.homeTeamName, match.awayTeamName) {
                                computeTimelineEventsWithScore(liveDetail.events, match)
                            }
                            val visibleNodes = remember(timelineNodes, onlyGoalsFilter) {
                                if (onlyGoalsFilter) {
                                    timelineNodes.filter { it.event.type.equals("Goal", true) }
                                } else {
                                    timelineNodes
                                }
                            }
                            if (visibleNodes.isEmpty()) {
                                Text("本场比赛暂无进球记录", style = MaterialTheme.typography.bodySmall, color = InkTertiary)
                            } else if (useTimelineView) {
                                MatchCenterAxisTimeline(
                                    nodes = visibleNodes,
                                    allNodes = timelineNodes,
                                    match = match,
                                    playerPortraitMap = playerPortraitMap,
                                    onClickPlayer = onClickEventPlayer,
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    visibleNodes.forEach { node ->
                                        val ev = node.event
                                        val minLabel = if (ev.extra != null) "${ev.elapsed}+${ev.extra}'" else "${ev.elapsed}'"
                                        val icon = when {
                                            ev.type.equals("Goal", true) -> "⚽"
                                            ev.type.equals("Card", true) && (ev.detail.contains("Red", true) || ev.detail.contains("红")) -> "🟥"
                                            ev.type.equals("Card", true) -> "🟨"
                                            ev.type.equals("subst", true) -> "🔄"
                                            else -> "•"
                                        }
                                        val portrait = playerPortraitMap[ev.playerName.lowercase()] ?: playerPortraitMap[ev.playerZhName].orEmpty()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { onClickEventPlayer(ev.playerName, ev.playerZhName) }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Surface(color = AppleCanvas, shape = RoundedCornerShape(6.dp)) {
                                                Text(
                                                    text = minLabel,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                                                    color = AppleRed,
                                                )
                                            }
                                            PlayerPortrait(url = portrait, playerName = ev.playerZhName, size = 26.dp)
                                            Text(icon, fontSize = 13.sp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                val titleLine = if (ev.type.equals("subst", true)) {
                                                    val outPart = ev.assistZhName?.let { oz -> " · ↓ $oz (${ev.assistName.orEmpty()})" } ?: ""
                                                    "↑ ${ev.playerZhName} (${ev.playerName})$outPart"
                                                } else {
                                                    val assistPart = ev.assistZhName?.let { az -> " · 助攻: $az" } ?: ""
                                                    val scoreTag = node.scoreSnapshot?.let { " [$it]" } ?: ""
                                                    "${ev.playerZhName}$scoreTag$assistPart"
                                                }
                                                Text(titleLine, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = InkPrimary)
                                                Text("${ev.teamZhName} · ${ev.detail}", fontSize = 10.sp, color = InkTertiary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Latest 3 text commentary items quick preview + link to full feed
                        if (liveDetail.commentary.isNotEmpty()) {
                            HorizontalDivider(color = AppleDivider)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("最新文字播报", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = InkPrimary)
                                Text(
                                    text = "全部 ${liveDetail.commentary.size} 条 ›",
                                    color = AppleRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { subMode = 1 }.padding(4.dp),
                                )
                            }
                            val orderedPreview = remember(liveDetail.commentary) {
                                liveDetail.commentary.sortedWith(
                                    compareByDescending<MatchCommentaryItem> { it.sequence }.thenByDescending { it.elapsed },
                                )
                            }
                            orderedPreview.take(3).forEach { comm ->
                                CommentaryItemRow(comm = comm)
                            }
                        }
                    } else {
                        // Full Real-Time Text Commentary Feed
                        if (liveDetail.commentary.isEmpty()) {
                            Text("本场暂无文字图文直播流（重要事件请切回「事件时间轴」）", style = MaterialTheme.typography.bodySmall, color = InkTertiary)
                        } else {
                            val orderedAll = remember(liveDetail.commentary) {
                                liveDetail.commentary.sortedWith(
                                    compareByDescending<MatchCommentaryItem> { it.sequence }.thenByDescending { it.elapsed },
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                orderedAll.forEach { comm ->
                                    CommentaryItemRow(comm = comm)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Venue / Conditions & Coaches / Form Card (图二底部样式!)
        val venueLabel = liveDetail?.venueName?.ifBlank { null } ?: match.venue.ifBlank { null }
        val attendanceNum = liveDetail?.attendance ?: 0
        val refName = liveDetail?.refereeName.orEmpty()
        if (venueLabel != null || refName.isNotBlank() || attendanceNum > 0) {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("赛场与执法信息", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = InkPrimary)
                    if (venueLabel != null) {
                        val attSuffix = if (attendanceNum > 0) " · 现场观众 ${attendanceNum} 人" else ""
                        DetailInfoRow("比赛球场", "$venueLabel$attSuffix")
                    }
                    if (refName.isNotBlank()) {
                        HorizontalDivider(color = AppleDivider)
                        DetailInfoRow("当值主裁", refName)
                    }
                    HorizontalDivider(color = AppleDivider)
                    DetailInfoRow("开球时间", "北京时间 ${MatchTimeFormatter.formatMonthDayTime(match.utcDate)}")
                }
            }
        }

        // Two-Team Recent Form Comparison Card (图二最下方双方近5场胜平负环形指示条)
        val homeRecent = h2hBundle?.homeRecentMatches.orEmpty()
        val awayRecent = h2hBundle?.awayRecentMatches.orEmpty()
        if (homeRecent.isNotEmpty() || awayRecent.isNotEmpty()) {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(
                        Triple(match.homeTeamName, match.homeTeamZhName, match.homeTeamCrest to match.homeTeamId) to homeRecent,
                        Triple(match.awayTeamName, match.awayTeamZhName, match.awayTeamCrest to match.awayTeamId) to awayRecent,
                    ).forEach { (info, recList) ->
                        val (tEn, tZh, crestId) = info
                        val last5 = recList.take(5)
                        val wins = last5.count { m ->
                            val isH = m.homeTeamZhName == tZh || m.homeTeamName.equals(tEn, true)
                            val my = if (isH) m.homeScore else m.awayScore
                            val op = if (isH) m.awayScore else m.homeScore
                            my != null && op != null && my > op
                        }
                        val draws = last5.count { m ->
                            val isH = m.homeTeamZhName == tZh || m.homeTeamName.equals(tEn, true)
                            val my = if (isH) m.homeScore else m.awayScore
                            val op = if (isH) m.awayScore else m.homeScore
                            my != null && op != null && my == op
                        }
                        val losses = (last5.size - wins - draws).coerceAtLeast(0)
                        Surface(
                            onClick = { onClickTeam(tEn, tZh, match.competitionCode, crestId.first, crestId.second) },
                            color = AppleCanvas,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TeamCrest(url = crestId.first, teamName = tZh, size = 20.dp)
                                    Text(tZh, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    text = if (last5.isNotEmpty()) "近${last5.size}场 ${wins}胜 ${draws}平 ${losses}负" else "暂无近期战绩",
                                    fontSize = 11.sp,
                                    color = AppleRed,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentaryItemRow(comm: MatchCommentaryItem) {
    val isGoalItem = comm.typeSlug.contains("goal", true) || comm.typeSlug.contains("penalty---scored", true)
    val isCardItem = comm.typeSlug.contains("card", true)
    Surface(
        color = if (isGoalItem) AppleRedTint.copy(alpha = 0.65f) else AppleCanvas,
        shape = RoundedCornerShape(12.dp),
        border = if (isGoalItem) androidx.compose.foundation.BorderStroke(1.dp, AppleRedBorder) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                color = if (isGoalItem) AppleRed else Color.White,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = comm.clockDisplay.ifBlank { if (comm.elapsed > 0) "${comm.elapsed}'" else "直播" },
                    color = if (isGoalItem) Color.White else AppleRed,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = comm.textZh.ifBlank { comm.textEn },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isGoalItem || isCardItem) FontWeight.Bold else FontWeight.Medium,
                    color = InkPrimary,
                )
                if (comm.textEn.isNotBlank() && comm.textZh != comm.textEn) {
                    Text(
                        text = comm.textEn,
                        fontSize = 10.sp,
                        color = InkTertiary,
                        lineHeight = 13.sp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchTacticalLineupsTabContent(
    match: MatchItem,
    liveDetail: LiveMatchDetail?,
    isLoading: Boolean,
    note: String?,
    onClickLineupPlayer: (LineupPlayerItem, MatchLineupSide) -> Unit,
    onClickTeam: (enName: String, zhName: String, compCode: String, crest: String, teamId: Int) -> Unit,
) {
    if (isLoading && liveDetail == null) {
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppleRed)
                Text("正在获取双方首发阵型与替补名单...", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
        }
        return
    }
    val lineups = liveDetail?.lineups.orEmpty()
    if (lineups.isEmpty()) {
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("双方首发阵容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = note ?: "开赛前约 45 分钟公布官方首发与阵型，届时自动展示球场阵型图与球员头像。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
            }
        }
        return
    }

    val homeSide = lineups.firstOrNull {
        it.teamZhName == match.homeTeamZhName || it.teamName.equals(match.homeTeamName, true)
    } ?: lineups[0]
    val awaySide = lineups.firstOrNull { it !== homeSide }

    // Map per-player match event badges from liveDetail.events as reliable fallback
    val eventBadges = remember(liveDetail?.events) {
        val map = mutableMapOf<String, MutableList<String>>()
        liveDetail?.events.orEmpty().forEach { ev ->
            val badge = when {
                ev.type.equals("Goal", true) -> "⚽"
                ev.type.equals("Card", true) && (ev.detail.contains("Red", true) || ev.detail.contains("红")) -> "🟥"
                ev.type.equals("Card", true) -> "🟨"
                else -> null
            }
            if (badge != null && ev.playerName.isNotBlank()) {
                map.getOrPut(ev.playerName.lowercase()) { mutableListOf() }.add(badge)
            }
            if (ev.type.equals("subst", true) && !ev.assistName.isNullOrBlank()) {
                map.getOrPut(ev.assistName.lowercase()) { mutableListOf() }.add("↓${ev.elapsed}'")
            }
            if (ev.type.equals("subst", true) && ev.playerName.isNotBlank()) {
                map.getOrPut(ev.playerName.lowercase()) { mutableListOf() }.add("↑${ev.elapsed}'")
            }
        }
        map
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Venue / Official pill bar above tactical pitch (图一顶部条)
        val venueTitle = liveDetail?.venueName?.ifBlank { null } ?: match.venue.ifBlank { match.competitionZhName }
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🏟 $venueTitle", fontSize = 11.sp, color = InkSecondary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (liveDetail?.refereeName?.isNotBlank() == true) {
                    Text("裁判：${liveDetail.refereeName}", fontSize = 11.sp, color = InkSecondary)
                }
            }
        }

        // Green Tactical Football Pitch Canvas (图一核心绿茵阵型图!)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF2E8D3F),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Mowed grass stripes & white pitch markings
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val stripeCount = 10
                    val stripeH = h / stripeCount
                    for (i in 0 until stripeCount) {
                        if (i % 2 == 0) {
                            drawRect(
                                color = Color(0xFF359A46),
                                topLeft = Offset(0f, i * stripeH),
                                size = Size(w, stripeH),
                            )
                        }
                    }
                    val lineColor = Color.White.copy(alpha = 0.36f)
                    val stroke = Stroke(width = 2.2f)
                    // Outer pitch border
                    drawRect(color = lineColor, topLeft = Offset(12f, 12f), size = Size(w - 24f, h - 24f), style = stroke)
                    // Halfway line & center circle
                    drawLine(color = lineColor, start = Offset(12f, h / 2f), end = Offset(w - 12f, h / 2f), strokeWidth = 2.2f)
                    drawCircle(color = lineColor, radius = w * 0.13f, center = Offset(w / 2f, h / 2f), style = stroke)
                    drawCircle(color = lineColor, radius = 5f, center = Offset(w / 2f, h / 2f))
                    // Top & bottom penalty boxes
                    val penW = w * 0.56f
                    val penH = h * 0.13f
                    drawRect(color = lineColor, topLeft = Offset((w - penW) / 2f, 12f), size = Size(penW, penH), style = stroke)
                    drawRect(color = lineColor, topLeft = Offset((w - penW) / 2f, h - 12f - penH), size = Size(penW, penH), style = stroke)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Home Team Badge Strip on Pitch Top
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onClickTeam(match.homeTeamName, match.homeTeamZhName, match.competitionCode, match.homeTeamCrest, match.homeTeamId)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TeamCrest(url = match.homeTeamCrest, teamName = homeSide.teamZhName, size = 22.dp)
                            Text(homeSide.teamZhName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (homeSide.formation.isNotBlank()) {
                            Surface(color = Color.Black.copy(alpha = 0.28f), shape = RoundedCornerShape(999.dp)) {
                                Text(
                                    "阵型 ${homeSide.formation}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    val homeRows = remember(homeSide.startXI, homeSide.formation) {
                        buildPitchFormationRows(homeSide.startXI, homeSide.formation, reverseRows = false)
                    }
                    homeRows.forEach { rowPlayers ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            rowPlayers.forEach { lp ->
                                PitchPlayerBadgeNode(
                                    player = lp,
                                    ringColor = AppleRed,
                                    eventTags = eventBadges[lp.name.lowercase()].orEmpty(),
                                    onClick = { onClickLineupPlayer(lp, homeSide) },
                                )
                            }
                        }
                    }

                    // Center Line Formation Summary Divider
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${homeSide.teamZhName} ${homeSide.formation.ifBlank { "首发11人" }}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (awaySide != null) {
                            Text(
                                text = "${awaySide.teamZhName} ${awaySide.formation.ifBlank { "首发11人" }}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (awaySide != null) {
                        val awayRows = remember(awaySide.startXI, awaySide.formation) {
                            buildPitchFormationRows(awaySide.startXI, awaySide.formation, reverseRows = true)
                        }
                        awayRows.forEach { rowPlayers ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                rowPlayers.forEach { lp ->
                                    PitchPlayerBadgeNode(
                                        player = lp,
                                        ringColor = UclBlue,
                                        eventTags = eventBadges[lp.name.lowercase()].orEmpty(),
                                        onClick = { onClickLineupPlayer(lp, awaySide) },
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onClickTeam(match.awayTeamName, match.awayTeamZhName, match.competitionCode, match.awayTeamCrest, match.awayTeamId)
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TeamCrest(url = match.awayTeamCrest, teamName = awaySide.teamZhName, size = 22.dp)
                                Text(awaySide.teamZhName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (awaySide.formation.isNotBlank()) {
                                Surface(color = Color.Black.copy(alpha = 0.28f), shape = RoundedCornerShape(999.dp)) {
                                    Text(
                                        "阵型 ${awaySide.formation}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Substitutes Two-Column Card + Head Coaches (图一中下部: 替补阵容与主帅!)
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("替补阵容（点击球员看个人档案）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                // Home vs Away Team & Coach Headers
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple(homeSide, match.homeTeamCrest, match.homeTeamName to match.homeTeamId),
                        Triple(awaySide, match.awayTeamCrest, match.awayTeamName to match.awayTeamId),
                    ).forEach { (side, crestUrl, nameId) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppleCanvas)
                                .clickable {
                                    if (side != null) {
                                        onClickTeam(nameId.first, side.teamZhName, match.competitionCode, crestUrl, nameId.second)
                                    }
                                }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TeamCrest(url = crestUrl, teamName = side?.teamZhName.orEmpty(), size = 22.dp)
                                Text(side?.teamZhName.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (side != null && (side.coachZhName.isNotBlank() || side.coachName.isNotBlank())) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PlayerPortrait(
                                        url = side.coachPortraitUrl,
                                        playerName = side.coachZhName.ifBlank { side.coachName },
                                        size = 26.dp,
                                    )
                                    Column {
                                        Text(
                                            text = side.coachZhName.ifBlank { side.coachName },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text("主教练", fontSize = 9.sp, color = InkTertiary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Dual-Column Substitutes Rows
                val homeSubs = homeSide.substitutes
                val awaySubs = awaySide?.substitutes.orEmpty()
                val maxSubs = maxOf(homeSubs.size, awaySubs.size)
                if (maxSubs == 0) {
                    Text("暂未公布替补名单", style = MaterialTheme.typography.bodySmall, color = InkTertiary)
                } else {
                    for (idx in 0 until maxSubs) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val hs = homeSubs.getOrNull(idx)
                            val aSub = awaySubs.getOrNull(idx)
                            Box(modifier = Modifier.weight(1f)) {
                                if (hs != null) {
                                    SubstitutePlayerRowCard(
                                        player = hs,
                                        tags = eventBadges[hs.name.lowercase()].orEmpty(),
                                        onClick = { onClickLineupPlayer(hs, homeSide) },
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                if (aSub != null && awaySide != null) {
                                    SubstitutePlayerRowCard(
                                        player = aSub,
                                        tags = eventBadges[aSub.name.lowercase()].orEmpty(),
                                        onClick = { onClickLineupPlayer(aSub, awaySide) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Event Legend Footer (图一底部图标说明)
                HorizontalDivider(color = AppleDivider)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("⚽ 进球", "👟 助攻", "🟨 黄牌", "🟥 红牌", "↑ 替补登场", "↓ 被换下").forEach { item ->
                        Text(item, fontSize = 10.sp, color = InkSecondary)
                    }
                }
            }
        }
    }
}

private fun buildPitchFormationRows(
    starters: List<LineupPlayerItem>,
    formation: String,
    reverseRows: Boolean,
): List<List<LineupPlayerItem>> {
    if (starters.isEmpty()) return emptyList()
    val rows = mutableListOf<List<LineupPlayerItem>>()
    val formParts = formation.split("-").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
    if (formParts.sum() == starters.size - 1 && starters.size >= 11) {
        rows += listOf(starters[0])
        var cursor = 1
        for (cnt in formParts) {
            rows += starters.subList(cursor, (cursor + cnt).coerceAtMost(starters.size))
            cursor += cnt
        }
    } else {
        val gks = starters.filter { it.pos.contains("G", true) }.ifEmpty { listOf(starters.first()) }
        val remain = starters.filter { it !in gks }
        val defs = remain.filter { it.pos.contains("D", true) || it.pos.contains("B", true) }
        val mids = remain.filter { it.pos.contains("M", true) }
        val fwds = remain.filter { it !in defs && it !in mids }
        rows += gks
        if (defs.isNotEmpty()) rows += defs else if (remain.size >= 4) rows += remain.take(4)
        if (mids.isNotEmpty()) rows += mids
        if (fwds.isNotEmpty()) rows += fwds
        if (rows.flatten().size < starters.size) {
            rows.clear()
            rows += listOf(starters.first())
            starters.drop(1).chunked(4).forEach { rows += it }
        }
    }
    return if (reverseRows) rows.reversed() else rows
}

@Composable
private fun PitchPlayerBadgeNode(
    player: LineupPlayerItem,
    ringColor: Color,
    eventTags: List<String>,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(66.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(2.dp, ringColor, CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                PlayerPortrait(url = player.portraitUrl, playerName = player.zhName, size = 36.dp)
            }
            if (player.number > 0) {
                Surface(
                    color = Color(0xCC1C1C1E),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        text = "${player.number}",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            val topTag = eventTags.firstOrNull()
                ?: when {
                    player.goals > 0 -> "⚽"
                    player.redCards > 0 -> "🟥"
                    player.yellowCards > 0 -> "🟨"
                    player.subbedOut -> "↓"
                    else -> null
                }
            if (topTag != null) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(999.dp),
                    shadowElevation = 1.dp,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(topTag, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp))
                }
            }
        }
        Surface(
            color = Color(0xB3111827),
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                text = player.zhName.substringAfter("·"),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        val extraSummary = buildString {
            if (player.goals > 0) append("⚽${player.goals} ")
            if (player.assists > 0) append("🎯${player.assists}")
        }.trim()
        if (extraSummary.isNotBlank()) {
            Text(extraSummary, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SubstitutePlayerRowCard(
    player: LineupPlayerItem,
    tags: List<String>,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = AppleCanvas,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlayerPortrait(url = player.portraitUrl, playerName = player.zhName, size = 30.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (player.number > 0) "#${player.number} ${player.zhName}" else player.zhName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = InkPrimary,
                )
                Text(
                    text = listOfNotNull(
                        player.pos.ifBlank { null },
                        tags.joinToString(" ").ifBlank { if (player.subbedIn) "↑ 登场" else null },
                    ).joinToString(" · "),
                    fontSize = 9.sp,
                    color = if (player.subbedIn || tags.any { it.contains("↑") }) Color(0xFF16A34A) else InkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MatchStatsTabContent(
    match: MatchItem,
    liveDetail: LiveMatchDetail?,
    isLoading: Boolean,
    note: String?,
    onClickLineupPlayer: (LineupPlayerItem, MatchLineupSide) -> Unit,
) {
    // 0 = 球队 (图三), 1 = 球员
    var statsSubTab by remember(match.id) { mutableIntStateOf(0) }
    var playerTeamScope by remember(match.id) { mutableIntStateOf(0) } // 0=全部, 1=主队, 2=客队

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Sub-Tab Selector: 球队 | 球员 (图三顶部切换)
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "球队统计", 1 to "球员数据").forEach { (idx, label) ->
                    val selected = statsSubTab == idx
                    Surface(
                        onClick = { statsSubTab = idx },
                        color = if (selected) AppleRed else AppleCanvas,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.White else InkSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp),
                        )
                    }
                }
            }
        }

        if (isLoading && liveDetail == null) {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AppleRed)
                    Text("正在拉取技术统计与球员数据...", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                }
            }
            return
        }

        val stats = liveDetail?.stats.orEmpty()
        if (statsSubTab == 0) {
            if (stats.isEmpty()) {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = note ?: "比赛开球后将自动更新控球率、射门、传球、抢断与扑救等全方位数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                return
            }

            // Hero Possession + Ring Visual Summary Card (图三上部可视化环图 + 双边条)
            val possession = stats.firstOrNull { it.labelZh.contains("控球") }
            val ringCandidates = stats.filter { it !== possession }.take(4)

            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TeamCrest(url = match.homeTeamCrest, teamName = match.homeTeamZhName, size = 24.dp)
                            Text(match.homeTeamZhName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text("VS", color = AppleRed, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(match.awayTeamZhName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            TeamCrest(url = match.awayTeamCrest, teamName = match.awayTeamZhName, size = 24.dp)
                        }
                    }

                    if (possession != null) {
                        DualProgressStatBar(
                            labelZh = "控球率",
                            homeValue = possession.homeValue,
                            awayValue = possession.awayValue,
                        )
                    }

                    if (ringCandidates.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            ringCandidates.forEach { sp ->
                                DualStatDonutMiniCard(stat = sp)
                            }
                        }
                    }
                }
            }

            // Grouped Detailed Comparison Cards (⚡ 进攻 / ⚡ 传球 / ⚡ 防守)
            val grouped = stats.filter { it !== possession }.groupBy { it.group }
            listOf("进攻", "传球", "防守", "综合", "关键").forEach { grp ->
                val list = grouped[grp].orEmpty()
                if (list.isNotEmpty()) {
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("⚡ $grp", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = InkPrimary)
                            list.forEach { sp ->
                                DualProgressStatBar(labelZh = sp.labelZh, homeValue = sp.homeValue, awayValue = sp.awayValue)
                            }
                        }
                    }
                }
            }
        } else {
            // 球员数据列表 (图三「统计 -> 球员」)
            val lineups = liveDetail?.lineups.orEmpty()
            val homeSide = lineups.firstOrNull {
                it.teamZhName == match.homeTeamZhName || it.teamName.equals(match.homeTeamName, true)
            } ?: lineups.getOrNull(0)
            val awaySide = lineups.firstOrNull { it !== homeSide }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = playerTeamScope == 0, onClick = { playerTeamScope = 0 }, label = { Text("全部") })
                if (homeSide != null) {
                    FilterChip(selected = playerTeamScope == 1, onClick = { playerTeamScope = 1 }, label = { Text(homeSide.teamZhName) })
                }
                if (awaySide != null) {
                    FilterChip(selected = playerTeamScope == 2, onClick = { playerTeamScope = 2 }, label = { Text(awaySide.teamZhName) })
                }
            }

            val scopedPlayers = when (playerTeamScope) {
                1 -> homeSide?.let { s -> (s.startXI + s.substitutes).map { it to s } }.orEmpty()
                2 -> awaySide?.let { s -> (s.startXI + s.substitutes).map { it to s } }.orEmpty()
                else -> lineups.flatMap { s -> (s.startXI + s.substitutes).map { it to s } }
            }.sortedByDescending { (p, _) ->
                p.goals * 100 + p.assists * 60 + p.shotsOnTarget * 15 + p.shots * 6 + p.tackles * 8 + p.saves * 12 + p.passes
            }

            if (scopedPlayers.isEmpty()) {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("暂无出场球员技术统计数据", style = MaterialTheme.typography.bodySmall, color = InkTertiary, modifier = Modifier.padding(20.dp))
                }
            } else {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        scopedPlayers.forEach { (lp, side) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppleCanvas)
                                    .clickable { onClickLineupPlayer(lp, side) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PlayerPortrait(url = lp.portraitUrl, playerName = lp.zhName, size = 34.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lp.number > 0) "#${lp.number} ${lp.zhName}" else lp.zhName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("${side.teamZhName} · ${lp.pos.ifBlank { "球员" }}", fontSize = 10.sp, color = InkSecondary)
                                }
                                val metricsText = buildList {
                                    if (lp.goals > 0) add("⚽${lp.goals}")
                                    if (lp.assists > 0) add("🎯${lp.assists}")
                                    if (lp.shots > 0) add("射${lp.shots}/${lp.shotsOnTarget}")
                                    if (lp.passes > 0) add("传${lp.passes}")
                                    if (lp.tackles > 0) add("断${lp.tackles}")
                                    if (lp.saves > 0) add("扑${lp.saves}")
                                    if (lp.yellowCards > 0) add("🟨")
                                    if (lp.redCards > 0) add("🟥")
                                }.joinToString(" ")
                                Text(
                                    text = metricsText.ifBlank { if (lp.subbedIn) "替补登场" else "首发" },
                                    fontSize = 11.sp,
                                    color = if (lp.goals > 0 || lp.assists > 0) AppleRed else InkPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseStatNumericValue(raw: String): Float =
    raw.replace("%", "").trim().toFloatOrNull() ?: 0f

@Composable
private fun DualProgressStatBar(
    labelZh: String,
    homeValue: String,
    awayValue: String,
) {
    val hNum = parseStatNumericValue(homeValue)
    val aNum = parseStatNumericValue(awayValue)
    val total = (hNum + aNum).takeIf { it > 0f } ?: 1f
    val hFrac = (hNum / total).coerceIn(0.05f, 0.95f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(homeValue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = AppleRed, style = TextStyle(fontFeatureSettings = "tnum"))
            Text(labelZh, fontSize = 11.sp, color = InkSecondary, fontWeight = FontWeight.Medium)
            Text(awayValue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = UclBlue, style = TextStyle(fontFeatureSettings = "tnum"))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(UclBlue.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(hFrac)
                    .background(AppleRed),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f - hFrac)
                    .background(UclBlue),
            )
        }
    }
}

@Composable
private fun DualStatDonutMiniCard(stat: MatchStatPair) {
    val hNum = parseStatNumericValue(stat.homeValue)
    val aNum = parseStatNumericValue(stat.awayValue)
    val sum = (hNum + aNum).takeIf { it > 0f } ?: 1f
    val homeSweep = (hNum / sum) * 360f
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 8f, cap = StrokeCap.Round)
                drawArc(color = UclBlue, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                drawArc(color = AppleRed, startAngle = -90f, sweepAngle = homeSweep, useCenter = false, style = stroke)
            }
            Text("${stat.homeValue}:${stat.awayValue}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = InkPrimary)
        }
        Text(stat.labelZh, fontSize = 10.sp, color = InkSecondary, maxLines = 1)
    }
}

@Composable
private fun MatchH2HAndFormSection(
    match: MatchItem,
    h2hBundle: MatchH2HBundle?,
    isLoadingH2H: Boolean,
    onClickMatch: (MatchItem) -> Unit,
    onClickTeam: (enName: String, zhName: String, compCode: String, crest: String, teamId: Int) -> Unit,
) {
    val h2h = h2hBundle?.h2hMatches.orEmpty()
    val homeRecent = h2hBundle?.homeRecentMatches.orEmpty()
    val awayRecent = h2hBundle?.awayRecentMatches.orEmpty()

    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("两队历史交锋记录 (H2H)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = InkPrimary)
                if (isLoadingH2H) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AppleRed)
                }
            }
            if (isLoadingH2H && h2h.isEmpty()) {
                Text("正在同步历史交锋与两队近期战绩...", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            } else if (h2h.isEmpty()) {
                Text("近两个赛季暂无两队历史交锋记录", style = MaterialTheme.typography.bodySmall, color = InkTertiary)
            } else {
                var homeWins = 0
                var draws = 0
                var awayWins = 0
                h2h.forEach { hm ->
                    val hs = hm.homeScore ?: return@forEach
                    val asScore = hm.awayScore ?: return@forEach
                    val homeWasHome = hm.homeTeamZhName == match.homeTeamZhName || hm.homeTeamName.equals(match.homeTeamName, true)
                    val mHome = if (homeWasHome) hs else asScore
                    val mAway = if (homeWasHome) asScore else hs
                    when {
                        mHome > mAway -> homeWins++
                        mHome == mAway -> draws++
                        else -> awayWins++
                    }
                }
                Surface(color = AppleCanvas, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = "近 ${h2h.size} 场交锋 · ${match.homeTeamZhName} ${homeWins}胜 ${draws}平 ${awayWins}负",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppleRed,
                        textAlign = TextAlign.Center,
                    )
                }
                h2h.take(8).forEach { hm ->
                    Surface(
                        onClick = { onClickMatch(hm) },
                        color = AppleCanvas,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TeamCrest(url = hm.homeTeamCrest, teamName = hm.homeTeamZhName, size = 22.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${hm.homeTeamZhName} (${hm.homeTeamName}) ${hm.homeScore ?: "-"} : ${hm.awayScore ?: "-"} ${hm.awayTeamZhName} (${hm.awayTeamName})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = InkPrimary,
                                )
                                Text(
                                    text = "${hm.competitionZhName} · 北京时间 ${MatchTimeFormatter.formatMonthDayTime(hm.utcDate)}",
                                    fontSize = 10.sp,
                                    color = InkSecondary,
                                )
                            }
                            TeamCrest(url = hm.awayTeamCrest, teamName = hm.awayTeamZhName, size = 22.dp)
                            Text("详情 ›", color = AppleRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (homeRecent.isNotEmpty() || awayRecent.isNotEmpty()) {
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("两队近期历史比赛记录（点击看场次详情）", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = InkPrimary)
                listOf(
                    Triple(match.homeTeamName, match.homeTeamZhName, match.homeTeamCrest to match.homeTeamId) to homeRecent,
                    Triple(match.awayTeamName, match.awayTeamZhName, match.awayTeamCrest to match.awayTeamId) to awayRecent,
                ).forEach { (teamInfo, recMatches) ->
                    if (recMatches.isNotEmpty()) {
                        val (tEn, tZh, crestId) = teamInfo
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onClickTeam(tEn, tZh, match.competitionCode, crestId.first, crestId.second)
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                TeamCrest(url = crestId.first, teamName = tZh, size = 20.dp)
                                Text("$tZh ($tEn) 近况 ›", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AppleRed)
                            }
                            recMatches.take(5).forEach { rm ->
                                val isH = rm.homeTeamZhName == tZh || rm.homeTeamName.equals(tEn, true)
                                val mySc = if (isH) rm.homeScore else rm.awayScore
                                val oppSc = if (isH) rm.awayScore else rm.homeScore
                                val badge = when {
                                    mySc == null || oppSc == null -> "完" to InkSecondary
                                    mySc > oppSc -> "胜" to AppleRed
                                    mySc == oppSc -> "平" to AmberWarm
                                    else -> "负" to InkTertiary
                                }
                                Surface(
                                    onClick = { onClickMatch(rm) },
                                    color = AppleCanvas,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Surface(color = badge.second.copy(alpha = 0.14f), shape = RoundedCornerShape(5.dp)) {
                                            Text(
                                                text = badge.first,
                                                color = badge.second,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            )
                                        }
                                        TeamCrest(url = rm.homeTeamCrest, teamName = rm.homeTeamZhName, size = 18.dp)
                                        Text(
                                            text = "${rm.homeTeamZhName} ${rm.homeScore ?: "-"}:${rm.awayScore ?: "-"} ${rm.awayTeamZhName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        TeamCrest(url = rm.awayTeamCrest, teamName = rm.awayTeamZhName, size = 18.dp)
                                        Text("${MatchTimeFormatter.formatMonthDayTime(rm.utcDate)} ›", fontSize = 10.sp, color = InkSecondary)
                                    }
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
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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

private data class TimelineRenderedNode(
    val event: MatchEventItem,
    val isHome: Boolean,
    val runningHomeScore: Int,
    val runningAwayScore: Int,
    val scoreSnapshot: String?,
)

private fun isHomeTeamEvent(ev: MatchEventItem, match: MatchItem): Boolean {
    val matchesHome = (ev.teamName.isNotBlank() && (ev.teamName.equals(match.homeTeamName, true) ||
        match.homeTeamName.contains(ev.teamName, true) || ev.teamName.contains(match.homeTeamName, true))) ||
        (ev.teamZhName.isNotBlank() && ev.teamZhName == match.homeTeamZhName)
    val matchesAway = (ev.teamName.isNotBlank() && (ev.teamName.equals(match.awayTeamName, true) ||
        match.awayTeamName.contains(ev.teamName, true) || ev.teamName.contains(match.awayTeamName, true))) ||
        (ev.teamZhName.isNotBlank() && ev.teamZhName == match.awayTeamZhName)
    return when {
        matchesHome && !matchesAway -> true
        matchesAway && !matchesHome -> false
        else -> ev.teamZhName == match.homeTeamZhName
    }
}

private fun computeTimelineEventsWithScore(
    events: List<MatchEventItem>,
    match: MatchItem,
): List<TimelineRenderedNode> {
    val sorted = events.sortedWith(compareBy<MatchEventItem>({ it.elapsed }, { it.extra ?: 0 }))
    var homeGoals = 0
    var awayGoals = 0
    return sorted.map { ev ->
        val isHome = isHomeTeamEvent(ev, match)
        val snap = if (ev.type.equals("Goal", true)) {
            if (isHome) homeGoals += 1 else awayGoals += 1
            "$homeGoals-$awayGoals"
        } else {
            null
        }
        TimelineRenderedNode(
            event = ev,
            isHome = isHome,
            runningHomeScore = homeGoals,
            runningAwayScore = awayGoals,
            scoreSnapshot = snap,
        )
    }
}

@Composable
private fun MatchCenterAxisTimeline(
    nodes: List<TimelineRenderedNode>,
    allNodes: List<TimelineRenderedNode>,
    match: MatchItem,
    playerPortraitMap: Map<String, String> = emptyMap(),
    onClickPlayer: (playerEn: String, playerZh: String) -> Unit = { _, _ -> },
) {
    val max45Extra = allNodes.filter { it.event.elapsed == 45 && it.event.extra != null }.maxOfOrNull { it.event.extra ?: 0 }
    val htNode = allNodes.lastOrNull { it.event.elapsed <= 45 }
    val htScoreStr = "${htNode?.runningHomeScore ?: 0}-${htNode?.runningAwayScore ?: 0}"
    val finalNode = allNodes.lastOrNull()
    val ftHome = match.homeScore ?: finalNode?.runningHomeScore ?: 0
    val ftAway = match.awayScore ?: finalNode?.runningAwayScore ?: 0

    var insertedInjuryBanner = false
    var insertedHalftimeBanner = false

    Column(modifier = Modifier.fillMaxWidth()) {
        // Kickoff Header Row matching reference style
        TimelineDividerBadge(
            text = "开球哨响 · ${match.homeTeamZhName} vs ${match.awayTeamZhName}",
            accent = true,
        )

        nodes.forEach { node ->
            val ev = node.event
            if (!insertedInjuryBanner && ev.elapsed == 45 && ev.extra != null && ev.extra > 0) {
                insertedInjuryBanner = true
                TimelineDividerBadge(text = "伤停补时 ${max45Extra ?: ev.extra}'")
            }
            if (!insertedHalftimeBanner && ev.elapsed > 45) {
                insertedHalftimeBanner = true
                TimelineDividerBadge(text = "半场结束 $htScoreStr")
            }

            val minuteText = if (ev.extra != null) "${ev.elapsed}+${ev.extra}'" else "${ev.elapsed}'"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (node.isHome) {
                        val portrait = playerPortraitMap[ev.playerName.lowercase()] ?: playerPortraitMap[ev.playerZhName].orEmpty()
                        TimelineEventCard(
                            node = node,
                            isHome = true,
                            portraitUrl = portrait,
                            onClick = { onClickPlayer(ev.playerName, ev.playerZhName) },
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(AppleDivider),
                    )
                    Surface(
                        color = AppleCanvas,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = minuteText,
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                            color = InkSecondary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (!node.isHome) {
                        val portrait = playerPortraitMap[ev.playerName.lowercase()] ?: playerPortraitMap[ev.playerZhName].orEmpty()
                        TimelineEventCard(
                            node = node,
                            isHome = false,
                            portraitUrl = portrait,
                            onClick = { onClickPlayer(ev.playerName, ev.playerZhName) },
                        )
                    }
                }
            }
        }

        if (match.status == MatchStatus.FINISHED) {
            if (!insertedHalftimeBanner && allNodes.any { it.event.elapsed <= 45 }) {
                TimelineDividerBadge(text = "半场结束 $htScoreStr")
            }
            TimelineDividerBadge(text = "全场比赛结束 $ftHome-$ftAway", accent = true)
        }
    }
}

@Composable
private fun TimelineDividerBadge(
    text: String,
    accent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).height(0.75.dp).background(AppleDivider))
        Surface(
            color = if (accent) AppleRedTint else Color.White,
            shape = RoundedCornerShape(999.dp),
            border = androidx.compose.foundation.BorderStroke(0.75.dp, if (accent) AppleRedBorder else AppleDivider),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (accent) AppleRed else InkSecondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Box(modifier = Modifier.weight(1f).height(0.75.dp).background(AppleDivider))
    }
}

@Composable
private fun TimelineEventCard(
    node: TimelineRenderedNode,
    isHome: Boolean,
    portraitUrl: String = "",
    onClick: () -> Unit = {},
) {
    val ev = node.event
    val isGoal = ev.type.equals("Goal", true)
    val isRedCard = ev.type.equals("Card", true) && (ev.detail.contains("Red", true) || ev.detail.contains("红"))
    val alignEnd = if (isHome) Alignment.End else Alignment.Start
    val textAlign = if (isHome) TextAlign.End else TextAlign.Start
    val cardGreen = Color(0xFF16A34A)

    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isGoal) 1.dp else 0.75.dp,
            color = if (isGoal) AppleRedBorder else AppleDivider,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isHome) {
                PlayerPortrait(url = portraitUrl, playerName = ev.playerZhName, size = 24.dp)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = alignEnd,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when {
                    isGoal -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (!isHome) Text("⚽", fontSize = 11.sp)
                            Text(
                                text = node.scoreSnapshot ?: "",
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum"),
                                color = AppleRed,
                            )
                            if (ev.detail.contains("点") || ev.detail.contains("乌龙")) {
                                Text("(${ev.detail})", fontSize = 9.sp, color = InkSecondary)
                            }
                            if (isHome) Text("⚽", fontSize = 11.sp)
                        }
                        Text(
                            text = "${ev.playerZhName} ⚽",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkPrimary,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = ev.playerName,
                            fontSize = 9.sp,
                            color = InkTertiary,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!ev.assistZhName.isNullOrBlank()) {
                            Text(
                                text = "${ev.assistZhName} 👟",
                                fontSize = 11.sp,
                                color = InkSecondary,
                                textAlign = textAlign,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!ev.assistName.isNullOrBlank()) {
                                Text(
                                    text = ev.assistName,
                                    fontSize = 8.sp,
                                    color = InkTertiary,
                                    textAlign = textAlign,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    ev.type.equals("Card", true) -> {
                        val badge = if (isRedCard) "🟥" else "🟨"
                        Text(
                            text = if (isHome) "${ev.playerZhName} $badge" else "$badge ${ev.playerZhName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkPrimary,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = ev.playerName,
                            fontSize = 9.sp,
                            color = InkTertiary,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = ev.detail.ifBlank { if (isRedCard) "红牌" else "黄牌犯规" },
                            fontSize = 10.sp,
                            color = InkSecondary,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ev.type.equals("subst", true) -> {
                        Text(
                            text = if (isHome) "${ev.playerZhName} ↑" else "↑ ${ev.playerZhName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardGreen,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = ev.playerName,
                            fontSize = 8.sp,
                            color = InkTertiary,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!ev.assistZhName.isNullOrBlank()) {
                            Text(
                                text = if (isHome) "${ev.assistZhName} ↓" else "↓ ${ev.assistZhName}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleRed,
                                textAlign = textAlign,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!ev.assistName.isNullOrBlank()) {
                                Text(
                                    text = ev.assistName,
                                    fontSize = 8.sp,
                                    color = InkTertiary,
                                    textAlign = textAlign,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = ev.detail.ifBlank { "战术换人" },
                            fontSize = 10.sp,
                            color = InkTertiary,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {
                        Text(
                            text = ev.playerZhName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkPrimary,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = ev.detail,
                            fontSize = 10.sp,
                            color = InkSecondary,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (isHome) {
                PlayerPortrait(url = portraitUrl, playerName = ev.playerZhName, size = 24.dp)
            }
        }
    }
}
