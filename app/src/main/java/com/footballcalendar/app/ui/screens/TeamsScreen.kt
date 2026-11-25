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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.StarPlayer
import com.footballcalendar.app.ui.CalendarUiState
import com.footballcalendar.app.ui.components.AppleSegmentedControl
import com.footballcalendar.app.ui.components.PlayerPortrait
import com.footballcalendar.app.ui.components.TeamCrest
import com.footballcalendar.app.ui.theme.AppleCanvas
import com.footballcalendar.app.ui.theme.AppleDivider
import com.footballcalendar.app.ui.theme.AppleRed
import com.footballcalendar.app.ui.theme.AppleRedBorder
import com.footballcalendar.app.ui.theme.AppleRedTint
import com.footballcalendar.app.ui.theme.InkPrimary
import com.footballcalendar.app.ui.theme.InkSecondary
import com.footballcalendar.app.ui.theme.InkTertiary
import com.footballcalendar.app.util.MatchTimeFormatter
import com.footballcalendar.app.util.TeamAssetCatalog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeamsScreen(
    state: CalendarUiState,
    onToggleCompetition: (String) -> Unit,
    onToggleFavoriteTeam: (String) -> Unit,
    onToggleFavoritePlayer: (String) -> Unit,
    onOpenPlayerDetail: (StarPlayer) -> Unit,
    onClosePlayerDetail: () -> Unit,
    onSearchExternalPlayer: (String) -> Unit,
    onOpenMatchDetail: (MatchItem) -> Unit,
) {
    var subTab by remember { mutableIntStateOf(0) } // 0=球星库, 1=主队档案
    var playerSearchQuery by remember { mutableStateOf("") }
    var onlyFavPlayers by remember { mutableStateOf(false) }
    var customTeamInput by remember { mutableStateOf("") }
    val favTeamsList = remember(state.prefs.favoriteTeams) { state.prefs.favoriteTeams.sorted() }
    var activeTeamName by remember(favTeamsList) {
        mutableStateOf(favTeamsList.firstOrNull() ?: "阿森纳")
    }

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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column {
                    Text(
                        text = "球星真人档案 · 俱乐部 & 国家队",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppleRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (subTab == 0) "球星库" else "主队档案",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = InkPrimary,
                        letterSpacing = (-0.5).sp,
                    )
                }

                AppleSegmentedControl(
                    options = listOf("球星库 (${state.starPlayers.size})", "主队档案 (${favTeamsList.size})"),
                    selectedIndex = subTab,
                    onSelect = { subTab = it },
                )
            }
        }

        if (subTab == 0) {
            // Star Players Hub
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = playerSearchQuery,
                            onValueChange = { playerSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("搜索球星（支持输入英文全名在线检索）") },
                            placeholder = { Text("例如：Mbappe / Messi / Son Heung-min") },
                            singleLine = true,
                            trailingIcon = {
                                if (state.isSearchingPlayer) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(
                                        onClick = { onSearchExternalPlayer(playerSearchQuery) },
                                        enabled = playerSearchQuery.isNotBlank(),
                                    ) {
                                        Icon(Icons.Filled.Search, contentDescription = "在线检索球星", tint = AppleRed)
                                    }
                                }
                            },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !onlyFavPlayers,
                                onClick = { onlyFavPlayers = false },
                                label = { Text("全部热门球星") },
                            )
                            FilterChip(
                                selected = onlyFavPlayers,
                                onClick = { onlyFavPlayers = true },
                                label = { Text("★ 我关注的球星") },
                            )
                        }
                    }
                }
            }

            val visiblePlayers = state.starPlayers.filter { p ->
                val favOk = !onlyFavPlayers || p.id in state.prefs.favoritePlayers
                val q = playerSearchQuery.trim()
                val queryOk = q.isEmpty() ||
                    p.zhName.contains(q, ignoreCase = true) ||
                    p.name.contains(q, ignoreCase = true) ||
                    p.clubZhName.contains(q, ignoreCase = true) ||
                    p.nationalZhName.contains(q, ignoreCase = true)
                favOk && queryOk
            }

            if (visiblePlayers.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("本地列表未找到「$playerSearchQuery」", fontWeight = FontWeight.Bold)
                            Button(
                                onClick = { onSearchExternalPlayer(playerSearchQuery) },
                                colors = ButtonDefaults.buttonColors(containerColor = AppleRed),
                                enabled = playerSearchQuery.isNotBlank() && !state.isSearchingPlayer,
                            ) {
                                Text("在线搜索该球星头像与资料")
                            }
                        }
                    }
                }
            } else {
                items(visiblePlayers, key = { it.id }) { player ->
                    StarPlayerCard(
                        player = player,
                        isFavorite = player.id in state.prefs.favoritePlayers,
                        onClick = { onOpenPlayerDetail(player) },
                        onToggleFavorite = { onToggleFavoritePlayer(player.id) },
                    )
                }
            }
        } else {
            // Favorite Clubs & National Teams Profile + Management
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    favTeamsList.forEach { team ->
                        val selected = team == activeTeamName
                        Surface(
                            onClick = { activeTeamName = team },
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) AppleRed else Color.White,
                            border = if (selected) null else androidx.compose.foundation.BorderStroke(0.75.dp, AppleDivider),
                        ) {
                            Text(
                                text = team,
                                color = if (selected) Color.White else InkPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }

            item {
                TeamProfileSummaryCard(
                    teamName = activeTeamName,
                    standing = state.teamRankByName[activeTeamName],
                    teamMatches = state.allMatches.filter { it.involvesTeam(activeTeamName) },
                    onOpenMatch = onOpenMatchDetail,
                )
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("订阅赛事范围（含国家队）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CompetitionCatalog.all.forEach { comp ->
                                val selected = comp.code in state.prefs.followedCompetitions
                                FilterChip(
                                    selected = selected,
                                    onClick = { onToggleCompetition(comp.code) },
                                    label = { Text("${comp.zhName} (${comp.code})") },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else {
                                        null
                                    },
                                )
                            }
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
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("关注球队管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = customTeamInput,
                            onValueChange = { customTeamInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("添加俱乐部或国家队（中文或英文）") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (customTeamInput.isNotBlank()) {
                                            val added = customTeamInput.trim()
                                            onToggleFavoriteTeam(added)
                                            activeTeamName = added
                                            customTeamInput = ""
                                        }
                                    },
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "添加球队", tint = AppleRed)
                                }
                            },
                        )

                        CompetitionCatalog.all.forEach { comp ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(comp.zhName, style = MaterialTheme.typography.labelLarge, color = AppleRed, fontWeight = FontWeight.Bold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    comp.popularTeams.forEach { team ->
                                        val isFav = team in state.prefs.favoriteTeams
                                        FilterChip(
                                            selected = isFav,
                                            onClick = {
                                                onToggleFavoriteTeam(team)
                                                activeTeamName = team
                                            },
                                            label = { Text(team) },
                                            leadingIcon = {
                                                Icon(
                                                    if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            },
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

    val detailPlayer = state.selectedStarPlayer
    if (detailPlayer != null) {
        ModalBottomSheet(
            onDismissRequest = onClosePlayerDetail,
            containerColor = Color.White,
        ) {
            val playerMatches = state.allMatches.filter { m ->
                (detailPlayer.clubZhName.isNotBlank() && m.involvesTeam(detailPlayer.clubZhName)) ||
                    (detailPlayer.clubName.isNotBlank() && m.involvesTeam(detailPlayer.clubName)) ||
                    (detailPlayer.nationalZhName.isNotBlank() && m.involvesTeam(detailPlayer.nationalZhName)) ||
                    (detailPlayer.nationalName.isNotBlank() && m.involvesTeam(detailPlayer.nationalName))
            }.sortedBy { it.utcDate }

            PlayerDetailSheet(
                player = detailPlayer,
                isFavorite = detailPlayer.id in state.prefs.favoritePlayers,
                upcomingMatches = playerMatches,
                onToggleFavorite = { onToggleFavoritePlayer(detailPlayer.id) },
                onClickMatch = { match ->
                    onClosePlayerDetail()
                    onOpenMatchDetail(match)
                },
            )
        }
    }
}

@Composable
private fun StarPlayerCard(
    player: StarPlayer,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = if (isFavorite) {
            androidx.compose.foundation.BorderStroke(1.dp, AppleRedBorder)
        } else {
            androidx.compose.foundation.BorderStroke(0.5.dp, AppleDivider)
        },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PlayerPortrait(url = player.portraitUrl, playerName = player.zhName, size = 58.dp)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = player.zhName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = InkPrimary,
                    )
                    Text(
                        text = "#${player.shirtNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppleRed,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "${player.nationalityZh} · ${player.positionZh}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (player.clubZhName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TeamCrest(url = player.clubCrestUrl, teamName = player.clubZhName, size = 18.dp)
                            Text(player.clubZhName, style = MaterialTheme.typography.labelSmall, color = InkPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (player.nationalZhName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TeamCrest(url = player.nationalCrestUrl, teamName = player.nationalZhName, size = 18.dp)
                            Text(player.nationalZhName, style = MaterialTheme.typography.labelSmall, color = InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "关注球星",
                    tint = if (isFavorite) AppleRed else InkTertiary,
                )
            }
        }
    }
}

@Composable
private fun TeamProfileSummaryCard(
    teamName: String,
    standing: com.footballcalendar.app.model.StandingEntry?,
    teamMatches: List<MatchItem>,
    onOpenMatch: (MatchItem) -> Unit,
) {
    val logoUrl = standing?.teamCrest?.ifBlank { TeamAssetCatalog.resolveTeamLogo(teamName) }
        ?: TeamAssetCatalog.resolveTeamLogo(teamName)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamCrest(url = logoUrl, teamName = teamName, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(teamName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = if (standing != null) {
                            "${CompetitionCatalog.zhNameOrFallback(standing.competitionCode, standing.competitionCode)} 第 ${standing.rank} 名 · ${standing.points} 分 · 净胜 ${standing.goalDifference}"
                        } else {
                            "共关联 ${teamMatches.size} 场近期与未来赛程"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                    )
                }
                if (standing != null) {
                    Surface(color = AppleRedTint, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = "#${standing.rank}",
                            color = AppleRed,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = AppleCanvas)

            if (teamMatches.isEmpty()) {
                Text("当前筛选周期内暂无该球队赛程", style = MaterialTheme.typography.bodySmall, color = InkTertiary)
            } else {
                teamMatches.sortedBy { it.utcDate }.take(5).forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenMatch(m) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${m.homeTeamZhName} vs ${m.awayTeamZhName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${m.competitionZhName} · ${MatchTimeFormatter.formatMonthDayTime(m.utcDate)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkSecondary,
                            )
                        }
                        Text(
                            text = if (m.homeScore != null && m.awayScore != null) {
                                "${m.homeScore}:${m.awayScore}"
                            } else {
                                m.status.zhLabel
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = AppleRed,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerDetailSheet(
    player: StarPlayer,
    isFavorite: Boolean,
    upcomingMatches: List<MatchItem>,
    onToggleFavorite: () -> Unit,
    onClickMatch: (MatchItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PlayerPortrait(url = player.portraitUrl, playerName = player.zhName, size = 76.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text("${player.zhName} · #${player.shirtNumber}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(player.name, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                Text("${player.nationalityZh} · ${player.positionZh}", style = MaterialTheme.typography.labelMedium, color = AppleRed, fontWeight = FontWeight.SemiBold)
            }
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

        if (player.bio.isNotBlank()) {
            Text(player.bio, style = MaterialTheme.typography.bodySmall, color = InkSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (player.clubZhName.isNotBlank()) {
                Surface(color = AppleCanvas, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TeamCrest(url = player.clubCrestUrl, teamName = player.clubZhName, size = 28.dp)
                        Column {
                            Text("所属俱乐部", style = MaterialTheme.typography.labelSmall, color = InkTertiary)
                            Text(player.clubZhName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (player.nationalZhName.isNotBlank()) {
                Surface(color = AppleCanvas, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TeamCrest(url = player.nationalCrestUrl, teamName = player.nationalZhName, size = 28.dp)
                        Column {
                            Text("国家队", style = MaterialTheme.typography.labelSmall, color = InkTertiary)
                            Text(player.nationalZhName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Text("关联俱乐部与国家队近期赛程", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (upcomingMatches.isEmpty()) {
            Text("当前同步周期内暂无该球员所属球队赛程", style = MaterialTheme.typography.bodySmall, color = InkTertiary)
        } else {
            upcomingMatches.take(4).forEach { m ->
                Surface(
                    onClick = { onClickMatch(m) },
                    color = AppleCanvas,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${m.homeTeamZhName} vs ${m.awayTeamZhName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("${m.competitionZhName} · ${MatchTimeFormatter.formatMonthDayTime(m.utcDate)}", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                        }
                        Text("查看 ›", color = AppleRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
