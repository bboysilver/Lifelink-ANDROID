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
}
