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
    val AccentDeep = Color(0xFF1E3A8A)
    val AccentSoftBg = Color(0xFFEFF6FF)
    val AccentSoftBorder = Color(0xFFBFDBFE)

    val LivePulse = Color(0xFFE11D48)
    val LivePulseDeep = Color(0xFFBE123C)
    val LivePulseBg = Color(0xFFFFF1F2)
    val LivePulseBorder = Color(0xFFFFE4E6)

    val LotteryGold = Color(0xFFB45309)
    val LotteryGoldBg = Color(0xFFFEF3C7)
    val LotteryGoldBorder = Color(0xFFFDE68A)
    val LotteryGoldInk = Color(0xFF78350F)

    val SuccessEmerald = Color(0xFF059669)
    val SuccessEmeraldBg = Color(0xFFECFDF5)
    val SuccessPitchGreen = Color(0xFF16A34A)

    val PureWhite = Color(0xFFFFFFFF)
    val WhiteRingSoft = Color(0x59FFFFFF)
    val PitchBadgeScrim = Color(0xCC1C1C1E)
    val PitchNameScrim = Color(0xB3111827)

    // Scheme C Liquid Glass & Navigation Tokens
    val GlassBackground = Color(0xB8FFFFFF)
    val GlassBlurBackground = Color(0xFFF8F9FB)
    val GlassFallbackScrim = Color(0xECFFFFFF)
    val GlassTrack = Color(0xFFE2E8F0)
    val GlassHighlightTop = Color(0xD9FFFFFF)
    val GlassHighlightBottom = Color(0x33FFFFFF)
    val GlassShadow = Color(0x160A0D12)
    val ModalScrim = Color(0x4A0A0D12)
    val SwitchUncheckedTrack = Color(0xFFE2E8F0)

    // Pitch & Standings Semantic Tokens
    val PitchGrass = Color(0xFF2E8D3F)
    val PitchGrassStripe = Color(0xFF359A46)
    val ZonePurple = Color(0xFF7C3AED)
    val ZoneOrange = Color(0xFFEA580C)
    val ZoneCyan = Color(0xFF0284C7)
    val ZoneUclCon = Color(0xFF10B981)
    val TableHeaderBg = Color(0xFFFAFAFC)
    val TableGroupBg = Color(0xFFF3F4F6)

    const val GlassBlurRadiusDp = 24f
    const val SpringDamping = 0.78f
    const val SpringStiffness = 400f

    val RadiusCard = 20.dp
    val RadiusCapsule = 999.dp
    val RadiusBadge = 8.dp
    val RadiusSheetTop = 28.dp
}
