package com.footballcalendar.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppleRed = Color(0xFFE11D48)
val AppleLiveRed = Color(0xFFFF3B30)
val AppleRedDeep = Color(0xFFBE123C)
val AppleRedTint = Color(0xFFFFF1F2)
val AppleRedBorder = Color(0xFFFFE4E6)

val AppleCanvas = Color(0xFFF2F2F7)
val AppleWhite = Color(0xFFFFFFFF)
val AppleSurfaceSubtle = Color(0xFFF8F8FA)
val AppleDivider = Color(0xFFE5E5EA)

val InkPrimary = Color(0xFF1C1C1E)
val InkSecondary = Color(0xFF6E6E73)
val InkTertiary = Color(0xFF8E8E93)

val AmberWarm = Color(0xFFD97706)
val AmberTint = Color(0xFFFEF3C7)
val UclBlue = Color(0xFF2563EB)

private val AppleRedWhiteScheme = lightColorScheme(
    primary = AppleRed,
    onPrimary = AppleWhite,
    primaryContainer = AppleRedTint,
    onPrimaryContainer = AppleRedDeep,
    secondary = AmberWarm,
    onSecondary = AppleWhite,
    secondaryContainer = AmberTint,
    onSecondaryContainer = Color(0xFF78350F),
    background = AppleCanvas,
    onBackground = InkPrimary,
    surface = AppleWhite,
    onSurface = InkPrimary,
    surfaceVariant = AppleCanvas,
    onSurfaceVariant = InkSecondary,
    outline = AppleDivider,
    error = AppleLiveRed,
)

@Composable
fun FootballCalendarTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppleRedWhiteScheme,
        content = content,
    )
}
