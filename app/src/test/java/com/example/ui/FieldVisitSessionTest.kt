package com.example.ui

import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringSnapshot
import com.example.data.MonitoringStore
import com.example.monitoring.SmsLine
import com.example.monitoring.SmsSetupIssue
import com.example.monitoring.SmsSetupState
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class FieldVisitSessionTest {
    private val now = 1_792_300_000_000L
    private val zone = TimeZone.getTimeZone("Asia/Seoul")
    private val snapshot = MonitoringSnapshot(true, MonitoringRuntimeState.RUNNING,
        "private error 01012345678", now + 60_000L, 60L, 0, now - 35_000L,
        "private activity reason", now - 10_000L, "private alias", lastAutomaticActivityMs = now - 45_000L,
        lastAutomaticActivityReason = "private automatic reason", lastAutomaticActivityApplied = true)
    private val report = FieldVisitReport(SafetyDiagnosticReport(snapshot,
        DiagnosticEnvironment(true, true, true, true, true, BatteryPolicy.UNRESTRICTED), null, now),
        SmsSetupState.Ready(SmsLine(99, 0, "private SIM name")), 1,
        SmsObservation(false, SmsObservationState.SENT, now - 25_000L),
        SmsObservation(true, SmsObservationState.DELIVERED, now - 20_000L))
    private val results = List(VisitTestKind.entries.size) { VisitTestResult.NOT_RUN }

    private fun capture(value: FieldVisitReport = report) = captureFieldVisitBaseline(value, "2.3.9", 36, now, zone)
    private fun summary(baseline: FieldVisitBaseline, current: FieldVisitReport = report,
                        operator: VisitOperatorLabel = VisitOperatorLabel.NOT_RECORDED) =
        fieldVisitSessionSummary(baseline, current, operator, results, "2.3.9", 36, zone)

    @Test fun baselineIsImmutableWhileNewActivityHeartbeatAndSmsRemainLaterObservations() {
        val baseline = capture()
        val before = baseline.copy(localFacts = baseline.localFacts.toList())
        val later = report.copy(diagnostics = report.diagnostics.copy(checkedAtMs = now + 60_000L,
            snapshot = snapshot.copy(lastActivityMs = now + 1_000L, lastHeartbeatMs = now + 2_000L)),
            actualSms = SmsObservation(false, SmsObservationState.FAILED_RETRYABLE, now + 10_000L))
        val text = summary(baseline, later)
        assertEquals(before, baseline)
        assertTrue(baseline.localFacts.any { it.contains(fieldVisitTime(snapshot.lastAutomaticActivityMs, zone)) })
        assertTrue(text.indexOf("[점검 시작 전 기록") < text.indexOf("[점검 이후 앱 내 관찰"))
        assertTrue(text.contains(SmsObservationState.SENT.label))
        assertTrue(text.contains(SmsObservationState.FAILED_RETRYABLE.label))
    }

    @Test fun capturedAndCurrentSummariesKeepTheShareAllowlist() {
        val baseline = capture()
        val text = summary(baseline, report, VisitOperatorLabel.STAFF_REPORTED)
        listOf("private", "01012345678", "99", snapshot.lastActivityReason,
            fieldVisitTime(snapshot.lastActivityMs, zone), fieldVisitTime(snapshot.lastHeartbeatMs, zone),
            fieldVisitTime(snapshot.lastAutomaticActivityMs, zone), fieldVisitTime(report.actualSms!!.updatedAtMs, zone),
            fieldVisitTime(report.testSms!!.updatedAtMs, zone)
        ).forEach { assertFalse("private value leaked: $it", text.contains(it)) }
        assertFalse(baseline.localFacts.any { it.contains("private") })
        assertTrue(text.contains("수동 선택"))
        assertTrue(text.contains("실제 조작자를 식별한 결과가 아닙니다"))
    }

    @Test fun noActivityOrSmsOutcomeAutomaticallySelectsAnOperatorOrTestConclusion() {
        val text = summary(capture())
        assertTrue(text.contains("조작 구분(수동 선택): ${VisitOperatorLabel.NOT_RECORDED.label}"))
        VisitTestKind.entries.forEach { assertTrue(text.contains("${it.label}: 미실행")) }
        assertFalse(text.contains(VisitTestResult.EXPECTED.label))
    }

    @Test fun clockRollbackDoesNotAttributeShiftedActivityToAPerson() {
        val back = report.copy(diagnostics = report.diagnostics.copy(checkedAtMs = now - 3_600_000L,
            snapshot = snapshot.copy(lastActivityMs = now - 3_601_000L)))
        assertTrue(summary(capture(), back).contains("전후 시간 비교는 미확인"))
        assertTrue(summary(capture(), back).contains(VisitOperatorLabel.NOT_RECORDED.label))
    }

    @Test fun missingStaleAndFutureHeartbeatNeverConfirmTheBaseline() {
        listOf(0L, now + 1L, now - MonitoringStore.HEARTBEAT_TIMEOUT_MS - 1L).forEach { heartbeat ->
            val value = report.copy(diagnostics = report.diagnostics.copy(snapshot = snapshot.copy(lastHeartbeatMs = heartbeat)))
            assertFalse(capture(value).shareSummary.contains("모니터링: 기기 실행 확인"))
        }
    }

    @Test fun aFutureSampleCannotMakeAFutureHeartbeatAppearFreshAtCapture() {
        val future = report.copy(diagnostics = report.diagnostics.copy(checkedAtMs = now + 60_000L,
            snapshot = snapshot.copy(lastHeartbeatMs = now + 10_000L)))
        assertFalse(capture(future).shareSummary.contains("모니터링: 기기 실행 확인"))
    }

    @Test fun anOldReportIsEvaluatedAtCaptureAndKeepsItsOriginalReadTime() {
        val readAt = now - 3_600_000L
        val old = report.copy(diagnostics = report.diagnostics.copy(checkedAtMs = readAt,
            snapshot = snapshot.copy(lastHeartbeatMs = readAt - 10_000L)))
        val baseline = capture(old)
        assertFalse(baseline.shareSummary.contains("모니터링: 기기 실행 확인"))
        assertTrue(baseline.localFacts.any { it.contains(fieldVisitTime(readAt, zone)) })
        assertTrue(baseline.localFacts.any { it.contains("서비스 응답 상태: 미확인") })
    }

    @Test fun settingsCountDoesNotCountSmsDeliveryOrManualTestsAsPassed() {
        assertEquals(7, report.installationChecks.count { it == CheckResult.PASS })
        val unknown = report.copy(contactCount = 0, actualSms = null, testSms = null,
            smsSetup = SmsSetupState.Blocked(SmsSetupIssue.CHECKING),
            diagnostics = report.diagnostics.copy(environment = report.diagnostics.environment.copy(batteryPolicy = BatteryPolicy.UNKNOWN)))
        assertEquals(3, unknown.installationChecks.count { it == CheckResult.NOT_CHECKED })
        assertTrue(summary(capture(unknown), unknown).contains("최근 실제 문자: 미확인"))
        assertTrue(summary(capture(unknown), unknown).contains("시험 문자: 미실행"))
    }

    @Test fun restrictedBatteryAndRevokedPermissionsAreNeverAllGreen() {
        val restricted = report.copy(diagnostics = report.diagnostics.copy(environment = report.diagnostics.environment.copy(
            batteryPolicy = BatteryPolicy.RESTRICTED, smsGranted = false, notificationsGranted = false)))
        assertTrue(restricted.installationChecks.count { it == CheckResult.PASS } < 7)
        assertFalse(summary(capture(restricted), restricted).contains("모니터링: 기기 실행 확인"))
        val onlyBattery = report.copy(diagnostics = report.diagnostics.copy(environment = report.diagnostics.environment.copy(batteryPolicy = BatteryPolicy.RESTRICTED)))
        assertTrue(onlyBattery.monitoringLabel.contains("백그라운드 제한"))
        assertFalse(summary(capture(onlyBattery), onlyBattery).contains("모니터링: 기기 실행 확인"))
    }
}
