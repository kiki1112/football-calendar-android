package com.footballcalendar.app

import com.footballcalendar.app.data.local.MatchEntity
import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchPhase
import com.footballcalendar.app.model.MatchPhaseResolver
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.util.MatchPhaseFormatter
import com.footballcalendar.app.util.MatchTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MatchPhaseStateMachineTest {
    private val fixedNowMillis = Instant.parse("2026-11-27T20:00:00Z").toEpochMilli()

    @Test
    fun upcomingPastKickoffRemainsConsistentAcrossTopBottomAndHeader() {
        // Regression guard: kickoff is 2 hours in the past, yet authoritative feed status is still TIMED.
        // Card top-right ("未开赛"), bottom-right entry, and detail header MUST all derive from MatchPhase.Upcoming
        // and must NEVER split into "未开赛" + "进行窗口" / "已结束".
        val match = sampleMatch(
            utcDate = "2026-11-27T18:00:00Z",
            status = MatchStatus.TIMED,
        )
        assertTrue(match.phase is MatchPhase.Upcoming)

        val labels = MatchPhaseFormatter.formatLabels(
            phase = match.phase,
            nowMillis = fixedNowMillis,
            zoneId = MatchTimeFormatter.BEIJING_ZONE,
        )
        assertEquals("未开赛", labels.topBadgeText)
        assertEquals("未开赛", labels.detailHeaderBadgeText)
        assertTrue(labels.bottomEntryText.contains("待开球"))
        assertFalse(labels.bottomEntryText.contains("进行窗口"))
        assertFalse(labels.bottomEntryText.contains("已结束"))
        assertEquals("未开赛 · 待开球", MatchPhaseFormatter.formatHeroCountdown(match.phase, match.utcDate, fixedNowMillis))
    }

    @Test
    fun futureUpcomingDisplaysConsistentUpcomingLabelsAndCountdown() {
        val match = sampleMatch(
            utcDate = "2026-11-27T22:30:00Z",
            status = MatchStatus.SCHEDULED,
        )
        assertTrue(match.phase is MatchPhase.Upcoming)

        val labels = MatchPhaseFormatter.formatLabels(match.phase, fixedNowMillis, MatchTimeFormatter.BEIJING_ZONE)
        assertEquals("未开赛", labels.topBadgeText)
        assertEquals("未开赛", labels.detailHeaderBadgeText)
        assertEquals("未开赛 · 2小时30分后 ›", labels.bottomEntryText)
        assertEquals("02:30:00", MatchPhaseFormatter.formatHeroCountdown(match.phase, match.utcDate, fixedNowMillis))
    }

    @Test
    fun liveMatchDisplaysConsistentMinuteAcrossAllThreePositions() {
        val match = sampleMatch(
            utcDate = "2026-11-27T19:15:00Z",
            status = MatchStatus.LIVE,
            elapsedMinutes = 67,
        )
        val phase = match.phase
        assertTrue(phase is MatchPhase.Live)

        val labels = MatchPhaseFormatter.formatLabels(phase, fixedNowMillis, MatchTimeFormatter.BEIJING_ZONE)
        assertEquals("● LIVE 67'", labels.topBadgeText)
        assertEquals("进行中 67' · 实时赛况 ›", labels.bottomEntryText)
        assertEquals("进行中 67'", labels.detailHeaderBadgeText)
    }

    @Test
    fun halfTimeDisplaysConsistentPausedLabels() {
        val match = sampleMatch(
            utcDate = "2026-11-27T19:00:00Z",
            status = MatchStatus.PAUSED,
            elapsedMinutes = 45,
        )
        assertEquals(MatchPhase.HalfTime, match.phase)

        val labels = MatchPhaseFormatter.formatLabels(match.phase, fixedNowMillis, MatchTimeFormatter.BEIJING_ZONE)
        assertEquals("中场休息", labels.topBadgeText)
        assertEquals("中场休息 · 实时赛况 ›", labels.bottomEntryText)
        assertEquals("中场休息", labels.detailHeaderBadgeText)
    }

    @Test
    fun finishedDisplaysConsistentFinalLabelsIncludingPenaltiesAndExtraTime() {
        val ftMatch = sampleMatch(utcDate = "2026-11-27T16:00:00Z", status = MatchStatus.FINISHED)
        val ftLabels = MatchPhaseFormatter.formatLabels(ftMatch.phase, fixedNowMillis, MatchTimeFormatter.BEIJING_ZONE)
        assertEquals("已完赛", ftLabels.topBadgeText)
        assertEquals("已完赛 · 查看复盘 ›", ftLabels.bottomEntryText)
        assertEquals("已完赛", ftLabels.detailHeaderBadgeText)

        val penDetail = LiveMatchDetail(matchId = ftMatch.id, statusShort = "PEN", homeScore = 1, awayScore = 1)
        val penPhase = ftMatch.effectivePhase(penDetail)
        val penLabels = MatchPhaseFormatter.formatLabels(penPhase, fixedNowMillis, MatchTimeFormatter.BEIJING_ZONE)
        assertEquals("已完赛 (点球)", penLabels.topBadgeText)
        assertEquals("已完赛 (点球) · 查看复盘 ›", penLabels.bottomEntryText)
        assertEquals("已完赛 (点球)", penLabels.detailHeaderBadgeText)
    }

    @Test
    fun postponedCancelledSuspendedMapToPostponedConsistently() {
        val postponed = MatchPhaseResolver.resolve(MatchStatus.POSTPONED, "2026-11-28T15:00:00Z")
        val pLabels = MatchPhaseFormatter.formatLabels(postponed, fixedNowMillis, MatchTimeFormatter.BEIJING_ZONE)
        assertEquals("已延期", pLabels.topBadgeText)
        assertEquals("已延期 · 查看详情 ›", pLabels.bottomEntryText)
        assertEquals("已延期", pLabels.detailHeaderBadgeText)

        val cancelled = MatchPhaseResolver.resolve(MatchStatus.CANCELLED, "2026-11-28T15:00:00Z")
        val cLabels = MatchPhaseFormatter.formatLabels(cancelled, fixedNowMillis, MatchTimeFormatter.BEIJING_ZONE)
        assertEquals("已取消", cLabels.topBadgeText)
        assertEquals("已取消 · 查看详情 ›", cLabels.bottomEntryText)
        assertEquals("已取消", cLabels.detailHeaderBadgeText)
    }

    @Test
    fun entityRoundTripPreservesLotteryAndFinalizationReservedFields() {
        val item = sampleMatch(
            utcDate = "2026-11-27T19:00:00Z", // 03:00 Asia/Shanghai on 2026-11-28 -> watchNight 2026-11-27
            status = MatchStatus.TIMED,
        ).copy(
            lotteryCode = "周六015",
            lotteryHandicap = -1,
            lotteryDeadlineUtc = 1795805700000L,
            isFinalized = true,
        )
        val entity = MatchEntity.fromDomain(item)
        assertEquals("2026-11-27", entity.watchNightDateKey)
        assertEquals("2026-11-28", entity.localDateKey)

        val restored = entity.toDomain()
        assertEquals("周六015", restored.lotteryCode)
        assertEquals(-1, restored.lotteryHandicap)
        assertEquals(1795805700000L, restored.lotteryDeadlineUtc)
        assertTrue(restored.isFinalized)
    }

    private fun sampleMatch(
        utcDate: String,
        status: MatchStatus,
        elapsedMinutes: Int? = null,
    ): MatchItem = MatchItem(
        id = "m-1",
        competitionCode = "PL",
        competitionName = "Premier League",
        competitionZhName = "英超",
        matchday = 12,
        stage = "REGULAR_SEASON",
        utcDate = utcDate,
        status = status,
        homeTeamId = 57,
        homeTeamName = "Arsenal FC",
        homeTeamShortName = "Arsenal",
        homeTeamZhName = "阿森纳",
        awayTeamId = 65,
        awayTeamName = "Manchester City FC",
        awayTeamShortName = "Man City",
        awayTeamZhName = "曼城",
        homeScore = null,
        awayScore = null,
        venue = "Emirates Stadium",
        lastUpdatedUtc = utcDate,
        elapsedMinutes = elapsedMinutes,
    )
}
