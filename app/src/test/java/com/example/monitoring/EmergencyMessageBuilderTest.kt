package com.example.monitoring

import android.location.Location
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmergencyMessageBuilderTest {
    @Test
    fun missingSignalsAreReportedAsUnavailable() {
        val message = EmergencyMessageBuilder.build(location = null, batteryPercent = null)

        assertTrue(message.contains("위치: 위치 확인 불가"))
        assertTrue(message.contains("배터리: 확인 불가"))
        assertFalse(message.contains("37.5665"))
        assertFalse(message.contains("78%"))
    }

    @Test
    fun availableLocationAndBatteryAreIncluded() {
        val location = Location("test").apply {
            latitude = 35.1796
            longitude = 129.0756
        }

        val message = EmergencyMessageBuilder.build(location, 42)

        assertTrue(message.contains("35.1796,129.0756"))
        assertTrue(message.contains("배터리: 42%"))
    }
}
