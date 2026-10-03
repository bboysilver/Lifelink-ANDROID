package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import com.example.BuildConfig
import com.example.monitoring.InactivityDeadlineScheduler
import com.example.monitoring.DailyCheckInScheduler
import com.example.monitoring.MonitoringService
import kotlin.math.abs
import kotlin.math.max

enum class MonitoringRuntimeState { STARTING, RUNNING, ERROR, STOPPED }

enum class TestSmsVerificationState { NOT_SENT, PENDING, SUCCESS, FAILED }

data class TestSmsVerification(
    val state: TestSmsVerificationState,
    val contactId: Int,
    val eventId: String,
    val message: String
)

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
    private val appContext = context.applicationContext
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

    val dailyCheckInEnabled: Boolean
        get() = preferences.getBoolean(KEY_DAILY_CHECK_IN_ENABLED, false)

    val dailyCheckInHour: Int
        get() = preferences.getInt(KEY_DAILY_CHECK_IN_HOUR, DailyCheckInCalculator.DEFAULT_HOUR)
            .coerceIn(0, 23)

    val dailyNextDueAtMs: Long
        get() = preferences.getLong(KEY_DAILY_NEXT_DUE_AT_MS, 0L)

    val dailyResponseDeadlineAtMs: Long
        get() = preferences.getLong(KEY_DAILY_RESPONSE_DEADLINE_AT_MS, 0L)

    var dailyCheckInError: String
        get() = preferences.getString(KEY_DAILY_CHECK_IN_ERROR, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_DAILY_CHECK_IN_ERROR, value).apply()

    val pendingSosEventMs: Long
        get() = preferences.getLong(KEY_PENDING_SOS_EVENT_MS, 0L)

    val activeSosEventMs: Long
        get() = preferences.getLong(KEY_ACTIVE_SOS_EVENT_MS, 0L)

    val sosEventMs: Long
        get() = activeSosEventMs.takeIf { it > 0L } ?: pendingSosEventMs

    val isSetupCompleted: Boolean
        get() = preferences.getBoolean(KEY_SETUP_COMPLETED, false)

    val setupStep: Int
        get() = preferences.getInt(KEY_SETUP_STEP, 0).coerceIn(0, 5)

    val onboardingVersion: Int
        get() = preferences.getInt(KEY_ONBOARDING_VERSION, 0)

    val isSetupCurrent: Boolean
        get() = isSetupCompleted && onboardingVersion >= CURRENT_ONBOARDING_VERSION

    val testSmsVerification: TestSmsVerification
        get() {
            val stateName = preferences.getString(KEY_TEST_SMS_STATE, null)
            val state = TestSmsVerificationState.entries.firstOrNull { it.name == stateName }
                ?: TestSmsVerificationState.NOT_SENT
            return TestSmsVerification(
                state = state,
                contactId = preferences.getInt(KEY_TEST_SMS_CONTACT_ID, -1),
                eventId = preferences.getString(KEY_TEST_SMS_EVENT_ID, "").orEmpty(),
                message = preferences.getString(KEY_TEST_SMS_MESSAGE, "").orEmpty()
            )
        }

    var debugMonitorMinutes: Int
        get() = if (BuildConfig.DEBUG) preferences.getInt(KEY_DEBUG_MONITOR_MINUTES, 0) else 0
        set(value) {
            if (BuildConfig.DEBUG) {
                preferences.edit()
                    .putInt(KEY_DEBUG_MONITOR_MINUTES, value.takeIf { it == 1 || it == 5 } ?: 0)
                    .apply()
            }
        }

    val deadlineMs: Long
        get() = preferences.getLong(KEY_DEADLINE_MS, 0L)

    // This identifies one inactivity cycle, even if its wall-clock deadline moves.
    val inactivityEventMs: Long
        get() = preferences.getLong(KEY_INACTIVITY_EVENT_MS, deadlineMs)

    fun setSetupStep(step: Int) {
        preferences.edit().putInt(KEY_SETUP_STEP, step.coerceIn(0, 5)).apply()
    }

    fun beginSetupReview() = synchronized(ACTIVITY_LOCK) {
        InactivityDeadlineScheduler(appContext).cancel()
        preferences.edit()
            .putBoolean(KEY_SETUP_COMPLETED, false)
            .putBoolean(KEY_DESIRED_ENABLED, false)
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.STOPPED.name)
            .putInt(KEY_SETUP_STEP, 0)
            .putString(KEY_TEST_SMS_STATE, TestSmsVerificationState.NOT_SENT.name)
            .remove(KEY_TEST_SMS_CONTACT_ID)
            .remove(KEY_TEST_SMS_EVENT_ID)
            .remove(KEY_TEST_SMS_MESSAGE)
            .apply()
    }

    fun completeSetup() {
        preferences.edit()
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .putInt(KEY_ONBOARDING_VERSION, CURRENT_ONBOARDING_VERSION)
            .putInt(KEY_SETUP_STEP, 5)
            .apply()
    }

    fun invalidateTestSmsVerification() {
        preferences.edit()
            .putString(KEY_TEST_SMS_STATE, TestSmsVerificationState.NOT_SENT.name)
            .remove(KEY_TEST_SMS_CONTACT_ID)
            .remove(KEY_TEST_SMS_EVENT_ID)
            .remove(KEY_TEST_SMS_MESSAGE)
            .apply()
    }

    fun markTestSmsPending(contactId: Int, eventId: String) {
        preferences.edit()
            .putString(KEY_TEST_SMS_STATE, TestSmsVerificationState.PENDING.name)
            .putInt(KEY_TEST_SMS_CONTACT_ID, contactId)
            .putString(KEY_TEST_SMS_EVENT_ID, eventId)
            .putString(KEY_TEST_SMS_MESSAGE, "시험 문자 발송 결과를 확인하고 있습니다.")
            .apply()
    }

    fun recordTestSmsResult(
        eventId: String,
        state: TestSmsVerificationState,
        message: String
    ) {
        if (testSmsVerification.eventId != eventId) return
        preferences.edit()
            .putString(KEY_TEST_SMS_STATE, state.name)
            .putString(KEY_TEST_SMS_MESSAGE, message)
            .apply()
    }

    fun setDesiredEnabled(enabled: Boolean) = synchronized(ACTIVITY_LOCK) {
        preferences.edit()
            .putBoolean(KEY_DESIRED_ENABLED, enabled)
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.STOPPED.name)
            .putLong(KEY_STATE_UPDATED_MS, System.currentTimeMillis())
            .apply()
    }

    fun beginStart(
        nowMs: Long = System.currentTimeMillis(),
        reason: String = "모니터링 시작",
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ) = synchronized(ACTIVITY_LOCK) {
        val newDeadline = configuredDeadlineMs(nowMs)
        val newEventMs = nextInactivityEventMs(newDeadline)
        preferences.edit()
            .putBoolean(KEY_DESIRED_ENABLED, true)
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.STARTING.name)
            .putLong(KEY_STATE_UPDATED_MS, nowMs)
            .putLong(KEY_LAST_HEARTBEAT_MS, 0L)
            .putLong(KEY_LAST_HEARTBEAT_ELAPSED_MS, elapsedRealtimeMs)
            .putLong(KEY_CLOCK_WALL_MS, nowMs)
            .putLong(KEY_CLOCK_ELAPSED_MS, elapsedRealtimeMs)
            .remove(KEY_WATCHDOG_ALERT_TOKEN)
            .putString(KEY_SERVICE_ERROR, "")
            .putLong(KEY_LAST_ACTIVITY_MS, nowMs)
            .putString(KEY_LAST_ACTIVITY_REASON, reason)
            .putLong(KEY_DEADLINE_MS, newDeadline)
            .putLong(KEY_INACTIVITY_EVENT_MS, newEventMs)
            .apply()
    }

    fun stop(nowMs: Long = System.currentTimeMillis()) = synchronized(ACTIVITY_LOCK) {
        InactivityDeadlineScheduler(appContext).cancel()
        preferences.edit()
            .putBoolean(KEY_DESIRED_ENABLED, false)
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.STOPPED.name)
            .putLong(KEY_STATE_UPDATED_MS, nowMs)
            .putLong(KEY_LAST_HEARTBEAT_MS, 0L)
            .remove(KEY_LAST_HEARTBEAT_ELAPSED_MS)
            .remove(KEY_WATCHDOG_ALERT_TOKEN)
            .putString(KEY_SERVICE_ERROR, "")
            .apply()
    }

    fun resetDeadline(
        nowMs: Long = System.currentTimeMillis(),
        reason: String,
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
        clockNowMs: Long = nowMs
    ): Long = synchronized(ACTIVITY_LOCK) {
        val newDeadline = configuredDeadlineMs(nowMs)
        val newEventMs = nextInactivityEventMs(newDeadline)
        preferences.edit()
            .putLong(KEY_LAST_ACTIVITY_MS, nowMs)
            .putString(KEY_LAST_ACTIVITY_REASON, reason)
            .putLong(KEY_DEADLINE_MS, newDeadline)
            .putLong(KEY_INACTIVITY_EVENT_MS, newEventMs)
            .putLong(KEY_CLOCK_WALL_MS, clockNowMs)
            .putLong(KEY_CLOCK_ELAPSED_MS, elapsedRealtimeMs)
            .apply()
        InactivityDeadlineScheduler(appContext).ensureScheduled(clockNowMs)
        newDeadline
    }

    fun recordActivity(
        activityAtMs: Long,
        reason: String,
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): Boolean =
        synchronized(ACTIVITY_LOCK) {
            rebaseAfterWallClockChange(nowMs, elapsedRealtimeMs)
            // Batched sensor events must not move the deadline backwards, or turn
            // yesterday's movement into activity at the time it was delivered.
            if (activityAtMs <= 0L || activityAtMs > nowMs ||
                activityAtMs <= preferences.getLong(KEY_LAST_ACTIVITY_MS, 0L)
            ) return@synchronized false
            val daily = dailyCheckInStatus(nowMs, elapsedRealtimeMs)
            val confirmedDaily = daily.needsResponse && activityAtMs >= preferences.getLong(KEY_DAILY_ACTIVITY_THRESHOLD_MS, daily.dueAtMs) &&
                confirmDailyCheckIn(nowMs, elapsedRealtimeMs = elapsedRealtimeMs) != null
            if (confirmedDaily) {
                DailyCheckInScheduler(appContext).ensureScheduled(nowMs)
                MonitoringService.cancelDailyNotification(appContext)
            }
            if (desiredEnabled) resetDeadline(activityAtMs, reason, elapsedRealtimeMs, nowMs)
            desiredEnabled || confirmedDaily
        }

    fun initializeDeadlineIfMissing(nowMs: Long = System.currentTimeMillis()) = synchronized(ACTIVITY_LOCK) {
        if (desiredEnabled && deadlineMs <= 0L) resetDeadline(nowMs, "초기 설정")
    }

    private fun nextInactivityEventMs(newDeadlineMs: Long): Long =
        maxOf(newDeadlineMs, inactivityEventMs + 1L)

    private fun configuredDeadlineMs(nowMs: Long): Long {
        val debugMinutes = debugMonitorMinutes
        return if (debugMinutes > 0) {
            nowMs + debugMinutes * 60_000L
        } else {
            DeadlineCalculator.deadlineMs(nowMs, monitorHours)
        }
    }

    fun markServiceStarting(nowMs: Long = System.currentTimeMillis()) = synchronized(ACTIVITY_LOCK) {
        preferences.edit()
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.STARTING.name)
            .putLong(KEY_STATE_UPDATED_MS, nowMs)
            .putString(KEY_SERVICE_ERROR, "")
            .apply()
    }

    fun markServiceRunning(
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ) = synchronized(ACTIVITY_LOCK) {
        rebaseAfterWallClockChange(nowMs, elapsedRealtimeMs)
        preferences.edit()
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.RUNNING.name)
            .putLong(KEY_STATE_UPDATED_MS, nowMs)
            .putLong(KEY_LAST_HEARTBEAT_MS, nowMs)
            .putLong(KEY_LAST_HEARTBEAT_ELAPSED_MS, elapsedRealtimeMs)
            .putLong(KEY_CLOCK_WALL_MS, nowMs)
            .putLong(KEY_CLOCK_ELAPSED_MS, elapsedRealtimeMs)
            .remove(KEY_WATCHDOG_ALERT_TOKEN)
            .putString(KEY_SERVICE_ERROR, "")
            .apply()
        InactivityDeadlineScheduler(appContext).ensureScheduled(nowMs)
    }

    fun markHeartbeat(
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): Boolean = synchronized(ACTIVITY_LOCK) {
        if (!desiredEnabled) return@synchronized false
        val clockCorrected = rebaseAfterWallClockChange(nowMs, elapsedRealtimeMs)
        preferences.edit()
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.RUNNING.name)
            .putLong(KEY_LAST_HEARTBEAT_MS, nowMs)
            .putLong(KEY_LAST_HEARTBEAT_ELAPSED_MS, elapsedRealtimeMs)
            .putLong(KEY_CLOCK_WALL_MS, nowMs)
            .putLong(KEY_CLOCK_ELAPSED_MS, elapsedRealtimeMs)
            .apply()
        clockCorrected
    }

    @SuppressLint("ApplySharedPref")
    fun rebaseAfterWallClockChange(
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): Boolean = synchronized(ACTIVITY_LOCK) {
        if (!desiredEnabled || deadlineMs <= 0L) return@synchronized false
        val previousWallMs = preferences.getLong(
            KEY_CLOCK_WALL_MS, preferences.getLong(KEY_LAST_HEARTBEAT_MS, 0L)
        )
        val previousElapsedMs = preferences.getLong(
            KEY_CLOCK_ELAPSED_MS, preferences.getLong(KEY_LAST_HEARTBEAT_ELAPSED_MS, -1L)
        )
        if (previousWallMs <= 0L || previousElapsedMs < 0L || elapsedRealtimeMs < previousElapsedMs) {
            return@synchronized false
        }

        val expectedWallMs = previousWallMs + (elapsedRealtimeMs - previousElapsedMs)
        val clockDeltaMs = nowMs - expectedWallMs
        if (abs(clockDeltaMs) < CLOCK_CHANGE_TOLERANCE_MS) return@synchronized false

        val oldDeadlineMs = deadlineMs
        val editor = preferences.edit()
            .putLong(KEY_INACTIVITY_EVENT_MS, inactivityEventMs)
            .putLong(KEY_DEADLINE_MS, oldDeadlineMs + clockDeltaMs)
            .putLong(KEY_CLOCK_WALL_MS, nowMs)
            .putLong(KEY_CLOCK_ELAPSED_MS, elapsedRealtimeMs)
        val heartbeatMs = preferences.getLong(KEY_LAST_HEARTBEAT_MS, 0L)
        if (heartbeatMs > 0L) editor.putLong(KEY_LAST_HEARTBEAT_MS, heartbeatMs + clockDeltaMs)
        val stateUpdatedMs = preferences.getLong(KEY_STATE_UPDATED_MS, 0L)
        if (stateUpdatedMs > 0L) editor.putLong(KEY_STATE_UPDATED_MS, stateUpdatedMs + clockDeltaMs)
        val lastActivityMs = preferences.getLong(KEY_LAST_ACTIVITY_MS, 0L)
        if (lastActivityMs > 0L) editor.putLong(KEY_LAST_ACTIVITY_MS, lastActivityMs + clockDeltaMs)
        if (wasPreAlerted(oldDeadlineMs)) {
            editor.putLong(KEY_PRE_ALERT_DEADLINE_MS, oldDeadlineMs + clockDeltaMs)
        }
        editor.commit()
        InactivityDeadlineScheduler(appContext).cancel()
        InactivityDeadlineScheduler(appContext).ensureScheduled(nowMs)
        true
    }

    fun markServiceError(message: String, nowMs: Long = System.currentTimeMillis()) = synchronized(ACTIVITY_LOCK) {
        if (!desiredEnabled) return@synchronized
        preferences.edit()
            .putString(KEY_RUNTIME_STATE, MonitoringRuntimeState.ERROR.name)
            .putLong(KEY_STATE_UPDATED_MS, nowMs)
            .putString(KEY_SERVICE_ERROR, message)
            .apply()
    }

    fun claimWatchdogAlert(token: Long): Boolean = synchronized(WATCHDOG_LOCK) {
        if (preferences.getLong(KEY_WATCHDOG_ALERT_TOKEN, Long.MIN_VALUE) == token) {
            return@synchronized false
        }
        preferences.edit().putLong(KEY_WATCHDOG_ALERT_TOKEN, token).commit()
    }

    fun dailyCheckInStatus(
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): DailyCheckInStatus {
        val result = synchronized(DAILY_LOCK) {
            val corrected = rebaseDailyResponseWindow(nowMs, elapsedRealtimeMs)
            val status = if (dailyCheckInEnabled && wasDailyCheckInPrompted(dailyNextDueAtMs) &&
                dailyNextDueAtMs > 0L && dailyResponseDeadlineAtMs > 0L
            ) {
                DailyCheckInStatus(
                    if (nowMs < dailyResponseDeadlineAtMs) DailyCheckInPhase.DUE else DailyCheckInPhase.OVERDUE,
                    dailyNextDueAtMs, dailyResponseDeadlineAtMs
                )
            } else DailyCheckInCalculator.status(
            nowMs = nowMs,
            enabled = dailyCheckInEnabled,
            nextDueAtMs = dailyNextDueAtMs,
            responseDeadlineAtMs = dailyResponseDeadlineAtMs
            )
            status to corrected
        }
        if (result.second) DailyCheckInScheduler(appContext).ensureScheduled(nowMs, elapsedRealtimeMs)
        return result.first
    }

    @SuppressLint("ApplySharedPref")
    private fun rebaseDailyResponseWindow(nowMs: Long, elapsedRealtimeMs: Long): Boolean {
        if (!dailyCheckInEnabled || !wasDailyCheckInPrompted(dailyNextDueAtMs) || dailyResponseDeadlineAtMs <= 0L) return false
        val previousWallMs = preferences.getLong(KEY_DAILY_CLOCK_WALL_MS, 0L)
        val previousElapsedMs = preferences.getLong(KEY_DAILY_CLOCK_ELAPSED_MS, -1L)
        if (previousWallMs <= 0L || previousElapsedMs < 0L) return false
        val newDeadline = if (elapsedRealtimeMs < previousElapsedMs) {
            // After a reboot, the elapsed clock cannot prove how long the phone was off.
            nowMs + DailyCheckInCalculator.RESPONSE_WINDOW_MS
        } else {
            val delta = nowMs - (previousWallMs + elapsedRealtimeMs - previousElapsedMs)
            if (abs(delta) < CLOCK_CHANGE_TOLERANCE_MS) return false
            dailyResponseDeadlineAtMs + delta
        }
        val deltaMs = newDeadline - dailyResponseDeadlineAtMs
        preferences.edit().putLong(KEY_DAILY_RESPONSE_DEADLINE_AT_MS, newDeadline)
            .putLong(KEY_DAILY_ACTIVITY_THRESHOLD_MS, preferences.getLong(KEY_DAILY_ACTIVITY_THRESHOLD_MS, dailyNextDueAtMs) + deltaMs)
            .putLong(KEY_DAILY_CLOCK_WALL_MS, nowMs)
            .putLong(KEY_DAILY_CLOCK_ELAPSED_MS, elapsedRealtimeMs).commit()
        return true
    }

    @SuppressLint("ApplySharedPref")
    fun configureDailyCheckIn(
        hour: Int?,
        nowMs: Long = System.currentTimeMillis()
    ): Long = synchronized(DAILY_LOCK) {
        if (hour == null) {
            preferences.edit()
                .putBoolean(KEY_DAILY_CHECK_IN_ENABLED, false)
                .remove(KEY_DAILY_NEXT_DUE_AT_MS)
                .remove(KEY_DAILY_PROMPTED_DUE_AT_MS)
                .remove(KEY_DAILY_ALERTED_DUE_AT_MS)
                .remove(KEY_DAILY_RESPONSE_DEADLINE_AT_MS)
                .remove(KEY_DAILY_CHECK_IN_ERROR)
                .commit()
            return@synchronized 0L
        }

        val normalizedHour = hour.coerceIn(0, 23)
        val nextDueAtMs = DailyCheckInCalculator.nextDueAt(nowMs, normalizedHour)
        preferences.edit()
            .putBoolean(KEY_DAILY_CHECK_IN_ENABLED, true)
            .putInt(KEY_DAILY_CHECK_IN_HOUR, normalizedHour)
            .putLong(KEY_DAILY_NEXT_DUE_AT_MS, nextDueAtMs)
            .remove(KEY_DAILY_PROMPTED_DUE_AT_MS)
            .remove(KEY_DAILY_ALERTED_DUE_AT_MS)
            .remove(KEY_DAILY_RESPONSE_DEADLINE_AT_MS)
            .remove(KEY_DAILY_CHECK_IN_ERROR)
            .remove(KEY_DAILY_CONFIRMED_DAY_START_MS)
            .remove(KEY_DAILY_PROMPTED_DAY_START_MS)
            .remove(KEY_DAILY_ALERTED_DAY_START_MS)
            .commit()
        nextDueAtMs
    }

    @SuppressLint("ApplySharedPref")
    fun ensureDailyCheckInScheduled(nowMs: Long = System.currentTimeMillis()): Long = synchronized(DAILY_LOCK) {
        if (!dailyCheckInEnabled) return@synchronized 0L
        if (dailyNextDueAtMs > 0L) return@synchronized dailyNextDueAtMs
        val dueAtMs = DailyCheckInCalculator.nextDueAt(nowMs, dailyCheckInHour)
        preferences.edit().putLong(KEY_DAILY_NEXT_DUE_AT_MS, dueAtMs).commit()
        dueAtMs
    }

    @SuppressLint("ApplySharedPref")
    fun recalculateDailyCheckInAfterClockChange(
        nowMs: Long = System.currentTimeMillis()
    ): Long = synchronized(DAILY_LOCK) {
        if (!dailyCheckInEnabled) return@synchronized 0L
        val currentDueAtMs = dailyNextDueAtMs
        val hasActivePrompt = currentDueAtMs > 0L &&
            wasDailyCheckInPrompted(currentDueAtMs) &&
            dailyResponseDeadlineAtMs > 0L
        if (hasActivePrompt) return@synchronized currentDueAtMs

        val recalculatedDueAtMs = DailyCheckInCalculator.nextDueAt(nowMs, dailyCheckInHour)
        preferences.edit()
            .putLong(KEY_DAILY_NEXT_DUE_AT_MS, recalculatedDueAtMs)
            .remove(KEY_DAILY_PROMPTED_DUE_AT_MS)
            .remove(KEY_DAILY_ALERTED_DUE_AT_MS)
            .remove(KEY_DAILY_RESPONSE_DEADLINE_AT_MS)
            .commit()
        recalculatedDueAtMs
    }
    @SuppressLint("ApplySharedPref")
    fun deferDailyCheckInToNow(nowMs: Long = System.currentTimeMillis()): Long = synchronized(DAILY_LOCK) {
        preferences.edit()
            .putLong(KEY_DAILY_NEXT_DUE_AT_MS, nowMs)
            .remove(KEY_DAILY_PROMPTED_DUE_AT_MS)
            .remove(KEY_DAILY_ALERTED_DUE_AT_MS)
            .remove(KEY_DAILY_RESPONSE_DEADLINE_AT_MS)
            .commit()
        nowMs
    }

    @SuppressLint("ApplySharedPref")
    fun markDailyCheckInPrompted(
        dueAtMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): Boolean = synchronized(DAILY_LOCK) {
        if (!dailyCheckInEnabled || dailyNextDueAtMs != dueAtMs) return@synchronized false
        preferences.edit()
            .putLong(KEY_DAILY_PROMPTED_DUE_AT_MS, dueAtMs)
            .putLong(KEY_DAILY_ACTIVITY_THRESHOLD_MS, dueAtMs)
            .putLong(KEY_DAILY_CLOCK_WALL_MS, nowMs)
            .putLong(KEY_DAILY_CLOCK_ELAPSED_MS, elapsedRealtimeMs)
            .putLong(
                KEY_DAILY_RESPONSE_DEADLINE_AT_MS,
                nowMs + DailyCheckInCalculator.RESPONSE_WINDOW_MS
            )
            .commit()
    }

    fun wasDailyCheckInPrompted(dueAtMs: Long): Boolean =
        preferences.getLong(KEY_DAILY_PROMPTED_DUE_AT_MS, 0L) == dueAtMs

    fun confirmDailyCheckIn(
        nowMs: Long = System.currentTimeMillis(),
        expectedDueAtMs: Long? = null,
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): Long? = synchronized(DAILY_LOCK) {
        val status = dailyCheckInStatus(nowMs, elapsedRealtimeMs)
        if (!status.needsResponse || status.dueAtMs <= 0L ||
            (expectedDueAtMs != null && status.dueAtMs != expectedDueAtMs)
        ) return@synchronized null
        val advanced = advanceDailyCheckIn(status.dueAtMs, nowMs)
        if (advanced) dailyCheckInError = ""
        status.dueAtMs.takeIf { advanced }
    }

    fun markDailyCheckInAlerted(dueAtMs: Long) = synchronized(DAILY_LOCK) {
        if (!dailyCheckInEnabled || dailyNextDueAtMs != dueAtMs) return@synchronized
        preferences.edit().putLong(KEY_DAILY_ALERTED_DUE_AT_MS, dueAtMs).apply()
    }

    fun wasDailyCheckInAlerted(dueAtMs: Long): Boolean =
        preferences.getLong(KEY_DAILY_ALERTED_DUE_AT_MS, 0L) == dueAtMs

    @SuppressLint("ApplySharedPref")
    fun advanceDailyCheckIn(completedDueAtMs: Long, notBeforeMs: Long = completedDueAtMs): Boolean = synchronized(DAILY_LOCK) {
        if (!dailyCheckInEnabled || dailyNextDueAtMs != completedDueAtMs) return@synchronized false
        val nextDueAtMs = DailyCheckInCalculator.nextDueAfter(completedDueAtMs, dailyCheckInHour)
            .takeIf { it > notBeforeMs }
            ?: DailyCheckInCalculator.nextDueAt(notBeforeMs, dailyCheckInHour)
        preferences.edit()
            .putLong(KEY_DAILY_NEXT_DUE_AT_MS, nextDueAtMs)
            .remove(KEY_DAILY_PROMPTED_DUE_AT_MS)
            .remove(KEY_DAILY_ALERTED_DUE_AT_MS)
            .remove(KEY_DAILY_RESPONSE_DEADLINE_AT_MS)
            .commit()
    }
    @SuppressLint("ApplySharedPref")
    fun beginSos(nowMs: Long = System.currentTimeMillis()): Long = synchronized(SOS_LOCK) {
        if (sosEventMs > 0L) return@synchronized sosEventMs
        preferences.edit().putLong(KEY_PENDING_SOS_EVENT_MS, nowMs).commit()
        nowMs
    }

    @SuppressLint("ApplySharedPref")
    fun claimPendingSos(
        nowMs: Long = System.currentTimeMillis(),
        expectedEventMs: Long? = null
    ): Long? = synchronized(SOS_LOCK) {
        val active = activeSosEventMs
        if (active > 0L) {
            return@synchronized active.takeIf { expectedEventMs == null || it == expectedEventMs }
        }
        val pending = pendingSosEventMs
        if (pending <= 0L || pending > nowMs ||
            (expectedEventMs != null && pending != expectedEventMs)
        ) return@synchronized null
        preferences.edit()
            .remove(KEY_PENDING_SOS_EVENT_MS)
            .putLong(KEY_ACTIVE_SOS_EVENT_MS, pending)
            .commit()
        pending
    }

    @SuppressLint("ApplySharedPref")
    fun cancelPendingSos(): Boolean = synchronized(SOS_LOCK) {
        if (pendingSosEventMs <= 0L) return@synchronized false
        preferences.edit().remove(KEY_PENDING_SOS_EVENT_MS).commit()
    }

    fun completeActiveSos(eventMs: Long) {
        synchronized(SOS_LOCK) {
            if (activeSosEventMs == eventMs) {
                preferences.edit().remove(KEY_ACTIVE_SOS_EVENT_MS).apply()
            }
        }
    }

    fun clearSos() {
        synchronized(SOS_LOCK) {
            preferences.edit()
                .remove(KEY_PENDING_SOS_EVENT_MS)
                .remove(KEY_ACTIVE_SOS_EVENT_MS)
                .apply()
        }
    }
    fun markPreAlert(deadlineMs: Long) = synchronized(ACTIVITY_LOCK) {
        if (this.deadlineMs != deadlineMs) return@synchronized
        preferences.edit().putLong(KEY_PRE_ALERT_DEADLINE_MS, deadlineMs).apply()
    }

    fun markEmergency(eventMs: Long) = synchronized(ACTIVITY_LOCK) {
        if (inactivityEventMs != eventMs) return@synchronized
        preferences.edit().putLong(KEY_EMERGENCY_DEADLINE_MS, eventMs).apply()
    }

    fun wasPreAlerted(deadlineMs: Long): Boolean =
        preferences.getLong(KEY_PRE_ALERT_DEADLINE_MS, -1L) == deadlineMs

    fun wasEmergencyDispatched(eventMs: Long): Boolean =
        preferences.getLong(KEY_EMERGENCY_DEADLINE_MS, -1L) == eventMs

    fun snapshot(nowMs: Long = System.currentTimeMillis()): MonitoringSnapshot = synchronized(ACTIVITY_LOCK) {
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
        val alertState = MonitoringAlertStatePolicy.resolve(
            desiredEnabled = desiredEnabled,
            runtimeState = runtimeState,
            deadlineMs = currentDeadline,
            remainingSeconds = remaining,
            preAlerted = wasPreAlerted(currentDeadline),
            emergencyDispatched = wasEmergencyDispatched(inactivityEventMs)
        )
        MonitoringSnapshot(
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
        private val ACTIVITY_LOCK = Any()
        private val DAILY_LOCK = Any()
        private const val FILE_NAME = "lifelink_monitoring"
        private const val KEY_MONITOR_HOURS = "monitor_hours"
        // Keep the existing preference key so upgrades preserve the user's intent.
        private const val KEY_DESIRED_ENABLED = "monitoring_enabled"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_SETUP_STEP = "setup_step"
        private const val KEY_ONBOARDING_VERSION = "onboarding_version"
        private const val KEY_TEST_SMS_STATE = "test_sms_state"
        private const val KEY_TEST_SMS_CONTACT_ID = "test_sms_contact_id"
        private const val KEY_TEST_SMS_EVENT_ID = "test_sms_event_id"
        private const val KEY_TEST_SMS_MESSAGE = "test_sms_message"
        private const val KEY_DEBUG_MONITOR_MINUTES = "debug_monitor_minutes"
        private const val KEY_DEADLINE_MS = "deadline_ms"
        private const val KEY_INACTIVITY_EVENT_MS = "inactivity_event_ms"
        private const val KEY_LAST_ACTIVITY_MS = "last_activity_ms"
        private const val KEY_LAST_ACTIVITY_REASON = "last_activity_reason"
        private const val KEY_PRE_ALERT_DEADLINE_MS = "pre_alert_deadline_ms"
        private const val KEY_EMERGENCY_DEADLINE_MS = "emergency_deadline_ms"
        private const val KEY_RUNTIME_STATE = "runtime_state"
        private const val KEY_STATE_UPDATED_MS = "runtime_state_updated_ms"
        private const val KEY_LAST_HEARTBEAT_MS = "last_heartbeat_ms"
        private const val KEY_LAST_HEARTBEAT_ELAPSED_MS = "last_heartbeat_elapsed_ms"
        private const val KEY_CLOCK_WALL_MS = "clock_wall_ms"
        private const val KEY_CLOCK_ELAPSED_MS = "clock_elapsed_ms"
        private const val KEY_WATCHDOG_ALERT_TOKEN = "watchdog_alert_token"
        private const val KEY_SERVICE_ERROR = "service_error"
        private const val KEY_DEVICE_ALIAS = "device_alias"
        private const val KEY_SMS_SUBSCRIPTION_ID = "sms_subscription_id"
        private const val KEY_DAILY_CHECK_IN_ENABLED = "daily_check_in_enabled"
        private const val KEY_DAILY_CHECK_IN_HOUR = "daily_check_in_hour"
        private const val KEY_DAILY_NEXT_DUE_AT_MS = "daily_next_due_at_ms"
        private const val KEY_DAILY_PROMPTED_DUE_AT_MS = "daily_prompted_due_at_ms"
        private const val KEY_DAILY_ALERTED_DUE_AT_MS = "daily_alerted_due_at_ms"
        private const val KEY_DAILY_RESPONSE_DEADLINE_AT_MS = "daily_response_deadline_at_ms"
        private const val KEY_DAILY_CLOCK_WALL_MS = "daily_clock_wall_ms"
        private const val KEY_DAILY_CLOCK_ELAPSED_MS = "daily_clock_elapsed_ms"
        private const val KEY_DAILY_ACTIVITY_THRESHOLD_MS = "daily_activity_threshold_ms"
        private const val KEY_DAILY_CHECK_IN_ERROR = "daily_check_in_error"
        private const val KEY_DAILY_CONFIRMED_DAY_START_MS = "daily_confirmed_day_start_ms"
        private const val KEY_DAILY_PROMPTED_DAY_START_MS = "daily_prompted_day_start_ms"
        private const val KEY_DAILY_ALERTED_DAY_START_MS = "daily_alerted_day_start_ms"
        private const val KEY_PENDING_SOS_EVENT_MS = "pending_sos_event_ms"
        private const val KEY_ACTIVE_SOS_EVENT_MS = "active_sos_event_ms"
        private const val INVALID_SUBSCRIPTION_ID = -1
        private const val DEFAULT_DEVICE_ALIAS = "라이프링크 사용자"
        const val CURRENT_ONBOARDING_VERSION = 1
        const val HEARTBEAT_TIMEOUT_MS = 150_000L
        const val START_TIMEOUT_MS = 30_000L
        private const val CLOCK_CHANGE_TOLERANCE_MS = 2_000L
        private val SOS_LOCK = Any()
        private val WATCHDOG_LOCK = Any()
    }
}

internal object MonitoringAlertStatePolicy {
    fun resolve(
        desiredEnabled: Boolean,
        runtimeState: MonitoringRuntimeState,
        deadlineMs: Long,
        remainingSeconds: Long,
        preAlerted: Boolean,
        emergencyDispatched: Boolean
    ): Int = when {
        !desiredEnabled || deadlineMs <= 0L -> 0
        emergencyDispatched -> 2
        runtimeState != MonitoringRuntimeState.RUNNING -> 0
        remainingSeconds == 0L -> 2
        preAlerted || remainingSeconds <= DeadlineCalculator.PRE_ALERT_SECONDS -> 1
        else -> 0
    }
}
