package com.example.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.data.MonitoringStore

class DailyCheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SAFE) {
            val dueAtMs = intent.getLongExtra(EXTRA_DUE_AT, 0L)
            if (dueAtMs <= 0L) return
            val store = MonitoringStore(context)
            val nowMs = System.currentTimeMillis()
            if (store.confirmDailyCheckIn(nowMs, expectedDueAtMs = dueAtMs) == null) return
            store.recordActivity(nowMs, "매일 안부 알림에서 직접 괜찮음 확인")
            NotificationManagerCompat.from(context).cancel(DailyCheckInTask.DAILY_NOTIFICATION_ID)
            DailyCheckInScheduler(context).ensureScheduled(nowMs)
            return
        }
        if (intent.action != DailyCheckInScheduler.ACTION_PROMPT &&
            intent.action != DailyCheckInScheduler.ACTION_OVERDUE
        ) return

        DailyCheckInWorker.enqueueFromAlarm(context, intent.action.orEmpty())
    }

    companion object {
        const val ACTION_SAFE = "com.bboysilver.lifelink.action.DAILY_RESPONSE"
        const val EXTRA_DUE_AT = "daily_due_at_ms"
    }
}
