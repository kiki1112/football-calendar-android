package com.footballcalendar.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Semantic aliases backed exclusively by ThemeTokens so zero Color(0x...) literals exist outside
// ThemeTokens.kt and TeamColorRegistry.kt.
val AppleRed = ThemeTokens.AccentFallback
val AppleLiveRed = ThemeTokens.LivePulse
val AppleRedDeep = ThemeTokens.AccentDeep
val AppleRedTint = ThemeTokens.AccentSoftBg
val AppleRedBorder = ThemeTokens.AccentSoftBorder

val AppleCanvas = ThemeTokens.CanvasPearl
val AppleWhite = ThemeTokens.SurfacePure
val AppleSurfaceSubtle = ThemeTokens.SurfaceSubtle
val AppleDivider = ThemeTokens.Hairline

val InkPrimary = ThemeTokens.InkPrimary
val InkSecondary = ThemeTokens.InkSecondary
val InkTertiary = ThemeTokens.InkMuted

val AmberWarm = ThemeTokens.LotteryGold
val AmberTint = ThemeTokens.LotteryGoldBg
val UclBlue = ThemeTokens.AccentFallback

/**
 * Shared tabular-numeral TextStyle for scores, kickoffs, match clocks, standings metrics,
 * player leaderboards, and lottery deadlines ("tnum").
 */
val TabularNumTextStyle = TextStyle(fontFeatureSettings = "tnum")

private val DaylightPearlColorScheme = lightColorScheme(
    primary = ThemeTokens.AccentFallback,
    onPrimary = ThemeTokens.SurfacePure,
    primaryContainer = ThemeTokens.AccentSoftBg,
    onPrimaryContainer = ThemeTokens.AccentDeep,
    secondary = ThemeTokens.AccentFallback,
    onSecondary = ThemeTokens.SurfacePure,
    secondaryContainer = ThemeTokens.SurfaceSubtle,
    onSecondaryContainer = ThemeTokens.AccentFallback,
    tertiary = ThemeTokens.LotteryGold,
    onTertiary = ThemeTokens.SurfacePure,
    tertiaryContainer = ThemeTokens.LotteryGoldBg,
    onTertiaryContainer = ThemeTokens.LotteryGoldInk,
    background = ThemeTokens.CanvasPearl,
    onBackground = ThemeTokens.InkPrimary,
    surface = ThemeTokens.SurfacePure,
    onSurface = ThemeTokens.InkPrimary,
    surfaceVariant = ThemeTokens.SurfaceSubtle,
    onSurfaceVariant = ThemeTokens.InkSecondary,
    outline = ThemeTokens.Hairline,
    outlineVariant = ThemeTokens.Hairline,
    error = ThemeTokens.LivePulse,
    onError = ThemeTokens.SurfacePure,
)

private val SchemeCTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Black,
        fontFeatureSettings = "tnum",
        color = ThemeTokens.InkPrimary,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
        color = ThemeTokens.InkPrimary,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
        color = ThemeTokens.InkPrimary,
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = ThemeTokens.InkPrimary,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = ThemeTokens.InkPrimary,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
        color = ThemeTokens.InkMuted,
    ),
)

@Composable
fun FootballCalendarTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DaylightPearlColorScheme,
        typography = SchemeCTypography,
        content = content,
    )
}
