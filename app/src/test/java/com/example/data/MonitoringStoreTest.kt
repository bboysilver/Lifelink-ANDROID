package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MonitoringStoreTest {
    private lateinit var context: Context

    @Before
    fun clearPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun activityAfterDailyDueConfirmsCheckInWithoutTouchingSos() {
        val store = MonitoringStore(context)
        val due = store.configureDailyCheckIn(18, 1_700_000_000_000L)
        store.beginStart(due - 60_000L)
        store.markDailyCheckInPrompted(due, due)
        val sos = store.beginSos(due + 1_000L)

        assertTrue(store.recordActivity(due + 2_000L, "unlock", due + 2_000L))

        assertTrue(store.dailyNextDueAtMs > due)
        assertEquals(DailyCheckInPhase.UPCOMING, store.dailyCheckInStatus(due + 2_000L).phase)
        assertEquals(sos, store.pendingSosEventMs)
        assertEquals(due + 2_000L + 12 * 60 * 60 * 1_000L, store.deadlineMs)
    }

    @Test
    fun delayedActivityBeforeDailyDueDoesNotConfirmTheCheckIn() {
        val store = MonitoringStore(context)
        val due = store.configureDailyCheckIn(18, 1_700_000_000_000L)
        store.beginStart(due - 60_000L)
        store.markDailyCheckInPrompted(due, due)
        store.recordActivity(due - 1_000L, "old steps", due + 60_000L)
        assertEquals(due, store.dailyNextDueAtMs)
        assertEquals(DailyCheckInPhase.DUE, store.dailyCheckInStatus(due + 60_000L).phase)
    }

    @Test
    fun directActivityCanConfirmDailyWhileInactivityMonitoringIsStopped() {
        val store = MonitoringStore(context)
        val due = store.configureDailyCheckIn(18, 1_700_000_000_000L)
        store.markDailyCheckInPrompted(due, due)
        assertTrue(store.recordActivity(due + 60_000L, "app input", due + 60_000L))
        assertFalse(store.desiredEnabled)
        assertTrue(store.dailyNextDueAtMs > due)
    }

    @Test
    fun responseAfterMultipleDaysSchedulesAFutureDayRatherThanAnotherOverdueDay() {
        val store = MonitoringStore(context)
        val due = store.configureDailyCheckIn(18, 1_700_000_000_000L)
        store.markDailyCheckInPrompted(due, due)
        val now = due + 3 * 24 * 60 * 60 * 1_000L
        store.confirmDailyCheckIn(now)
        assertTrue(store.dailyNextDueAtMs > now)
    }

    @Test
    fun delayedSensorActivityCannotOverwriteNewerPhoneUse() {
        val store = MonitoringStore(context)
        store.beginStart(nowMs = 1_000L)
        assertTrue(store.recordActivity(50_000L, "unlock", nowMs = 60_000L))
        val currentDeadline = store.deadlineMs
        assertFalse(store.recordActivity(40_000L, "batched steps", nowMs = 70_000L))
        assertEquals(currentDeadline, store.deadlineMs)
        assertEquals("unlock", store.snapshot().lastActivityReason)
    }

    @Test
    fun delayedActivityUsesOccurrenceTimeAndCannotResumeStoppedMonitoring() {
        val store = MonitoringStore(context)
        store.beginStart(nowMs = 1_000L)
        assertTrue(store.recordActivity(10_000L, "steps", nowMs = 80_000L))
        assertEquals(10_000L + 12 * 60 * 60 * 1_000L, store.deadlineMs)
        assertFalse(store.recordActivity(90_000L, "future", nowMs = 80_000L))
        store.stop(90_000L)
        assertFalse(store.recordActivity(100_000L, "unlock", nowMs = 100_000L))
        assertFalse(store.desiredEnabled)
    }

    @Test
    fun deadlineAndDesiredStateSurviveStoreRecreation() {
        val firstStore = MonitoringStore(context)
        firstStore.monitorHours = 6
        firstStore.beginStart(nowMs = 10_000L, reason = "test")

        val restored = MonitoringStore(context).snapshot(nowMs = 20_000L)

        assertTrue(restored.desiredEnabled)
        assertEquals(MonitoringRuntimeState.STARTING, restored.runtimeState)
        assertFalse(restored.isRunning)
        assertEquals(10_000L + 6 * 60 * 60 * 1_000L, restored.deadlineMs)
        assertEquals("test", restored.lastActivityReason)
    }

    @Test
    fun freshHeartbeatIsRunningAndStaleHeartbeatIsError() {
        val store = MonitoringStore(context)
        store.beginStart(nowMs = 1_000L)
        store.markServiceRunning(nowMs = 2_000L)

        assertTrue(store.snapshot(nowMs = 2_001L).isRunning)

        val stale = store.snapshot(nowMs = 2_000L + MonitoringStore.HEARTBEAT_TIMEOUT_MS + 1L)
        assertEquals(MonitoringRuntimeState.ERROR, stale.runtimeState)
        assertFalse(stale.isRunning)
    }

    @Test
    fun manualClockChangePreservesRemainingInactivityTime() {
        val store = MonitoringStore(context)
        store.monitorHours = 6
        store.beginStart(nowMs = 10_000L, elapsedRealtimeMs = 1_000L)
        store.markServiceRunning(nowMs = 11_000L, elapsedRealtimeMs = 2_000L)
        val oldDeadlineMs = store.deadlineMs
        store.markPreAlert(oldDeadlineMs)

        val changed = store.rebaseAfterWallClockChange(
            nowMs = 3_612_000L,
            elapsedRealtimeMs = 3_000L
        )

        assertTrue(changed)
        assertEquals(oldDeadlineMs + 3_600_000L, store.deadlineMs)
        assertTrue(store.wasPreAlerted(store.deadlineMs))
        assertEquals(
            oldDeadlineMs - 12_000L,
            store.snapshot(nowMs = 3_612_000L).remainingSeconds * 1_000L
        )
    }

    @Test
    fun userCanStopMonitoringEvenWhenServiceIsNotRunning() {
        val store = MonitoringStore(context)
        store.beginStart(nowMs = 1_000L)

        store.stop(nowMs = 2_000L)

        val snapshot = store.snapshot(nowMs = 3_000L)
        assertFalse(snapshot.desiredEnabled)
        assertEquals(MonitoringRuntimeState.STOPPED, snapshot.runtimeState)
    }

    @Test
    fun resettingDeadlineCreatesANewAlertEvent() {
        val store = MonitoringStore(context)
        store.beginStart(nowMs = 1_000L, reason = "first")
        val firstDeadline = store.deadlineMs
        store.markPreAlert(firstDeadline)
        store.markEmergency(firstDeadline)

        store.resetDeadline(nowMs = 2_000L, reason = "movement")

        assertFalse(store.wasPreAlerted(store.deadlineMs))
        assertFalse(store.wasEmergencyDispatched(store.deadlineMs))
    }

    @Test
    fun dailyCheckInStartsAtTheNextFutureOccurrenceAndSurvivesRecreation() {
        val nowMs = 1_721_659_200_000L

        val dueAtMs = MonitoringStore(context).configureDailyCheckIn(hour = 9, nowMs = nowMs)
        val restored = MonitoringStore(context)

        assertTrue(restored.dailyCheckInEnabled)
        assertEquals(9, restored.dailyCheckInHour)
        assertEquals(dueAtMs, restored.dailyNextDueAtMs)
        assertEquals(DailyCheckInPhase.UPCOMING, restored.dailyCheckInStatus(nowMs).phase)
        assertTrue(dueAtMs > nowMs)
    }

    @Test
    fun ordinaryEarlyActivityCannotCompleteDailyCheckIn() {
        val store = MonitoringStore(context)
        val dueAtMs = store.configureDailyCheckIn(hour = 18, nowMs = 1_000L)

        assertEquals(null, store.confirmDailyCheckIn(nowMs = dueAtMs - 1L))
        assertEquals(dueAtMs, store.dailyNextDueAtMs)
    }

    @Test
    fun confirmingDueCheckInAdvancesToANewDueEvent() {
        val store = MonitoringStore(context)
        val dueAtMs = store.configureDailyCheckIn(hour = 18, nowMs = 1_000L)
        store.markDailyCheckInPrompted(dueAtMs)

        assertEquals(dueAtMs, store.confirmDailyCheckIn(nowMs = dueAtMs))
        assertTrue(store.dailyNextDueAtMs > dueAtMs)
        assertFalse(store.wasDailyCheckInPrompted(store.dailyNextDueAtMs))
    }

    @Test
    fun pendingSosCanBeCancelledBeforeButNotAfterDispatchClaim() {
        val store = MonitoringStore(context)
        store.beginSos(1_000L)

        assertTrue(store.cancelPendingSos())
        assertEquals(0L, store.sosEventMs)

        store.beginSos(2_000L)
        assertEquals(2_000L, store.claimPendingSos(nowMs = 2_000L))
        assertFalse(store.cancelPendingSos())
        assertEquals(2_000L, store.activeSosEventMs)

        store.completeActiveSos(2_000L)
        assertEquals(0L, store.sosEventMs)
    }
    @Test
    fun setupProgressAndTestVerificationSurviveRecreation() {
        val store = MonitoringStore(context)
        store.setSetupStep(SetupStepValue.TEST_SMS)
        store.markTestSmsPending(contactId = 7, eventId = "test:100:7")
        store.recordTestSmsResult(
            eventId = "test:100:7",
            state = TestSmsVerificationState.SUCCESS,
            message = "확인됨"
        )

        val restored = MonitoringStore(context)
        assertEquals(SetupStepValue.TEST_SMS, restored.setupStep)
        assertEquals(TestSmsVerificationState.SUCCESS, restored.testSmsVerification.state)
        assertEquals(7, restored.testSmsVerification.contactId)
    }

    @Test
    fun setupIsCurrentOnlyAfterTheVerifiedFlowCompletes() {
        val store = MonitoringStore(context)
        store.beginSetupReview()
        assertFalse(store.isSetupCurrent)

        store.completeSetup()

        assertTrue(store.isSetupCurrent)
        assertEquals(MonitoringStore.CURRENT_ONBOARDING_VERSION, store.onboardingVersion)
    }

    @Test
    fun debugMinuteDeadlineIsUsedOnlyForExplicitDebugSelection() {
        val store = MonitoringStore(context)
        store.monitorHours = 6
        store.debugMonitorMinutes = 1

        store.beginStart(nowMs = 10_000L)

        assertEquals(70_000L, store.deadlineMs)
    }

    private object SetupStepValue {
        const val TEST_SMS = 4
    }
    @Test
    fun staleServiceDoesNotShowPreAlertOrEmergencyState() {
        assertEquals(
            0,
            MonitoringAlertStatePolicy.resolve(
                desiredEnabled = true,
                runtimeState = MonitoringRuntimeState.ERROR,
                deadlineMs = 10_000L,
                remainingSeconds = 0L,
                preAlerted = true,
                emergencyDispatched = false
            )
        )
    }

    @Test
    fun runningServiceShowsPreAlertAndConfirmedEmergency() {
        assertEquals(
            1,
            MonitoringAlertStatePolicy.resolve(
                desiredEnabled = true,
                runtimeState = MonitoringRuntimeState.RUNNING,
                deadlineMs = 10_000L,
                remainingSeconds = DeadlineCalculator.PRE_ALERT_SECONDS,
                preAlerted = false,
                emergencyDispatched = false
            )
        )
        assertEquals(
            2,
            MonitoringAlertStatePolicy.resolve(
                desiredEnabled = true,
                runtimeState = MonitoringRuntimeState.ERROR,
                deadlineMs = 10_000L,
                remainingSeconds = 0L,
                preAlerted = true,
                emergencyDispatched = true
            )
        )
    }
}
