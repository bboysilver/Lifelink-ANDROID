package com.example.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.MonitoringStore

class DailyCheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DailyCheckInScheduler.ACTION_PROMPT &&
            intent.action != DailyCheckInScheduler.ACTION_OVERDUE
        ) return

        try {
            MonitoringService.evaluateDailyCheckIn(context)
        } catch (error: RuntimeException) {
            MonitoringStore(context).dailyCheckInError =
                error.message ?: "매일 안부 확인을 시작하지 못했습니다."
        }
    }
}