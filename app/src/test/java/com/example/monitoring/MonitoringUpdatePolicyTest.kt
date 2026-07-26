package com.example.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringUpdatePolicyTest {
    @Test
    fun heartbeatIsWrittenAtMostOncePerMinute() {
        assertFalse(MonitoringUpdatePolicy.shouldWriteHeartbeat(1_000L, 60_999L))
        assertTrue(MonitoringUpdatePolicy.shouldWriteHeartbeat(1_000L, 61_000L))
    }

    @Test
    fun ongoingNotificationBucketChangesOncePerRemainingMinute() {
        assertEquals(2L, MonitoringUpdatePolicy.remainingMinute(120L))
        assertEquals(2L, MonitoringUpdatePolicy.remainingMinute(61L))
        assertEquals(1L, MonitoringUpdatePolicy.remainingMinute(60L))
        assertEquals(0L, MonitoringUpdatePolicy.remainingMinute(0L))
    }
}