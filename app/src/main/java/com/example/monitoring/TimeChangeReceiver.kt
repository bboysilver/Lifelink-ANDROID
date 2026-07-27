package com.example.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.MonitoringStore

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val store = MonitoringStore(context.applicationContext)
        if (!store.isSetupCompleted || !store.dailyCheckInEnabled) return

        val nowMs = System.currentTimeMillis()
        store.recalculateDailyCheckInAfterClockChange(nowMs)
        DailyCheckInScheduler(context).ensureScheduled(nowMs)

    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED
        )
    }
}
