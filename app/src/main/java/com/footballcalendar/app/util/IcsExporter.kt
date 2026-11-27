package com.footballcalendar.app.util

import com.footballcalendar.app.model.MatchItem
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object IcsExporter {
    private val icsTimestampFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun buildIcsContent(
        matches: List<MatchItem>,
        calendarName: String = "足球赛事日历",
        generatedAt: Instant = Instant.now(),
    ): String {
        val dtStamp = icsTimestampFormatter.format(generatedAt)
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//FootballCalendar//Android App//ZH\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")
        sb.append("X-WR-CALNAME:${escape(calendarName)}\r\n")

        for (m in matches) {
            val start = MatchTimeFormatter.parseInstant(m.utcDate) ?: continue
            val end = start.plusSeconds(110 * 60)
            val scoreSuffix = if (m.homeScore != null && m.awayScore != null) {
                " (${m.homeScore}-${m.awayScore})"
            } else {
                ""
            }
            val summary = "[${m.competitionZhName}] ${m.homeTeamZhName} vs ${m.awayTeamZhName}$scoreSuffix"
            val phaseLabel = MatchPhaseFormatter.formatLabels(m.phase).topBadgeText
            val description = "赛事: ${m.competitionZhName} (${m.competitionName})\\n状态: $phaseLabel"
            val status = if (m.phase.isPostponed) "CANCELLED" else "CONFIRMED"

            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:${m.id}@football-calendar\r\n")
            sb.append("DTSTAMP:$dtStamp\r\n")
            sb.append("DTSTART:${icsTimestampFormatter.format(start)}\r\n")
            sb.append("DTEND:${icsTimestampFormatter.format(end)}\r\n")
            sb.append("SUMMARY:${escape(summary)}\r\n")
            sb.append("DESCRIPTION:$description\r\n")
            sb.append("STATUS:$status\r\n")
            if (m.venue.isNotBlank()) {
                sb.append("LOCATION:${escape(m.venue)}\r\n")
            }
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun escape(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace(",", "\\,")
            .replace(";", "\\;")
    }
}
