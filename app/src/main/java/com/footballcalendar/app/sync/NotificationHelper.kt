package com.footballcalendar.app.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.footballcalendar.app.MainActivity
import com.footballcalendar.app.R
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.ScheduleChange
import com.footballcalendar.app.util.MatchTimeFormatter

class NotificationHelper(private val context: Context) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val updatesChannel = NotificationChannel(
            CHANNEL_SCHEDULE_UPDATES,
            "赛程自动更新提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "当关注的比赛发生开球时间调整或延期时通知"
        }
        val reminderChannel = NotificationChannel(
            CHANNEL_MATCH_REMINDERS,
            "开球前提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "关注的焦点比赛即将开球时提醒"
        }
        manager.createNotificationChannel(updatesChannel)
        manager.createNotificationChannel(reminderChannel)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyScheduleChanges(changes: List<ScheduleChange>) {
        if (changes.isEmpty() || !hasNotificationPermission()) return
        ensureChannels()

        val title = if (changes.size == 1) {
            "足球赛程变更提醒"
        } else {
            "共有 ${changes.size} 场关注赛程更新"
        }
        val body = changes.take(3).joinToString("\n") { it.summary }

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(changes.first().summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CHANGES, notification)
    }

    @SuppressLint("MissingPermission")
    fun notifyUpcomingMatch(match: MatchItem) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val kickoff = MatchTimeFormatter.formatKickoffTime(match.utcDate)
        val title = "[${match.competitionZhName}] 即将开赛 ($kickoff)"
        val body = "${match.homeTeamZhName} vs ${match.awayTeamZhName}"

        val notification = NotificationCompat.Builder(context, CHANNEL_MATCH_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(match.id.hashCode(), notification)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_SCHEDULE_UPDATES = "schedule_updates"
        const val CHANNEL_MATCH_REMINDERS = "match_reminders"
        private const val NOTIFICATION_ID_CHANGES = 1001
    }
}
