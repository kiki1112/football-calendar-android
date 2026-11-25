package com.footballcalendar.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.ui.CalendarUiState
import com.footballcalendar.app.ui.components.TeamCrest
import com.footballcalendar.app.ui.theme.AmberWarm
import com.footballcalendar.app.ui.theme.AppleCanvas
import com.footballcalendar.app.ui.theme.AppleDivider
import com.footballcalendar.app.ui.theme.AppleRed
import com.footballcalendar.app.ui.theme.AppleRedTint
import com.footballcalendar.app.ui.theme.InkPrimary
import com.footballcalendar.app.ui.theme.InkSecondary
import com.footballcalendar.app.ui.theme.InkTertiary
import com.footballcalendar.app.ui.theme.UclBlue

@Composable
fun StandingsScreen(
    state: CalendarUiState,
    onSelectLeague: (String) -> Unit,
) {
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
                    text = "2026/27 赛季 · 自动同步更新",
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
                CompetitionCatalog.standingsCodes.forEach { code ->
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFAFAFC))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("#", modifier = Modifier.width(26.dp), style = MaterialTheme.typography.labelSmall, color = InkTertiary, fontWeight = FontWeight.Bold)
                        Text("球队", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = InkTertiary, fontWeight = FontWeight.Bold)
                        Text("赛", modifier = Modifier.width(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = InkTertiary, fontWeight = FontWeight.Bold)
                        Text("净胜", modifier = Modifier.width(38.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = InkTertiary, fontWeight = FontWeight.Bold)
                        Text("近5场", modifier = Modifier.width(52.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = InkTertiary, fontWeight = FontWeight.Bold)
                        Text("积分", modifier = Modifier.width(34.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = InkTertiary, fontWeight = FontWeight.Bold)
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
                                text = "暂无该联赛积分数据，可在主页下拉同步刷新",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary,
                            )
                        }
                    } else {
                        rows.forEachIndexed { idx, entry ->
                            val isFav = entry.teamZhName in state.prefs.favoriteTeams || entry.teamName in state.prefs.favoriteTeams
                            StandingRowItem(entry = entry, isFavorite = isFav)
                            if (idx < rows.lastIndex) {
                                HorizontalDivider(color = AppleCanvas)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LegendDot(color = UclBlue, label = "欧冠席位 (1-4)")
                LegendDot(color = AmberWarm, label = "欧战区 (5-6)")
                LegendDot(color = AppleRed, label = "胜 / 关注主队")
            }
        }
    }
}

@Composable
private fun StandingRowItem(entry: StandingEntry, isFavorite: Boolean) {
    val zoneBarColor = when {
        entry.rank <= 4 -> UclBlue
        entry.rank <= 6 -> AmberWarm
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isFavorite) AppleRedTint.copy(alpha = 0.72f) else Color.White)
            .padding(end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(24.dp)
                .background(if (isFavorite) AppleRed else zoneBarColor),
        )
        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = entry.rank.toString(),
            modifier = Modifier.width(24.dp),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = if (isFavorite) AppleRed else InkPrimary,
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TeamCrest(url = entry.teamCrest, teamName = entry.teamZhName, size = 24.dp)
            Text(
                text = if (isFavorite) "${entry.teamZhName} ★" else entry.teamZhName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isFavorite) FontWeight.Bold else FontWeight.SemiBold,
                color = InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = entry.played.toString(),
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum"),
            color = InkSecondary,
        )
        Text(
            text = if (entry.goalDifference > 0) "+${entry.goalDifference}" else entry.goalDifference.toString(),
            modifier = Modifier.width(38.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum"),
            color = InkSecondary,
        )

        Row(
            modifier = Modifier.width(52.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tokens = entry.form.split(",").filter { it.isNotBlank() }.takeLast(5)
            tokens.forEach { res ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 1.5.dp)
                        .size(6.dp)
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

        Text(
            text = entry.points.toString(),
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum"),
            color = if (isFavorite) AppleRed else InkPrimary,
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
