package com.footballcalendar.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.ui.CalendarUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeamsScreen(
    state: CalendarUiState,
    onToggleCompetition: (String) -> Unit,
    onToggleFavoriteTeam: (String) -> Unit,
) {
    var customTeamInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = "关注联赛与主队",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "订阅联赛",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "勾选你需要自动同步与展示的赛事范围（至少保留 1 项）：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
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
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "我的主队（${state.prefs.favoriteTeams.size}）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "星标球队将在日历以琥珀色圆点标注，并优先收到改期/开赛提醒。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = customTeamInput,
                                onValueChange = { customTeamInput = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("添加球队名称（中文或英文）") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (customTeamInput.isNotBlank()) {
                                                onToggleFavoriteTeam(customTeamInput.trim())
                                                customTeamInput = ""
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "添加球队")
                                    }
                                },
                            )
                        }

                        if (state.prefs.favoriteTeams.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.prefs.favoriteTeams.sorted().forEach { team ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { onToggleFavoriteTeam(team) },
                                        label = { Text(team) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Star,
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

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "热门球队快速收藏",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        CompetitionCatalog.all.forEach { comp ->
                            Column {
                                Text(
                                    text = comp.zhName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    comp.popularTeams.forEach { team ->
                                        val isFav = team in state.prefs.favoriteTeams
                                        FilterChip(
                                            selected = isFav,
                                            onClick = { onToggleFavoriteTeam(team) },
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
}
