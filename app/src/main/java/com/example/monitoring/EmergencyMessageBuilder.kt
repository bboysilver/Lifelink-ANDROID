package com.example.monitoring

object EmergencyMessageBuilder {
    fun build(
        deviceAlias: String,
        batteryPercent: Int?,
        lastActivityMs: Long = 0L,
        monitorHours: Int? = null,
        deadlineMs: Long = 0L,
        evaluatedAtMs: Long = 0L,
        monitoringInterrupted: Boolean = false
    ): String {
        val batteryText = batteryPercent?.let { "$it%" } ?: "확인 불가"
        val delayMs = if (deadlineMs > 0L && evaluatedAtMs >= deadlineMs) evaluatedAtMs - deadlineMs else 0L
        val delayed = delayMs >= 60_000L
        val delayNotice = if (delayed) {
            "예정 마감: ${formatTime(deadlineMs)}\n" +
                "점검 시각: ${formatTime(evaluatedAtMs)}\n" +
                "${delayMs / 60_000L}분 지연 점검입니다. 앱 중단·절전 등으로 활동 기록이 누락됐을 수 있습니다.\n"
        } else ""
        return "[라이프링크 안전 알림]\n" +
            (if (delayed) "$deviceAlias 님의 활동 기록이 없어 늦게 안전 확인을 요청합니다.\n"
                else "$deviceAlias 님의 스마트폰 활동이 설정 시간 동안 확인되지 않았습니다.\n") +
            "발송 사유: ${monitorHours?.let { "${it}시간" } ?: "설정 시간"} ${if (delayed) "마감 후 지연 점검" else "무활동"}\n" +
            "마지막 감지 활동: ${formatTime(lastActivityMs)}\n" +
            delayNotice +
            (if (monitoringInterrupted) "활동 감시 상태 확인 불가: 감시 중단 등으로 활동 기록이 누락됐을 수 있습니다.\n" else "") +
            "배터리: $batteryText\n" +
            "위치 정보는 수집하지 않습니다.\n" +
            "같은 무활동 경고를 설정 시간마다 반복하지 않습니다.\n" +
            "추가 문자를 기다리지 말고 지금 직접 연락해 안전을 확인해 주세요.\n" +
            RESPONSE_GUIDANCE
    }

    fun buildSos(deviceAlias: String, requestedAtMs: Long): String {
        val requestedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA)
            .format(java.util.Date(requestedAtMs))
        return "[라이프링크 SOS]\n" +
            "$deviceAlias 님이 도움 요청 버튼을 눌렀습니다.\n" +
            "요청 시각: $requestedAt\n" +
            "위치 정보는 수집하지 않습니다. 즉시 직접 연락해 주세요.\n" + RESPONSE_GUIDANCE
    }

    fun buildDailyCheckInMissed(
        deviceAlias: String,
        dueAtMs: Long = 0L,
        lastActivityMs: Long = 0L
    ): String =
        "[라이프링크 안부 확인]\n" +
            "$deviceAlias 님의 안부 확인 기간에 유효한 활동이나 응답이 확인되지 않았습니다.\n" +
            "발송 사유: 안부 요청 후 2시간 동안 유효 활동·응답 없음\n" +
            "안부 확인 기준 시각: ${formatTime(dueAtMs)}\n" +
            "마지막 감지 활동: ${formatTime(lastActivityMs)}\n" +
            "위치 정보는 수집하지 않습니다. 직접 연락해 안전을 확인해 주세요.\n" + RESPONSE_GUIDANCE

    private const val RESPONSE_GUIDANCE =
        "연락이 안 되면 다른 보호자·가까운 분과 확인해 주세요. 응급상황이 의심되면 기다리지 말고 119에 알려주세요. 앱이 자동 신고하지는 않습니다."

    private fun formatTime(timestampMs: Long): String = if (timestampMs > 0L) {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm z", java.util.Locale.KOREA)
            .format(java.util.Date(timestampMs))
    } else {
        "기록 없음"
    }

    fun buildTest(deviceAlias: String): String =
        "[라이프링크 테스트]\n" +
            "$deviceAlias 님의 긴급 연락처 테스트 문자입니다.\n" +
            "긴급 상황이 아닙니다. 이 문자를 받았다면 SIM 문자 발송 설정이 정상입니다."
}
