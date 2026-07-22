package com.example.monitoring

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object SafetyNotificationCapability {
    const val ALERT_CHANNEL_ID = "lifelink_safety_alerts"

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