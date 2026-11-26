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
import com.footballcalendar.app.model.LiveBroadcastAlert
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
        val broadcastChannel = NotificationChannel(
            CHANNEL_LIVE_BROADCAST,
            "比赛实时进球与赛况播报",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "开球、实时进球与全场终场比分即时消息推送"
            enableVibration(true)
        }
        manager.createNotificationChannel(updatesChannel)
        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(broadcastChannel)
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

    @SuppressLint("MissingPermission")
    fun notifyLiveBroadcasts(alerts: List<LiveBroadcastAlert>) {
        if (alerts.isEmpty() || !hasNotificationPermission()) return
        ensureChannels()
        alerts.take(4).forEach { alert ->
            val notification = NotificationCompat.Builder(context, CHANNEL_LIVE_BROADCAST)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(alert.headline)
                .setContentText(alert.detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.detail))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setContentIntent(openAppIntent(alert.match.id))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyTestBroadcast(match: MatchItem?): Boolean {
        if (!hasNotificationPermission()) return false
        ensureChannels()
        val title = if (match != null) {
            "⚽ 实时播报测试 · ${match.homeTeamZhName} vs ${match.awayTeamZhName}"
        } else {
            "⚽ 足球日历 · 实时赛况推送已就绪"
        }
        val body = if (match != null) {
            "${match.competitionZhName} · 点击直接打开比赛时间线与图文直播"
        } else {
            "当关注比赛开赛、进球或终场时，系统将实时推送播报通知。"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_LIVE_BROADCAST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(match?.id))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TEST_BROADCAST, notification)
        return true
    }

    private fun openAppIntent(matchId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!matchId.isNullOrBlank()) {
                putExtra(EXTRA_OPEN_MATCH_ID, matchId)
            }
        }
        return PendingIntent.getActivity(
            context,
            matchId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_OPEN_MATCH_ID = "extra_open_match_id"
        const val CHANNEL_SCHEDULE_UPDATES = "schedule_updates"
        const val CHANNEL_MATCH_REMINDERS = "match_reminders"
        const val CHANNEL_LIVE_BROADCAST = "live_match_broadcast"
        private const val NOTIFICATION_ID_CHANGES = 1001
        private const val NOTIFICATION_ID_TEST_BROADCAST = 1009
    }
}
