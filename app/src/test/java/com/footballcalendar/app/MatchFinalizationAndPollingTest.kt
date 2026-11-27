package com.footballcalendar.app

import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchPhase
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.util.DetailPollingPolicy
import com.footballcalendar.app.util.DetailStreamController
import com.footballcalendar.app.util.MatchFinalizationPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MatchFinalizationAndPollingTest {
    @Test
    fun finishedMatchBecomesFinalizedAfter15MinutesOrLocalSnapshot() {
        val kickoff = "2026-11-27T14:00:00Z" // Estimated end ~15:55Z, +15m grace = 16:10Z
        val justFinished = Instant.parse("2026-11-27T16:00:00Z").toEpochMilli()
        val afterGrace = Instant.parse("2026-11-27T16:15:00Z").toEpochMilli()
        val phase = MatchPhase.Finished()

        assertFalse(
            MatchFinalizationPolicy.isFinalized(
                phase = phase,
                utcDate = kickoff,
                hasLocalSnapshot = false,
                nowMillis = justFinished,
            ),
        )
        assertTrue(
            MatchFinalizationPolicy.isFinalized(
                phase = phase,
                utcDate = kickoff,
                hasLocalSnapshot = true,
                nowMillis = justFinished,
            ),
        )
        assertTrue(
            MatchFinalizationPolicy.isFinalized(
                phase = phase,
                utcDate = kickoff,
                hasLocalSnapshot = false,
                nowMillis = afterGrace,
            ),
        )
    }

    @Test
    fun pollingCoroutineNeverStartsWhenMatchIsFinalized() = runTest {
        val finalizedMatch = baseMatch(
            status = MatchStatus.FINISHED,
            isFinalized = true,
        )
        assertFalse(DetailPollingPolicy.shouldStartPolling(finalizedMatch, hasLocalSnapshot = false))
        assertFalse(DetailPollingPolicy.shouldStartPolling(finalizedMatch, hasLocalSnapshot = true))

        var fetchInvocations = 0
        val job = DetailStreamController.launchIfNeeded(
            scope = this,
            initialMatch = finalizedMatch,
            hasLocalSnapshot = true,
            currentMatchProvider = { finalizedMatch },
            currentDetailProvider = { null },
            fetchAndApply = { _, _ -> fetchInvocations++ },
        )

        assertNull("Finalized matches must never start a polling coroutine", job)
        assertEquals(0, fetchInvocations)
    }

    @Test
    fun activePollingTerminatesAsSoonAsMatchFinalizes() = runTest {
        var currentMatch = baseMatch(status = MatchStatus.LIVE, isFinalized = false)
        var currentDetail: LiveMatchDetail? = null
        var fetchInvocations = 0

        val job = DetailStreamController.launchIfNeeded(
            scope = this,
            initialMatch = currentMatch,
            hasLocalSnapshot = false,
            currentMatchProvider = { currentMatch },
            currentDetailProvider = { currentDetail },
            fetchAndApply = { match, _ ->
                fetchInvocations++
                currentDetail = LiveMatchDetail(matchId = match.id, statusShort = "FT", homeScore = 2, awayScore = 0)
                currentMatch = match.copy(status = MatchStatus.FINISHED, isFinalized = true)
            },
        )

        job?.join()
        assertEquals(1, fetchInvocations)
    }

    private fun baseMatch(status: MatchStatus, isFinalized: Boolean): MatchItem = MatchItem(
        id = "m-final",
        competitionCode = "PL",
        competitionName = "Premier League",
        competitionZhName = "英超",
        matchday = 1,
        stage = "REGULAR_SEASON",
        utcDate = "2026-11-27T14:00:00Z",
        status = status,
        homeTeamId = 1,
        homeTeamName = "Arsenal FC",
        homeTeamShortName = "Arsenal",
        homeTeamZhName = "阿森纳",
        awayTeamId = 2,
        awayTeamName = "Chelsea FC",
        awayTeamShortName = "Chelsea",
        awayTeamZhName = "切尔西",
        homeScore = 2,
        awayScore = 0,
        venue = "Emirates Stadium",
        lastUpdatedUtc = "2026-11-27T16:20:00Z",
        isFinalized = isFinalized,
    )
}
