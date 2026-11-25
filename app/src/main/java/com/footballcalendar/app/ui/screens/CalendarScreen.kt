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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.ScheduleChange
import com.footballcalendar.app.ui.CalendarUiState
import com.footballcalendar.app.ui.theme.AmberHighlight
import com.footballcalendar.app.util.MatchTimeFormatter
import java.time.DayOfWeek
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
    onToggleOnlyFavorites: () -> Unit,
    onSelectLeagueFilter: (String?) -> Unit,
    onRefresh: () -> Unit,
    onDismissChanges: () -> Unit,
    onOpenMatchDetail: (MatchItem) -> Unit,
    onCloseMatchDetail: () -> Unit,
    onToggleFavoriteTeam: (String) -> Unit,
    onBuildCalendarIntent: (MatchItem) -> Intent,
    onBuildShareIcsIntent: (List<MatchItem>, String) -> Intent?,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "足球赛事日历",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.prefs.lastSyncSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                IconButton(onClick = onRefresh, enabled = !state.isSyncing) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "立即同步",
                        )
                    }
                }
            },
        )

        // Filter row: Only Favorites + Followed Competitions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.onlyFavorites,
                onClick = onToggleOnlyFavorites,
                label = { Text("只看主队") },
                leadingIcon = {
                    Icon(
                        imageVector = if (state.onlyFavorites) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            FilterChip(
                selected = state.selectedLeagueFilter == null,
                onClick = { onSelectLeagueFilter(null) },
                label = { Text("全部联赛") },
            )
            CompetitionCatalog.all
                .filter { it.code in state.prefs.followedCompetitions }
                .forEach { comp ->
                    FilterChip(
                        selected = state.selectedLeagueFilter == comp.code,
                        onClick = { onSelectLeagueFilter(comp.code) },
                        label = { Text(comp.zhName) },
                    )
                }
        }

        // Calendar grid card (Month or Week view)
        CalendarGridCard(
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

        // Schedule changes alert banner
        if (state.recentChanges.isNotEmpty()) {
            ChangesBanner(
                changes = state.recentChanges,
                onDismiss = onDismissChanges,
            )
        }

        // Selected day matches list with Pull-to-Refresh
        PullToRefreshBox(
            isRefreshing = state.isSyncing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    DaySectionHeader(
                        date = state.selectedDate,
                        matchCount = state.selectedDayMatches.size,
                        watchNightGrouping = state.prefs.watchNightGrouping,
                    )
                }

                if (state.selectedDayMatches.isEmpty()) {
                    item {
                        EmptyDayCard(
                            onRefresh = onRefresh,
                            upcomingMatches = state.filteredMatches
                                .filter { !it.status.isCancelledOrPostponed }
                                .take(3),
                            onSelectDate = { match ->
                                MatchTimeFormatter.displayDateFor(
                                    utcIso = match.utcDate,
                                    watchNightGrouping = state.prefs.watchNightGrouping,
                                )?.let(onSelectDate)
                            },
                        )
                    }
                } else {
                    items(state.selectedDayMatches, key = { it.id }) { match ->
                        MatchCard(
                            match = match,
                            favoriteTeams = state.prefs.favoriteTeams,
                            onClick = { onOpenMatchDetail(match) },
                            onToggleFavoriteTeam = onToggleFavoriteTeam,
                        )
                    }
                }
            }
        }
    }

    val detail = state.selectedMatchDetail
    if (detail != null) {
        ModalBottomSheet(onDismissRequest = onCloseMatchDetail) {
            MatchDetailSheet(
                match = detail,
                favoriteTeams = state.prefs.favoriteTeams,
                onToggleFavorite = onToggleFavoriteTeam,
                onAddToSystemCalendar = {
                    context.startActivity(onBuildCalendarIntent(detail))
                },
                onShareIcs = {
                    onBuildShareIcsIntent(
                        listOf(detail),
                        "match_${detail.id}.ics",
                    )?.let { intent ->
                        context.startActivity(Intent.createChooser(intent, "分享赛程日历 (.ics)"))
                    }
                },
            )
        }
    }
}

