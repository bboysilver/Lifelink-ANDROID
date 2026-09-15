package com.example.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.data.MonitoringStore
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class InactivityDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deadline = intent.getLongExtra(InactivityDeadlineScheduler.EXTRA_DEADLINE, 0L)
        if (deadline <= 0L) return
        if (intent.action == ACTION_SAFE) {
            val store = MonitoringStore(context)
            if (store.desiredEnabled && store.deadlineMs == deadline) {
                store.resetDeadline(reason = "사전 알림에서 직접 무사 확인")
                NotificationManagerCompat.from(context).cancel(1002)
            }
            return
        }
        if (intent.action != InactivityDeadlineScheduler.ACTION_CHECK) return
        val appContext = context.applicationContext
        // Persist fallback first; direct evaluation uses the alarm's short wake window.
        InactivityDeadlineWorker.enqueue(appContext, deadline)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000L) {
                    InactivityDeadlineTask(appContext).run(deadline)
                }
            } catch (error: RuntimeException) {
                Log.e("InactivityDeadline", "Wakeup check failed; persisted worker will retry", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SAFE = "com.bboysilver.lifelink.action.INACTIVITY_SAFE"
    }
}

class InactivityDeadlineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deadline = inputData.getLong(InactivityDeadlineScheduler.EXTRA_DEADLINE, 0L)
        return try {
            if (deadline > 0L) InactivityDeadlineTask(applicationContext).run(deadline)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            Log.e("InactivityDeadline", "Persisted inactivity check failed", error)
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context, deadline: Long) {
            val work = OneTimeWorkRequestBuilder<InactivityDeadlineWorker>()
                .setInputData(Data.Builder().putLong(InactivityDeadlineScheduler.EXTRA_DEADLINE, deadline).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "inactivity-check:$deadline", ExistingWorkPolicy.KEEP, work
            )
        }
    }
}
