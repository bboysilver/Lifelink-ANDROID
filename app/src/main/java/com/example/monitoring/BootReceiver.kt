package com.example.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.MonitoringStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val store = MonitoringStore(context)
        if (store.isSetupCompleted && store.desiredEnabled) {
            MonitoringService.start(context)
        }
        if (store.isSetupCompleted && store.dailyCheckInEnabled) {
            DailyCheckInScheduler(context).ensureScheduled()
        }
    }
}
