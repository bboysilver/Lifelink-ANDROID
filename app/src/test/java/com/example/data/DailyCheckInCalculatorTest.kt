package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCheckInCalculatorTest {
    private val dayStartMs = 1_700_000_000_000L

    @Test
    fun disabledScheduleNeverRequestsAResponse() {
        val status = DailyCheckInCalculator.status(
            nowMs = dayStartMs + 12 * HOUR_MS,
            enabled = false,
            hour = 9,
            lastConfirmedDayStartMs = 0L,
            timeZoneId = "UTC"
        )

        assertEquals(DailyCheckInPhase.DISABLED, status.phase)
        assertFalse(status.needsResponse)
    }

    @Test
    fun dueAndOverdueWindowsAreDistinct() {
        val actualDayStart = DailyCheckInCalculator.status(
            nowMs = dayStartMs,
            enabled = true,
            hour = 9,
            lastConfirmedDayStartMs = 0L,
            timeZoneId = "UTC"
        ).dayStartMs

        val due = DailyCheckInCalculator.status(
            nowMs = actualDayStart + 9 * HOUR_MS,
            enabled = true,
            hour = 9,
            lastConfirmedDayStartMs = 0L,
            timeZoneId = "UTC"
        )
        val overdue = DailyCheckInCalculator.status(
            nowMs = actualDayStart + 11 * HOUR_MS,
            enabled = true,
            hour = 9,
            lastConfirmedDayStartMs = 0L,
            timeZoneId = "UTC"
        )

        assertEquals(DailyCheckInPhase.DUE, due.phase)
        assertEquals(DailyCheckInPhase.OVERDUE, overdue.phase)
        assertTrue(due.needsResponse)
    }

    @Test
    fun confirmationCompletesOnlyTheCurrentDay() {
        val initial = DailyCheckInCalculator.status(
            nowMs = dayStartMs,
            enabled = true,
            hour = 9,
            lastConfirmedDayStartMs = 0L,
            timeZoneId = "UTC"
        )
        val confirmed = DailyCheckInCalculator.status(
            nowMs = initial.dayStartMs + 12 * HOUR_MS,
            enabled = true,
            hour = 9,
            lastConfirmedDayStartMs = initial.dayStartMs,
            timeZoneId = "UTC"
        )

        assertEquals(DailyCheckInPhase.COMPLETE, confirmed.phase)
    }

    private companion object {
        const val HOUR_MS = 60L * 60L * 1_000L
    }
}
