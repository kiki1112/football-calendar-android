package com.footballcalendar.app.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object MatchTimeFormatter {
    val BEIJING_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val monthDayTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
    private val dateKeyFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
    private val dateTitleFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

    fun parseInstant(utcIso: String): Instant? {
        return runCatching {
            Instant.parse(utcIso.trim())
        }.recoverCatching {
            OffsetDateTime.parse(utcIso.trim()).toInstant()
        }.getOrNull()
    }

    fun toLocalZonedDateTime(utcIso: String, zoneId: ZoneId = BEIJING_ZONE): ZonedDateTime? {
        return parseInstant(utcIso)?.atZone(zoneId)
    }

    /**
     * Maps a kickoff timestamp to the calendar bucket date in Beijing Time (UTC+8).
     * When [watchNightGrouping] is true, early-morning matches (00:00..05:59) are grouped
     * under the previous evening's "watch night" date so European late fixtures are easy to spot.
     */
    fun displayDateFor(
        utcIso: String,
        zoneId: ZoneId = BEIJING_ZONE,
        watchNightGrouping: Boolean = true,
    ): LocalDate? {
        val localDt = toLocalZonedDateTime(utcIso, zoneId) ?: return null
        return if (watchNightGrouping && localDt.hour in 0..5) {
            localDt.toLocalDate().minusDays(1)
        } else {
            localDt.toLocalDate()
        }
    }

    fun isEarlyMorning(utcIso: String, zoneId: ZoneId = BEIJING_ZONE): Boolean {
        val localDt = toLocalZonedDateTime(utcIso, zoneId) ?: return false
        return localDt.hour in 0..5
    }

    fun formatKickoffTime(utcIso: String, zoneId: ZoneId = BEIJING_ZONE): String {
        val localDt = toLocalZonedDateTime(utcIso, zoneId) ?: return "--:--"
        return timeFormatter.format(localDt)
    }

    fun formatMonthDayTime(utcIso: String, zoneId: ZoneId = BEIJING_ZONE): String {
        val localDt = toLocalZonedDateTime(utcIso, zoneId) ?: return utcIso
        return monthDayTimeFormatter.format(localDt)
    }

    fun formatMonthDayTime(instant: Instant, zoneId: ZoneId = BEIJING_ZONE): String =
        monthDayTimeFormatter.format(instant.atZone(zoneId))

    fun dateKeyFor(
        utcIso: String,
        zoneId: ZoneId = BEIJING_ZONE,
        watchNightGrouping: Boolean = true,
    ): String {
        val d = displayDateFor(utcIso, zoneId, watchNightGrouping) ?: return ""
        return dateKeyFormatter.format(d)
    }

    fun formatYearMonth(date: LocalDate): String = yearMonthFormatter.format(date)

    fun formatDateTitle(date: LocalDate): String = dateTitleFormatter.format(date)

    fun formatRelativeCountdown(
        utcIso: String,
        now: Instant = Instant.now(),
        zoneId: ZoneId = BEIJING_ZONE,
    ): String {
        val kickoff = parseInstant(utcIso) ?: return ""
        val diff = Duration.between(now, kickoff)
        val minutes = diff.toMinutes()
        return when {
            minutes <= 0 -> "待开球"
            minutes < 60 -> "${minutes}分钟后开球"
            minutes < 24 * 60 -> "${minutes / 60}小时${minutes % 60}分后"
            else -> formatMonthDayTime(utcIso, zoneId)
        }
    }
}
