package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import kotlin.math.max

enum class MonitoringRuntimeState { STARTING, RUNNING, ERROR, STOPPED }

data class MonitoringSnapshot(
    val desiredEnabled: Boolean,
    val runtimeState: MonitoringRuntimeState,
    val serviceError: String,
    val deadlineMs: Long,
    val remainingSeconds: Long,
    val alertState: Int,
    val lastActivityMs: Long,
    val lastActivityReason: String,
    val lastHeartbeatMs: Long,
    val deviceAlias: String
) {
    val isRunning: Boolean
        get() = runtimeState == MonitoringRuntimeState.RUNNING
}

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

    var deviceAlias: String
        get() = preferences.getString(KEY_DEVICE_ALIAS, DEFAULT_DEVICE_ALIAS)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: DEFAULT_DEVICE_ALIAS
        set(value) {
            val normalized = value.trim().take(30).ifEmpty { DEFAULT_DEVICE_ALIAS }
            preferences.edit().putString(KEY_DEVICE_ALIAS, normalized).apply()
        }

    val desiredEnabled: Boolean
        get() = preferences.getBoolean(KEY_DESIRED_ENABLED, false)

    var smsSubscriptionId: Int
        get() = preferences.getInt(KEY_SMS_SUBSCRIPTION_ID, INVALID_SUBSCRIPTION_ID)
        set(value) = preferences.edit().putInt(KEY_SMS_SUBSCRIPTION_ID, value).apply()

    var dailyCheckInEnabled: Boolean
        get() = preferences.getBoolean(KEY_DAILY_CHECK_IN_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_DAILY_CHECK_IN_ENABLED, value).apply()

    var dailyCheckInHour: Int
        get() = preferences.getInt(KEY_DAILY_CHECK_IN_HOUR, DailyCheckInCalculator.DEFAULT_HOUR)
            .coerceIn(0, 23)
        set(value) = preferences.edit().putInt(KEY_DAILY_CHECK_IN_HOUR, value.coerceIn(0, 23)).apply()

    val pendingSosEventMs: Long
        get() = preferences.getLong(KEY_PENDING_SOS_EVENT_MS, 0L)

    val isSetupCompleted: Boolean
        get() = preferences.getBoolean(KEY_SETUP_COMPLETED, false)

    val deadlineMs: Long
        get() = preferences.getLong(KEY_DEADLINE_MS, 0L)

    fun completeSetup() {
        preferences.edit().putBoolean(KEY_SETUP_COMPLETED, true).apply()
    }

    fun setDesiredEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_DESIRED_ENABLED, enabled)
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.STOPPED.name)
            .putLong(KEY_STATE_UPDATED_MS, System.currentTimeMillis())
            .apply()
    }

    fun beginStart(nowMs: Long = System.currentTimeMillis(), reason: String = "모니터링 시작") {
        val newDeadline = DeadlineCalculator.deadlineMs(nowMs, monitorHours)
        preferences.edit()
            .putBoolean(KEY_DESIRED_ENABLED, true)
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.STARTING.name)
            .putLong(KEY_STATE_UPDATED_MS, nowMs)
            .putLong(KEY_LAST_HEARTBEAT_MS, 0L)
            .putString(KEY_SERVICE_ERROR, "")
            .putLong(KEY_LAST_ACTIVITY_MS, nowMs)
            .putString(KEY_LAST_ACTIVITY_REASON, reason)
            .putLong(KEY_DEADLINE_MS, newDeadline)
            .apply()
    }

    fun stop(nowMs: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putBoolean(KEY_DESIRED_ENABLED, false)
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.STOPPED.name)
            .putLong(KEY_STATE_UPDATED_MS, nowMs)
            .putLong(KEY_LAST_HEARTBEAT_MS, 0L)
            .putString(KEY_SERVICE_ERROR, "")
            .apply()
    }

    fun resetDeadline(nowMs: Long = System.currentTimeMillis(), reason: String): Long {
        val newDeadline = DeadlineCalculator.deadlineMs(nowMs, monitorHours)
        preferences.edit()
            .putLong(KEY_LAST_ACTIVITY_MS, nowMs)
            .putString(KEY_LAST_ACTIVITY_REASON, reason)
            .putLong(KEY_DEADLINE_MS, newDeadline)
            .apply()
        return newDeadline
    }

    fun initializeDeadlineIfMissing(nowMs: Long = System.currentTimeMillis()) {
        if (desiredEnabled && deadlineMs <= 0L) resetDeadline(nowMs, "초기 설정")
    }

    fun markServiceStarting(nowMs: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.STARTING.name)
            .putLong(KEY_STATE_UPDATED_MS, nowMs)
            .putString(KEY_SERVICE_ERROR, "")
            .apply()
    }

    fun markServiceRunning(nowMs: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.RUNNING.name)
            .putLong(KEY_STATE_UPDATED_MS, nowMs)
            .putLong(KEY_LAST_HEARTBEAT_MS, nowMs)
            .putString(KEY_SERVICE_ERROR, "")
            .apply()
    }

    fun markHeartbeat(nowMs: Long = System.currentTimeMillis()) {
        if (!desiredEnabled) return
        preferences.edit()
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.RUNNING.name)
            .putLong(KEY_LAST_HEARTBEAT_MS, nowMs)
            .apply()
    }

    fun markServiceError(message: String, nowMs: Long = System.currentTimeMillis()) {
        if (!desiredEnabled) return
        preferences.edit()
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.ERROR.name)
            .putLong(KEY_STATE_UPDATED_MS, nowMs)
            .putString(KEY_SERVICE_ERROR, message)
            .apply()
    }

    fun dailyCheckInStatus(nowMs: Long = System.currentTimeMillis()): DailyCheckInStatus =
        DailyCheckInCalculator.status(
            nowMs = nowMs,
            enabled = dailyCheckInEnabled,
            hour = dailyCheckInHour,
            lastConfirmedDayStartMs = preferences.getLong(KEY_DAILY_CONFIRMED_DAY_START_MS, 0L)
        )

    fun markDailyCheckInPrompted(dayStartMs: Long) {
        preferences.edit().putLong(KEY_DAILY_PROMPTED_DAY_START_MS, dayStartMs).apply()
    }

    fun wasDailyCheckInPrompted(dayStartMs: Long): Boolean =
        preferences.getLong(KEY_DAILY_PROMPTED_DAY_START_MS, 0L) == dayStartMs

    fun confirmDailyCheckIn(nowMs: Long = System.currentTimeMillis()): Long {
        val dayStartMs = dailyCheckInStatus(nowMs).dayStartMs
        preferences.edit().putLong(KEY_DAILY_CONFIRMED_DAY_START_MS, dayStartMs).apply()
        return dayStartMs
    }

    fun markDailyCheckInAlerted(dayStartMs: Long) {
        preferences.edit().putLong(KEY_DAILY_ALERTED_DAY_START_MS, dayStartMs).apply()
    }

    fun wasDailyCheckInAlerted(dayStartMs: Long): Boolean =
        preferences.getLong(KEY_DAILY_ALERTED_DAY_START_MS, 0L) == dayStartMs

    @SuppressLint("ApplySharedPref")
    fun beginSos(nowMs: Long = System.currentTimeMillis()): Long {
        if (pendingSosEventMs > 0L) return pendingSosEventMs
        preferences.edit().putLong(KEY_PENDING_SOS_EVENT_MS, nowMs).commit()
        return nowMs
    }

    fun clearPendingSos() {
        preferences.edit().remove(KEY_PENDING_SOS_EVENT_MS).apply()
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
        val heartbeatMs = preferences.getLong(KEY_LAST_HEARTBEAT_MS, 0L)
        val stateUpdatedMs = preferences.getLong(KEY_STATE_UPDATED_MS, 0L)
        val storedState = preferences.getString(KEY_RUNTIME_STATE, null)
            ?.let { name -> MonitoringRuntimeState.entries.firstOrNull { it.name == name } }
            ?: MonitoringRuntimeState.STOPPED
        val runtimeState = when {
            !desiredEnabled -> MonitoringRuntimeState.STOPPED
            storedState == MonitoringRuntimeState.RUNNING &&
                (heartbeatMs == 0L || nowMs - heartbeatMs > HEARTBEAT_TIMEOUT_MS) -> MonitoringRuntimeState.ERROR
            storedState == MonitoringRuntimeState.STARTING &&
                nowMs - stateUpdatedMs > START_TIMEOUT_MS -> MonitoringRuntimeState.ERROR
            else -> storedState
        }
        val error = when {
            runtimeState != MonitoringRuntimeState.ERROR -> ""
            storedState == MonitoringRuntimeState.RUNNING -> "모니터링 서비스 응답이 중단되었습니다."
            storedState == MonitoringRuntimeState.STARTING -> "모니터링 서비스를 시작하지 못했습니다."
            else -> preferences.getString(KEY_SERVICE_ERROR, "모니터링이 중단되었습니다.")
                ?: "모니터링이 중단되었습니다."
        }
        val alertState = when {
            !desiredEnabled -> 0
            currentDeadline <= 0L -> 0
            wasEmergencyDispatched(currentDeadline) || remaining == 0L -> 2
            wasPreAlerted(currentDeadline) || remaining <= DeadlineCalculator.PRE_ALERT_SECONDS -> 1
            else -> 0
        }
        return MonitoringSnapshot(
            desiredEnabled = desiredEnabled,
            runtimeState = runtimeState,
            serviceError = error,
            deadlineMs = currentDeadline,
            remainingSeconds = remaining,
            alertState = alertState,
            lastActivityMs = preferences.getLong(KEY_LAST_ACTIVITY_MS, 0L),
            lastActivityReason = preferences.getString(KEY_LAST_ACTIVITY_REASON, "활동 기록 없음")
                ?: "활동 기록 없음",
            lastHeartbeatMs = heartbeatMs,
            deviceAlias = deviceAlias
        )
    }

    companion object {
        private const val FILE_NAME = "lifelink_monitoring"
        private const val KEY_MONITOR_HOURS = "monitor_hours"
        // Keep the existing preference key so upgrades preserve the user's intent.
        private const val KEY_DESIRED_ENABLED = "monitoring_enabled"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_DEADLINE_MS = "deadline_ms"
        private const val KEY_LAST_ACTIVITY_MS = "last_activity_ms"
        private const val KEY_LAST_ACTIVITY_REASON = "last_activity_reason"
        private const val KEY_PRE_ALERT_DEADLINE_MS = "pre_alert_deadline_ms"
        private const val KEY_EMERGENCY_DEADLINE_MS = "emergency_deadline_ms"
        private const val KEY_RUNTIME_STATE = "runtime_state"
        private const val KEY_STATE_UPDATED_MS = "runtime_state_updated_ms"
        private const val KEY_LAST_HEARTBEAT_MS = "last_heartbeat_ms"
        private const val KEY_SERVICE_ERROR = "service_error"
        private const val KEY_DEVICE_ALIAS = "device_alias"
        private const val KEY_SMS_SUBSCRIPTION_ID = "sms_subscription_id"
        private const val KEY_DAILY_CHECK_IN_ENABLED = "daily_check_in_enabled"
        private const val KEY_DAILY_CHECK_IN_HOUR = "daily_check_in_hour"
        private const val KEY_DAILY_CONFIRMED_DAY_START_MS = "daily_confirmed_day_start_ms"
        private const val KEY_DAILY_PROMPTED_DAY_START_MS = "daily_prompted_day_start_ms"
        private const val KEY_DAILY_ALERTED_DAY_START_MS = "daily_alerted_day_start_ms"
        private const val KEY_PENDING_SOS_EVENT_MS = "pending_sos_event_ms"
        private const val INVALID_SUBSCRIPTION_ID = -1
        private const val DEFAULT_DEVICE_ALIAS = "라이프링크 사용자"
        const val HEARTBEAT_TIMEOUT_MS = 45_000L
        const val START_TIMEOUT_MS = 30_000L
    }
}
