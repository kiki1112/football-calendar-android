package com.footballcalendar.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.footballcalendar.app.ui.theme.ThemeTokens

/**
 * Dynamic dual-team chroma halo header (~220.dp) used in Match Detail views.
 * Radiates home team color from top-left and away team color from top-right, then feathers
 * smoothly across the bottom 64.dp into [bottomSurfaceColor].
 */
@Composable
fun TeamGradientHeader(
    homeColor: Color,
    awayColor: Color,
    modifier: Modifier = Modifier,
    minHeight: Dp = 220.dp,
    bottomSurfaceColor: Color = ThemeTokens.SurfacePure,
    content: @Composable BoxScope.() -> Unit,
) {
    // Anchor light jerseys (e.g. white or bright yellow) with a subtle deep chroma mix so white
    // hero scores and captions maintain crisp contrast in Daylight Pearl mode.
    val anchoredHome = ensureHeroChromaContrast(homeColor)
    val anchoredAway = ensureHeroChromaContrast(awayColor)
    val midBlend = lerp(anchoredHome, anchoredAway, 0.5f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(minHeight)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(anchoredHome, midBlend, anchoredAway),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        homeColor.copy(alpha = 0.52f),
                        Color.Transparent,
                    ),
                    center = Offset(x = size.width * 0.14f, y = size.height * 0.16f),
                    radius = size.width * 0.65f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        awayColor.copy(alpha = 0.52f),
                        Color.Transparent,
                    ),
                    center = Offset(x = size.width * 0.86f, y = size.height * 0.16f),
                    radius = size.width * 0.65f,
                ),
            )
        }

        // Gentle atmospheric top contrast veil so white scores and crests stay sharp on vivid kits.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to ThemeTokens.InkPrimary.copy(alpha = 0.24f),
                        0.55f to ThemeTokens.InkPrimary.copy(alpha = 0.14f),
                        1.0f to Color.Transparent,
                    ),
                ),
        )

        // Bottom 64.dp smooth feather fade into pure white card body below.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            bottomSurfaceColor.copy(alpha = 0.55f),
                            bottomSurfaceColor,
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}

private fun ensureHeroChromaContrast(raw: Color): Color {
    val lum = raw.luminance()
    return when {
        lum > 0.52f -> lerp(raw, ThemeTokens.InkPrimary, 0.36f)
        lum > 0.36f -> lerp(raw, ThemeTokens.InkPrimary, 0.22f)
        else -> raw
    }
}
