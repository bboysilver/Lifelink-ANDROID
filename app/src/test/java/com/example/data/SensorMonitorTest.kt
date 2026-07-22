package com.example.data

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
class SensorMonitorTest {
    @Test
    fun screenOnDoesNotResetButUserPresentDoes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensorManager = context.getSystemService(SensorManager::class.java)
        shadowOf(sensorManager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        val reasons = mutableListOf<String>()
        val monitor = SensorMonitor(context, reasons::add)

        assertEquals(SensorStartResult.REPEATED_MOTION, monitor.start())

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(emptyList<String>(), reasons)

        context.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("휴대전화 잠금 해제 감지"), reasons)

        monitor.stop()
    }

    @Test
    fun sensorRegistrationFailureIsReported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val shadowSensorManager = shadowOf(sensorManager)
        shadowSensorManager.addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        shadowSensorManager.setForceListenersToFail(true)

        val result = SensorMonitor(context) {}.start()

        assertEquals(SensorStartResult.FAILED, result)
    }
}
