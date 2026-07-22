package com.example.monitoring

object EmergencyMessageBuilder {
    fun build(deviceAlias: String, batteryPercent: Int?): String {
        val batteryText = batteryPercent?.let { "$it%" } ?: "확인 불가"
        return "[라이프링크 안전 알림]\n" +
            "$deviceAlias 님의 스마트폰 활동이 설정 시간 동안 확인되지 않았습니다.\n" +
            "배터리: $batteryText\n" +
            "위치 정보는 수집하지 않습니다.\n" +
            "직접 연락해 안전을 확인해 주세요."
    }

    fun buildSos(deviceAlias: String, requestedAtMs: Long): String {
        val requestedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA)
            .format(java.util.Date(requestedAtMs))
        return "[라이프링크 SOS]\n" +
            "$deviceAlias 님이 도움 요청 버튼을 눌렀습니다.\n" +
            "요청 시각: $requestedAt\n" +
            "위치 정보는 수집하지 않습니다. 즉시 직접 연락해 주세요."
    }

    fun buildDailyCheckInMissed(deviceAlias: String): String =
        "[라이프링크 안부 확인]\n" +
            "$deviceAlias 님이 오늘의 안부 확인에 응답하지 않았습니다.\n" +
            "위치 정보는 수집하지 않습니다. 직접 연락해 안전을 확인해 주세요."

    fun buildTest(deviceAlias: String): String =
        "[라이프링크 테스트]\n" +
            "$deviceAlias 님의 긴급 연락처 테스트 문자입니다.\n" +
            "긴급 상황이 아닙니다. 이 문자를 받았다면 SIM 문자 발송 설정이 정상입니다."
}
