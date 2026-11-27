package com.footballcalendar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.SquadMember
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.ui.CalendarUiState
import com.footballcalendar.app.ui.components.BilingualTextColumn
import com.footballcalendar.app.ui.components.PlayerPortrait
import com.footballcalendar.app.ui.components.TeamCrest
import com.footballcalendar.app.ui.theme.AmberWarm
import com.footballcalendar.app.ui.theme.AppleCanvas
import com.footballcalendar.app.ui.theme.AppleDivider
import com.footballcalendar.app.ui.theme.AppleRed
import com.footballcalendar.app.ui.theme.InkPrimary
import com.footballcalendar.app.ui.theme.InkSecondary
import com.footballcalendar.app.ui.theme.InkTertiary
import com.footballcalendar.app.ui.theme.ThemeTokens
import com.footballcalendar.app.util.MatchTimeFormatter

private fun colorForZone(zoneType: String): Color = when (zoneType) {
    "UCL" -> ThemeTokens.ZoneUcl
    "UCL_QUAL" -> ThemeTokens.ZoneCyan
    "PROMOTION" -> ThemeTokens.SuccessEmerald
    "UEL" -> ThemeTokens.LotteryGold
    "UECL" -> ThemeTokens.ZoneUclCon
    "PLAYOFF" -> ThemeTokens.ZonePurple
    "RELEGATION_PLAYOFF" -> ThemeTokens.ZoneOrange
    "RELEGATION" -> ThemeTokens.LivePulse
    else -> Color.Transparent
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StandingsScreen(
    state: CalendarUiState,
    onSelectLeague: (String) -> Unit,
    onClickTeam: (StandingEntry) -> Unit,
) {
    val availableLeagues = rememberAvailableStandingLeagues(state.standingsByLeague)
    val rows = state.standingsByLeague[state.selectedStandingsLeague].orEmpty()

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
                    text = "完整席位分区 · 点击任意球队查看详情与阵容",
                    style = MaterialTheme.typography.labelMedium,
                    color = ThemeTokens.AccentFallback,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "联赛积分榜",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ThemeTokens.InkPrimary,
                    letterSpacing = (-0.5).sp,
                )
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableLeagues.forEach { code ->
                        val name = CompetitionCatalog.findByCode(code)?.zhName ?: code
                        val selected = state.selectedStandingsLeague == code
                        Surface(
                            onClick = { onSelectLeague(code) },
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) ThemeTokens.AccentFallback else ThemeTokens.SurfacePure,
                            border = if (selected) null else androidx.compose.foundation.BorderStroke(0.75.dp, ThemeTokens.Hairline),
                        ) {
                            Text(
                                text = name,
                                color = if (selected) ThemeTokens.PureWhite else ThemeTokens.InkPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(24.dp)
                        .height(36.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, ThemeTokens.CanvasPearl),
                            ),
                        ),
                )
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = ThemeTokens.SurfacePure,
                shape = RoundedCornerShape(20.dp),
            ) {
                Column {
                    val showFormColumn = rows.any { it.form.isNotBlank() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ThemeTokens.TableHeaderBg)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("No.", modifier = Modifier.width(34.dp), style = MaterialTheme.typography.labelSmall, color = ThemeTokens.InkMuted, fontWeight = FontWeight.Bold)
                        Text("球队", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = ThemeTokens.InkMuted, fontWeight = FontWeight.Bold)
                        Text("赛", modifier = Modifier.width(26.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = ThemeTokens.InkMuted, fontWeight = FontWeight.Bold)
                        Text("净", modifier = Modifier.width(34.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = ThemeTokens.InkMuted, fontWeight = FontWeight.Bold)
                        Text(
                            if (showFormColumn) "近5场" else "胜平负",
                            modifier = Modifier.width(52.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = ThemeTokens.InkMuted,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("积分", modifier = Modifier.width(32.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = ThemeTokens.InkMuted, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = ThemeTokens.Hairline, thickness = 1.dp)

                    if (rows.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "暂无该联赛积分数据，可在日历页下拉同步刷新",
                                style = MaterialTheme.typography.bodySmall,
                                color = ThemeTokens.InkSecondary,
                            )
                        }
                    } else {
                        val groupedRows = if (rows.any { it.groupName.isNotBlank() }) {
                            rows.groupBy { it.groupName.ifBlank { "总榜" } }
                        } else {
                            linkedMapOf("" to rows)
                        }
                        groupedRows.entries.forEachIndexed { groupIdx, (groupTitle, groupEntries) ->
                            if (groupTitle.isNotBlank() && groupedRows.size > 1) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ThemeTokens.TableGroupBg,
                                ) {
                                    Text(
                                        text = groupTitle,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ThemeTokens.AccentFallback,
                                    )
                                }
                            }
                            groupEntries.forEachIndexed { idx, entry ->
                                val isFav = entry.teamZhName in state.prefs.favoriteTeams || entry.teamName in state.prefs.favoriteTeams
                                StandingRowItem(
                                    entry = entry,
                                    isFavorite = isFav,
                                    showFormColumn = showFormColumn,
                                    onClick = { onClickTeam(entry) },
                                )
                                if (idx < groupEntries.lastIndex || groupIdx < groupedRows.size - 1) {
                                    HorizontalDivider(color = ThemeTokens.Hairline, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Complete Dynamic Zone Legend (Promotion, UCL, UEL, UECL, Playoffs, Relegation)
        item {
            val activeZones = rows
                .filter { it.zoneType != "NONE" && it.zoneLabel.isNotBlank() }
                .groupBy { it.zoneType to it.zoneLabel }
                .map { (pair, entries) ->
                    val minR = entries.minOf { it.rank }
                    val maxR = entries.maxOf { it.rank }
                    val rangeLabel = if (minR == maxR) "No.$minR" else "No.$minR-$maxR"
                    Triple(pair.first, pair.second, rangeLabel)
                }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
            ) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    activeZones.forEach { (zType, zLabel, rangeLabel) ->
                        LegendDot(color = colorForZone(zType), label = "$zLabel ($rangeLabel)")
                    }
                    LegendDot(color = AppleRed, label = "★ 关注主队")
                }
            }
        }
    }
}

private fun rememberAvailableStandingLeagues(byLeague: Map<String, List<StandingEntry>>): List<String> {
    val base = CompetitionCatalog.standingsCodes.toMutableList()
    byLeague.keys.forEach { k -> if (k !in base) base += k }
    return base
}

@Composable
private fun StandingRowItem(
    entry: StandingEntry,
    isFavorite: Boolean,
    showFormColumn: Boolean,
    onClick: () -> Unit,
) {
    val zoneBarColor = colorForZone(entry.zoneType)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isFavorite) ThemeTokens.AccentSoftBg else ThemeTokens.SurfacePure)
            .clickable(onClick = onClick)
            .padding(end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp))
                .background(if (isFavorite) ThemeTokens.AccentFallback else zoneBarColor),
        )
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${entry.rank}",
            modifier = Modifier.width(30.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = if (isFavorite) ThemeTokens.AccentFallback else ThemeTokens.InkPrimary,
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TeamCrest(url = entry.teamCrest, teamName = entry.teamZhName, size = 24.dp)
            BilingualTextColumn(
                zhName = if (isFavorite) "${entry.teamZhName} ★" else entry.teamZhName,
                enName = entry.teamName,
                zhFontSize = 13.sp,
                enFontSize = 9.sp,
                zhFontWeight = if (isFavorite) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = entry.played.toString(),
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum"),
            color = ThemeTokens.InkSecondary,
        )
        Text(
            text = if (entry.goalDifference > 0) "+${entry.goalDifference}" else entry.goalDifference.toString(),
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum"),
            color = ThemeTokens.InkSecondary,
        )

        if (showFormColumn) {
            Row(
                modifier = Modifier.width(52.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tokens = entry.form.split(",").filter { it.isNotBlank() }.takeLast(5)
                tokens.forEach { res ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 1.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                when (res.trim().uppercase()) {
                                    "W" -> ThemeTokens.SuccessEmerald
                                    "D" -> ThemeTokens.LotteryGold
                                    else -> ThemeTokens.SwitchUncheckedTrack
                                },
                            ),
                    )
                }
            }
        } else {
            Text(
                text = "${entry.won}/${entry.draw}/${entry.lost}",
                modifier = Modifier.width(52.dp),
                textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 11.sp, fontFeatureSettings = "tnum"),
                color = ThemeTokens.InkSecondary,
            )
        }

        Text(
            text = entry.points.toString(),
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum"),
            color = if (isFavorite) ThemeTokens.AccentFallback else ThemeTokens.InkPrimary,
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = ThemeTokens.InkMuted,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkSecondary)
    }
}

