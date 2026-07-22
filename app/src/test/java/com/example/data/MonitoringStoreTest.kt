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
}
