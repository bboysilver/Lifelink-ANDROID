package com.example.monitoring

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class MonitoringServiceTest {
    private lateinit var context: Application
    private lateinit var store: MonitoringStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = MonitoringStore(context)
    }

    @Test
    fun startCommandAfterInitializationFailureCannotReportRunning() {
        store.beginStart()
        val controller = Robolectric.buildService(MonitoringService::class.java).create()
        try {
            val initializationError = store.snapshot().serviceError
            assertTrue(initializationError.isNotEmpty())

            val result = controller.get().onStartCommand(
                Intent(context, MonitoringService::class.java)
                    .setAction(MonitoringService.ACTION_START),
                0,
                1
            )

            assertEquals(Service.START_NOT_STICKY, result)
            assertEquals(MonitoringRuntimeState.ERROR, store.snapshot().runtimeState)
            assertFalse(store.snapshot().isRunning)
            assertEquals(initializationError, store.snapshot().serviceError)
        } finally {
            controller.destroy()
        }
    }
}
