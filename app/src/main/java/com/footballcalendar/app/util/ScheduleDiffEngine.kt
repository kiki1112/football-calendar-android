package com.footballcalendar.app.util

import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.ScheduleChange
import com.footballcalendar.app.model.ScheduleChangeType
import java.time.ZoneId

data class DiffResult(
    val mergedMatches: List<MatchItem>,
    val changes: List<ScheduleChange>,
)

/**
 * Compares existing cached fixtures with newly fetched fixtures to detect reschedules,
 * postponements, score updates, and newly added matches.
 */
object ScheduleDiffEngine {
    fun diff(
        existing: List<MatchItem>,
        incoming: List<MatchItem>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DiffResult {
        val existingById = existing.associateBy { it.id }
        val isInitialSeed = existing.isEmpty()
        val changes = mutableListOf<ScheduleChange>()

        for (match in incoming) {
            val prev = existingById[match.id]
            if (prev == null) {
                if (!isInitialSeed) {
                    changes += ScheduleChange(
                        match = match,
                        type = ScheduleChangeType.ADDED,
                        summary = "新增赛程：${match.homeTeamZhName} vs ${match.awayTeamZhName} (${MatchTimeFormatter.formatMonthDayTime(match.utcDate, zoneId)})",
                    )
                }
                continue
            }

            if (prev.status != match.status && match.status.isCancelledOrPostponed) {
                changes += ScheduleChange(
                    match = match,
                    type = ScheduleChangeType.POSTPONED,
                    summary = "赛程状态变更(${match.status.zhLabel})：${match.homeTeamZhName} vs ${match.awayTeamZhName}",
                )
            } else if (prev.utcDate != match.utcDate) {
                val oldTime = MatchTimeFormatter.formatMonthDayTime(prev.utcDate, zoneId)
                val newTime = MatchTimeFormatter.formatMonthDayTime(match.utcDate, zoneId)
                changes += ScheduleChange(
                    match = match,
                    type = ScheduleChangeType.RESCHEDULED,
                    summary = "开球改期：${match.homeTeamZhName} vs ${match.awayTeamZhName} 由 $oldTime 调整为 $newTime",
                )
            } else if (prev.homeScore != match.homeScore || prev.awayScore != match.awayScore ||
                (prev.status != match.status && (match.status == MatchStatus.FINISHED || match.status.isLive))
            ) {
                val scorePart = if (match.homeScore != null && match.awayScore != null) {
                    "${match.homeScore}:${match.awayScore}"
                } else {
                    match.status.zhLabel
                }
                changes += ScheduleChange(
                    match = match,
                    type = ScheduleChangeType.SCORE_UPDATED,
                    summary = "赛果更新：${match.homeTeamZhName} $scorePart ${match.awayTeamZhName}",
                )
            }
        }

        // Preserve existing lottery tags only if the incoming feed payload carries zero Sporttery tags
        // (guards against transient upstream outage without keeping expired tags when Sporttery is healthy).
        val incomingHasLottery = incoming.any { !it.lotteryCode.isNullOrBlank() }
        val normalizedIncoming = incoming.map { match ->
            val prev = existingById[match.id]
            val hydratedLottery = if (!incomingHasLottery && match.lotteryCode.isNullOrBlank() && prev?.lotteryCode != null) {
                match.copy(
                    lotteryCode = prev.lotteryCode,
                    lotteryHandicap = prev.lotteryHandicap,
                    lotteryDeadlineUtc = prev.lotteryDeadlineUtc,
                )
            } else {
                match
            }
            if (prev?.isFinalized == true && !hydratedLottery.isFinalized && hydratedLottery.status == MatchStatus.FINISHED) {
                hydratedLottery.copy(isFinalized = true)
            } else {
                hydratedLottery
            }
        }

        // Only preserve historical FINISHED matches outside the latest fetch window.
        // Never keep un-played/scheduled ghosts from stale caches or old sources.
        val incomingIds = normalizedIncoming.asSequence().map { it.id }.toSet()
        val preservedFinished = existing.filter {
            it.id !in incomingIds && it.status == MatchStatus.FINISHED
        }
        val merged = (normalizedIncoming + preservedFinished).sortedBy { it.utcDate }

        return DiffResult(
            mergedMatches = merged,
            changes = changes,
        )
    }
}
