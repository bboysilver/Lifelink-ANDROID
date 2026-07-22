package com.example.monitoring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.MonitoringStore

class DailyCheckInScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val store = MonitoringStore(appContext)

    fun ensureScheduled(nowMs: Long = System.currentTimeMillis()) {
        cancel()
        if (!store.dailyCheckInEnabled) return

        var dueAtMs = store.ensureDailyCheckInScheduled(nowMs)
        val status = store.dailyCheckInStatus(nowMs)
        if (status.phase == com.example.data.DailyCheckInPhase.OVERDUE &&
            !store.wasDailyCheckInPrompted(status.dueAtMs)
        ) {
            dueAtMs = store.deferDailyCheckInToNow(nowMs)
        }
        if (dueAtMs <= 0L) return

        scheduleAlarm(ACTION_PROMPT, PROMPT_REQUEST_CODE, dueAtMs)
        scheduleAlarm(
            ACTION_OVERDUE,
            OVERDUE_REQUEST_CODE,
            dueAtMs + com.example.data.DailyCheckInCalculator.RESPONSE_WINDOW_MS
        )
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent(ACTION_PROMPT, PROMPT_REQUEST_CODE))
        alarmManager.cancel(pendingIntent(ACTION_OVERDUE, OVERDUE_REQUEST_CODE))
    }

    private fun scheduleAlarm(action: String, requestCode: Int, triggerAtMs: Long) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            pendingIntent(action, requestCode)
        )
    }

    private fun pendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            requestCode,
            Intent(appContext, DailyCheckInReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val ACTION_PROMPT = "com.bboysilver.lifelink.action.DAILY_PROMPT"
        const val ACTION_OVERDUE = "com.bboysilver.lifelink.action.DAILY_OVERDUE"
        private const val PROMPT_REQUEST_CODE = 4101
        private const val OVERDUE_REQUEST_CODE = 4102
    }
}