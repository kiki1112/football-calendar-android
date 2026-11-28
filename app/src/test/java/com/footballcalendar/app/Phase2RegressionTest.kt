package com.footballcalendar.app

import com.footballcalendar.app.model.DataSourceMode
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.ui.CalendarViewModel
import com.footballcalendar.app.ui.screens.formatLotteryBadgeText
import com.footballcalendar.app.ui.screens.formatLotteryDeadlineLabel
import com.footballcalendar.app.util.CoachResolver
import com.footballcalendar.app.util.MatchTimeFormatter
import com.footballcalendar.app.util.ScheduleDiffEngine
import com.footballcalendar.app.util.ZhEntityDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Phase2RegressionTest {
    private fun makeMatch(
        id: String,
        utcDate: String,
        lotteryCode: String? = null,
        lotteryHandicap: Int? = null,
        lotteryDeadlineUtc: Long? = null,
        isFinalized: Boolean = false,
    ): MatchItem = MatchItem(
        id = id,
        competitionCode = "PL",
        competitionName = "Premier League",
        competitionZhName = "英超",
        matchday = 12,
        stage = "REGULAR_SEASON",
        utcDate = utcDate,
        status = if (isFinalized) MatchStatus.FINISHED else MatchStatus.TIMED,
        homeTeamId = 66,
        homeTeamName = "Manchester United FC",
        homeTeamShortName = "Man Utd",
        homeTeamZhName = "曼联",
        awayTeamId = 402,
        awayTeamName = "Brentford FC",
        awayTeamShortName = "Brentford",
        awayTeamZhName = "布伦特福德",
        homeScore = null,
        awayScore = null,
        venue = "Old Trafford",
        lastUpdatedUtc = utcDate,
        lotteryCode = lotteryCode,
        lotteryHandicap = lotteryHandicap,
        lotteryDeadlineUtc = lotteryDeadlineUtc,
        isFinalized = isFinalized,
    )

    @Test
    fun dateSwitchRejectsStaleRoomSliceFromWrongDate() {
        val nov25Match = makeMatch(
            id = "m-nov25",
            utcDate = "2026-11-25T19:00:00Z", // 2026-11-26 03:00 BJT -> watch-night 2026-11-25
            lotteryCode = "周三002",
        )
        val nov27StaleMatch = makeMatch(
            id = "m-nov27",
            utcDate = "2026-11-26T23:00:00Z", // 2026-11-27 07:00 BJT -> 2026-11-27
            lotteryCode = "周五001",
        )

        val resolved = CalendarViewModel.resolveDayMatches(
            selectedDate = LocalDate.of(2026, 11, 25),
            lotteryFilterOnly = false,
            watchNightGrouping = true,
            roomDateKey = "2026-11-27", // Simulates stale Room IO flow emission during date switch
            roomLotteryOnly = false,
            roomWatchNightGrouping = true,
            roomMatches = listOf(nov27StaleMatch),
            fallbackGroupedMatches = listOf(nov25Match),
            matchAllowed = { true },
        )

        assertEquals(listOf("m-nov25"), resolved.map { it.id })
    }

    @Test
    fun lotteryFilterOnlyEmptyDayNeverFallsBackToAllMatches() {
        val nonLotteryNov25 = makeMatch(
            id = "m-non-lot",
            utcDate = "2026-11-25T12:00:00Z",
            lotteryCode = null,
        )
        val resolved = CalendarViewModel.resolveDayMatches(
            selectedDate = LocalDate.of(2026, 11, 25),
            lotteryFilterOnly = true,
            watchNightGrouping = true,
            roomDateKey = "2026-11-25",
            roomLotteryOnly = true,
            roomWatchNightGrouping = true,
            roomMatches = emptyList(),
            fallbackGroupedMatches = listOf(nonLotteryNov25),
            matchAllowed = { !it.lotteryCode.isNullOrBlank() },
        )
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun lotteryBadgeAndStopSaleClockFormatAsSpecified() {
        assertEquals("周六008 · 让球 -1", formatLotteryBadgeText("周六008", -1))
        assertEquals("周六009 · 让球 +1", formatLotteryBadgeText("周六009", 1))
        assertEquals("周六010 · 让球 0", formatLotteryBadgeText("周六010", 0))
        assertEquals("周六011", formatLotteryBadgeText("周六011", null))

        // 2026-11-28T14:50:00Z == 2026-11-28 22:50 Asia/Shanghai
        val deadlineMs = Instant.parse("2026-11-28T14:50:00Z").toEpochMilli()
        assertEquals("停售 22:50", formatLotteryDeadlineLabel(deadlineMs))
    }

    @Test
    fun legacyFootballDataApiModeMigratesCleanlyToCloudPagesMode() {
        assertEquals(DataSourceMode.GITHUB_PAGES, DataSourceMode.fromName("FOOTBALL_DATA_API"))
        assertEquals(DataSourceMode.GITHUB_PAGES, DataSourceMode.fromName("GITHUB_PAGES"))
        assertEquals(DataSourceMode.GITHUB_PAGES, DataSourceMode.fromName(null))
        assertEquals(DataSourceMode.DEMO, DataSourceMode.fromName("DEMO"))
    }

    @Test
    fun scheduleDiffPreservesLotteryTagsDuringTransientZeroLotterySync() {
        val existing = listOf(
            makeMatch(
                id = "m-1",
                utcDate = "2026-11-28T15:00:00Z",
                lotteryCode = "周六008",
                lotteryHandicap = -1,
                lotteryDeadlineUtc = 1795877400000L,
            ),
        )
        val transientEmptyLotteryIncoming = listOf(
            makeMatch(
                id = "m-1",
                utcDate = "2026-11-28T15:00:00Z",
                lotteryCode = null,
                lotteryHandicap = null,
                lotteryDeadlineUtc = null,
            ),
        )

        val preserved = ScheduleDiffEngine.diff(existing, transientEmptyLotteryIncoming, MatchTimeFormatter.BEIJING_ZONE)
        assertEquals("周六008", preserved.mergedMatches.single().lotteryCode)
        assertEquals(-1, preserved.mergedMatches.single().lotteryHandicap)
    }

    @Test
    fun staleHistoricalRealMadridCoachFixedEvenWithoutLiveDetails() {
        ZhEntityDictionary.ensureLoaded(null)
        val staleRm = TeamDetailInfo(
            teamId = 86,
            competitionCode = "PD",
            name = "Real Madrid CF",
            shortName = "Real Madrid",
            zhName = "皇家马德里",
            crest = "",
            venue = "Santiago Bernabéu",
            founded = 1902,
            clubColors = "White",
            coachName = "José Mourinho",
            coachZhName = "何塞·莫乌里恩霍",
            coachNationality = "Portugal",
            squad = emptyList(),
        )
        val fixed = CoachResolver.applyAuthoritativeCoach(
            team = staleRm,
            matches = emptyList(),
            detailsByMatchId = emptyMap(),
        )
        assertEquals("Álvaro Arbeloa", fixed.coachName)
        assertEquals("阿韦洛亚", fixed.coachZhName)
    }
}
