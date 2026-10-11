package com.example.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringUpdatePolicyTest {
    @Test
    fun heartbeatIsWrittenAtMostOncePerMinute() {
        assertFalse(MonitoringUpdatePolicy.shouldWriteHeartbeat(1_000L, 60_999L))
        assertTrue(MonitoringUpdatePolicy.shouldWriteHeartbeat(1_000L, 61_000L))
    }

}
