package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.KeyguardManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

enum class SensorStartResult { STEP_DETECTOR, STEP_COUNTER, REPEATED_MOTION, FAILED }

class SensorMonitor(
    private val context: Context,
    private val onActivityDetected: (String, Long) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val repeatedMotionDetector = RepeatedMotionDetector()
    private val stepCounterDetector = RepeatedStepCounterDetector()
    private val presenceDetector = UserPresenceDetector()
    private val repeatedStepDetector = RepeatedMotionDetector(
        requiredEvents = 3,
        minimumSpanMs = 2_000L,
        windowMs = 15_000L,
        cooldownMs = 60_000L
    )
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var hasAccelerometerSample = false
    private var isRegistered = false
    private var stepsRegistered = false
    private var counterRegistered = false
    private var lastStepReportedMs = Long.MIN_VALUE
    private var startResult = SensorStartResult.FAILED

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> checkUserPresence(explicitUserPresent = true)
                Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF -> checkUserPresence()
            }
        }
    }

    @Synchronized
    fun start(): SensorStartResult {
        if (isRegistered) return startResult

        resetDetectors()
        stepsRegistered = registerStepSensor(Sensor.TYPE_STEP_DETECTOR)
        counterRegistered = registerStepSensor(Sensor.TYPE_STEP_COUNTER)
        val motionRegistered = registerSensor(accelerometer)
        startResult = when {
            stepsRegistered -> SensorStartResult.STEP_DETECTOR
            counterRegistered -> SensorStartResult.STEP_COUNTER
            motionRegistered -> SensorStartResult.REPEATED_MOTION
            else -> SensorStartResult.FAILED
        }
        if (startResult == SensorStartResult.FAILED) {
            Log.e(TAG, "No activity sensor could be registered")
            return startResult
        }

        try {
            ContextCompat.registerReceiver(
                context,
                statusReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT).apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
                // USER_PRESENT is a protected system broadcast; some OEMs send it from System UI.
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (error: RuntimeException) {
            sensorManager.unregisterListener(this)
            stepsRegistered = false
            counterRegistered = false
            startResult = SensorStartResult.FAILED
            Log.e(TAG, "Unlock receiver registration failed", error)
            return startResult
        }

        isRegistered = true
        presenceDetector.reset(keyguardManager.isKeyguardLocked, SystemClock.elapsedRealtime())
        Log.d(TAG, "Sensing engine started with $startResult")
        return startResult
    }

    @Synchronized
    fun stop() {
        if (isRegistered) {
            sensorManager.unregisterListener(this)
            try {
                context.unregisterReceiver(statusReceiver)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Status receiver was already unregistered", error)
            }
        }
        isRegistered = false
        stepsRegistered = false
        counterRegistered = false
        startResult = SensorStartResult.FAILED
        resetDetectors()
        Log.d(TAG, "Sensing engine stopped")
    }

    @Synchronized
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRegistered || event == null) return
        val eventWallMs = SensorEventTime.toWallTimeMs(
            event.timestamp, SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis()
        ) ?: return
        val eventElapsedMs = event.timestamp / 1_000_000L
        when (event.sensor?.type) {
            Sensor.TYPE_STEP_DETECTOR -> handleStep(eventElapsedMs, eventWallMs)
            Sensor.TYPE_STEP_COUNTER -> event.values.firstOrNull()?.let { count ->
                if (stepCounterDetector.record(count, eventElapsedMs)) {
                    reportSteps("걸음 수 증가 감지", eventElapsedMs, eventWallMs)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event, eventElapsedMs, eventWallMs)
        }
    }

    private fun handleStep(eventElapsedMs: Long, eventWallMs: Long) {
        if (repeatedStepDetector.record(eventElapsedMs)) {
            reportSteps("반복된 걸음 감지", eventElapsedMs, eventWallMs)
        }
    }

    private fun reportSteps(reason: String, eventElapsedMs: Long, eventWallMs: Long) {
        // Both step sensors can describe the same walk; keep one shared reporting cooldown.
        if (lastStepReportedMs != Long.MIN_VALUE && eventElapsedMs - lastStepReportedMs < 60_000L) return
        lastStepReportedMs = eventElapsedMs
        onActivityDetected(reason, eventWallMs)
    }

    fun checkUserPresence() = checkUserPresence(explicitUserPresent = false)

    @Synchronized
    private fun checkUserPresence(explicitUserPresent: Boolean) {
        if (!isRegistered) return
        if (presenceDetector.record(
                powerManager.isInteractive, keyguardManager.isKeyguardLocked,
                explicitUserPresent, SystemClock.elapsedRealtime()
            )) {
            onActivityDetected("휴대전화 잠금 해제 감지", System.currentTimeMillis())
        }
    }

    private fun handleAccelerometer(event: SensorEvent, eventElapsedMs: Long, eventWallMs: Long) {
        // When steps are available, handset motion only supplements active, unlocked use.
        if ((stepsRegistered || counterRegistered) && (!powerManager.isInteractive || keyguardManager.isKeyguardLocked)) {
            repeatedMotionDetector.reset()
            hasAccelerometerSample = false
            return
        }
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        if (hasAccelerometerSample) {
            val deltaX = x - lastX
            val deltaY = y - lastY
            val deltaZ = z - lastZ
            val motion = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
            if (motion > MOTION_THRESHOLD && repeatedMotionDetector.record(eventElapsedMs)) {
                onActivityDetected("반복된 휴대전화 움직임 감지", eventWallMs)
            }
        }
        hasAccelerometerSample = true
        lastX = x
        lastY = y
        lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerStepSensor(type: Int): Boolean =
        listOfNotNull(sensorManager.getDefaultSensor(type, true), sensorManager.getDefaultSensor(type))
            .distinct().any { registerSensor(it) }

    private fun registerSensor(sensor: Sensor?): Boolean {
        if (sensor == null) return false
        return try {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (error: SecurityException) {
            Log.e(TAG, "Activity sensor permission is unavailable", error)
            false
        }
    }

    private fun resetDetectors() {
        repeatedMotionDetector.reset()
        repeatedStepDetector.reset()
        stepCounterDetector.reset()
        presenceDetector.reset()
        lastStepReportedMs = Long.MIN_VALUE
        hasAccelerometerSample = false
        lastX = 0f
        lastY = 0f
        lastZ = 0f
    }

    companion object {
        private const val TAG = "SensorMonitor"
        private const val MOTION_THRESHOLD = 3.5f
    }
}

internal object SensorEventTime {
    fun toWallTimeMs(eventNanos: Long, nowElapsedNanos: Long, nowWallMs: Long): Long? {
        if (eventNanos <= 0L || eventNanos > nowElapsedNanos) return null
        val eventWallMs = nowWallMs - (nowElapsedNanos - eventNanos) / 1_000_000L
        return eventWallMs.takeIf { it > 0L }
    }
}
