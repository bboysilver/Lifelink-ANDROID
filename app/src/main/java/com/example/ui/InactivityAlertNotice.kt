package com.example.ui

import com.example.data.MonitoringSnapshot
import com.example.data.SafetyReviewItem

internal data class InactivityAlertNotice(val headline: String, val result: String)

internal fun inactivityAlertNotice(
    snapshot: MonitoringSnapshot,
    reviews: List<SafetyReviewItem>
): InactivityAlertNotice? {
    if (!snapshot.desiredEnabled || snapshot.deadlineMs <= 0L ||
        (snapshot.remainingSeconds > 0L && !snapshot.inactivityAlertHandled)
    ) return null
    val review = reviews.firstOrNull { it.incidentId == "emergency:${snapshot.inactivityEventMs}" }
    val sent = review?.sentRecipients ?: 0
    val total = review?.totalRecipients ?: 0
    return when {
        total > 0 && sent == total -> InactivityAlertNotice(
            "문자 발송 확인 · 활동 대기",
            "보호자 ${total}명 문자 발송을 확인했습니다. 보호자가 읽거나 안부를 확인했다는 뜻은 아닙니다."
        )
        snapshot.inactivityAlertHandled && total > 0 -> InactivityAlertNotice(
            "문자 발송 확인 필요",
            "보호자 ${total}명 중 ${sent}명 발송 확인. 발송 시도가 종료됐습니다. 직접 연락해 주세요."
        )
        snapshot.inactivityAlertHandled -> InactivityAlertNotice(
            "경고 처리 종료 · 활동 대기",
            "새 활동을 기다리고 있습니다. 문자 발송 여부는 기록에서 확인해 주세요."
        )
        else -> InactivityAlertNotice(
            "안전 문자 결과 확인 필요",
            "아직 문자 발송 결과를 확인하지 못했습니다. 기록의 발송·재시도 상태를 확인해 주세요."
        )
    }
}

internal const val INACTIVITY_CYCLE_GUIDANCE =
    "같은 무활동 경고는 반복하지 않습니다. 유효 활동이나 '무사합니다' 확인 후 설정 시간을 다시 셉니다. 발송 실패 재시도는 별도입니다."
