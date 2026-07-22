package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

enum class SensorStartResult { STEP_DETECTOR, REPEATED_MOTION, FAILED }

class SensorMonitor(
    private val context: Context,
    private val onActivityDetected: (String) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val repeatedMotionDetector = RepeatedMotionDetector()
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
    private var startResult = SensorStartResult.FAILED

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            ActivitySignalClassifier.reasonForBroadcast(intent?.action)?.let(onActivityDetected)
        }
    }

    fun start(): SensorStartResult {
        if (isRegistered) return startResult

        startResult = when {
            stepDetector != null && sensorManager.registerListener(
                this,
                stepDetector,
                SensorManager.SENSOR_DELAY_NORMAL
            ) -> SensorStartResult.STEP_DETECTOR
            accelerometer != null && sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL
            ) -> SensorStartResult.REPEATED_MOTION
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
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (error: RuntimeException) {
            sensorManager.unregisterListener(this)
            startResult = SensorStartResult.FAILED
            Log.e(TAG, "Unlock receiver registration failed", error)
            return startResult
        }

        isRegistered = true
        Log.d(TAG, "Sensing engine started with $startResult")
        return startResult
    }

    fun stop() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        try {
            context.unregisterReceiver(statusReceiver)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Status receiver was already unregistered", error)
        }
        isRegistered = false
        Log.d(TAG, "Sensing engine stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_STEP_DETECTOR -> handleStep(System.currentTimeMillis())
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event, System.currentTimeMillis())
        }
    }

    private fun handleStep(nowMs: Long) {
        if (repeatedStepDetector.record(nowMs)) {
            onActivityDetected("반복된 걸음 감지")
        }
    }

    private fun handleAccelerometer(event: SensorEvent, nowMs: Long) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        if (hasAccelerometerSample) {
            val deltaX = x - lastX
            val deltaY = y - lastY
            val deltaZ = z - lastZ
            val motion = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
            if (motion > MOTION_THRESHOLD && repeatedMotionDetector.record(nowMs)) {
                onActivityDetected("반복된 휴대전화 움직임 감지")
            }
        }
        hasAccelerometerSample = true
        lastX = x
        lastY = y
        lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val TAG = "SensorMonitor"
        private const val MOTION_THRESHOLD = 3.5f
    }
}
