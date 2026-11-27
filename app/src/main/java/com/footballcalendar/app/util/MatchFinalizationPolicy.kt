package com.footballcalendar.app.util

import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Determines when a finished match is sealed (`isFinalized = true`) so that
 * detail screens read permanently from local Room cache and stop live polling.
 */
object MatchFinalizationPolicy {
    const val FINALIZATION_GRACE_MILLIS: Long = 15L * 60L * 1000L
    private const val REGULAR_DURATION_MINUTES: Long = 115L
    private const val EXTRA_TIME_DURATION_MINUTES: Long = 150L

    fun isFinalized(
        phase: MatchPhase,
        utcDate: String,
        elapsedMinutes: Int? = null,
        hasLocalSnapshot: Boolean = false,
        alreadyFinalized: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (phase !is MatchPhase.Finished) return false
        if (alreadyFinalized || hasLocalSnapshot) return true
        val kickoffMillis = MatchTimeFormatter.parseInstant(utcDate)?.toEpochMilli() ?: return false
        val durationMinutes = when {
            elapsedMinutes != null && elapsedMinutes >= 90 -> elapsedMinutes.toLong() + 20L
            phase.isAfterExtraTime || phase.isPenalties -> EXTRA_TIME_DURATION_MINUTES
            else -> REGULAR_DURATION_MINUTES
        }
        val estimatedEndMillis = kickoffMillis + durationMinutes * 60_000L
        return nowMillis >= estimatedEndMillis + FINALIZATION_GRACE_MILLIS
    }
}

/**
 * Guards real-time detail polling coroutines so finalized / postponed / out-of-window
 * matches never start or linger in 10s network polling loops.
 */
object DetailPollingPolicy {
    fun shouldStartPolling(
        match: MatchItem,
        hasLocalSnapshot: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (match.isFinalized) return false
        val phase = match.phase
        if (phase is MatchPhase.Finished) {
            return !MatchFinalizationPolicy.isFinalized(
                phase = phase,
                utcDate = match.utcDate,
                elapsedMinutes = match.elapsedMinutes,
                hasLocalSnapshot = hasLocalSnapshot,
                alreadyFinalized = match.isFinalized,
                nowMillis = nowMillis,
            )
        }
        if (phase is MatchPhase.Postponed) return false
        if (phase.isLiveOrHalfTime) return true
        val koMillis = (phase as MatchPhase.Upcoming).kickoffUtcMillis
        if (koMillis <= 0L) return false
        val mins = (nowMillis - koMillis) / 60_000L
        return mins in -75L..195L
    }

    fun shouldContinuePolling(
        match: MatchItem,
        currentDetail: LiveMatchDetail?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (match.isFinalized) return false
        val effective = match.effectivePhase(currentDetail)
        if (effective is MatchPhase.Finished || effective is MatchPhase.Postponed) return false
        return shouldStartPolling(match, hasLocalSnapshot = currentDetail != null, nowMillis = nowMillis)
    }
}

/**
 * Shared coroutine orchestrator for detail-sheet polling.
 * Guarantees zero polling coroutines are launched when [MatchItem.isFinalized] is true.
 */
object DetailStreamController {
    fun launchIfNeeded(
        scope: CoroutineScope,
        initialMatch: MatchItem,
        hasLocalSnapshot: Boolean,
        nowProvider: () -> Long = { System.currentTimeMillis() },
        currentMatchProvider: () -> MatchItem?,
        currentDetailProvider: () -> LiveMatchDetail?,
        fetchAndApply: suspend (MatchItem, Boolean) -> Unit,
    ): Job? {
        if (!DetailPollingPolicy.shouldStartPolling(initialMatch, hasLocalSnapshot, nowProvider())) {
            return null
        }
        return scope.launch {
            fetchAndApply(initialMatch, false)
            while (isActive) {
                val curMatch = currentMatchProvider() ?: break
                val curDetail = currentDetailProvider()
                if (!DetailPollingPolicy.shouldContinuePolling(curMatch, curDetail, nowProvider())) {
                    break
                }
                delay(if (curMatch.phase.isLiveOrHalfTime) 4_000L else 10_000L)
                val target = currentMatchProvider() ?: break
                if (target.isFinalized) break
                fetchAndApply(target, true)
            }
        }
    }
}
