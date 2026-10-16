package com.example.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyMessageBuilderTest {
    @Test
    fun inactivityMessageHasCauseAndRealLastActivityTime() {
        val timestamp = 1_700_000_000_000L
        val expectedTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm z", java.util.Locale.KOREA)
            .format(java.util.Date(timestamp))
        val message = EmergencyMessageBuilder.build("어머니", 42, timestamp, 12)
        assertTrue(message.contains("발송 사유: 12시간 무활동"))
        assertTrue(message.contains("마지막 감지 활동: $expectedTime"))
    }

    @Test
    fun dayLateEvaluationExplainsTheDelayRatherThanClaimingContinuousInactivity() {
        val deadline = 1_700_000_000_000L
        val message = EmergencyMessageBuilder.build("업무용 폰", 72, deadline - 6 * 3_600_000L, 6,
            deadlineMs = deadline, evaluatedAtMs = deadline + 83_050_000L)
        assertTrue(message.contains("1384분 지연 점검"))
        assertTrue(message.contains("예정 마감:"))
        assertTrue(message.contains("점검 시각:"))
        assertTrue(message.contains("활동 기록이 누락됐을 수 있습니다"))
        assertFalse(message.contains("6시간 무활동"))
    }

    @Test
    fun fiftyTwoMinuteDelayIsNotHiddenInTheNormalMessage() {
        val deadline = 1_700_000_000_000L
        val message = EmergencyMessageBuilder.build("업무용 폰", 72, deadline - 6 * 3_600_000L, 6,
            deadlineMs = deadline, evaluatedAtMs = deadline + 3_161_000L)
        assertTrue(message.contains("52분 지연 점검"))
        assertTrue(message.contains("6시간 마감 후 지연 점검"))
    }

    @Test
    fun onTimeEvaluationKeepsTheCoreInactivityWarning() {
        val deadline = 1_700_000_000_000L
        val message = EmergencyMessageBuilder.build("업무용 폰", 72, deadline - 6 * 3_600_000L, 6,
            deadlineMs = deadline, evaluatedAtMs = deadline + 59_999L)
        assertTrue(message.contains("6시간 무활동"))
        assertFalse(message.contains("지연 점검"))
    }

    @Test
    fun unknownActivityDoesNotBecome1970OrSafetyClaim() {
        val message = EmergencyMessageBuilder.build("어머니", null)
        assertTrue(message.contains("마지막 감지 활동: 기록 없음"))
        assertFalse(message.contains("1970"))
        assertFalse(message.contains("안전합니다"))
    }

    @Test
    fun dailyMessageExplainsIndependentTwoHourReason() {
        val message = EmergencyMessageBuilder.buildDailyCheckInMissed("아버지", 1_700_000_000_000L, 1_699_999_000_000L)
        assertTrue(message.contains("2시간 동안 유효 활동·응답 없음"))
        assertTrue(message.contains("안부 확인 기준 시각"))
        assertTrue(message.contains("마지막 감지 활동"))
    }

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
        assertTrue(message.contains("유효한 활동이나 응답이 확인되지 않았습니다"))
        assertTrue(message.contains("직접 연락"))
    }
}
