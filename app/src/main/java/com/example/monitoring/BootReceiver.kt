package com.example.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.MonitoringStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val store = MonitoringStore(context)
        MaintenanceWorker.ensureScheduled(context)
        SafetySmsRetryWorker.enqueueRecovery(context)
        if (store.isSetupCompleted && store.desiredEnabled) {
            MonitoringWatchdogWorker.ensureScheduled(context)
            try {
                MonitoringService.start(context)
            } catch (error: RuntimeException) {
                // MonitoringService.start records the actionable error for the UI and watchdog.
                Log.e(TAG, "Unable to restore monitoring after boot or app update", error)
            }
        }
        if (store.isSetupCompleted && store.dailyCheckInEnabled) {
            DailyCheckInScheduler(context).ensureScheduled()
            val status = store.dailyCheckInStatus()
            if (status.phase == com.example.data.DailyCheckInPhase.DUE &&
                store.wasDailyCheckInPrompted(status.dueAtMs)
            ) {
                DailyCheckInWorker.enqueueFromRecovery(context)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
