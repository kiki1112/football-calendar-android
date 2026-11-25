package com.footballcalendar.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.footballcalendar.app.FootballCalendarApplication
import com.footballcalendar.app.MainActivity
import com.footballcalendar.app.R
import com.footballcalendar.app.util.MatchTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class NextMatchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgetsInternal(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NextMatchWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                updateWidgetsInternal(context, manager, ids)
            }
        }

        private suspend fun updateWidgetsInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            val app = context.applicationContext as? FootballCalendarApplication ?: return
            val prefs = app.container.prefsRepository.currentPreferences()
            val matches = app.container.fixtureRepository.getAllMatchesOnce()
            val now = Instant.now().minusSeconds(1800)

            val upcoming = matches.filter { match ->
                !match.status.isCancelledOrPostponed &&
                    match.competitionCode in prefs.followedCompetitions &&
                    (MatchTimeFormatter.parseInstant(match.utcDate)?.isAfter(now) == true)
            }
            val preferred = upcoming.firstOrNull { it.involvesAnyTeam(prefs.favoriteTeams) }
                ?: upcoming.firstOrNull()

            val launchIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_next_match)
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                if (preferred != null) {
                    views.setTextViewText(R.id.widget_league, preferred.competitionZhName)
                    views.setTextViewText(
                        R.id.widget_time,
                        MatchTimeFormatter.formatMonthDayTime(preferred.utcDate),
                    )
                    views.setTextViewText(
                        R.id.widget_teams,
                        "${preferred.homeTeamZhName} vs ${preferred.awayTeamZhName}",
                    )
                    val countdown = MatchTimeFormatter.formatRelativeCountdown(preferred.utcDate)
                    val meta = if (preferred.venue.isNotBlank()) "$countdown · ${preferred.venue}" else countdown
                    views.setTextViewText(R.id.widget_meta, meta)
                } else {
                    views.setTextViewText(R.id.widget_league, "足球赛事日历")
                    views.setTextViewText(R.id.widget_time, "自动更新")
                    views.setTextViewText(R.id.widget_teams, context.getString(R.string.widget_empty_title))
                    views.setTextViewText(R.id.widget_meta, context.getString(R.string.widget_empty_sub))
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
