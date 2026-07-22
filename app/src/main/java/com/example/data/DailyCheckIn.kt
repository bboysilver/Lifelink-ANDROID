package com.example.data

import java.util.Calendar
import java.util.TimeZone

enum class DailyCheckInPhase { DISABLED, UPCOMING, DUE, OVERDUE }

data class DailyCheckInStatus(
    val phase: DailyCheckInPhase,
    val dueAtMs: Long,
    val overdueAtMs: Long
) {
    val needsResponse: Boolean
        get() = phase == DailyCheckInPhase.DUE || phase == DailyCheckInPhase.OVERDUE
}

object DailyCheckInCalculator {
    const val DEFAULT_HOUR = 9
    const val RESPONSE_WINDOW_MS = 2 * 60 * 60 * 1_000L

    fun nextDueAt(
        nowMs: Long,
        hour: Int,
        timeZoneId: String = TimeZone.getDefault().id
    ): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId)).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMs) add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    fun nextDueAfter(
        dueAtMs: Long,
        hour: Int,
        timeZoneId: String = TimeZone.getDefault().id
    ): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId)).apply {
            timeInMillis = dueAtMs
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun status(nowMs: Long, enabled: Boolean, nextDueAtMs: Long): DailyCheckInStatus {
        val overdueAtMs = nextDueAtMs + RESPONSE_WINDOW_MS
        val phase = when {
            !enabled -> DailyCheckInPhase.DISABLED
            nextDueAtMs <= 0L || nowMs < nextDueAtMs -> DailyCheckInPhase.UPCOMING
            nowMs < overdueAtMs -> DailyCheckInPhase.DUE
            else -> DailyCheckInPhase.OVERDUE
        }
        return DailyCheckInStatus(phase, nextDueAtMs, overdueAtMs)
    }
}