package com.example.monitoring

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {
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
    fun disabledMonitoringIsNotRestoredAfterBoot() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(null, shadowOf(context).nextStartedService)
    }

    @Test
    fun duePromptIsQueuedForRedisplayAfterReboot() {
        val nowMs = System.currentTimeMillis()
        val dueAtMs = nowMs - 60 * 60 * 1_000L
        val store = MonitoringStore(context)
        store.completeSetup()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("daily_check_in_enabled", true)
            .putLong("daily_next_due_at_ms", dueAtMs)
            .putLong("daily_prompted_due_at_ms", dueAtMs)
            .putLong("daily_response_deadline_at_ms", nowMs + 60 * 60 * 1_000L)
            .commit()

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val work = WorkManager.getInstance(context)
            .getWorkInfosByTag(DailyCheckInWorker.WORK_TAG)
            .get(3, TimeUnit.SECONDS)
        assertTrue(work.isNotEmpty())
    }

    @Test
    fun unsupportedSmsDeviceIsNotReportedAsRestored() {
        val store = MonitoringStore(context)
        store.completeSetup()
        store.beginStart(nowMs = 1_000L)

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val snapshot = store.snapshot(nowMs = 1_001L)
        assertFalse(snapshot.isRunning)
        assertEquals(MonitoringRuntimeState.ERROR, snapshot.runtimeState)
        assertEquals(null, shadowOf(context).nextStartedService)
    }
}
