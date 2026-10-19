package com.example.data

import org.junit.Assert.*
import org.junit.Test

class ActivityEvidenceTest {
    @Test fun screenWakesAndAlreadyUnlockedRestartsAreNotActivity() {
        val detector = UserPresenceDetector()
        assertFalse(detector.record(false, false, false, 1_000))
        assertFalse(detector.record(true, false, false, 2_000))
        detector.reset()
        assertFalse(detector.record(true, false, false, 3_000))
    }

    @Test fun observedUnlockIsRecoveredWithoutTheBroadcast() {
        val detector = UserPresenceDetector()
        assertFalse(detector.record(false, true, false, 1_000))
        assertFalse(detector.record(true, true, false, 2_000))
        assertTrue(detector.record(true, false, false, 3_000))
        assertFalse(detector.record(true, false, false, 4_000))
    }

    @Test fun explicitUnlockWaitsForUnlockedInteractiveStateAndDeduplicates() {
        val detector = UserPresenceDetector()
        assertFalse(detector.record(false, true, true, 1_000))
        assertTrue(detector.record(true, false, false, 2_000))
        assertFalse(detector.record(true, false, true, 2_500))
        assertTrue(detector.record(true, false, true, 60_000))
    }

    @Test fun restartingDoesNotReuseAnObservedLock() {
        val detector = UserPresenceDetector()
        detector.record(false, true, false, 1_000)
        detector.reset()
        assertFalse(detector.record(true, false, false, 2_000))
    }

    @Test fun staleUnlockAfterHoursWithoutObservationIsNotActivityNow() {
        val detector = UserPresenceDetector()
        detector.record(false, true, false, 1_000)
        assertFalse(detector.record(true, false, false, 8 * 3_600_000L))
        assertFalse(detector.record(false, true, false, 8 * 3_600_000L + 1_000))
        assertTrue(detector.record(true, false, false, 8 * 3_600_000L + 2_000))
    }

    @Test fun stepCounterRequiresAnInitialBaselineAndRepeatedNewSteps() {
        val detector = RepeatedStepCounterDetector()
        assertFalse(detector.record(12_000f, 1_000))
        assertFalse(detector.record(12_001f, 2_000))
        assertFalse(detector.record(12_002f, 3_000))
        assertTrue(detector.record(12_003f, 4_000))
        assertFalse(detector.record(12_004f, 5_000))
    }

    @Test fun counterCannotAccumulateScatteredStepsAcrossHours() {
        val detector = RepeatedStepCounterDetector()
        assertFalse(detector.record(100f, 1_000))
        assertFalse(detector.record(101f, 61_000))
        assertFalse(detector.record(102f, 121_000))
        assertFalse(detector.record(103f, 181_000))
        assertTrue(detector.record(106f, 184_000))
    }

    @Test fun counterResetsDuplicatesAndMalformedValuesAreNotActivity() {
        val detector = RepeatedStepCounterDetector()
        assertFalse(detector.record(Float.NaN, 100))
        assertFalse(detector.record(Float.POSITIVE_INFINITY, 200))
        assertFalse(detector.record(-1f, 300))
        assertFalse(detector.record(1.5f, 400))
        assertFalse(detector.record(100f, 1_000))
        assertFalse(detector.record(103f, 1_500))
        assertFalse(detector.record(103f, 4_000))
        assertFalse(detector.record(0f, 5_000))
        assertTrue(detector.record(3f, 8_000))
        detector.reset()
        assertFalse(detector.record(5_000f, 10_000))
    }

    @Test fun counterBackwardsTimestampsStartANewBaseline() {
        val detector = RepeatedStepCounterDetector()
        detector.record(100f, 10_000)
        assertTrue(detector.record(103f, 13_000))
        assertFalse(detector.record(106f, 1_000))
        assertFalse(detector.record(107f, 2_000))
    }
}
