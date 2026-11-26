package com.footballcalendar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.footballcalendar.app.FootballCalendarApplication
import com.footballcalendar.app.ui.theme.AppleDivider
import com.footballcalendar.app.ui.theme.AppleRed
import com.footballcalendar.app.ui.theme.AppleRedTint
import com.footballcalendar.app.ui.theme.InkPrimary
import com.footballcalendar.app.ui.theme.InkSecondary
import com.footballcalendar.app.ui.theme.InkTertiary

@Composable
fun BilingualTextColumn(
    zhName: String,
    enName: String,
    modifier: Modifier = Modifier,
    zhColor: Color = InkPrimary,
    enColor: Color = InkTertiary,
    zhFontSize: TextUnit = 15.sp,
    enFontSize: TextUnit = 10.sp,
    zhFontWeight: FontWeight = FontWeight.Bold,
    alignment: Alignment.Horizontal = Alignment.Start,
    textAlign: TextAlign = TextAlign.Start,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = alignment,
    ) {
        Text(
            text = zhName.ifBlank { enName },
            color = zhColor,
            fontSize = zhFontSize,
            fontWeight = zhFontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
        if (enName.isNotBlank()) {
            Text(
                text = enName,
                color = enColor,
                fontSize = enFontSize,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                lineHeight = (enFontSize.value + 2f).sp,
            )
        }
    }
}

@Composable
fun TeamCrest(
    url: String,
    teamName: String,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        (context.applicationContext as FootballCalendarApplication).container.imageLoader
    }
    var loadFailed by remember(url) { mutableStateOf(url.isBlank()) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (loadFailed) AppleRedTint else Color.White)
            .border(0.75.dp, AppleDivider, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!loadFailed && url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = teamName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size * 0.78f),
                onError = { loadFailed = true },
            )
        } else {
            val initial = teamName.firstOrNull()?.toString() ?: "?"
            Text(
                text = initial,
                color = AppleRed,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.40f).sp,
            )
        }
    }
}

@Composable
fun PlayerPortrait(
    url: String,
    playerName: String,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        (context.applicationContext as FootballCalendarApplication).container.imageLoader
    }
    var loadFailed by remember(url) { mutableStateOf(url.isBlank()) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(AppleRedTint)
            .border(1.5.dp, AppleRed.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!loadFailed && url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = playerName,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.size(size),
                onError = { loadFailed = true },
            )
        } else {
            val initial = playerName.firstOrNull()?.toString() ?: "★"
            Text(
                text = initial,
                color = AppleRed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.38f).sp,
            )
        }
    }
}

@Composable
fun AppleSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFE5E5EA),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(index) },
                    color = if (selected) Color.White else Color.Transparent,
                    shadowElevation = if (selected) 1.dp else 0.dp,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) InkPrimary else InkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
