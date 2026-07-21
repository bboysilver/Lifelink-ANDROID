package com.example.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyMessageBuilderTest {
    @Test
    fun missingBatteryIsReportedWithoutInventingLocation() {
        val message = EmergencyMessageBuilder.build(
            deviceAlias = "아버지 휴대전화",
            batteryPercent = null
        )

        assertTrue(message.contains("아버지 휴대전화"))
        assertTrue(message.contains("배터리: 확인 불가"))
        assertTrue(message.contains("위치 정보는 수집하지 않습니다"))
        assertFalse(message.contains("37.5665"))
        assertFalse(message.contains("78%"))
    }

    @Test
    fun batteryAndDeviceAliasAreIncluded() {
        val message = EmergencyMessageBuilder.build("어머니", 42)

        assertTrue(message.contains("어머니"))
        assertTrue(message.contains("배터리: 42%"))
    }

    @Test
    fun testMessageIsClearlyMarkedAsTest() {
        val message = EmergencyMessageBuilder.buildTest("어머니")

        assertTrue(message.contains("테스트"))
        assertTrue(message.contains("긴급 상황이 아닙니다"))
    }
}
