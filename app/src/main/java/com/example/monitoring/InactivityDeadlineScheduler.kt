package com.example.monitoring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.DeadlineCalculator
import com.example.data.MonitoringStore

/** One inexact wakeup checks the next safety boundary without keeping the CPU awake. */
class InactivityDeadlineScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)
    private val store = MonitoringStore(this.context)
    private val preferences = this.context.getSharedPreferences("inactivity_alarm", Context.MODE_PRIVATE)

    fun ensureScheduled(nowMs: Long = System.currentTimeMillis()) = synchronized(LOCK) {
        val deadline = store.deadlineMs
        if (!store.desiredEnabled || !store.isSetupCurrent || deadline <= 0L ||
            store.wasEmergencyDispatched(deadline)
        ) {
            cancel()
            return@synchronized
        }
        // Do not postpone a pending alarm on every pass of the service loop.
        if (preferences.getLong("deadline", 0L) == deadline &&
            preferences.getLong("trigger", 0L) > nowMs
        ) return@synchronized

        val preAlertAt = deadline - DeadlineCalculator.PRE_ALERT_SECONDS * 1_000L
        val trigger = when {
            !store.wasPreAlerted(deadline) && nowMs < deadline -> maxOf(preAlertAt, nowMs + 1_000L)
            nowMs < deadline -> deadline
            else -> nowMs + RETRY_MS
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent(deadline))
        preferences.edit().putLong("deadline", deadline).putLong("trigger", trigger).apply()
    }

    fun cancel() = synchronized(LOCK) {
        alarms.cancel(pendingIntent(0L))
        preferences.edit().clear().apply()
    }

    private fun pendingIntent(deadline: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 4201,
        Intent(context, InactivityDeadlineReceiver::class.java)
            .setAction(ACTION_CHECK).putExtra(EXTRA_DEADLINE, deadline),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val ACTION_CHECK = "com.bboysilver.lifelink.action.INACTIVITY_CHECK"
        const val EXTRA_DEADLINE = "deadline_ms"
        private const val RETRY_MS = 5 * 60_000L
        private val LOCK = Any()
    }
}
