package com.example.monitoring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.LifeLinkRepository
import java.util.concurrent.TimeUnit

class MaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val nowMs = System.currentTimeMillis()
        SmsDispatchStore(applicationContext).pruneExpired(nowMs)
        LifeLinkRepository(AppDatabase.getDatabase(applicationContext))
            .deleteLogsBefore(nowMs - SmsDispatchStore.RETENTION_MS)
        Result.success()
    } catch (_: RuntimeException) {
        Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "lifelink-maintenance"
        internal const val WORK_TAG = "lifelink-maintenance-work"

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(24, TimeUnit.HOURS)
                .addTag(WORK_TAG)
                .build()
            val workManager = try {
                WorkManager.getInstance(context.applicationContext)
            } catch (_: IllegalStateException) {
                // AndroidX Startup initializes WorkManager in production; local previews may omit it.
                return
            }
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}