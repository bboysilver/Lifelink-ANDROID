package com.example.monitoring

import android.Manifest
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object SafetyNotificationCapability {
    const val ALERT_CHANNEL_ID = "lifelink_safety_alerts"
    const val STATUS_CHANNEL_ID = "lifelink_safety_status"

    internal fun createStatusChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(STATUS_CHANNEL_ID, "문자 처리 상태", NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = "발송 완료 상태를 소리와 진동 없이 표시합니다."
                        setSound(null, null)
                        enableVibration(false)
                    }
            )
        }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true

        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(ALERT_CHANNEL_ID)
        return isAvailable(
            permissionGranted = true,
            appNotificationsEnabled = true,
            channelImportance = channel?.importance
        )
    }

    internal fun isAvailable(
        permissionGranted: Boolean,
        appNotificationsEnabled: Boolean,
        channelImportance: Int?
    ): Boolean =
        permissionGranted &&
            appNotificationsEnabled &&
            channelImportance != NotificationManager.IMPORTANCE_NONE
}
