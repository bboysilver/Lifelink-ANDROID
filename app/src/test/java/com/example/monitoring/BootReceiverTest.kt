package com.example.monitoring

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
