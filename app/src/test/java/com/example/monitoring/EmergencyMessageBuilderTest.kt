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

    @Test
    fun sosMessageIsClearlyIdentifiedAndIncludesTime() {
        val message = EmergencyMessageBuilder.buildSos("어머니", 1_700_000_000_000L)

        assertTrue(message.contains("SOS"))
        assertTrue(message.contains("도움 요청"))
        assertTrue(message.contains("요청 시각"))
    }

    @Test
    fun missedDailyCheckInMessageDoesNotClaimAnEmergencyService() {
        val message = EmergencyMessageBuilder.buildDailyCheckInMissed("아버지")

        assertTrue(message.contains("안부 확인"))
        assertTrue(message.contains("응답하지 않았습니다"))
        assertTrue(message.contains("직접 연락"))
    }
}
