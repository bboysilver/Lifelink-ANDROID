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

class SensorMonitor(
    private val context: Context,
    private val onActivityDetected: (String) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastMotionMs = 0L
    private var isRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> onActivityDetected("화면 켜짐 감지")
                Intent.ACTION_USER_PRESENT -> onActivityDetected("휴대전화 잠금 해제 감지")
            }
        }
    }

    fun start() {
        if (isRegistered) return
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            context,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isRegistered = true
        Log.d(TAG, "Sensing engine started")
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
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val deltaX = x - lastX
        val deltaY = y - lastY
        val deltaZ = z - lastZ
        val motion = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
        val nowMs = System.currentTimeMillis()

        if (lastX != 0f && motion > MOTION_THRESHOLD && nowMs - lastMotionMs >= MOTION_COOLDOWN_MS) {
            lastMotionMs = nowMs
            onActivityDetected("걷기 또는 휴대전화 움직임 감지")
        }

        lastX = x
        lastY = y
        lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val TAG = "SensorMonitor"
        private const val MOTION_THRESHOLD = 3.5f
        private const val MOTION_COOLDOWN_MS = 60_000L
    }
}