@Composable
private fun CalendarGridCard(
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevMonth, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
                    }
                    Text(
                        text = MatchTimeFormatter.formatYearMonth(currentMonth.atDay(1)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onJumpToday) {
                        Text("今天")
                    }
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "展开/收起月历",
                        )
                    }
                }
            }

            // Weekday header (Mon..Sun)
            val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val datesToShow = if (isExpanded) {
                buildMonthDates(currentMonth)
            } else {
                buildWeekDates(selectedDate)
            }

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
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> Color.Transparent
                                    },
                                )
                                .then(
                                    if (isToday && !isSelected) {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(10.dp),
                                        )
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
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        !inCurrentMonth && isExpanded -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (dayMatches.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    hasFavorite -> AmberHighlight
                                                    else -> MaterialTheme.colorScheme.primary
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
private fun ChangesBanner(
    changes: List<ScheduleChange>,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "检测到 ${changes.size} 项赛程变化",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = changes.first().summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    }
}

@Composable
private fun DaySectionHeader(
    date: LocalDate,
    matchCount: Int,
    watchNightGrouping: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${MatchTimeFormatter.formatDateTitle(date)} · $matchCount 场",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (watchNightGrouping) {
            Text(
                text = "含次日凌晨场",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MatchCard(
    match: MatchItem,
    favoriteTeams: Set<String>,
    onClick: () -> Unit,
    onToggleFavoriteTeam: (String) -> Unit,
) {
    val leagueColor = CompetitionCatalog.findByCode(match.competitionCode)
        ?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary
    val kickoff = MatchTimeFormatter.formatKickoffTime(match.utcDate)
    val isEarly = MatchTimeFormatter.isEarlyMorning(match.utcDate)
    val isFavMatch = match.involvesAnyTeam(favoriteTeams)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isFavMatch) {
                    Modifier.border(
                        1.dp,
                        AmberHighlight.copy(alpha = 0.55f),
                        RoundedCornerShape(16.dp),
                    )
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = leagueColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = match.competitionZhName,
                            color = leagueColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = kickoff,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isEarly) {
                        Surface(
                            color = AmberHighlight.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "次日凌晨",
                                color = AmberHighlight,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Surface(
                    color = when {
                        match.status.isCancelledOrPostponed -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        match.status.isLive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = match.status.zhLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            match.status.isCancelledOrPostponed -> MaterialTheme.colorScheme.error
                            match.status.isLive -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val homeFav = match.homeTeamZhName in favoriteTeams || match.homeTeamName in favoriteTeams
                val awayFav = match.awayTeamZhName in favoriteTeams || match.awayTeamName in favoriteTeams

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (homeFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "收藏主队",
                        tint = if (homeFav) AmberHighlight else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onToggleFavoriteTeam(match.homeTeamZhName) },
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = match.homeTeamZhName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    val centerText = if (match.homeScore != null && match.awayScore != null) {
                        "${match.homeScore} : ${match.awayScore}"
                    } else {
                        "VS"
                    }
                    Text(
                        text = centerText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = match.awayTeamZhName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (awayFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "收藏客队",
                        tint = if (awayFav) AmberHighlight else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onToggleFavoriteTeam(match.awayTeamZhName) },
                    )
                }
            }

            if (match.venue.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${match.venue} · ${MatchTimeFormatter.formatRelativeCountdown(match.utcDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyDayCard(
    onRefresh: () -> Unit,
    upcomingMatches: List<MatchItem>,
    onSelectDate: (MatchItem) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "当天暂无符合筛选条件的比赛",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "可下拉刷新获取最新赛程，或点选下方近期焦点场次跳转：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            upcomingMatches.forEach { m ->
                AssistChip(
                    onClick = { onSelectDate(m) },
                    label = {
                        Text("${MatchTimeFormatter.formatMonthDayTime(m.utcDate)} · ${m.homeTeamZhName} vs ${m.awayTeamZhName}")
                    },
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(onClick = onRefresh) {
                Text("立即刷新赛程")
            }
        }
    }
}

@Composable
private fun MatchDetailSheet(
    match: MatchItem,
    favoriteTeams: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onAddToSystemCalendar: () -> Unit,
    onShareIcs: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${match.competitionZhName} · ${match.status.zhLabel}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${match.homeTeamZhName} vs ${match.awayTeamZhName}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "开球时间（本地）：${MatchTimeFormatter.formatMonthDayTime(match.utcDate)}\n球场：${match.venue.ifBlank { "待定" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onAddToSystemCalendar,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("加入系统日历")
            }
            OutlinedButton(
                onClick = onShareIcs,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("导出 .ics")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
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
