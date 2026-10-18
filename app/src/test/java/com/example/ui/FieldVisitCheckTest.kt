package com.example.ui

import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringSnapshot
import com.example.data.MonitoringStore
import com.example.data.SafetyReviewItem
import com.example.monitoring.SmsDispatchStore
import com.example.monitoring.SmsLine
import com.example.monitoring.SmsSetupIssue
import com.example.monitoring.SmsSetupState
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class FieldVisitCheckTest {
    private val now = 1_792_300_000_000L
    private val environment = DiagnosticEnvironment(true, true, true, true, true, BatteryPolicy.OPTIMIZED)
    private val snapshot = MonitoringSnapshot(true, MonitoringRuntimeState.RUNNING, "private error",
        now + 60_000L, 60L, 0, now - 123_456L, "홍길동 01012345678 비공개 활동 이유",
        now - 10_000L, "홍길동 01012345678")
    private val report = FieldVisitReport(
        SafetyDiagnosticReport(snapshot, environment, null, now),
        SmsSetupState.Ready(SmsLine(123, 0, "private SIM label 01012345678")), 1,
        SmsObservation(false, SmsObservationState.SENT, now - 5_000L),
        SmsObservation(true, SmsObservationState.DELIVERED, now - 6_000L)
    )

    private fun summary(value: FieldVisitReport = report, alias: String = "방문기기-A0B1C2", include: Boolean = false) =
        fieldVisitSummary(value, "2.3.5", 36, alias, include, TimeZone.getTimeZone("Asia/Seoul"))

    @Test fun defaultSummaryIsAnAllowlistWithoutPersonalDataOrExactActivityHistory() {
        val text = summary()
        listOf("홍길동", "01012345678", "비공개 활동 이유", "private error", "private SIM label", "방문기기-A0B1C2",
            fieldVisitTime(snapshot.lastActivityMs, TimeZone.getTimeZone("Asia/Seoul")),
            fieldVisitTime(snapshot.lastHeartbeatMs, TimeZone.getTimeZone("Asia/Seoul")),
            fieldVisitTime(report.actualSms!!.updatedAtMs, TimeZone.getTimeZone("Asia/Seoul"))
        ).forEach { assertFalse("Unexpected private value: $it", text.contains(it)) }
        assertTrue(text.contains("2.3.5 / Android API 36"))
        assertTrue(text.contains("+09:00 [Asia/Seoul]"))
        assertTrue(text.contains("시간·이유 비공개"))
        assertTrue(text.contains(FIELD_VISIT_LIMITATION))
        assertTrue(text.contains("전체 성공 아님"))
        assertTrue(text.contains("열람이나 실제 안전 확인을 뜻하지 않습니다"))
    }

    @Test fun aliasRequiresSeparateOptInAndOptOutRemovesIt() {
        assertTrue(summary(include = true).contains("방문기기-A0B1C2"))
        assertFalse(summary(include = false).contains("방문기기-A0B1C2"))
        listOf("홍길동", "01012345678", "방문기기-A0B1C2\n01012345678").forEach {
            assertFalse(summary(alias = it, include = true).contains("임시 점검 별칭:"))
        }
    }

    @Test fun staleFutureMissingAndStoppedHeartbeatNeverBecomeRunning() {
        listOf(0L, now + 1L, now - MonitoringStore.HEARTBEAT_TIMEOUT_MS - 1L).forEach { heartbeat ->
            val stale = report.copy(diagnostics = report.diagnostics.copy(snapshot = snapshot.copy(lastHeartbeatMs = heartbeat)))
            assertFalse(summary(stale).contains("모니터링: 기기 실행 확인"))
        }
        val stopped = report.copy(diagnostics = report.diagnostics.copy(snapshot = snapshot.copy(runtimeState = MonitoringRuntimeState.STOPPED)))
        assertFalse(summary(stopped).contains("모니터링: 기기 실행 확인"))
    }

    @Test fun missingSensorPermissionSimAndAirplaneModeAreNotReady() {
        listOf(environment.copy(smsGranted = false), environment.copy(phoneGranted = false),
            environment.copy(hasActivitySensor = false), environment.copy(airplaneModeEnabled = true)
        ).forEach { env ->
            val blocked = report.copy(diagnostics = report.diagnostics.copy(environment = env))
            assertFalse(summary(blocked).contains("모니터링: 기기 실행 확인"))
        }
        val noSim = report.copy(smsSetup = SmsSetupState.Blocked(SmsSetupIssue.NO_ACTIVE_SIM),
            diagnostics = report.diagnostics.copy(setupProblem = "SIM 없음"))
        assertFalse(noSim.smsReady)
        assertFalse(summary(noSim).contains("모니터링: 기기 실행 확인"))
        assertFalse(summary(noSim).contains("SMS 기능: SIM·권한 확인됨"))
        assertFalse(report.copy(diagnostics = report.diagnostics.copy(environment = environment.copy(airplaneModeEnabled = true))).smsReady)
    }

    @Test fun futureOrMissingActivityIsNotAnObservedSignal() {
        listOf(0L, now + 1).forEach {
            assertFalse(report.copy(diagnostics = report.diagnostics.copy(snapshot = snapshot.copy(lastActivityMs = it))).hasObservedActivity)
        }
    }

    @Test fun smsStatesAreSeparateAndNeverAcknowledgePersonSafety() {
        assertEquals(SmsObservationState.SENT, smsObservation(false, "SENT", now).stateAt(now))
        assertEquals(SmsObservationState.DELIVERED, smsObservation(false, "DELIVERED", now).stateAt(now))
        assertEquals(SmsObservationState.FAILED_FINAL, smsObservation(false, "FAILED_FINAL", now).stateAt(now))
        assertEquals(SmsObservationState.FAILED_RETRYABLE, smsObservation(false, "FAILED_RETRYABLE", now).stateAt(now))
        assertEquals(SmsObservationState.UNKNOWN, smsObservation(false, "invalid", now).stateAt(now))
        assertEquals(SmsObservationState.UNKNOWN, smsObservation(false, "FAILED_FINAL", now, SmsDispatchStore.RESULT_CALLBACK_TIMEOUT).stateAt(now))
        assertEquals(SmsObservationState.UNKNOWN, smsObservation(false, "SENT", now + 1).stateAt(now))
        assertEquals(SmsObservationState.UNKNOWN, smsObservation(false, "SENT", 0L).stateAt(now))
        assertEquals(SmsObservationState.UNKNOWN, null.stateAt(now))
    }

    @Test fun queuedTimeoutIsOnlyUnknownAndDoesNotMutateDispatchState() {
        val queued = SmsObservation(true, SmsObservationState.QUEUED, now - SmsDispatchStore.CALLBACK_TIMEOUT_MS)
        assertEquals(SmsObservationState.UNKNOWN, queued.stateAt(now))
        assertEquals(SmsObservationState.QUEUED, queued.state)
        assertEquals(SmsObservationState.QUEUED, queued.stateAt(now - 1))
    }

    @Test fun unknownBatteryPolicyIsNotOptimisticallyHealthy() {
        assertTrue(summary(report.copy(diagnostics = report.diagnostics.copy(environment = environment.copy(batteryPolicy = BatteryPolicy.UNKNOWN)))).contains("배터리 제한: 미확인"))
    }

    @Test fun filtersSortByIncidentTimeAndDoNotConvertSmsSuccessIntoFollowUp() {
        val items = listOf(
            SafetyReviewItem("late-sync", "emergency", 100, 1, 1, null, null),
            SafetyReviewItem("old-note", "sos", 200, 0, 1, "CONTACTING", 1),
            SafetyReviewItem("safe", "daily", 300, 1, 1, "CONFIRMED_SAFE", 1),
            SafetyReviewItem("help", "sos", 400, 0, 1, "HELP_NEEDED", 1),
            SafetyReviewItem("unknown", "sos", 500, 1, 1, "future-status", 1)
        )
        assertEquals(listOf("unknown", "help", "safe", "old-note", "late-sync"), filterSafetyReviews(items, FamilyReviewFilter.ALL).map { it.incidentId })
        assertEquals(listOf("unknown", "late-sync"), filterSafetyReviews(items, FamilyReviewFilter.NOT_RECORDED).map { it.incidentId })
        assertEquals("old-note", filterSafetyReviews(items, FamilyReviewFilter.CONTACTING).single().incidentId)
        assertEquals("safe", filterSafetyReviews(items, FamilyReviewFilter.CONFIRMED_SAFE).single().incidentId)
        assertEquals("help", filterSafetyReviews(items, FamilyReviewFilter.HELP_NEEDED).single().incidentId)
        assertNull(items.first().followUpStatus)
    }
}
