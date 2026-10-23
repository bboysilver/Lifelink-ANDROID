package com.example.monitoring

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.MonitoringStore
import com.example.data.MonitoringRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class MonitoringWatchdogWorkerTest {
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun staleDesiredMonitoringProducesAnInterruptionMessage() {
        val store = MonitoringStore(context)
        store.beginStart(nowMs = 1_000L, elapsedRealtimeMs = 1_000L)
        store.markServiceRunning(nowMs = 2_000L, elapsedRealtimeMs = 2_000L)

        val snapshot = store.snapshot(2_000L + MonitoringStore.HEARTBEAT_TIMEOUT_MS + 1L)

        assertNotNull(MonitoringWatchdogPolicy.interruptionMessage(snapshot))
    }

    @Test
    fun explicitlyStoppedMonitoringDoesNotProduceAnInterruptionMessage() {
        val store = MonitoringStore(context)
        store.beginStart(nowMs = 1_000L, elapsedRealtimeMs = 1_000L)
        store.stop(nowMs = 2_000L)

        assertNull(MonitoringWatchdogPolicy.interruptionMessage(store.snapshot(3_000L)))
    }

    @Test
    fun oneInterruptionCanOnlyClaimOneUserAlert() {
        val store = MonitoringStore(context)
        store.beginStart(nowMs = 1_000L, elapsedRealtimeMs = 1_000L)
        store.markServiceRunning(nowMs = 2_000L, elapsedRealtimeMs = 2_000L)
        val token = MonitoringWatchdogPolicy.alertToken(
            store.snapshot(2_000L + MonitoringStore.HEARTBEAT_TIMEOUT_MS + 1L)
        )

        org.junit.Assert.assertTrue(store.claimWatchdogAlert(token))
        org.junit.Assert.assertFalse(store.claimWatchdogAlert(token))
    }

    @Test
    fun staleObservationCannotOverwriteARecoveredHeartbeatOrConsumeItsAlert() {
        val store = MonitoringStore(context)
        store.beginStart(1_000L, elapsedRealtimeMs = 1_000L)
        store.markServiceRunning(2_000L, 2_000L)
        val interruptedAt = 2_000L + MonitoringStore.HEARTBEAT_TIMEOUT_MS + 1L
        val observed = store.snapshot(interruptedAt)
        store.markHeartbeat(interruptedAt + 1_000L, interruptedAt + 1_000L)

        var posted = false
        assertNull(store.claimWatchdogInterruption(observed, interruptedAt + 1_001L) { posted = true })
        org.junit.Assert.assertFalse(posted)
        assertEquals(MonitoringRuntimeState.RUNNING, store.snapshot(interruptedAt + 1_001L).runtimeState)
        val token = MonitoringWatchdogPolicy.alertToken(observed)
        org.junit.Assert.assertTrue(store.claimWatchdogAlert(token))
    }

    @Test
    fun staleObservationCannotOverwriteAUserStopOrFreshRestart() {
        val store = MonitoringStore(context)
        store.beginStart(1_000L, elapsedRealtimeMs = 1_000L)
        store.markServiceRunning(2_000L, 2_000L)
        val interruptedAt = 2_000L + MonitoringStore.HEARTBEAT_TIMEOUT_MS + 1L
        val observed = store.snapshot(interruptedAt)
        store.stop(interruptedAt + 1_000L)

        assertNull(store.claimWatchdogInterruption(observed, interruptedAt + 1_001L))
        assertEquals(MonitoringRuntimeState.STOPPED, store.snapshot(interruptedAt + 1_001L).runtimeState)
        store.beginStart(interruptedAt + 2_000L, elapsedRealtimeMs = interruptedAt + 2_000L)
        assertNull(store.claimWatchdogInterruption(observed, interruptedAt + 2_001L))
        assertEquals(MonitoringRuntimeState.STARTING, store.snapshot(interruptedAt + 2_001L).runtimeState)
    }

    @Test
    fun currentInterruptionIsRecordedAndClaimedOnlyOnce() {
        val store = MonitoringStore(context)
        store.beginStart(1_000L, elapsedRealtimeMs = 1_000L)
        store.markServiceRunning(2_000L, 2_000L)
        val interruptedAt = 2_000L + MonitoringStore.HEARTBEAT_TIMEOUT_MS + 1L
        val observed = store.snapshot(interruptedAt)

        var posts = 0
        assertEquals(observed.serviceError,
            store.claimWatchdogInterruption(observed, interruptedAt) { posts++ })
        assertEquals(MonitoringRuntimeState.ERROR, store.snapshot(interruptedAt).runtimeState)
        assertNull(store.claimWatchdogInterruption(observed, interruptedAt + 1_000L) { posts++ })
        assertEquals(1, posts)
    }

    @Test
    @Config(sdk = [32])
    fun recoveredHeartbeatClearsAnAlreadyPostedInterruptionNotice() {
        val store = MonitoringStore(context)
        store.beginStart(1_000L, elapsedRealtimeMs = 1_000L)
        store.markServiceRunning(2_000L, 2_000L)
        val interruptedAt = 2_000L + MonitoringStore.HEARTBEAT_TIMEOUT_MS + 1L
        val observed = store.snapshot(interruptedAt)
        val notifications = context.getSystemService(NotificationManager::class.java)

        assertNotNull(store.claimWatchdogInterruption(observed, interruptedAt) { message ->
            MonitoringStatusNotifier.showUnexpectedStop(context, message)
        })
        org.junit.Assert.assertTrue(notifications.activeNotifications.any {
            it.id == MonitoringStatusNotifier.NOTIFICATION_ID
        })

        store.markHeartbeat(interruptedAt + 1_000L, interruptedAt + 1_000L)

        org.junit.Assert.assertFalse(notifications.activeNotifications.any {
            it.id == MonitoringStatusNotifier.NOTIFICATION_ID
        })
        assertEquals(MonitoringRuntimeState.RUNNING, store.snapshot(interruptedAt + 1_000L).runtimeState)
    }

    @Test
    fun schedulingTwiceKeepsOnePeriodicWatchdog() {
        MonitoringWatchdogWorker.ensureScheduled(context)
        MonitoringWatchdogWorker.ensureScheduled(context)

        val work = WorkManager.getInstance(context)
            .getWorkInfosByTag(MonitoringWatchdogWorker.WORK_TAG)
            .get(3, TimeUnit.SECONDS)

        assertEquals(1, work.size)
    }
}
