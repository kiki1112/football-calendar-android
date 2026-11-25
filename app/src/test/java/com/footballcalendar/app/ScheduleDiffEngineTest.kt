package com.footballcalendar.app

import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.ScheduleChangeType
import com.footballcalendar.app.util.ScheduleDiffEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ScheduleDiffEngineTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun sampleMatch(
        id: String = "m-1",
        utcDate: String = "2026-11-26T19:00:00Z",
        status: MatchStatus = MatchStatus.TIMED,
        homeScore: Int? = null,
        awayScore: Int? = null,
    ) = MatchItem(
        id = id,
        competitionCode = "PL",
        competitionName = "Premier League",
        competitionZhName = "英超",
        matchday = 12,
        stage = "REGULAR_SEASON",
        utcDate = utcDate,
        status = status,
        homeTeamId = 1,
        homeTeamName = "Arsenal FC",
        homeTeamShortName = "Arsenal",
        homeTeamZhName = "阿森纳",
        awayTeamId = 2,
        awayTeamName = "Chelsea FC",
        awayTeamShortName = "Chelsea",
        awayTeamZhName = "切尔西",
        homeScore = homeScore,
        awayScore = awayScore,
        venue = "Emirates Stadium",
        lastUpdatedUtc = "2026-11-25T10:00:00Z",
    )

    @Test
    fun initialSeedProducesNoChangeAlerts() {
        val result = ScheduleDiffEngine.diff(
            existing = emptyList(),
            incoming = listOf(sampleMatch()),
            zoneId = zone,
        )
        assertEquals(1, result.mergedMatches.size)
        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun detectsReschedulePostponeScoreUpdateAndDropsStaleScheduledGhosts() {
        val existing = listOf(
            sampleMatch(id = "m-1", utcDate = "2026-11-26T19:00:00Z"),
            sampleMatch(id = "m-2", status = MatchStatus.TIMED),
            sampleMatch(id = "m-3", status = MatchStatus.TIMED),
            sampleMatch(id = "demo-ghost", utcDate = "2026-11-25T17:30:00Z", status = MatchStatus.TIMED),
            sampleMatch(id = "m-old", utcDate = "2026-11-20T19:00:00Z", status = MatchStatus.FINISHED),
        )
        val incoming = listOf(
            sampleMatch(id = "m-1", utcDate = "2026-11-26T21:00:00Z"),
            sampleMatch(id = "m-2", status = MatchStatus.POSTPONED),
            sampleMatch(id = "m-3", status = MatchStatus.FINISHED, homeScore = 2, awayScore = 0),
            sampleMatch(id = "m-4", utcDate = "2026-11-28T15:00:00Z"),
        )

        val result = ScheduleDiffEngine.diff(existing, incoming, zone)
        val types = result.changes.associate { it.match.id to it.type }

        assertEquals(ScheduleChangeType.RESCHEDULED, types["m-1"])
        assertEquals(ScheduleChangeType.POSTPONED, types["m-2"])
        assertEquals(ScheduleChangeType.SCORE_UPDATED, types["m-3"])
        assertEquals(ScheduleChangeType.ADDED, types["m-4"])
        // Historical FINISHED m-old preserved; stale unplayed demo-ghost dropped
        assertEquals(5, result.mergedMatches.size)
        assertTrue(result.mergedMatches.none { it.id == "demo-ghost" })
    }
}
