package com.footballcalendar.app.ui.screens

import android.content.Intent
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
                items(state.selectedDayMatches, key = { it.id }) { match ->
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

@OptIn(ExperimentalLayoutApi::class)
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
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
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
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (canNavigateBack) {
            item {
                Surface(
                    onClick = onNavigateBack,
                    color = AppleCanvas,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = "‹ 返回上一页",
                        color = AppleRed,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = AppleRedTint, shape = RoundedCornerShape(999.dp)) {
                    val minSuffix = (liveDetail?.elapsedMinutes ?: match.elapsedMinutes)?.let { " ${it}'" } ?: ""
                    Text(
                        text = "${match.competitionZhName} · ${match.status.zhLabel}$minSuffix",
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
        }

        // Expanded Hero Matchup Card (tap either team crest/name to open Team Detail)
        item {
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
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onClickTeam(match.homeTeamName, match.homeTeamZhName, match.competitionCode, match.homeTeamCrest, match.homeTeamId)
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TeamCrest(url = match.homeTeamCrest, teamName = match.homeTeamZhName, size = 64.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        BilingualTextColumn(
                            zhName = match.homeTeamZhName,
                            enName = match.homeTeamName,
                            zhFontSize = 15.sp,
                            enFontSize = 10.sp,
                            alignment = Alignment.CenterHorizontally,
                            textAlign = TextAlign.Center,
                        )
                        if (homeStanding != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "No.${homeStanding.rank} · ${homeStanding.points}分",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppleRed,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            "看球队档案 ›",
                            fontSize = 10.sp,
                            color = InkTertiary,
                        )
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val hScore = liveDetail?.homeScore ?: match.homeScore
                        val aScore = liveDetail?.awayScore ?: match.awayScore
                        Text(
                            text = if (hScore != null && aScore != null) {
                                "$hScore : $aScore"
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
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onClickTeam(match.awayTeamName, match.awayTeamZhName, match.competitionCode, match.awayTeamCrest, match.awayTeamId)
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TeamCrest(url = match.awayTeamCrest, teamName = match.awayTeamZhName, size = 64.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        BilingualTextColumn(
                            zhName = match.awayTeamZhName,
                            enName = match.awayTeamName,
                            zhFontSize = 15.sp,
                            enFontSize = 10.sp,
                            alignment = Alignment.CenterHorizontally,
                            textAlign = TextAlign.Center,
                        )
                        if (awayStanding != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "No.${awayStanding.rank} · ${awayStanding.points}分",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppleRed,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            "看球队档案 ›",
                            fontSize = 10.sp,
                            color = InkTertiary,
                        )
                    }
                }
            }
        }

        // Grouped iOS Info Card
        item {
            Surface(
                color = AppleCanvas.copy(alpha = 0.85f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    DetailInfoRow("开球时间", "北京时间 ${MatchTimeFormatter.formatMonthDayTime(match.utcDate)}")
                    HorizontalDivider(color = AppleDivider)
                    DetailInfoRow(
                        "赛事阶段",
                        if (match.matchday != null) "${match.competitionZhName} · Round ${match.matchday}" else match.competitionZhName,
                    )
                    HorizontalDivider(color = AppleDivider)
                    DetailInfoRow("比赛场地", match.venue.ifBlank { "待官方公布" })
                    HorizontalDivider(color = AppleDivider)
                    DetailInfoRow("当前状态", match.status.zhLabel)
                }
            }
        }

        // Live / Historical Match Timeline, Text Commentary, Events, Lineups & Stats
        item {
            MatchLiveDetailSection(
                match = match,
                liveDetail = liveDetail,
                isLoading = isLoadingLiveDetail,
                note = liveDetailNote,
                useTimelineView = useTimelineView,
                onlyGoalsFilter = onlyGoalsFilter,
                onSetTimelineView = onSetTimelineView,
                onToggleOnlyGoals = onToggleOnlyGoals,
                onClickLineupPlayer = onClickLineupPlayer,
            )
        }

        // Head-to-Head (H2H) History Between the Two Teams
        item {
            MatchH2HAndFormSection(
                match = match,
                h2hBundle = h2hBundle,
                isLoadingH2H = isLoadingH2H,
                onClickMatch = onClickMatch,
                onClickTeam = onClickTeam,
            )
        }

        // Standings Comparison if available (clickable teams)
        if (homeStanding != null || awayStanding != null) {
            item {
                Surface(
                    color = AppleCanvas.copy(alpha = 0.85f),
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
                                    TeamCrest(url = st.teamCrest, teamName = st.teamZhName, size = 22.dp)
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
        }

        // Related Players
        if (relatedPlayers.isNotEmpty()) {
            item {
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
                                        Text(player.name, fontSize = 9.sp, color = InkTertiary)
                                        Text("#${player.shirtNumber} · ${player.positionZh}", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
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
        }

        item {
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
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchLiveDetailSection(
    match: MatchItem,
    liveDetail: LiveMatchDetail?,
    isLoading: Boolean,
    note: String?,
    useTimelineView: Boolean,
    onlyGoalsFilter: Boolean,
    onSetTimelineView: (Boolean) -> Unit,
    onToggleOnlyGoals: () -> Unit,
    onClickLineupPlayer: (LineupPlayerItem, MatchLineupSide) -> Unit,
) {
    var expandCommentary by remember(match.id) { mutableStateOf(false) }

    Surface(
        color = AppleCanvas.copy(alpha = 0.85f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "实时赛况与图文直播",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = AppleRedTint,
                ) {
                    Text(
                        text = when {
                            isLoading -> "同步中..."
                            match.status.isLive -> "● 4秒实时轮询中"
                            else -> "● 已连接官方事件流"
                        },
                        color = AppleRed,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (isLoading && liveDetail == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AppleRed)
                    Text("正在拉取比赛过程与图文直播...", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                }
            }

            if (
                !note.isNullOrBlank() &&
                (liveDetail == null || (liveDetail.events.isEmpty() && liveDetail.lineups.isEmpty() && liveDetail.stats.isEmpty() && liveDetail.commentary.isEmpty()))
            ) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }

            if (liveDetail != null) {
                if (liveDetail.events.isNotEmpty()) {
                    HorizontalDivider(color = AppleDivider)

                    // View Toggle (时间线视图 / 经典列表) + 只看进球 Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(999.dp),
                            border = androidx.compose.foundation.BorderStroke(0.75.dp, AppleDivider),
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                listOf(true to "时间线", false to "经典列表").forEach { (isTimeline, label) ->
                                    val selected = useTimelineView == isTimeline
                                    Surface(
                                        onClick = { onSetTimelineView(isTimeline) },
                                        color = if (selected) AppleRed else Color.Transparent,
                                        shape = RoundedCornerShape(999.dp),
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (selected) Color.White else InkSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
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
                        Text(
                            text = if (onlyGoalsFilter) "本场比赛暂无进球记录" else "暂无事件记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkTertiary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else if (useTimelineView) {
                        MatchCenterAxisTimeline(
                            nodes = visibleNodes,
                            allNodes = timelineNodes,
                            match = match,
                        )
                    } else {
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(color = Color.White, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = minLabel,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                                        color = AppleRed,
                                    )
                                }
                                Text(icon, fontSize = 13.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    val titleLine = if (ev.type.equals("subst", true)) {
                                        val outPart = ev.assistZhName?.let { oz -> " · 下场: $oz (${ev.assistName.orEmpty()})" } ?: ""
                                        "上场: ${ev.playerZhName} (${ev.playerName})$outPart"
                                    } else {
                                        val assistPart = ev.assistZhName?.let { az ->
                                            val aEn = ev.assistName ?: ""
                                            " · 助攻: $az ($aEn)"
                                        } ?: ""
                                        val scoreTag = node.scoreSnapshot?.let { " [$it]" } ?: ""
                                        "${ev.playerZhName}$scoreTag$assistPart"
                                    }
                                    Text(
                                        text = titleLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = InkPrimary,
                                    )
                                    Text(
                                        text = "${ev.playerName} · ${ev.teamZhName} (${ev.teamName}) · ${ev.detail}",
                                        fontSize = 10.sp,
                                        color = InkTertiary,
                                    )
                                }
                            }
                        }
                    }
                }

                // Real-time Streaming Text Commentary (4-second polling on live matches)
                if (liveDetail.commentary.isNotEmpty()) {
                    HorizontalDivider(color = AppleDivider)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "实时文字图文直播（${liveDetail.commentary.size} 条）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppleRed,
                        )
                        if (match.status.isLive) {
                            Text("准零延迟 · 自动更新", fontSize = 10.sp, color = AppleRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    val orderedCommentary = remember(liveDetail.commentary) {
                        liveDetail.commentary.sortedByDescending { it.sequence }
                    }
                    val commCap = if (expandCommentary) orderedCommentary.size else 8
                    orderedCommentary.take(commCap).forEach { comm ->
                        val isGoalItem = comm.typeSlug.contains("goal", true) || comm.typeSlug.contains("penalty---scored", true)
                        val isCardItem = comm.typeSlug.contains("card", true)
                        Surface(
                            color = if (isGoalItem) AppleRedTint.copy(alpha = 0.65f) else Color.White,
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
                                    color = if (isGoalItem) AppleRed else AppleCanvas,
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
                    if (liveDetail.commentary.size > 8) {
                        TextButton(
                            onClick = { expandCommentary = !expandCommentary },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                text = if (expandCommentary) "收起图文直播 ▴" else "展开全部 ${liveDetail.commentary.size} 条图文直播 ▾",
                                color = AppleRed,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                if (liveDetail.lineups.isNotEmpty()) {
                    HorizontalDivider(color = AppleDivider)
                    Text("双方阵型与首发（点击球员看档案）", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = UclBlue)
                    liveDetail.lineups.forEach { side ->
                        Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("${side.teamZhName} (${side.teamName})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    if (side.formation.isNotBlank()) {
                                        Text(side.formation, style = MaterialTheme.typography.labelSmall, color = AppleRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (side.coachZhName.isNotBlank()) {
                                    Text("主帅：${side.coachZhName} (${side.coachName})", fontSize = 10.sp, color = InkSecondary)
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    side.startXI.forEach { lp ->
                                        Surface(
                                            onClick = { onClickLineupPlayer(lp, side) },
                                            color = AppleCanvas,
                                            shape = RoundedCornerShape(999.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            ) {
                                                PlayerPortrait(url = lp.portraitUrl, playerName = lp.zhName, size = 22.dp)
                                                Text(
                                                    text = if (lp.number > 0) "#${lp.number} ${lp.zhName}" else lp.zhName,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = InkPrimary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (liveDetail.stats.isNotEmpty()) {
                    HorizontalDivider(color = AppleDivider)
                    Text("技术统计", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = InkPrimary)
                    liveDetail.stats.forEach { sp ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sp.homeValue, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppleRed, modifier = Modifier.width(56.dp))
                            Text(sp.labelZh, fontSize = 11.sp, color = InkSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Text(sp.awayValue, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UclBlue, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
                        }
                    }
                }
            }
        }
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

    // 1. Head-to-Head History
    Surface(
        color = AppleCanvas.copy(alpha = 0.85f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "两队历史交锋记录 (H2H)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                )
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
                Surface(color = Color.White, shape = RoundedCornerShape(10.dp)) {
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
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${hm.homeTeamZhName} (${hm.homeTeamName}) ${hm.homeScore ?: "-"} : ${hm.awayScore ?: "-"} ${hm.awayTeamZhName} (${hm.awayTeamName})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = InkPrimary,
                                )
                                Text(
                                    text = "${hm.competitionZhName} · ${MatchTimeFormatter.formatMonthDayTime(hm.utcDate)}",
                                    fontSize = 10.sp,
                                    color = InkSecondary,
                                )
                            }
                            Text("详情 ›", color = AppleRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    // 2. Both Teams' Recent Historical Results
    if (homeRecent.isNotEmpty() || awayRecent.isNotEmpty()) {
        Surface(
            color = AppleCanvas.copy(alpha = 0.85f),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "两队近期历史比赛记录（点击看历史场次详情）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                )
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
                                Text(
                                    text = "$tZh ($tEn) 近况 ›",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleRed,
                                )
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
                                    color = Color.White,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                        Text(
                                            text = "${rm.homeTeamZhName} ${rm.homeScore ?: "-"}:${rm.awayScore ?: "-"} ${rm.awayTeamZhName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "${MatchTimeFormatter.formatMonthDayTime(rm.utcDate)} ›",
                                            fontSize = 10.sp,
                                            color = InkSecondary,
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
                        TimelineEventCard(node = node, isHome = true)
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
                        TimelineEventCard(node = node, isHome = false)
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
) {
    val ev = node.event
    val isGoal = ev.type.equals("Goal", true)
    val isRedCard = ev.type.equals("Card", true) && (ev.detail.contains("Red", true) || ev.detail.contains("红"))
    val alignEnd = if (isHome) Alignment.End else Alignment.Start
    val textAlign = if (isHome) TextAlign.End else TextAlign.Start
    val cardGreen = Color(0xFF16A34A)

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isGoal) 1.dp else 0.75.dp,
            color = if (isGoal) AppleRedBorder else AppleDivider,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
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
    }
}
