package com.footballcalendar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PitchGreen = Color(0xFF22C55E)
val PitchGreenDark = Color(0xFF16A34A)
val PitchSurfaceDark = Color(0xFF0B1310)
val PitchCardDark = Color(0xFF13201B)
val PitchCardVariantDark = Color(0xFF1A2D25)
val PitchTextLight = Color(0xFFECF5F0)
val PitchMutedLight = Color(0xFF9AB3A7)
val AmberHighlight = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)

private val DarkColorScheme = darkColorScheme(
    primary = PitchGreen,
    onPrimary = Color(0xFF04220E),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = AmberHighlight,
    onSecondary = Color(0xFF281800),
    background = PitchSurfaceDark,
    onBackground = PitchTextLight,
    surface = PitchCardDark,
    onSurface = PitchTextLight,
    surfaceVariant = PitchCardVariantDark,
    onSurfaceVariant = PitchMutedLight,
    outline = Color(0xFF274236),
    error = DangerRed,
)

private val LightColorScheme = lightColorScheme(
    primary = PitchGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    background = Color(0xFFF5F8F6),
    onBackground = Color(0xFF0F1D16),
    surface = Color.White,
    onSurface = Color(0xFF0F1D16),
    surfaceVariant = Color(0xFFE9F1EC),
    onSurfaceVariant = Color(0xFF476154),
    outline = Color(0xFFC9DBD1),
    error = DangerRed,
)

@Composable
fun FootballCalendarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
