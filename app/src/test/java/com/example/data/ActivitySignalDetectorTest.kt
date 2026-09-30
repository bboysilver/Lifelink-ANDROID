package com.example.data

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitySignalDetectorTest {
    @Test
    fun screenOnIsNotAnActivitySignal() {
        assertNull(ActivitySignalClassifier.reasonForBroadcast(Intent.ACTION_SCREEN_ON))
    }

    @Test
    fun userPresentIsAnActivitySignal() {
        assertTrue(
            ActivitySignalClassifier.reasonForBroadcast(Intent.ACTION_USER_PRESENT)
                ?.contains("잠금 해제") == true
        )
    }

    @Test
    fun oneImpactDoesNotReportActivity() {
        val detector = RepeatedMotionDetector()

        assertFalse(detector.record(1_000L))
    }

    @Test
    fun tightlyGroupedImpactsDoNotReportActivity() {
        val detector = RepeatedMotionDetector()

        assertFalse(detector.record(1_000L))
        assertFalse(detector.record(1_200L))
        assertFalse(detector.record(1_400L))
        assertFalse(detector.record(1_600L))
    }

    @Test
    fun repeatedMotionAcrossThreeSecondsReportsOnce() {
        val detector = RepeatedMotionDetector()

        assertFalse(detector.record(1_000L))
        assertFalse(detector.record(2_000L))
        assertFalse(detector.record(3_000L))
        assertTrue(detector.record(4_000L))
        assertFalse(detector.record(5_000L))
    }

    @Test
    fun oldMotionFallsOutsideTheWindow() {
        val detector = RepeatedMotionDetector()

        assertFalse(detector.record(1_000L))
        assertFalse(detector.record(20_000L))
        assertFalse(detector.record(21_000L))
        assertFalse(detector.record(22_000L))
    }

    @Test
    fun resetClearsAnEarlierDetectionCooldown() {
        val detector = RepeatedMotionDetector()
        detector.record(1_000L)
        detector.record(2_000L)
        detector.record(3_000L)
        assertTrue(detector.record(4_000L))

        detector.reset()

        assertFalse(detector.record(5_000L))
        assertFalse(detector.record(6_000L))
        assertFalse(detector.record(7_000L))
        assertTrue(detector.record(8_000L))
    }

    @Test
    fun backwardsEventClockDoesNotLeaveTheDetectorStuckInCooldown() {
        val detector = RepeatedMotionDetector()
        detector.record(11_000L)
        detector.record(12_000L)
        detector.record(13_000L)
        assertTrue(detector.record(14_000L))

        assertFalse(detector.record(1_000L))
        assertFalse(detector.record(2_000L))
        assertFalse(detector.record(3_000L))
        assertTrue(detector.record(4_000L))
    }
}
