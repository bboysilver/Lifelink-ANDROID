package com.example.monitoring

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringSnapshot
import com.example.data.MonitoringStore
import java.util.concurrent.TimeUnit

class MonitoringWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = MonitoringStore(applicationContext)
        val snapshot = store.snapshot()
        val message = MonitoringWatchdogPolicy.interruptionMessage(snapshot) ?: return Result.success()
        val alertToken = MonitoringWatchdogPolicy.alertToken(snapshot)
        if (!store.claimWatchdogAlert(alertToken)) return Result.success()

        store.markServiceError(message)
        MonitoringStatusNotifier.showUnexpectedStop(applicationContext, message)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "lifelink-monitoring-watchdog"
        internal const val WORK_TAG = "lifelink-monitoring-watchdog-work"

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitoringWatchdogWorker>(
                15,
                TimeUnit.MINUTES
            )
                .addTag(WORK_TAG)
                .build()
            workManager(context)?.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            workManager(context)?.cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        private fun workManager(context: Context): WorkManager? = try {
            WorkManager.getInstance(context.applicationContext)
        } catch (error: IllegalStateException) {
            Log.e(WORK_TAG, "WorkManager is unavailable; watchdog was not scheduled", error)
            null
        }
    }
}

internal object MonitoringWatchdogPolicy {
    fun interruptionMessage(snapshot: MonitoringSnapshot): String? = when {
        !snapshot.desiredEnabled -> null
        snapshot.runtimeState != MonitoringRuntimeState.ERROR -> null
        snapshot.serviceError.isNotBlank() -> snapshot.serviceError
        else -> "모니터링 서비스의 응답이 없습니다."
    }

    fun alertToken(snapshot: MonitoringSnapshot): Long =
        snapshot.lastHeartbeatMs.takeIf { it > 0L } ?: snapshot.deadlineMs
}
