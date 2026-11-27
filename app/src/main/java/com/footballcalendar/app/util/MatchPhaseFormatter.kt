package com.footballcalendar.app.util

import com.footballcalendar.app.model.MatchPhase
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class MatchPhaseLabels(
    val topBadgeText: String,
    val bottomEntryText: String,
    val detailHeaderBadgeText: String,
)

/**
 * Single presentation mapper for [MatchPhase] so the calendar card top-right badge,
 * card bottom-right entry label, and detail sheet header badge never contradict each other.
 */
object MatchPhaseFormatter {
    fun formatLabels(
        phase: MatchPhase,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = MatchTimeFormatter.BEIJING_ZONE,
    ): MatchPhaseLabels {
        return when (phase) {
            is MatchPhase.Upcoming -> {
                val bottom = formatUpcomingBottomText(phase.kickoffUtcMillis, nowMillis, zoneId)
                MatchPhaseLabels(
                    topBadgeText = "未开赛",
                    bottomEntryText = bottom,
                    detailHeaderBadgeText = "未开赛",
                )
            }
            is MatchPhase.Live -> {
                val minSuffix = formatLiveMinuteSuffix(phase.elapsedMinute, phase.extraMinute)
                MatchPhaseLabels(
                    topBadgeText = if (minSuffix.isNotEmpty()) "● LIVE $minSuffix" else "● 进行中",
                    bottomEntryText = if (minSuffix.isNotEmpty()) "进行中 $minSuffix · 实时赛况 ›" else "进行中 · 实时赛况 ›",
                    detailHeaderBadgeText = if (minSuffix.isNotEmpty()) "进行中 $minSuffix" else "进行中",
                )
            }
            MatchPhase.HalfTime -> MatchPhaseLabels(
                topBadgeText = "中场休息",
                bottomEntryText = "中场休息 · 实时赛况 ›",
                detailHeaderBadgeText = "中场休息",
            )
            is MatchPhase.Finished -> {
                val suffix = when {
                    phase.isPenalties -> " (点球)"
                    phase.isAfterExtraTime -> " (加时)"
                    else -> ""
                }
                MatchPhaseLabels(
                    topBadgeText = "已完赛$suffix",
                    bottomEntryText = "已完赛$suffix · 查看复盘 ›",
                    detailHeaderBadgeText = "已完赛$suffix",
                )
            }
            is MatchPhase.Postponed -> {
                val label = phase.reason?.trim()?.takeIf { it.isNotEmpty() } ?: "已改期"
                MatchPhaseLabels(
                    topBadgeText = label,
                    bottomEntryText = "$label · 查看详情 ›",
                    detailHeaderBadgeText = label,
                )
            }
        }
    }

    fun formatHeroCountdown(
        phase: MatchPhase,
        utcDate: String,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = MatchTimeFormatter.BEIJING_ZONE,
    ): String {
        return when (phase) {
            is MatchPhase.Live -> {
                val min = formatLiveMinuteSuffix(phase.elapsedMinute, phase.extraMinute)
                if (min.isNotEmpty()) "进行中 $min" else "进行中"
            }
            MatchPhase.HalfTime -> "中场休息"
            is MatchPhase.Finished -> "已完赛"
            is MatchPhase.Postponed -> phase.reason?.takeIf { it.isNotBlank() } ?: "已改期"
            is MatchPhase.Upcoming -> {
                if (phase.kickoffUtcMillis <= 0L) {
                    "--:--:--"
                } else {
                    val sec = Duration.between(
                        Instant.ofEpochMilli(nowMillis),
                        Instant.ofEpochMilli(phase.kickoffUtcMillis),
                    ).seconds
                    when {
                        sec <= 0L -> "未开赛 · 待开球"
                        sec < 86_400L -> {
                            val h = sec / 3600
                            val m = (sec % 3600) / 60
                            val s = sec % 60
                            "%02d:%02d:%02d".format(h, m, s)
                        }
                        else -> MatchTimeFormatter.formatMonthDayTime(utcDate, zoneId)
                    }
                }
            }
        }
    }

    private fun formatUpcomingBottomText(
        kickoffUtcMillis: Long,
        nowMillis: Long,
        zoneId: ZoneId,
    ): String {
        if (kickoffUtcMillis <= 0L) return "未开赛 · 待定 ›"
        val minutes = Duration.between(
            Instant.ofEpochMilli(nowMillis),
            Instant.ofEpochMilli(kickoffUtcMillis),
        ).toMinutes()
        return when {
            minutes <= 0L -> "未开赛 · 待开球 ›"
            minutes < 60L -> "未开赛 · ${minutes}分钟后开球 ›"
            minutes < 24L * 60L -> "未开赛 · ${minutes / 60}小时${minutes % 60}分后 ›"
            else -> "未开赛 · ${MatchTimeFormatter.formatMonthDayTime(Instant.ofEpochMilli(kickoffUtcMillis), zoneId)} ›"
        }
    }

    private fun formatLiveMinuteSuffix(elapsedMinute: Int, extraMinute: Int?): String {
        if (elapsedMinute <= 0) return ""
        return if (extraMinute != null && extraMinute > 0) {
            "${elapsedMinute}+${extraMinute}'"
        } else {
            "${elapsedMinute}'"
        }
    }
}
