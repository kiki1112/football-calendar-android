package com.footballcalendar.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Single Source of Truth for Design Tokens defined in DESIGN_SYSTEM.md.
 * All new UI code must reference tokens from ThemeTokens or TeamColorRegistry
 * rather than inline Color(0x...) literals.
 */
object ThemeTokens {
    val CanvasPearl = Color(0xFFF8F9FB)
    val SurfacePure = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFF1F5F9)

    val InkPrimary = Color(0xFF0A0D12)
    val InkSecondary = Color(0xFF475569)
    val InkMuted = Color(0xFF64748B)
    val Hairline = Color(0xFFE2E8F0)

    val AccentFallback = Color(0xFF1D4ED8)
    val LivePulse = Color(0xFFE11D48)
    val LotteryGold = Color(0xFFB45309)
    val LotteryGoldBg = Color(0xFFFEF3C7)
    val SuccessEmerald = Color(0xFF059669)

    val PureWhite = Color(0xFFFFFFFF)
    val WhiteRingSoft = Color(0x59FFFFFF)
    val PitchBadgeScrim = Color(0xCC1C1C1E)
    val PitchNameScrim = Color(0xB3111827)

    val RadiusCard = 20.dp
    val RadiusCapsule = 999.dp
    val RadiusBadge = 8.dp
    val RadiusSheetTop = 28.dp
}
