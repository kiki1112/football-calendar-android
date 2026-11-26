package com.footballcalendar.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.footballcalendar.app.FootballCalendarApplication
import com.footballcalendar.app.model.ScheduleChangeType
import com.footballcalendar.app.util.MatchTimeFormatter
import com.footballcalendar.app.widget.NextMatchWidgetProvider
import java.time.Duration
import java.time.Instant

class FixtureSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FootballCalendarApplication).container
        val prefs = container.prefsRepository.currentPreferences()

        val syncResult = container.fixtureRepository.syncFixtures()
        if (syncResult.isFailure) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        val summary = syncResult.getOrThrow()

        // Notify only meaningful schedule changes (reschedule/postpone/new followed matches)
        if (prefs.notifyOnScheduleChange) {
            val relevantChanges = summary.changes.filter { change ->
                val affectsFollowed = prefs.favoriteTeams.isEmpty() ||
                    change.match.involvesAnyTeam(prefs.favoriteTeams) ||
                    change.match.competitionCode in prefs.followedCompetitions
                affectsFollowed && (
                    change.type == ScheduleChangeType.RESCHEDULED ||
                        change.type == ScheduleChangeType.POSTPONED ||
                        change.type == ScheduleChangeType.ADDED
                    )
            }
            if (relevantChanges.isNotEmpty()) {
                container.notificationHelper.notifyScheduleChanges(relevantChanges)
            }
        }

        // Live broadcast push check for ongoing/just-scored/fulltime matches in background
        if (prefs.notifyOnLiveBroadcast) {
            val alerts = container.fixtureRepository.pollLiveUpdatesWithBroadcasts().getOrDefault(emptyList())
            if (alerts.isNotEmpty()) {
                container.notificationHelper.notifyLiveBroadcasts(alerts)
            }
        }

        // Pre-match reminder check for favorite teams (or all followed competitions if no favorite set)
        if (prefs.notifyBeforeMatchMinutes > 0) {
            val allMatches = container.fixtureRepository.getAllMatchesOnce()
            val now = Instant.now()
            val newlyNotified = mutableSetOf<String>()
            val windowMinutes = prefs.notifyBeforeMatchMinutes.toLong() + 30L

            for (match in allMatches) {
                if (match.status.isCancelledOrPostponed || match.id in prefs.notifiedMatchIds) continue
                val interested = if (prefs.favoriteTeams.isNotEmpty()) {
                    match.involvesAnyTeam(prefs.favoriteTeams)
                } else {
                    match.competitionCode in prefs.followedCompetitions
                }
                if (!interested) continue

                val kickoff = MatchTimeFormatter.parseInstant(match.utcDate) ?: continue
                val minsUntil = Duration.between(now, kickoff).toMinutes()
                if (minsUntil in 0..windowMinutes) {
                    container.notificationHelper.notifyUpcomingMatch(match)
                    newlyNotified += match.id
                }
            }
            container.prefsRepository.markMatchesNotified(newlyNotified)
        }

        // Optional managed sync to device system calendar
        if (prefs.autoSyncSystemCalendar) {
            val matches = container.fixtureRepository.getAllMatchesOnce().filter { match ->
                val leagueOk = match.competitionCode in prefs.followedCompetitions
                if (prefs.favoriteTeams.isNotEmpty()) {
                    leagueOk && match.involvesAnyTeam(prefs.favoriteTeams)
                } else {
                    leagueOk
                }
            }
            container.systemCalendarSyncManager.syncMatchesToDeviceCalendar(matches)
        }

        NextMatchWidgetProvider.updateAllWidgets(applicationContext)
        return Result.success()
    }
}
