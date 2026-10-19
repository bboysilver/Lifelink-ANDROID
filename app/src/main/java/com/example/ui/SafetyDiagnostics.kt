package com.example.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringSnapshot
import com.example.data.MonitoringStore
import com.example.monitoring.SafetyNotificationCapability

enum class BatteryPolicy { UNRESTRICTED, OPTIMIZED, RESTRICTED, UNKNOWN }

data class DiagnosticEnvironment(
    val smsGranted: Boolean,
    val phoneGranted: Boolean,
    val activityGranted: Boolean,
    val notificationsGranted: Boolean,
    val hasActivitySensor: Boolean,
    val batteryPolicy: BatteryPolicy,
    val airplaneModeEnabled: Boolean = false,
    val activitySensorSummary: String = "지원 정보 확인 필요"
) {
    val permissionsGranted: Boolean
        get() = smsGranted && phoneGranted && activityGranted && notificationsGranted
}

data class SafetyDiagnosticReport(
    val snapshot: MonitoringSnapshot,
    val environment: DiagnosticEnvironment,
    val setupProblem: String?,
    val checkedAtMs: Long
) {
    val heartbeatFresh: Boolean
        get() = snapshot.lastHeartbeatMs in 1..checkedAtMs &&
            checkedAtMs - snapshot.lastHeartbeatMs <= MonitoringStore.HEARTBEAT_TIMEOUT_MS

    val monitoringConfirmed: Boolean
        get() = snapshot.desiredEnabled && snapshot.isRunning && heartbeatFresh &&
            setupProblem == null && environment.permissionsGranted && environment.hasActivitySensor

    val headline: String
        get() = when {
            setupProblem != null || !environment.permissionsGranted || !environment.hasActivitySensor -> "설정을 확인해 주세요"
            !snapshot.desiredEnabled -> "모니터링이 꺼져 있습니다"
            snapshot.runtimeState == MonitoringRuntimeState.STARTING -> "모니터링 시작 중"
            !monitoringConfirmed -> "모니터링 실행 확인이 필요합니다"
            else -> "모니터링 실행이 확인되었습니다"
        }
}

internal object SafetyDiagnosticsReader {
    fun read(context: Context): DiagnosticEnvironment {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

        val sensors = context.getSystemService(SensorManager::class.java)
        val sensorNames = listOfNotNull(
            "걸음 감지".takeIf { sensors?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null ||
                sensors?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR, true) != null },
            "걸음 수".takeIf { sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null ||
                sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true) != null },
            "움직임".takeIf { sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null }
        )
        val power = context.getSystemService(PowerManager::class.java)
        val backgroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        return DiagnosticEnvironment(
            smsGranted = granted(Manifest.permission.SEND_SMS),
            phoneGranted = granted(Manifest.permission.READ_PHONE_STATE),
            activityGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                granted(Manifest.permission.ACTIVITY_RECOGNITION),
            notificationsGranted = SafetyNotificationCapability.canPost(context),
            hasActivitySensor = sensorNames.isNotEmpty(),
            activitySensorSummary = sensorNames.joinToString(" · ").ifBlank { "지원 센서 없음" },
            airplaneModeEnabled = Settings.Global.getInt(
                context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0
            ) != 0,
            batteryPolicy = when {
                backgroundRestricted -> BatteryPolicy.RESTRICTED
                power == null -> BatteryPolicy.UNKNOWN
                power.isIgnoringBatteryOptimizations(context.packageName) -> BatteryPolicy.UNRESTRICTED
                else -> BatteryPolicy.OPTIMIZED
            }
        )
    }
}
