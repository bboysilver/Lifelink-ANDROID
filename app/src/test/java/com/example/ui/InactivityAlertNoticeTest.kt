package com.example.ui

import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringSnapshot
import com.example.data.SafetyReviewItem
import org.junit.Assert.*
import org.junit.Test

class InactivityAlertNoticeTest {
    private val snapshot = MonitoringSnapshot(true, MonitoringRuntimeState.RUNNING, "", 1_000L, 0L,
        2, 500L, "unlock", 1_000L, "user", inactivityEventMs = 1_000L)
    private fun review(sent: Int, total: Int = 2, id: String = "emergency:1000") =
        SafetyReviewItem(id, "emergency", 1_000L, sent, total, null, null)

    @Test fun countdownAndStoppedMonitoringDoNotShowAnOldAlert() {
        assertNull(inactivityAlertNotice(snapshot.copy(remainingSeconds = 1L), listOf(review(2))))
        assertNull(inactivityAlertNotice(snapshot.copy(desiredEnabled = false), listOf(review(2))))
    }

    @Test fun sentCallbacksAreNotPresentedAsFamilyConfirmation() {
        val notice = inactivityAlertNotice(snapshot.copy(inactivityAlertHandled = true), listOf(review(2)))!!
        assertEquals("문자 발송 확인 · 활동 대기", notice.headline)
        assertTrue(notice.result.contains("안부를 확인했다는 뜻은 아닙니다"))
    }

    @Test fun exhaustedFailuresAreNeverShownAsSuccessfulSends() {
        val notice = inactivityAlertNotice(snapshot.copy(inactivityAlertHandled = true), listOf(review(0)))!!
        assertEquals("문자 발송 확인 필요", notice.headline)
        assertTrue(notice.result.contains("0명 발송 확인"))
        assertTrue(notice.result.contains("발송 시도가 종료"))
    }

    @Test fun partialFailureKeepsTheActualRecipientCount() {
        val notice = inactivityAlertNotice(snapshot.copy(inactivityAlertHandled = true), listOf(review(1, 3)))!!
        assertTrue(notice.result.contains("3명 중 1명"))
        assertFalse(notice.headline.contains("활동 대기"))
    }

    @Test fun deletedHistoryDoesNotInventASuccess() {
        val notice = inactivityAlertNotice(snapshot.copy(inactivityAlertHandled = true), emptyList())!!
        assertEquals("경고 처리 종료 · 활동 대기", notice.headline)
        assertTrue(notice.result.contains("발송 여부는 기록"))
    }

    @Test fun pendingAndOldCycleResultsAreNotClaimedAsCompletion() {
        val notice = inactivityAlertNotice(snapshot, listOf(review(2, id = "emergency:999")))!!
        assertEquals("안전 문자 결과 확인 필요", notice.headline)
        assertFalse(notice.result.contains("발송을 확인했습니다"))
        assertFalse(notice.result.contains("시도가 종료"))
    }

    @Test fun shiftedWallClockMatchesTheStableIncidentNotTheMovedDeadline() {
        val notice = inactivityAlertNotice(snapshot.copy(deadlineMs = 3_601_000L), listOf(review(2)))!!
        assertEquals("문자 발송 확인 · 활동 대기", notice.headline)
    }
}
