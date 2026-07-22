package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailyCheckInCalculatorTest {
    @Test
    fun selectingAnAlreadyPassedHourStartsTomorrow() {
        val nowMs = utcTime(2026, Calendar.JULY, 22, 14)

        val dueAtMs = DailyCheckInCalculator.nextDueAt(nowMs, hour = 9, timeZoneId = "UTC")

        assertEquals(utcTime(2026, Calendar.JULY, 23, 9), dueAtMs)
    }

    @Test
    fun selectingAFutureHourStartsToday() {
        val nowMs = utcTime(2026, Calendar.JULY, 22, 14)

        val dueAtMs = DailyCheckInCalculator.nextDueAt(nowMs, hour = 18, timeZoneId = "UTC")

        assertEquals(utcTime(2026, Calendar.JULY, 22, 18), dueAtMs)
    }

    @Test
    fun dueAndOverdueWindowsUseTheStoredDueTime() {
        val dueAtMs = utcTime(2026, Calendar.JULY, 22, 9)

        val due = DailyCheckInCalculator.status(
            nowMs = dueAtMs,
            enabled = true,
            nextDueAtMs = dueAtMs
        )
        val overdue = DailyCheckInCalculator.status(
            nowMs = dueAtMs + DailyCheckInCalculator.RESPONSE_WINDOW_MS,
            enabled = true,
            nextDueAtMs = dueAtMs
        )

        assertEquals(DailyCheckInPhase.DUE, due.phase)
        assertEquals(DailyCheckInPhase.OVERDUE, overdue.phase)
        assertTrue(due.needsResponse)
    }

    @Test
    fun disabledScheduleNeverRequestsAResponse() {
        val status = DailyCheckInCalculator.status(
            nowMs = utcTime(2026, Calendar.JULY, 22, 12),
            enabled = false,
            nextDueAtMs = utcTime(2026, Calendar.JULY, 22, 9)
        )

        assertEquals(DailyCheckInPhase.DISABLED, status.phase)
        assertFalse(status.needsResponse)
    }

    private fun utcTime(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis
}