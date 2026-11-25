package com.footballcalendar.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncScheduler(private val context: Context) {

    fun configurePeriodicSync(
        enabled: Boolean,
        intervalHours: Int,
        wifiOnly: Boolean,
    ) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<FixtureSyncWorker>(
            intervalHours.toLong().coerceAtLeast(6L),
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun triggerImmediateSync() {
        val request = OneTimeWorkRequestBuilder<FixtureSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "football_calendar_periodic_sync"
        private const val IMMEDIATE_WORK_NAME = "football_calendar_immediate_sync"
    }
}
