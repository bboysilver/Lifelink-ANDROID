package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeadlineCalculatorTest {
    @Test
    fun deadlineIsBasedOnLastActivityInsteadOfAnInMemoryCountdown() {
        val lastActivity = 1_000L

        assertEquals(lastActivity + 12 * 60 * 60 * 1_000L, DeadlineCalculator.deadlineMs(lastActivity, 12))
    }

    @Test
    fun monitorHoursAreClampedToSupportedRange() {
        assertEquals(6 * 60 * 60 * 1_000L, DeadlineCalculator.deadlineMs(0L, 1))
        assertEquals(72 * 60 * 60 * 1_000L, DeadlineCalculator.deadlineMs(0L, 100))
    }

    @Test
    fun remainingSecondsRoundsUpAndNeverBecomesNegative() {
        assertEquals(2L, DeadlineCalculator.remainingSeconds(deadlineMs = 2_001L, nowMs = 1_000L))
        assertEquals(0L, DeadlineCalculator.remainingSeconds(deadlineMs = 999L, nowMs = 1_000L))
    }
}
