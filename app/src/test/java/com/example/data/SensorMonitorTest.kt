package com.example.data

import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class SensorMonitorTest {
    private lateinit var context: Context
    private lateinit var sensorManager: SensorManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sensorManager = context.getSystemService(SensorManager::class.java)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(100))
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
    }

    @Test
    fun screenOnDoesNotResetButUserPresentDoes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensorManager = context.getSystemService(SensorManager::class.java)
        shadowOf(sensorManager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        val reasons = mutableListOf<String>()
        val monitor = SensorMonitor(context) { reason, _ -> reasons.add(reason) }

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

        val result = SensorMonitor(context) { _, _ -> }.start()

        assertEquals(SensorStartResult.FAILED, result)
    }

    @Test
    fun phoneWithStepSensorAlsoListensForHandsetMotion() {
        val steps = addSensor(Sensor.TYPE_STEP_DETECTOR)
        val motion = addSensor(Sensor.TYPE_ACCELEROMETER)
        val monitor = SensorMonitor(context) { _, _ -> }

        assertEquals(SensorStartResult.STEP_DETECTOR, monitor.start())
        assertTrue(shadowOf(sensorManager).hasListener(monitor, steps))
        assertTrue(shadowOf(sensorManager).hasListener(monitor, motion))

        monitor.stop()
        assertFalse(shadowOf(sensorManager).hasListener(monitor))
    }

    @Test
    fun batchedStepsUseTheirOriginalTimingAndDoNotBecomeCurrentActivity() {
        val sensor = addSensor(Sensor.TYPE_STEP_DETECTOR)
        val events = mutableListOf<Long>()
        val monitor = SensorMonitor(context) { _, atMs -> events.add(atMs) }
        monitor.start()
        val baseNanos = SystemClock.elapsedRealtimeNanos() - 10_000_000_000L
        val receiptWallMs = System.currentTimeMillis()

        sendEvent(sensor, baseNanos)
        sendEvent(sensor, baseNanos + 1_000_000_000L)
        sendEvent(sensor, baseNanos + 2_000_000_000L)

        assertEquals(listOf(receiptWallMs - 8_000L), events)
        monitor.stop()
    }

    @Test
    fun seatedUnlockedHandsetUseIsDetectedEvenWithStepSensor() {
        addSensor(Sensor.TYPE_STEP_DETECTOR)
        val sensor = addSensor(Sensor.TYPE_ACCELEROMETER)
        val events = mutableListOf<String>()
        val monitor = SensorMonitor(context) { reason, _ -> events.add(reason) }
        monitor.start()

        sendRepeatedMotion(sensor)

        assertEquals(listOf("반복된 휴대전화 움직임 감지"), events)
        monitor.stop()
    }

    @Test
    fun lockedHandsetMotionDoesNotSupplementStepSensor() {
        addSensor(Sensor.TYPE_STEP_DETECTOR)
        val sensor = addSensor(Sensor.TYPE_ACCELEROMETER)
        val events = mutableListOf<String>()
        val monitor = SensorMonitor(context) { reason, _ -> events.add(reason) }
        monitor.start()
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)

        sendRepeatedMotion(sensor)

        assertTrue(events.isEmpty())
        monitor.stop()
    }

    @Test
    fun screenOffHandsetMotionDoesNotSupplementStepSensor() {
        addSensor(Sensor.TYPE_STEP_DETECTOR)
        val sensor = addSensor(Sensor.TYPE_ACCELEROMETER)
        val events = mutableListOf<String>()
        val monitor = SensorMonitor(context) { reason, _ -> events.add(reason) }
        monitor.start()
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)

        sendRepeatedMotion(sensor)

        assertTrue(events.isEmpty())
        monitor.stop()
    }

    @Test
    fun deviceWithoutStepSensorRetainsItsMotionFallback() {
        val sensor = addSensor(Sensor.TYPE_ACCELEROMETER)
        val events = mutableListOf<String>()
        val monitor = SensorMonitor(context) { reason, _ -> events.add(reason) }
        assertEquals(SensorStartResult.REPEATED_MOTION, monitor.start())
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)

        sendRepeatedMotion(sensor)

        assertEquals(listOf("반복된 휴대전화 움직임 감지"), events)
        monitor.stop()
    }

    @Test
    fun aSingleShockCannotResetActivity() {
        addSensor(Sensor.TYPE_STEP_DETECTOR)
        val sensor = addSensor(Sensor.TYPE_ACCELEROMETER)
        val events = mutableListOf<String>()
        val monitor = SensorMonitor(context) { reason, _ -> events.add(reason) }
        monitor.start()
        val baseNanos = SystemClock.elapsedRealtimeNanos() - 10_000_000_000L

        sendEvent(sensor, baseNanos, 0f)
        sendEvent(sensor, baseNanos + 3_000_000_000L, 10f)

        assertTrue(events.isEmpty())
        monitor.stop()
    }

    @Test
    fun restartDoesNotReuseStepsFromPreviousMonitoringSession() {
        val sensor = addSensor(Sensor.TYPE_STEP_DETECTOR)
        val events = mutableListOf<String>()
        val monitor = SensorMonitor(context) { reason, _ -> events.add(reason) }
        monitor.start()
        val baseNanos = SystemClock.elapsedRealtimeNanos() - 10_000_000_000L
        sendEvent(sensor, baseNanos)
        sendEvent(sensor, baseNanos + 1_000_000_000L)
        monitor.stop()
        monitor.start()

        sendEvent(sensor, baseNanos + 2_000_000_000L)

        assertTrue(events.isEmpty())
        sendEvent(sensor, baseNanos + 3_000_000_000L)
        sendEvent(sensor, baseNanos + 4_000_000_000L)
        assertEquals(listOf("반복된 걸음 감지"), events)
        monitor.stop()
    }

    @Test
    fun invalidOrFutureSensorTimestampsAreRejected() {
        assertNull(SensorEventTime.toWallTimeMs(0L, 10_000_000L, 100L))
        assertNull(SensorEventTime.toWallTimeMs(11_000_000L, 10_000_000L, 100L))
        assertNull(SensorEventTime.toWallTimeMs(1L, 10_000_000_000L, 100L))
        assertEquals(98L, SensorEventTime.toWallTimeMs(8_000_000L, 10_000_000L, 100L))
    }

    private fun addSensor(type: Int): Sensor = ShadowSensor.newInstance(type).also {
        shadowOf(sensorManager).addSensor(it)
    }

    private fun sendRepeatedMotion(sensor: Sensor) {
        val baseNanos = SystemClock.elapsedRealtimeNanos() - 10_000_000_000L
        repeat(5) { index ->
            sendEvent(sensor, baseNanos + index * 1_000_000_000L, (index % 2) * 10f)
        }
    }

    private fun sendEvent(sensor: Sensor, timestampNanos: Long, x: Float = 1f) {
        val event = ShadowSensorManager.createSensorEvent(3).apply {
            this.sensor = sensor
            timestamp = timestampNanos
            values[0] = x
        }
        shadowOf(sensorManager).sendSensorEventToListeners(event, sensor)
    }
}
