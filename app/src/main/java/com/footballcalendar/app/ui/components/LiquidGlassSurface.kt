package com.footballcalendar.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.footballcalendar.app.ui.theme.ThemeTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * Shared [HazeState] provided by the root scaffold so floating Liquid Glass bars and pills
 * sample and blur live scrolled content underneath without tight coupling.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

fun <T> schemeCSpringSpec(): SpringSpec<T> = spring(
    dampingRatio = ThemeTokens.SpringDamping,
    stiffness = ThemeTokens.SpringStiffness,
)

/**
 * Scheme C "Liquid Glass" floating surface combining real-time Haze backdrop blur (24.dp),
 * Android < 12 graceful frosted scrim fallback, white translucency, and 1.dp specular highlight border.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
    shape: Shape = RoundedCornerShape(999.dp),
    blurRadius: Dp = ThemeTokens.GlassBlurRadiusDp.dp,
    tintColor: Color = ThemeTokens.GlassBackground,
    elevation: Dp = 10.dp,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val highlightStroke = remember {
        Brush.verticalGradient(
            colors = listOf(
                ThemeTokens.GlassHighlightTop,
                ThemeTokens.GlassHighlightBottom,
            ),
        )
    }

    val blurModifier = if (hazeState != null) {
        Modifier.hazeChild(
            state = hazeState,
            shape = shape,
            style = HazeStyle(
                backgroundColor = ThemeTokens.GlassBlurBackground,
                tint = HazeTint(tintColor),
                blurRadius = blurRadius,
                noiseFactor = 0.05f,
                fallbackTint = HazeTint(ThemeTokens.GlassFallbackScrim),
            ),
        )
    } else {
        Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    ThemeTokens.GlassFallbackScrim,
                    ThemeTokens.GlassBackground,
                ),
            ),
            shape = shape,
        )
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = ThemeTokens.GlassShadow,
                spotColor = ThemeTokens.GlassShadow,
            )
            .clip(shape)
            .then(blurModifier)
            .border(width = borderWidth, brush = highlightStroke, shape = shape),
        content = content,
    )
}

/**
 * Centered floating Liquid Glass segmented pill (e.g. "全部赛事 | 体彩焦点") with spring-damped
 * active thumb glide and micro-haptic confirmation.
 */
@Composable
fun LiquidGlassSegmentedPill(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
    accentColor: Color = ThemeTokens.AccentFallback,
) {
    if (options.isEmpty()) return
    val clampedIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val haptic = LocalHapticFeedback.current

    LiquidGlassSurface(
        modifier = modifier.height(42.dp),
        hazeState = hazeState,
        shape = RoundedCornerShape(999.dp),
        elevation = 8.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
        ) {
            val segmentWidth = maxWidth / options.size
            val thumbOffset by animateDpAsState(
                targetValue = segmentWidth * clampedIndex,
                animationSpec = schemeCSpringSpec(),
                label = "glassSegmentedThumbOffset",
            )

            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(ThemeTokens.SurfacePure)
                    .border(
                        width = 0.5.dp,
                        color = ThemeTokens.Hairline,
                        shape = RoundedCornerShape(999.dp),
                    ),
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEachIndexed { idx, label ->
                    val isSelected = idx == clampedIndex
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) accentColor else ThemeTokens.InkSecondary,
                        animationSpec = schemeCSpringSpec(),
                        label = "glassSegmentedLabelColor",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onSelect(idx)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
