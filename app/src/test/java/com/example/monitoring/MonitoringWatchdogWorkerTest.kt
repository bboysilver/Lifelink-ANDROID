package com.example.monitoring

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.MonitoringStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    fun schedulingTwiceKeepsOnePeriodicWatchdog() {
        MonitoringWatchdogWorker.ensureScheduled(context)
        MonitoringWatchdogWorker.ensureScheduled(context)

        val work = WorkManager.getInstance(context)
            .getWorkInfosByTag(MonitoringWatchdogWorker.WORK_TAG)
            .get(3, TimeUnit.SECONDS)

        assertEquals(1, work.size)
    }
}
