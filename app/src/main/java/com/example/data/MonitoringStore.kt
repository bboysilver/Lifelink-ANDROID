package com.example.data

import android.content.Context
import kotlin.math.max

data class MonitoringSnapshot(
    val enabled: Boolean,
    val deadlineMs: Long,
    val remainingSeconds: Long,
    val alertState: Int,
    val lastActivityMs: Long,
    val lastActivityReason: String
)

object DeadlineCalculator {
    const val PRE_ALERT_SECONDS = 30 * 60L

    fun deadlineMs(lastActivityMs: Long, monitorHours: Int): Long =
        lastActivityMs + monitorHours.coerceIn(6, 72) * 60L * 60L * 1000L

    fun remainingSeconds(deadlineMs: Long, nowMs: Long): Long =
        max(0L, (deadlineMs - nowMs + 999L) / 1000L)
}

class MonitoringStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var monitorHours: Int
        get() = preferences.getInt(KEY_MONITOR_HOURS, 12).coerceIn(6, 72)
        set(value) = preferences.edit().putInt(KEY_MONITOR_HOURS, value.coerceIn(6, 72)).apply()

    val isEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)

    val isSetupCompleted: Boolean
        get() = preferences.getBoolean(KEY_SETUP_COMPLETED, false)

    val deadlineMs: Long
        get() = preferences.getLong(KEY_DEADLINE_MS, 0L)

    fun completeSetup() {
        preferences.edit().putBoolean(KEY_SETUP_COMPLETED, true).apply()
    }

    fun start(nowMs: Long = System.currentTimeMillis(), reason: String = "모니터링 시작") {
        resetDeadline(nowMs, reason, enable = true)
    }

    fun stop() {
        preferences.edit().putBoolean(KEY_ENABLED, false).apply()
    }

    fun resetDeadline(
        nowMs: Long = System.currentTimeMillis(),
        reason: String,
        enable: Boolean = isEnabled
    ): Long {
        val newDeadline = DeadlineCalculator.deadlineMs(nowMs, monitorHours)
        preferences.edit()
            .putBoolean(KEY_ENABLED, enable)
            .putLong(KEY_LAST_ACTIVITY_MS, nowMs)
            .putString(KEY_LAST_ACTIVITY_REASON, reason)
            .putLong(KEY_DEADLINE_MS, newDeadline)
            .apply()
        return newDeadline
    }

    fun initializeDeadlineIfMissing(nowMs: Long = System.currentTimeMillis()) {
        if (deadlineMs <= 0L) {
            resetDeadline(nowMs, "초기 설정", enable = isEnabled)
        }
    }

    fun canAttemptEmergency(deadlineMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        val attemptedDeadline = preferences.getLong(KEY_EMERGENCY_ATTEMPT_DEADLINE_MS, -1L)
        val lastAttemptMs = preferences.getLong(KEY_EMERGENCY_ATTEMPT_MS, 0L)
        return attemptedDeadline != deadlineMs || nowMs - lastAttemptMs >= EMERGENCY_RETRY_INTERVAL_MS
    }

    fun markEmergencyAttempt(deadlineMs: Long, nowMs: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong(KEY_EMERGENCY_ATTEMPT_DEADLINE_MS, deadlineMs)
            .putLong(KEY_EMERGENCY_ATTEMPT_MS, nowMs)
            .apply()
    }
    fun markPreAlert(deadlineMs: Long) {
        preferences.edit().putLong(KEY_PRE_ALERT_DEADLINE_MS, deadlineMs).apply()
    }

    fun markEmergency(deadlineMs: Long) {
        preferences.edit().putLong(KEY_EMERGENCY_DEADLINE_MS, deadlineMs).apply()
    }

    fun wasPreAlerted(deadlineMs: Long): Boolean =
        preferences.getLong(KEY_PRE_ALERT_DEADLINE_MS, -1L) == deadlineMs

    fun wasEmergencyDispatched(deadlineMs: Long): Boolean =
        preferences.getLong(KEY_EMERGENCY_DEADLINE_MS, -1L) == deadlineMs

    fun snapshot(nowMs: Long = System.currentTimeMillis()): MonitoringSnapshot {
        val currentDeadline = deadlineMs
        val remaining = DeadlineCalculator.remainingSeconds(currentDeadline, nowMs)
        val state = when {
            !isEnabled -> 0
            currentDeadline <= 0L -> 0
            wasEmergencyDispatched(currentDeadline) || remaining == 0L -> 2
            wasPreAlerted(currentDeadline) || remaining <= DeadlineCalculator.PRE_ALERT_SECONDS -> 1
            else -> 0
        }
        return MonitoringSnapshot(
            enabled = isEnabled,
            deadlineMs = currentDeadline,
            remainingSeconds = remaining,
            alertState = state,
            lastActivityMs = preferences.getLong(KEY_LAST_ACTIVITY_MS, 0L),
            lastActivityReason = preferences.getString(KEY_LAST_ACTIVITY_REASON, "활동 기록 없음")
                ?: "활동 기록 없음"
        )
    }

    companion object {
        private const val FILE_NAME = "lifelink_monitoring"
        private const val KEY_MONITOR_HOURS = "monitor_hours"
        private const val KEY_ENABLED = "monitoring_enabled"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_DEADLINE_MS = "deadline_ms"
        private const val KEY_LAST_ACTIVITY_MS = "last_activity_ms"
        private const val KEY_LAST_ACTIVITY_REASON = "last_activity_reason"
        private const val KEY_PRE_ALERT_DEADLINE_MS = "pre_alert_deadline_ms"
        private const val KEY_EMERGENCY_DEADLINE_MS = "emergency_deadline_ms"
        private const val KEY_EMERGENCY_ATTEMPT_DEADLINE_MS = "emergency_attempt_deadline_ms"
        private const val KEY_EMERGENCY_ATTEMPT_MS = "emergency_attempt_ms"
        const val EMERGENCY_RETRY_INTERVAL_MS = 5 * 60 * 1_000L
    }
}
