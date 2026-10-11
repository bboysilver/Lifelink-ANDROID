package com.example.ui

import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringSnapshot
import com.example.data.MonitoringStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyDiagnosticsTest {
    private val now = 1_700_000_000_000L
    private val environment = DiagnosticEnvironment(true, true, true, true, true, BatteryPolicy.OPTIMIZED)
    private val snapshot = MonitoringSnapshot(
        desiredEnabled = true, runtimeState = MonitoringRuntimeState.RUNNING,
        serviceError = "", deadlineMs = now + 10_000L, remainingSeconds = 10L,
        alertState = 0, lastActivityMs = now - 1_000L, lastActivityReason = "unlock",
        lastHeartbeatMs = now - 60_000L, deviceAlias = "사용자"
    )

    @Test fun runningWithRecentHeartbeatAndReadySetupIsConfirmed() {
        val report = SafetyDiagnosticReport(snapshot, environment, null, now)
        assertTrue(report.monitoringConfirmed)
        assertFalse(report.headline.contains("안전 보장"))
    }

    @Test fun staleHeartbeatDoesNotAppearNormal() {
        val stale = snapshot.copy(lastHeartbeatMs = now - MonitoringStore.HEARTBEAT_TIMEOUT_MS - 1)
        assertFalse(SafetyDiagnosticReport(stale, environment, null, now).monitoringConfirmed)
    }

    @Test fun heartbeatExactlyAtTimeoutIsStillFresh() {
        val edge = snapshot.copy(lastHeartbeatMs = now - MonitoringStore.HEARTBEAT_TIMEOUT_MS)
        assertTrue(SafetyDiagnosticReport(edge, environment, null, now).heartbeatFresh)
    }

    @Test fun missingAndFutureHeartbeatAreNotTrusted() {
        listOf(0L, now + 1L).forEach { heartbeat ->
            assertFalse(SafetyDiagnosticReport(snapshot.copy(lastHeartbeatMs = heartbeat), environment, null, now).monitoringConfirmed)
        }
    }

    @Test fun requestedButStoppedServiceIsNotConfirmed() {
        val stopped = snapshot.copy(runtimeState = MonitoringRuntimeState.STOPPED)
        assertFalse(SafetyDiagnosticReport(stopped, environment, null, now).monitoringConfirmed)
    }

    @Test fun startingIsNotReportedAsRunning() {
        val report = SafetyDiagnosticReport(snapshot.copy(runtimeState = MonitoringRuntimeState.STARTING), environment, null, now)
        assertFalse(report.monitoringConfirmed)
        assertEquals("모니터링 시작 중", report.headline)
    }

    @Test fun userStoppedStateIgnoresOldHeartbeat() {
        assertFalse(SafetyDiagnosticReport(snapshot.copy(desiredEnabled = false), environment, null, now).monitoringConfirmed)
    }

    @Test fun missingPermissionsSensorOrSimPreventNormalStatus() {
        listOf(
            environment.copy(smsGranted = false), environment.copy(phoneGranted = false),
            environment.copy(activityGranted = false), environment.copy(notificationsGranted = false),
            environment.copy(hasActivitySensor = false)
        ).forEach { state -> assertFalse(SafetyDiagnosticReport(snapshot, state, null, now).monitoringConfirmed) }
        assertFalse(SafetyDiagnosticReport(snapshot, environment, "SIM 없음", now).monitoringConfirmed)
    }

    @Test fun optimizationWarningDoesNotStopExistingMonitoring() {
        assertTrue(SafetyDiagnosticReport(snapshot, environment, null, now).monitoringConfirmed)
    }

    @Test fun unavailableTimesNeverBecomeInventedDates() {
        assertEquals("기록 없음", diagnosticTime(null))
        assertEquals("기록 없음", diagnosticTime(0L))
    }
}