@Composable
fun TeamDetailBottomSheet(
    team: TeamDetailInfo,
    standing: StandingEntry?,
    matches: List<MatchItem>,
    isLoading: Boolean,
    isLoadingHistory: Boolean = false,
    isFavorite: Boolean,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onCloseAll: () -> Unit = onNavigateBack,
    onToggleFavorite: () -> Unit,
    onClickSquadMember: (SquadMember) -> Unit,
    onClickMatch: (MatchItem) -> Unit,
) {
    // 0 = 球队总览, 1 = 全部即将赛程, 2 = 全部历史战绩
    var teamSubPageMode by remember(team.zhName, team.name) { mutableIntStateOf(0) }

    val recentFinished = remember(matches, team.name) {
        matches
            .filter { it.phase.isFinished || (it.homeScore != null && it.awayScore != null && !it.phase.isLiveOrHalfTime) }
            .sortedByDescending { it.utcDate }
    }
    val upcomingMatches = remember(matches, team.name) {
        matches
            .filter { !it.phase.isFinished && !it.phase.isPostponed && !(it.homeScore != null && it.awayScore != null && !it.phase.isLiveOrHalfTime) }
            .sortedBy { it.utcDate }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = {
                        when {
                            teamSubPageMode != 0 -> teamSubPageMode = 0
                            canNavigateBack -> onNavigateBack()
                            else -> onCloseAll()
                        }
                    },
                    color = AppleCanvas,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = when {
                            teamSubPageMode != 0 -> "‹ 返回球队档案"
                            canNavigateBack -> "‹ 返回上一页"
                            else -> "‹ 返回"
                        },
                        color = AppleRed,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Text(
                    text = when (teamSubPageMode) {
                        1 -> "${team.zhName} · 即将赛程 (${upcomingMatches.size})"
                        2 -> "${team.zhName} · 历史战绩 (${recentFinished.size})"
                        else -> "${team.zhName} (${team.name})"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Surface(
                    onClick = onCloseAll,
                    color = AppleCanvas,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = "关闭",
                        color = InkSecondary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            HorizontalDivider(color = AppleDivider)

            if (teamSubPageMode != 0) {
                val subList = if (teamSubPageMode == 1) upcomingMatches else recentFinished
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (subList.isEmpty()) {
                        item {
                            Text("暂无相关比赛记录", style = MaterialTheme.typography.bodySmall, color = InkTertiary)
                        }
                    } else {
                        itemsIndexed(subList, key = { idx, m -> "tsub_${teamSubPageMode}_${m.id}_$idx" }) { _, m ->
                            TeamMatchRowCard(
                                match = m,
                                team = team,
                                isHistory = teamSubPageMode == 2,
                                onClick = { onClickMatch(m) },
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TeamCrest(url = team.crest, teamName = team.zhName, size = 56.dp)
                            BilingualTextColumn(
                                zhName = team.zhName,
                                enName = team.name,
                                zhFontSize = 20.sp,
                                enFontSize = 12.sp,
                                zhFontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = isFavorite,
                                onClick = onToggleFavorite,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = ThemeTokens.SurfaceSubtle,
                                    labelColor = ThemeTokens.InkSecondary,
                                    selectedContainerColor = ThemeTokens.AccentSoftBg,
                                    selectedLabelColor = ThemeTokens.AccentFallback,
                                    iconColor = ThemeTokens.InkMuted,
                                    selectedLeadingIconColor = ThemeTokens.AccentFallback,
                                ),
                                border = null,
                                label = { Text(if (isFavorite) "已关注" else "关注") },
                                leadingIcon = {
                                    Icon(
                                        if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }

                    if (standing != null) {
                        item {
                            Surface(color = AppleCanvas.copy(alpha = 0.85f), shape = RoundedCornerShape(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(
                                            "${CompetitionCatalog.zhNameOrFallback(standing.competitionCode, standing.competitionCode)} No.${standing.rank}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = AppleRed,
                                        )
                                        Text(
                                            "${standing.won}胜 ${standing.draw}平 ${standing.lost}负 · 净胜 ${standing.goalDifference} · ${standing.points} 分",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = InkSecondary,
                                        )
                                    }
                                    if (standing.zoneLabel.isNotBlank()) {
                                        Surface(
                                            color = colorForZone(standing.zoneType).copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(999.dp),
                                        ) {
                                            Text(
                                                text = standing.zoneLabel,
                                                color = colorForZone(standing.zoneType),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Club Metadata + Head Coach Portrait
                    item {
                        Surface(color = AppleCanvas.copy(alpha = 0.85f), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                if (team.coachZhName.isNotBlank() || team.coachName.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("主教练", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            PlayerPortrait(
                                                url = team.coachPortraitUrl,
                                                playerName = team.coachZhName.ifBlank { team.coachName },
                                                size = 26.dp,
                                            )
                                            Text(
                                                text = "${team.coachZhName} (${team.coachName})",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = InkPrimary,
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = AppleDivider)
                                }
                                if (team.venue.isNotBlank()) {
                                    ClubMetaRow("主场球场", team.venue)
                                    HorizontalDivider(color = AppleDivider)
                                }
                                if (team.founded > 0) {
                                    ClubMetaRow("成立年份", "${team.founded} 年")
                                    HorizontalDivider(color = AppleDivider)
                                }
                                ClubMetaRow("队列人数", "${team.squad.size} 名一线队成员")
                            }
                        }
                    }

                    // Section 1: Upcoming Matches (compact top 3 + click to view full list)
                    if (upcomingMatches.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("即将进行的赛程", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "查看全部 (${upcomingMatches.size}) ›",
                                    color = AppleRed,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { teamSubPageMode = 1 }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        itemsIndexed(upcomingMatches.take(3), key = { idx, m -> "up_${m.id}_$idx" }) { _, m ->
                            TeamMatchRowCard(match = m, team = team, isHistory = false, onClick = { onClickMatch(m) })
                        }
                        if (upcomingMatches.size > 3) {
                            item {
                                OutlinedButton(
                                    onClick = { teamSubPageMode = 1 },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("查看全部 ${upcomingMatches.size} 场即将赛程 ›", color = AppleRed, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Section 2: Historical Matches (compact top 3 + click to view full list)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("历史比赛记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (isLoadingHistory) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AppleRed)
                                }
                            }
                            if (recentFinished.isNotEmpty()) {
                                Text(
                                    text = "查看全部 (${recentFinished.size}) ›",
                                    color = AppleRed,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { teamSubPageMode = 2 }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (recentFinished.isEmpty() && isLoadingHistory) {
                        item { Text("正在同步全赛季历史战绩...", style = MaterialTheme.typography.bodySmall, color = InkSecondary) }
                    } else if (recentFinished.isEmpty()) {
                        item { Text("暂无该球队近期已完赛历史记录", style = MaterialTheme.typography.bodySmall, color = InkTertiary) }
                    } else {
                        itemsIndexed(recentFinished.take(3), key = { idx, m -> "hist_${m.id}_$idx" }) { _, m ->
                            TeamMatchRowCard(match = m, team = team, isHistory = true, onClick = { onClickMatch(m) })
                        }
                        if (recentFinished.size > 3) {
                            item {
                                OutlinedButton(
                                    onClick = { teamSubPageMode = 2 },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("查看全部 ${recentFinished.size} 场历史战绩与赛况 ›", color = AppleRed, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Squad List with Real-Person Portraits (lazy items with collision-proof keys!)
                    item { Text("全队球员名单（点击查看详情与简介）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    if (isLoading && team.squad.isEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AppleRed)
                                Text("正在获取球队阵容与头像...", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                            }
                        }
                    } else if (team.squad.isEmpty()) {
                        item { Text("当前暂未收录该球队大名单（可查看上方历史战绩与比赛详情）", style = MaterialTheme.typography.bodySmall, color = InkTertiary) }
                    } else {
                        itemsIndexed(team.squad, key = { idx, m -> "${m.id}_${m.name}_$idx" }) { _, member ->
                            Surface(
                                onClick = { onClickSquadMember(member) },
                                color = AppleCanvas.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    PlayerPortrait(
                                        url = member.portraitUrl,
                                        playerName = member.zhName,
                                        size = 38.dp,
                                    )
                                    Text(
                                        text = if (member.shirtNumber > 0) "#${member.shirtNumber}" else "-",
                                        modifier = Modifier.width(30.dp),
                                        fontWeight = FontWeight.Bold,
                                        color = AppleRed,
                                        fontSize = 12.sp,
                                    )
                                    BilingualTextColumn(
                                        zhName = member.zhName,
                                        enName = member.name,
                                        zhFontSize = 14.sp,
                                        enFontSize = 10.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = member.positionZh,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkSecondary,
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TeamMatchRowCard(
    match: MatchItem,
    team: TeamDetailInfo,
    isHistory: Boolean,
    onClick: () -> Unit,
) {
    val isHome = match.homeTeamZhName == team.zhName || match.homeTeamName.equals(team.name, true)
    val myScore = if (isHome) match.homeScore else match.awayScore
    val oppScore = if (isHome) match.awayScore else match.homeScore
    val resTag = when {
        !isHistory || myScore == null || oppScore == null -> null
        myScore > oppScore -> "胜" to AppleRed
        myScore == oppScore -> "平" to AmberWarm
        else -> "负" to InkTertiary
    }
    Surface(
        onClick = onClick,
        color = AppleCanvas.copy(alpha = 0.85f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (resTag != null) {
                Surface(
                    color = resTag.second.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = resTag.first,
                        color = resTag.second,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            TeamCrest(url = match.homeTeamCrest, teamName = match.homeTeamZhName, size = 24.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (match.homeScore != null && match.awayScore != null) {
                        "${match.homeTeamZhName} (${match.homeTeamName}) ${match.homeScore} : ${match.awayScore} ${match.awayTeamZhName} (${match.awayTeamName})"
                    } else {
                        "${match.homeTeamZhName} (${match.homeTeamName}) vs ${match.awayTeamZhName} (${match.awayTeamName})"
                    },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "${match.competitionZhName} · 北京时间 ${MatchTimeFormatter.formatMonthDayTime(match.utcDate)}",
                    fontSize = 11.sp,
                    color = InkSecondary,
                )
            }
            TeamCrest(url = match.awayTeamCrest, teamName = match.awayTeamZhName, size = 24.dp)
            Text(
                text = if (isHistory) "详情 ›" else "查看 ›",
                color = AppleRed,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ClubMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = InkPrimary)
    }
}
