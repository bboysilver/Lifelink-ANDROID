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
    private var isRegistered = false

    // 움직임 판단 임계값 (스마트폰 흔들림 및 걷기 감지)
    private val motionThreshold = 3.5f 
    private var lastTime: Long = 0

    // 기기 상태 브로드캐스트 리시버
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    onActivityDetected("화면 켜짐(Screen On) 감지")
                }
                Intent.ACTION_USER_PRESENT -> {
                    onActivityDetected("스마트폰 잠금 해제 감지")
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    onActivityDetected("충전 케이블 연결 감지")
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    onActivityDetected("충전 케이블 분리 감지")
                }
            }
        }
    }

    fun start() {
        if (isRegistered) return

        // 1. 센서 등록 (움직임 감지용 가속도)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // 2. 브로드캐스트 리시버 등록
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            statusReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isRegistered = true
        Log.d("SensorMonitor", "Sensing Engine Started")
    }

    fun stop() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        try {
            context.unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            Log.e("SensorMonitor", "Receiver unregister error: ${e.message}")
        }
        isRegistered = false
        Log.d("SensorMonitor", "Sensing Engine Stopped")
    }

    // SensorEventListener 구현
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val currentTime = System.currentTimeMillis()
        // 센서 감지 쿨다운 적용 (최소 1초 간격으로 움직임 전파)
        if (currentTime - lastTime > 1500) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // 가속도 벡터 변화량 산출
            val deltaX = x - lastX
            val deltaY = y - lastY
            val deltaZ = z - lastZ

            val speed = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
            
            // 임계치를 넘었는지 체크하며, 지구 중력가속도 초기화 보정
            if (speed > motionThreshold && lastX != 0f) {
                lastTime = currentTime
                onActivityDetected("걸음걸이 및 기기 흔들림 감지")
            }

            lastX = x
            lastY = y
            lastZ = z
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 단말 상태에 따른 정확도 보정 필요 없음
    }
}
