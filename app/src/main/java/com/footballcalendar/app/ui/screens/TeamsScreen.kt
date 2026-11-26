package com.footballcalendar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.StarPlayer
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.ui.CalendarUiState
import com.footballcalendar.app.ui.components.AppleSegmentedControl
import com.footballcalendar.app.ui.components.BilingualTextColumn
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
import com.footballcalendar.app.util.TeamLocalization

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeamsScreen(
    state: CalendarUiState,
    onToggleCompetition: (String) -> Unit,
    onToggleFavoriteTeam: (String) -> Unit,
    onToggleFavoritePlayer: (String) -> Unit,
    onOpenPlayerDetail: (StarPlayer) -> Unit,
    onOpenTeamDetail: (enName: String, zhName: String, compCode: String, crest: String) -> Unit,
    onSearchExternalPlayer: (String) -> Unit,
    onOpenMatchDetail: (MatchItem) -> Unit,
) {
    var subTab by remember { mutableIntStateOf(0) } // 0=球员榜, 1=主队与全部球队
    var playerSearchQuery by remember { mutableStateOf("") }
    var onlyFavPlayers by remember { mutableStateOf(false) }
    var onlyScorerLeaders by remember { mutableStateOf(false) }
    var selectedPlayerLeague by remember { mutableStateOf<String?>(null) }
    var playerDisplayLimit by remember(selectedPlayerLeague, playerSearchQuery, onlyFavPlayers, onlyScorerLeaders) {
        mutableIntStateOf(60)
    }
    var customTeamInput by remember { mutableStateOf("") }
    var teamSearchQuery by remember { mutableStateOf("") }
    var expandedTeamLeagues by remember {
        mutableStateOf(state.prefs.followedCompetitions.take(2).toSet())
    }

    val favTeamsList = remember(state.prefs.favoriteTeams) { state.prefs.favoriteTeams.sorted() }
    var activeTeamName by remember(favTeamsList) {
        mutableStateOf(favTeamsList.firstOrNull() ?: "阿森纳")
    }

    val filteredPlayers = remember(
        subTab,
        state.starPlayers,
        onlyFavPlayers,
        onlyScorerLeaders,
        selectedPlayerLeague,
        playerSearchQuery,
        state.prefs.favoritePlayers,
    ) {
        if (subTab != 0) {
            emptyList()
        } else {
            val q = playerSearchQuery.trim()
            state.starPlayers.filter { p ->
                val favOk = !onlyFavPlayers || p.id in state.prefs.favoritePlayers
                val scorerOk = !onlyScorerLeaders || p.goals > 0 || p.rank > 0
                val leagueOk = selectedPlayerLeague == null || p.competitionCode == selectedPlayerLeague
                val queryOk = q.isEmpty() ||
                    p.zhName.contains(q, ignoreCase = true) ||
                    p.name.contains(q, ignoreCase = true) ||
                    p.clubZhName.contains(q, ignoreCase = true) ||
                    p.clubName.contains(q, ignoreCase = true) ||
                    p.nationalZhName.contains(q, ignoreCase = true)
                favOk && scorerOk && leagueOk && queryOk
            }
        }
    }

    val teamsByComp = remember(subTab, state.standingsByLeague, state.allTeams, state.allMatches, teamSearchQuery) {
        if (subTab != 1) {
            emptyList()
        } else {
            val q = teamSearchQuery.trim()
            CompetitionCatalog.all.mapNotNull { comp ->
                val rawMap = buildMap<String, String> {
                    state.standingsByLeague[comp.code].orEmpty().forEach { s ->
                        put(s.teamZhName, s.teamName)
                    }
                    state.allTeams.filter { it.competitionCode == comp.code }.forEach { t ->
                        putIfAbsent(t.zhName, t.name)
                    }
                    state.allMatches.filter { it.competitionCode == comp.code }.forEach { m ->
                        putIfAbsent(m.homeTeamZhName, m.homeTeamName)
                        putIfAbsent(m.awayTeamZhName, m.awayTeamName)
                    }
                    if (isEmpty()) {
                        comp.popularTeams.forEach { pt -> put(pt, pt) }
                    }
                }
                val filteredMap = if (q.isEmpty()) {
                    rawMap
                } else {
                    rawMap.filter { (zh, en) ->
                        zh.contains(q, ignoreCase = true) || en.contains(q, ignoreCase = true)
                    }
                }
                if (filteredMap.isEmpty()) null else comp to filteredMap
            }
        }
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
                        text = "全赛事射手榜 · 全队列球员 · 中英双语档案",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppleRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (subTab == 0) "球员榜" else "主队与全部球队",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = InkPrimary,
                        letterSpacing = (-0.5).sp,
                    )
                }

                AppleSegmentedControl(
                    options = listOf("球员榜 (${state.starPlayers.size})", "主队档案 (${favTeamsList.size})"),
                    selectedIndex = subTab,
                    onSelect = { subTab = it },
                )
            }
        }

        if (subTab == 0) {
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
                            label = { Text("搜索球员（中文 / 英文全名 / 球队）") },
                            placeholder = { Text("支持搜索任意一线队球员或在线百科补充") },
                            singleLine = true,
                            trailingIcon = {
                                if (state.isSearchingPlayer) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(
                                        onClick = { onSearchExternalPlayer(playerSearchQuery) },
                                        enabled = playerSearchQuery.isNotBlank(),
                                    ) {
                                        Icon(Icons.Filled.Search, contentDescription = "在线检索球员", tint = AppleRed)
                                    }
                                }
                            },
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = !onlyFavPlayers && !onlyScorerLeaders,
                                onClick = {
                                    onlyFavPlayers = false
                                    onlyScorerLeaders = false
                                },
                                label = { Text("全部球员 (${state.starPlayers.size})") },
                            )
                            FilterChip(
                                selected = onlyScorerLeaders,
                                onClick = {
                                    onlyScorerLeaders = !onlyScorerLeaders
                                    onlyFavPlayers = false
                                },
                                label = { Text("⚽ 射手榜") },
                            )
                            FilterChip(
                                selected = onlyFavPlayers,
                                onClick = {
                                    onlyFavPlayers = !onlyFavPlayers
                                    onlyScorerLeaders = false
                                },
                                label = { Text("★ 我关注的球员") },
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = selectedPlayerLeague == null,
                                onClick = { selectedPlayerLeague = null },
                                label = { Text("全赛事") },
                            )
                            CompetitionCatalog.standingsCodes.forEach { code ->
                                val zh = CompetitionCatalog.zhNameOrFallback(code, code)
                                FilterChip(
                                    selected = selectedPlayerLeague == code,
                                    onClick = {
                                        selectedPlayerLeague = if (selectedPlayerLeague == code) null else code
                                    },
                                    label = { Text(zh) },
                                )
                            }
                        }
                    }
                }
            }

            if (filteredPlayers.isEmpty()) {
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
                            Text("当前列表未找到「$playerSearchQuery」", fontWeight = FontWeight.Bold)
                            Button(
                                onClick = { onSearchExternalPlayer(playerSearchQuery) },
                                colors = ButtonDefaults.buttonColors(containerColor = AppleRed),
                                enabled = playerSearchQuery.isNotBlank() && !state.isSearchingPlayer,
                            ) {
                                Text("在线搜索该球员资料")
                            }
                        }
                    }
                }
            } else {
                val displayed = filteredPlayers.take(playerDisplayLimit)
                itemsIndexed(displayed, key = { idx, p -> "${p.id}_${p.clubName}_$idx" }) { _, player ->
                    PlayerRankCard(
                        player = player,
                        isFavorite = player.id in state.prefs.favoritePlayers,
                        onClick = { onOpenPlayerDetail(player) },
                        onClickTeam = { en, zh, cc, cr -> onOpenTeamDetail(en, zh, cc, cr) },
                        onToggleFavorite = { onToggleFavoritePlayer(player.id) },
                    )
                }
                if (filteredPlayers.size > playerDisplayLimit) {
                    val remaining = (filteredPlayers.size - playerDisplayLimit).coerceAtMost(100)
                    item {
                        OutlinedButton(
                            onClick = { playerDisplayLimit += 100 },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("加载更多 +$remaining 名球员（已显示 ${displayed.size} / ${filteredPlayers.size}）")
                        }
                    }
                }
            }
        } else {
            // Favorite Teams + All Competition Teams
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
                                text = "★ $team",
                                color = if (selected) Color.White else InkPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            if (activeTeamName.isNotBlank()) {
                val standing = state.teamRankByName[activeTeamName]
                val teamDetail = state.teamsByName[activeTeamName] ?: state.teamsByName[activeTeamName.lowercase()]
                val teamMatches = state.allMatches.filter {
                    it.involvesTeam(activeTeamName) || (teamDetail != null && it.involvesTeam(teamDetail.name))
                }
                item {
                    TeamProfileSummaryCard(
                        teamName = activeTeamName,
                        teamDetail = teamDetail,
                        standing = standing,
                        teamMatches = teamMatches,
                        onOpenTeam = {
                            onOpenTeamDetail(
                                teamDetail?.name ?: standing?.teamName ?: activeTeamName,
                                activeTeamName,
                                teamDetail?.competitionCode ?: standing?.competitionCode ?: "",
                                teamDetail?.crest ?: standing?.teamCrest ?: "",
                            )
                        },
                        onOpenMatch = onOpenMatchDetail,
                    )
                }
            }

            // Followed Competitions Management (includes Asian, MLS, European Cups, National Youth Cups)
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("关注赛事订阅（俱 & 国家队 & 青少年）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CompetitionCatalog.all.forEach { comp ->
                                val active = comp.code in state.prefs.followedCompetitions
                                FilterChip(
                                    selected = active,
                                    onClick = { onToggleCompetition(comp.code) },
                                    label = { Text("${comp.zhName} (${comp.enName})") },
                                )
                            }
                        }
                    }
                }
            }

            // All Teams Catalog grouped lazily by Competition (collapsible to avoid 400+ synchronous chips)
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("关注球队管理（按赛事折叠加载）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = customTeamInput,
                            onValueChange = { customTeamInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("添加自定义球队（中文或英文）") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (customTeamInput.isNotBlank()) {
                                            val added = TeamLocalization.localizeTeam(customTeamInput.trim())
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
                        OutlinedTextField(
                            value = teamSearchQuery,
                            onValueChange = { teamSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索俱乐部或国家队（中/英文）...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                        )
                    }
                }
            }

            itemsIndexed(teamsByComp, key = { idx, (comp, _) -> "team_comp_${comp.code}_$idx" }) { _, (comp, teamsFromMatches) ->
                val isExpanded = teamSearchQuery.isNotBlank() || comp.code in expandedTeamLeagues
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    expandedTeamLeagues = if (comp.code in expandedTeamLeagues) {
                                        expandedTeamLeagues - comp.code
                                    } else {
                                        expandedTeamLeagues + comp.code
                                    }
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${comp.zhName} · ${comp.enName} (${teamsFromMatches.size} 队)",
                                style = MaterialTheme.typography.labelLarge,
                                color = AppleRed,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isExpanded) "收起 ▴" else "展开 ▾",
                                style = MaterialTheme.typography.labelMedium,
                                color = InkSecondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (isExpanded) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                teamsFromMatches.entries.forEach { (zh, en) ->
                                    val isFav = zh in state.prefs.favoriteTeams || en in state.prefs.favoriteTeams
                                    FilterChip(
                                        selected = isFav,
                                        onClick = {
                                            activeTeamName = zh
                                            onOpenTeamDetail(
                                                en,
                                                zh,
                                                comp.code,
                                                TeamAssetCatalog.resolveTeamLogo(en),
                                            )
                                        },
                                        label = {
                                            Text(if (en.equals(zh, true)) zh else "$zh ($en)", fontSize = 11.sp)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                                contentDescription = "关注或查看球队",
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .clickable { onToggleFavoriteTeam(zh) },
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

@Composable
private fun PlayerRankCard(
    player: StarPlayer,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onClickTeam: (enName: String, zhName: String, compCode: String, crest: String) -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (player.rank > 0) {
                Surface(color = AppleRedTint, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "No.${player.rank}",
                        color = AppleRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    )
                }
            }
            PlayerPortrait(url = player.portraitUrl, playerName = player.zhName, size = 52.dp)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BilingualTextColumn(
                        zhName = player.zhName,
                        enName = player.name,
                        zhFontSize = 16.sp,
                        enFontSize = 10.sp,
                        zhFontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (player.shirtNumber > 0) {
                        Text(
                            text = "#${player.shirtNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppleRed,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                val statsPart = if (player.goals > 0 || player.assists > 0) {
                    " · ⚽${player.goals}球 🎯${player.assists}助"
                } else {
                    ""
                }
                val compTag = if (player.competitionCode.isNotBlank()) {
                    "${CompetitionCatalog.zhNameOrFallback(player.competitionCode, player.competitionCode)} · "
                } else {
                    ""
                }
                Text(
                    text = "$compTag${player.positionZh}$statsPart",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (player.clubZhName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onClickTeam(player.clubName, player.clubZhName, player.competitionCode, player.clubCrestUrl)
                                }
                                .padding(vertical = 2.dp, horizontal = 2.dp),
                        ) {
                            TeamCrest(url = player.clubCrestUrl, teamName = player.clubZhName, size = 18.dp)
                            Text(
                                text = if (player.clubName.isNotBlank() && !player.clubName.equals(player.clubZhName, true)) {
                                    "${player.clubZhName} (${player.clubName})"
                                } else {
                                    player.clubZhName
                                },
                                fontSize = 11.sp,
                                color = AppleRed,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (player.nationalZhName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onClickTeam(player.nationalName, player.nationalZhName, "INT", player.nationalCrestUrl)
                                }
                                .padding(vertical = 2.dp, horizontal = 2.dp),
                        ) {
                            TeamCrest(url = player.nationalCrestUrl, teamName = player.nationalZhName, size = 16.dp)
                            Text(
                                text = player.nationalZhName,
                                fontSize = 11.sp,
                                color = InkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "关注球员",
                    tint = if (isFavorite) AppleRed else InkTertiary,
                )
            }
        }
    }
}

@Composable
private fun TeamProfileSummaryCard(
    teamName: String,
    teamDetail: TeamDetailInfo?,
    standing: StandingEntry?,
    teamMatches: List<MatchItem>,
    onOpenTeam: () -> Unit,
    onOpenMatch: (MatchItem) -> Unit,
) {
    val logoUrl = teamDetail?.crest?.ifBlank { null }
        ?: standing?.teamCrest?.ifBlank { null }
        ?: TeamAssetCatalog.resolveTeamLogo(teamName)
    val enName = teamDetail?.name ?: standing?.teamName ?: ""

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenTeam)
                    .padding(vertical = 2.dp),
            ) {
                TeamCrest(url = logoUrl, teamName = teamName, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    BilingualTextColumn(
                        zhName = teamName,
                        enName = enName,
                        zhFontSize = 17.sp,
                        enFontSize = 11.sp,
                        zhFontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (standing != null) {
                            "${CompetitionCatalog.zhNameOrFallback(standing.competitionCode, standing.competitionCode)} No.${standing.rank} · ${standing.points} 分 · 点击看全队档案与战绩 ›"
                        } else {
                            "共关联 ${teamMatches.size} 场赛程 · 点击看全队档案与历史战绩 ›"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleRed,
                    )
                }
                if (standing != null) {
                    Surface(color = AppleRedTint, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = "No.${standing.rank}",
                            color = AppleRed,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            if (teamDetail != null && (teamDetail.coachZhName.isNotBlank() || teamDetail.venue.isNotBlank())) {
                Text(
                    text = listOfNotNull(
                        teamDetail.coachZhName.takeIf { it.isNotBlank() }?.let { "主帅: $it (${teamDetail.coachName})" },
                        teamDetail.venue.takeIf { it.isNotBlank() }?.let { "主场: $it" },
                        teamDetail.squad.size.takeIf { it > 0 }?.let { "阵容: ${it}人" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary,
                )
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TeamCrest(url = m.homeTeamCrest, teamName = m.homeTeamZhName, size = 22.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${m.homeTeamZhName} (${m.homeTeamName}) vs ${m.awayTeamZhName} (${m.awayTeamName})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${m.competitionZhName} · 北京时间 ${MatchTimeFormatter.formatMonthDayTime(m.utcDate)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkSecondary,
                            )
                        }
                        TeamCrest(url = m.awayTeamCrest, teamName = m.awayTeamZhName, size = 22.dp)
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
fun PlayerDetailSheet(
    player: StarPlayer,
    isFavorite: Boolean,
    upcomingMatches: List<MatchItem>,
    isLoadingHistory: Boolean = false,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onCloseAll: () -> Unit = onNavigateBack,
    onToggleFavorite: () -> Unit,
    onClickTeam: (enName: String, zhName: String, compCode: String, crest: String) -> Unit = { _, _, _, _ -> },
    onClickMatch: (MatchItem) -> Unit,
) {
    // 0 = Player overview, 1 = Full Upcoming Schedule list, 2 = Full Historical Matches list
    var subPageMode by remember(player.id) { mutableIntStateOf(0) }
    var teamScopeFilter by remember(player.id, subPageMode) { mutableIntStateOf(0) } // 0=全部, 1=俱乐部, 2=国家队

    val upcomingList = remember(upcomingMatches) {
        upcomingMatches
            .filter { it.status != MatchStatus.FINISHED && !it.status.isCancelledOrPostponed && !(it.homeScore != null && it.awayScore != null && !it.status.isLive) }
            .sortedBy { it.utcDate }
    }
    val finishedList = remember(upcomingMatches) {
        upcomingMatches
            .filter { it.status == MatchStatus.FINISHED || (it.homeScore != null && it.awayScore != null && !it.status.isLive) }
            .sortedByDescending { it.utcDate }
    }
    val resolvedBio = remember(player.id, player.bio) {
        player.bio.ifBlank { TeamAssetCatalog.buildStructuredPlayerBio(player) }
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
                            subPageMode != 0 -> subPageMode = 0
                            canNavigateBack -> onNavigateBack()
                            else -> onCloseAll()
                        }
                    },
                    color = AppleCanvas,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = when {
                            subPageMode != 0 -> "‹ 返回球员档案"
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
                    text = when (subPageMode) {
                        1 -> "${player.zhName} · 即将赛程 (${upcomingList.size})"
                        2 -> "${player.zhName} · 历史战绩 (${finishedList.size})"
                        else -> "${player.zhName} (${player.name})"
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

            if (subPageMode != 0) {
                val baseList = if (subPageMode == 1) upcomingList else finishedList
                val filteredSubList = remember(baseList, teamScopeFilter, player.clubZhName, player.clubName, player.nationalZhName, player.nationalName) {
                    when (teamScopeFilter) {
                        1 -> baseList.filter { m ->
                            (player.clubZhName.isNotBlank() && m.involvesTeam(player.clubZhName)) ||
                                (player.clubName.isNotBlank() && m.involvesTeam(player.clubName))
                        }
                        2 -> baseList.filter { m ->
                            (player.nationalZhName.isNotBlank() && m.involvesTeam(player.nationalZhName)) ||
                                (player.nationalName.isNotBlank() && m.involvesTeam(player.nationalName))
                        }
                        else -> baseList
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = teamScopeFilter == 0, onClick = { teamScopeFilter = 0 }, label = { Text("全部") })
                            if (player.clubZhName.isNotBlank()) {
                                FilterChip(selected = teamScopeFilter == 1, onClick = { teamScopeFilter = 1 }, label = { Text(player.clubZhName) })
                            }
                            if (player.nationalZhName.isNotBlank()) {
                                FilterChip(selected = teamScopeFilter == 2, onClick = { teamScopeFilter = 2 }, label = { Text(player.nationalZhName) })
                            }
                        }
                    }
                    if (filteredSubList.isEmpty()) {
                        item { Text("当前筛选条件下暂无赛事记录", style = MaterialTheme.typography.bodySmall, color = InkTertiary) }
                    } else {
                        itemsIndexed(filteredSubList, key = { idx, m -> "sub_${subPageMode}_${m.id}_$idx" }) { _, m ->
                            PlayerMatchRowCard(match = m, onClick = { onClickMatch(m) })
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PlayerPortrait(url = player.portraitUrl, playerName = player.zhName, size = 84.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (player.shirtNumber > 0) "${player.zhName} · #${player.shirtNumber}" else player.zhName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(player.name, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                                val statSuffix = if (player.goals > 0 || player.assists > 0) " · ⚽${player.goals} 🎯${player.assists}" else ""
                                Text(
                                    text = "${player.nationalityZh} · ${player.positionZh}$statSuffix",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppleRed,
                                    fontWeight = FontWeight.SemiBold,
                                )
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
                    }

                    // Universal Player Bio Card
                    item {
                        Surface(
                            color = AppleCanvas,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("球员简介", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = AppleRed)
                                Text(resolvedBio, style = MaterialTheme.typography.bodySmall, color = InkPrimary, lineHeight = 19.sp)
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (player.clubZhName.isNotBlank()) {
                                Surface(
                                    onClick = {
                                        onClickTeam(player.clubName, player.clubZhName, player.competitionCode, player.clubCrestUrl)
                                    },
                                    color = AppleCanvas,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TeamCrest(url = player.clubCrestUrl, teamName = player.clubZhName, size = 28.dp)
                                        BilingualTextColumn(zhName = player.clubZhName, enName = player.clubName, zhFontSize = 13.sp, enFontSize = 9.sp, modifier = Modifier.weight(1f))
                                        Text("›", color = AppleRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (player.nationalZhName.isNotBlank()) {
                                Surface(
                                    onClick = {
                                        onClickTeam(player.nationalName, player.nationalZhName, "INT", player.nationalCrestUrl)
                                    },
                                    color = AppleCanvas,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TeamCrest(url = player.nationalCrestUrl, teamName = player.nationalZhName, size = 28.dp)
                                        BilingualTextColumn(zhName = player.nationalZhName, enName = player.nationalName, zhFontSize = 13.sp, enFontSize = 9.sp, modifier = Modifier.weight(1f))
                                        Text("›", color = AppleRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Section 1: Upcoming Matches (compact 3 + tap to view full list)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("即将进行的赛程", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (upcomingList.isNotEmpty()) {
                                Text(
                                    text = "查看全部 (${upcomingList.size}) ›",
                                    color = AppleRed,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { subPageMode = 1 }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (upcomingList.isEmpty()) {
                        item {
                            Text("近期暂无已排期即将赛程，可点击上方俱乐部查看详情", style = MaterialTheme.typography.bodySmall, color = InkTertiary)
                        }
                    } else {
                        itemsIndexed(upcomingList.take(3), key = { idx, m -> "up_p_${m.id}_$idx" }) { _, m ->
                            PlayerMatchRowCard(match = m, onClick = { onClickMatch(m) })
                        }
                        if (upcomingList.size > 3) {
                            item {
                                OutlinedButton(
                                    onClick = { subPageMode = 1 },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("查看全部 ${upcomingList.size} 场即将赛程 ›", color = AppleRed, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Section 2: Historical Matches (compact 3 + tap to view full list)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("历史比赛记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (isLoadingHistory) {
                                    CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = AppleRed)
                                }
                            }
                            if (finishedList.isNotEmpty()) {
                                Text(
                                    text = "查看全部 (${finishedList.size}) ›",
                                    color = AppleRed,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { subPageMode = 2 }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (finishedList.isEmpty() && isLoadingHistory) {
                        item {
                            Text("正在拉取关联球队赛季历史战绩...", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                    } else if (finishedList.isEmpty()) {
                        item {
                            Text("暂无该球员关联球队近期已完赛记录", style = MaterialTheme.typography.bodySmall, color = InkTertiary)
                        }
                    } else {
                        itemsIndexed(finishedList.take(3), key = { idx, m -> "fin_p_${m.id}_$idx" }) { _, m ->
                            PlayerMatchRowCard(match = m, onClick = { onClickMatch(m) })
                        }
                        if (finishedList.size > 3) {
                            item {
                                OutlinedButton(
                                    onClick = { subPageMode = 2 },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("查看全部 ${finishedList.size} 场历史战绩与比赛明细 ›", color = AppleRed, fontWeight = FontWeight.SemiBold)
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
private fun PlayerMatchRowCard(
    match: MatchItem,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = AppleCanvas,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamCrest(url = match.homeTeamCrest, teamName = match.homeTeamZhName, size = 24.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${match.homeTeamZhName} (${match.homeTeamName}) ${if (match.homeScore != null && match.awayScore != null) "${match.homeScore}:${match.awayScore}" else "vs"} ${match.awayTeamZhName} (${match.awayTeamName})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "${match.competitionZhName} · 北京时间 ${MatchTimeFormatter.formatMonthDayTime(match.utcDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary,
                )
            }
            TeamCrest(url = match.awayTeamCrest, teamName = match.awayTeamZhName, size = 24.dp)
            Text(
                text = if (match.homeScore != null && match.awayScore != null) "详情 ›" else "查看 ›",
                color = AppleRed,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
