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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.SquadMember
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.ui.CalendarUiState
import com.footballcalendar.app.ui.components.BilingualTextColumn
import com.footballcalendar.app.ui.components.TeamCrest
import com.footballcalendar.app.ui.theme.AmberWarm
import com.footballcalendar.app.ui.theme.AppleCanvas
import com.footballcalendar.app.ui.theme.AppleDivider
import com.footballcalendar.app.ui.theme.AppleLiveRed
import com.footballcalendar.app.ui.theme.AppleRed
import com.footballcalendar.app.ui.theme.AppleRedTint
import com.footballcalendar.app.ui.theme.InkPrimary
import com.footballcalendar.app.ui.theme.InkSecondary
import com.footballcalendar.app.ui.theme.InkTertiary
import com.footballcalendar.app.ui.theme.UclBlue
import com.footballcalendar.app.util.MatchTimeFormatter

private val ZoneGreen = Color(0xFF059669)
private val ZonePurple = Color(0xFF7C3AED)
private val ZoneOrange = Color(0xFFEA580C)
private val ZoneCyan = Color(0xFF0284C7)

private fun colorForZone(zoneType: String): Color = when (zoneType) {
    "UCL" -> UclBlue
    "UCL_QUAL" -> ZoneCyan
    "PROMOTION" -> ZoneGreen
    "UEL" -> AmberWarm
    "UECL" -> Color(0xFF10B981)
    "PLAYOFF" -> ZonePurple
    "RELEGATION_PLAYOFF" -> ZoneOrange
    "RELEGATION" -> AppleLiveRed
    else -> Color.Transparent
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StandingsScreen(
    state: CalendarUiState,
    onSelectLeague: (String) -> Unit,
    onClickTeam: (StandingEntry) -> Unit,
    onCloseTeamDetail: () -> Unit,
    onToggleFavoriteTeam: (String) -> Unit,
    onClickSquadMember: (SquadMember, TeamDetailInfo) -> Unit,
    onOpenMatchDetail: (MatchItem) -> Unit,
) {
    val availableLeagues = rememberAvailableStandingLeagues(state.standingsByLeague)
    val rows = state.standingsByLeague[state.selectedStandingsLeague].orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleCanvas),
        contentPadding = PaddingValues(bottom = 24.dp),
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
                    color = AppleRed,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "联赛积分榜",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkPrimary,
                    letterSpacing = (-0.5).sp,
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableLeagues.forEach { code ->
                    val name = CompetitionCatalog.findByCode(code)?.zhName ?: code
                    val selected = state.selectedStandingsLeague == code
                    Surface(
                        onClick = { onSelectLeague(code) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) AppleRed else Color.White,
                        border = if (selected) null else androidx.compose.foundation.BorderStroke(0.75.dp, AppleDivider),
                    ) {
                        Text(
                            text = name,
                            color = if (selected) Color.White else InkPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 1.dp,
            ) {
                Column {
                    val showFormColumn = rows.any { it.form.isNotBlank() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFAFAFC))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("No.", modifier = Modifier.width(34.dp), style = MaterialTheme.typography.labelSmall, color = InkTertiary, fontWeight = FontWeight.Bold)
                        Text("球队", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = InkTertiary, fontWeight = FontWeight.Bold)
                        Text("赛", modifier = Modifier.width(26.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = InkTertiary, fontWeight = FontWeight.Bold)
                        Text("净", modifier = Modifier.width(34.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = InkTertiary, fontWeight = FontWeight.Bold)
                        Text(
                            if (showFormColumn) "近5场" else "胜平负",
                            modifier = Modifier.width(52.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = InkTertiary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("积分", modifier = Modifier.width(32.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = InkTertiary, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = AppleCanvas)

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
                                color = InkSecondary,
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
                                    color = Color(0xFFF3F4F6),
                                ) {
                                    Text(
                                        text = groupTitle,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppleRed,
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
                                    HorizontalDivider(color = AppleCanvas)
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

    val selectedTeam = state.selectedTeamDetail
    if (selectedTeam != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onCloseTeamDetail,
            sheetState = sheetState,
            containerColor = Color.White.copy(alpha = 0.94f),
            scrimColor = Color(0x4A1C1C1E),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            val teamStanding = state.standingsByLeague[selectedTeam.competitionCode]
                ?.firstOrNull {
                    it.teamName.equals(selectedTeam.name, true) || it.teamZhName == selectedTeam.zhName
                }
            val teamMatches = state.allMatches.filter { m ->
                m.involvesTeam(selectedTeam.zhName) || m.involvesTeam(selectedTeam.name)
            }.sortedBy { it.utcDate }

            TeamDetailBottomSheet(
                team = selectedTeam,
                standing = teamStanding,
                matches = teamMatches,
                isLoading = state.isLoadingTeamDetail,
                isFavorite = selectedTeam.zhName in state.prefs.favoriteTeams || selectedTeam.name in state.prefs.favoriteTeams,
                onToggleFavorite = { onToggleFavoriteTeam(selectedTeam.zhName) },
                onClickSquadMember = { member -> onClickSquadMember(member, selectedTeam) },
                onClickMatch = { match ->
                    onCloseTeamDetail()
                    onOpenMatchDetail(match)
                },
            )
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
            .background(if (isFavorite) AppleRedTint.copy(alpha = 0.72f) else Color.White)
            .clickable(onClick = onClick)
            .padding(end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(30.dp)
                .background(if (isFavorite) AppleRed else zoneBarColor),
        )
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "No.${entry.rank}",
            modifier = Modifier.width(34.dp),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = if (isFavorite) AppleRed else InkPrimary,
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
            color = InkSecondary,
        )
        Text(
            text = if (entry.goalDifference > 0) "+${entry.goalDifference}" else entry.goalDifference.toString(),
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum"),
            color = InkSecondary,
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
                                    "W" -> AppleRed
                                    "D" -> AmberWarm
                                    else -> Color(0xFFD1D1D6)
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
                color = InkSecondary,
            )
        }

        Text(
            text = entry.points.toString(),
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum"),
            color = if (isFavorite) AppleRed else InkPrimary,
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = InkTertiary,
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
private fun TeamDetailBottomSheet(
    team: TeamDetailInfo,
    standing: StandingEntry?,
    matches: List<MatchItem>,
    isLoading: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClickSquadMember: (SquadMember) -> Unit,
    onClickMatch: (MatchItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.84f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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

        if (standing != null) {
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

        // Club Metadata
        Surface(color = AppleCanvas.copy(alpha = 0.85f), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                if (team.coachZhName.isNotBlank() || team.coachName.isNotBlank()) {
                    ClubMetaRow("主教练", "${team.coachZhName} (${team.coachName})")
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

        // Squad List
        Text("全队球员名单（点击查看详情）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AppleRed)
                Text("正在获取球队阵容...", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
        } else if (team.squad.isEmpty()) {
            Text("请使用日历页刷新同步完整一线队阵容名单", style = MaterialTheme.typography.bodySmall, color = InkTertiary)
        } else {
            team.squad.forEach { member ->
                Surface(
                    onClick = { onClickSquadMember(member) },
                    color = AppleCanvas.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
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

        if (matches.isNotEmpty()) {
            Text("近期与未来赛程", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            matches.take(5).forEach { m ->
                Surface(
                    onClick = { onClickMatch(m) },
                    color = AppleCanvas.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${m.homeTeamZhName} (${m.homeTeamName}) vs ${m.awayTeamZhName} (${m.awayTeamName})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("${m.competitionZhName} · ${MatchTimeFormatter.formatMonthDayTime(m.utcDate)}", fontSize = 11.sp, color = InkSecondary)
                        }
                        Text("查看 ›", color = AppleRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
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
