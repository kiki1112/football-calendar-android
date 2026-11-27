package com.footballcalendar.app.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.util.MatchPhaseFormatter
import com.footballcalendar.app.util.MatchTimeFormatter
import java.time.Instant
import java.util.TimeZone

class SystemCalendarSyncManager(private val context: Context) {

    fun hasCalendarPermissions(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    fun buildAddToCalendarIntent(match: MatchItem): Intent {
        val startMillis = MatchTimeFormatter.parseInstant(match.utcDate)?.toEpochMilli()
            ?: System.currentTimeMillis()
        val endMillis = startMillis + MATCH_DURATION_MILLIS
        val title = "[${match.competitionZhName}] ${match.homeTeamZhName} vs ${match.awayTeamZhName}"
        val phaseLabel = MatchPhaseFormatter.formatLabels(match.phase).topBadgeText
        val desc = "联赛: ${match.competitionZhName} (${match.competitionName})\n状态: $phaseLabel\n[FCID:${match.id}]"
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, desc)
            if (match.venue.isNotBlank()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, match.venue)
            }
        }
    }

    fun syncMatchesToDeviceCalendar(matches: List<MatchItem>): Int {
        if (!hasCalendarPermissions()) return 0
        val calendarId = ensureAppCalendarId() ?: return 0

        val now = Instant.now().minusSeconds(6 * 3600)
        val activeMatches = matches.filter { m ->
            !m.phase.isPostponed &&
                (MatchTimeFormatter.parseInstant(m.utcDate)?.isAfter(now) == true)
        }
        val activeById = activeMatches.associateBy { it.id }
        val existingEventsByMatchId = queryManagedEvents(calendarId)

        var syncedCount = 0
        val resolver = context.contentResolver

        // Remove stale or postponed/unfollowed managed events
        for ((matchId, eventInfo) in existingEventsByMatchId) {
            if (matchId !in activeById) {
                val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventInfo.eventId)
                resolver.delete(deleteUri, null, null)
            }
        }

        // Insert or update active events
        for (match in activeMatches) {
            val startMillis = MatchTimeFormatter.parseInstant(match.utcDate)?.toEpochMilli() ?: continue
            val endMillis = startMillis + MATCH_DURATION_MILLIS
            val title = "[${match.competitionZhName}] ${match.homeTeamZhName} vs ${match.awayTeamZhName}"
            val phaseLabel = MatchPhaseFormatter.formatLabels(match.phase).topBadgeText
            val desc = "联赛: ${match.competitionZhName} (${match.competitionName})\n状态: $phaseLabel\n[FCID:${match.id}]"

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, desc)
                put(CalendarContract.Events.EVENT_LOCATION, match.venue)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val existing = existingEventsByMatchId[match.id]
            if (existing == null) {
                resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                syncedCount++
            } else if (existing.dtStart != startMillis || existing.title != title) {
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing.eventId)
                resolver.update(updateUri, values, null, null)
                syncedCount++
            }
        }
        return syncedCount
    }

    private fun ensureAppCalendarId(): Long? {
        val resolver = context.contentResolver
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val args = arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL)

        resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, args, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0)
                }
            }

        val syncAdapterUri: Uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, CALENDAR_INTERNAL_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFFE11D48.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }

        val inserted = resolver.insert(syncAdapterUri, values) ?: return null
        return ContentUris.parseId(inserted)
    }

    private data class ExistingEvent(val eventId: Long, val dtStart: Long, val title: String)

    private fun queryManagedEvents(calendarId: Long): Map<String, ExistingEvent> {
        val result = mutableMapOf<String, ExistingEvent>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.TITLE,
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val args = arrayOf(calendarId.toString())

        context.contentResolver.query(CalendarContract.Events.CONTENT_URI, projection, selection, args, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val desc = cursor.getString(1) ?: ""
                    val dtStart = cursor.getLong(2)
                    val title = cursor.getString(3) ?: ""
                    val matchId = FCID_REGEX.find(desc)?.groupValues?.getOrNull(1)
                    if (!matchId.isNullOrBlank()) {
                        result[matchId] = ExistingEvent(eventId, dtStart, title)
                    }
                }
            }
        return result
    }

    companion object {
        private const val ACCOUNT_NAME = "FootballCalendar"
        private const val CALENDAR_INTERNAL_NAME = "football_calendar_auto_sync"
        private const val CALENDAR_DISPLAY_NAME = "足球赛事日历"
        private const val MATCH_DURATION_MILLIS = 110L * 60L * 1000L
        private val FCID_REGEX = Regex("\\[FCID:([^\\]]+)]")
    }
}
