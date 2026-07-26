package com.example.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailyCheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DailyCheckInScheduler.ACTION_PROMPT &&
            intent.action != DailyCheckInScheduler.ACTION_OVERDUE
        ) return

        DailyCheckInWorker.enqueueFromAlarm(context, intent.action.orEmpty())

    }
}