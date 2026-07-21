package com.example.monitoring

import android.location.Location

object EmergencyMessageBuilder {
    fun build(location: Location?, batteryPercent: Int?): String {
        val locationText = if (location == null) {
            "위치 확인 불가"
        } else {
            "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        }
        val batteryText = batteryPercent?.let { "$it%" } ?: "확인 불가"
        return "[라이프링크 안전 알림]\n" +
            "등록된 사용자의 활동이 설정 시간 동안 확인되지 않았습니다.\n" +
            "위치: $locationText\n" +
            "배터리: $batteryText\n" +
            "직접 연락해 안전을 확인해 주세요."
    }
}
