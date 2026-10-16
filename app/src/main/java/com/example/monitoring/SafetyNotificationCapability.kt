package com.example.monitoring

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

object SafetyNotificationCapability {
    const val ALERT_CHANNEL_ID = "lifelink_safety_alerts_unbadged"
    const val STATUS_CHANNEL_ID = "lifelink_safety_status_unbadged"
    const val MONITORING_CHANNEL_ID = "lifelink_monitoring_unbadged"
    private const val LEGACY_STATUS_CHANNEL_ID = "lifelink_safety_status"
    private const val LEGACY_MONITORING_CHANNEL_ID = "lifelink_monitoring"
    private const val LEGACY_ALERT_CHANNEL_ID = "lifelink_safety_alerts"

    internal fun createAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val legacy = manager.getNotificationChannel(LEGACY_ALERT_CHANNEL_ID)
        val channel = NotificationChannel(ALERT_CHANNEL_ID, "안전 확인 알림",
            legacy?.importance ?: NotificationManager.IMPORTANCE_HIGH).apply {
            description = "무활동 경고, 모니터링 중단과 문자 실패를 알립니다. 앱 배지는 표시하지 않습니다."
            setShowBadge(false)
            if (legacy != null) {
                setSound(legacy.sound, legacy.audioAttributes)
                vibrationPattern = legacy.vibrationPattern
                enableVibration(legacy.shouldVibrate())
                enableLights(legacy.shouldShowLights())
                lightColor = legacy.lightColor
            } else enableVibration(true)
        }
        manager.createNotificationChannel(channel)
        migrateLegacyAlerts(context)
    }

    @SuppressLint("MissingPermission")
    private fun migrateLegacyAlerts(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val openApp = PendingIntent.getActivity(context, 0, MainActivity.openIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.activeNotifications.filter { it.notification.channelId == LEGACY_ALERT_CHANNEL_ID }
            .forEach { existing ->
                // Preserve failures and actions; moving channels must not alert again.
                val notification = NotificationCompat.Builder(context, existing.notification)
                    .setChannelId(ALERT_CHANNEL_ID)
                    .setContentIntent(openApp)
                    .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                    .setNumber(0)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .build()
                manager.notify(existing.tag, existing.id, notification)
            }
    }

    internal fun createStatusChannel(context: Context) {
        createQuietChannel(context, STATUS_CHANNEL_ID, LEGACY_STATUS_CHANNEL_ID,
            "문자 처리 상태", "발송 완료 상태를 소리와 진동 없이 표시합니다.")
    }

    internal fun createMonitoringChannel(context: Context) {
        createQuietChannel(context, MONITORING_CHANNEL_ID, LEGACY_MONITORING_CHANNEL_ID,
            "안심 모니터링", "활동 감지가 실행 중임을 무음으로 표시합니다.")
    }

    private fun createQuietChannel(context: Context, id: String, legacyId: String,
        name: String, descriptionText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            // Badge preference cannot be changed after creation. Migrate only quiet channels,
            // preserving a user's existing block instead of enabling notifications again.
            val importance = manager.getNotificationChannel(legacyId)?.importance
                ?.coerceAtMost(NotificationManager.IMPORTANCE_LOW) ?: NotificationManager.IMPORTANCE_LOW
            manager.createNotificationChannel(
                NotificationChannel(id, name, importance)
                    .apply {
                        description = descriptionText
                        setShowBadge(false)
                        setSound(null, null)
                        enableVibration(false)
                    }
            )
        }
    }

    internal fun cancelLegacyConfirmedNotifications(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter {
            it.id == SafetySmsStatusNotifier.CONFIRMED_NOTIFICATION_ID &&
                it.notification.channelId == LEGACY_STATUS_CHANNEL_ID
        }.forEach { manager.cancel(it.tag, it.id) }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(ALERT_CHANNEL_ID)
            ?: manager.getNotificationChannel(LEGACY_ALERT_CHANNEL_ID)
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
