package com.footballcalendar.app

import com.footballcalendar.app.data.remote.GitHubPagesFixtureDataSource
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.util.IcsExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class IcsAndUrlTest {
    @Test
    fun normalizesGitHubPagesUrlsConsistently() {
        assertEquals(
            "https://alice.github.io/fc/fixtures.json",
            GitHubPagesFixtureDataSource.normalizeFeedUrl("https://alice.github.io/fc"),
        )
        assertEquals(
            "https://alice.github.io/fc/fixtures.json",
            GitHubPagesFixtureDataSource.normalizeFeedUrl("https://alice.github.io/fc/"),
        )
        assertEquals(
            "https://alice.github.io/fc/fixtures.json",
            GitHubPagesFixtureDataSource.normalizeFeedUrl("https://alice.github.io/fc/index.html"),
        )
        assertEquals(
            "https://alice.github.io/fc/fixtures.json",
            GitHubPagesFixtureDataSource.normalizeFeedUrl("https://alice.github.io/fc/fixtures.json"),
        )
    }

    @Test
    fun exportsRfc5545CalendarWithCancelledStatusForPostponed() {
        val match = MatchItem(
            id = "m-10",
            competitionCode = "CL",
            competitionName = "UEFA Champions League",
            competitionZhName = "欧冠",
            matchday = 5,
            stage = "LEAGUE_PHASE",
            utcDate = "2026-11-26T20:00:00Z",
            status = MatchStatus.POSTPONED,
            homeTeamId = 1,
            homeTeamName = "Real Madrid CF",
            homeTeamShortName = "Real Madrid",
            homeTeamZhName = "皇家马德里",
            awayTeamId = 2,
            awayTeamName = "FC Bayern München",
            awayTeamShortName = "Bayern",
            awayTeamZhName = "拜仁慕尼黑",
            homeScore = null,
            awayScore = null,
            venue = "Santiago Bernabéu",
            lastUpdatedUtc = "2026-11-25T10:00:00Z",
        )

        val ics = IcsExporter.buildIcsContent(
            matches = listOf(match),
            generatedAt = Instant.parse("2026-11-25T12:00:00Z"),
        )

        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("UID:m-10@football-calendar"))
        assertTrue(ics.contains("DTSTART:20261126T200000Z"))
        assertTrue(ics.contains("DTEND:20261126T215000Z"))
        assertTrue(ics.contains("STATUS:CANCELLED"))
        assertTrue(ics.contains("SUMMARY:[欧冠] 皇家马德里 vs 拜仁慕尼黑"))
        assertTrue(ics.contains("END:VCALENDAR"))
    }
}
