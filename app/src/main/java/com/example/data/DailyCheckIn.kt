package com.example.data

import java.util.Calendar

enum class DailyCheckInPhase { DISABLED, UPCOMING, DUE, OVERDUE, COMPLETE }

data class DailyCheckInStatus(
    val phase: DailyCheckInPhase,
    val dayStartMs: Long,
    val dueAtMs: Long,
    val overdueAtMs: Long
) {
    val needsResponse: Boolean
        get() = phase == DailyCheckInPhase.DUE || phase == DailyCheckInPhase.OVERDUE
}

object DailyCheckInCalculator {
    const val DEFAULT_HOUR = 9
    const val RESPONSE_WINDOW_MS = 2 * 60 * 60 * 1_000L

    fun status(
        nowMs: Long,
        enabled: Boolean,
        hour: Int,
        lastConfirmedDayStartMs: Long,
        timeZoneId: String = java.util.TimeZone.getDefault().id
    ): DailyCheckInStatus {
        val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone(timeZoneId)).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStartMs = calendar.timeInMillis
        val dueAtMs = dayStartMs + hour.coerceIn(0, 23) * 60L * 60L * 1_000L
        val overdueAtMs = dueAtMs + RESPONSE_WINDOW_MS
        val phase = when {
            !enabled -> DailyCheckInPhase.DISABLED
            lastConfirmedDayStartMs == dayStartMs -> DailyCheckInPhase.COMPLETE
            nowMs < dueAtMs -> DailyCheckInPhase.UPCOMING
            nowMs < overdueAtMs -> DailyCheckInPhase.DUE
            else -> DailyCheckInPhase.OVERDUE
        }
        return DailyCheckInStatus(phase, dayStartMs, dueAtMs, overdueAtMs)
    }
}
