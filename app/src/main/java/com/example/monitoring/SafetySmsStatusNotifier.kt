package com.example.monitoring

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

internal object SafetySmsStatusNotifier {
    internal const val NOTIFICATION_ID = 1002
    internal const val CONFIRMED_NOTIFICATION_ID = 1007

    @SuppressLint("MissingPermission")
    fun showCompletion(
        context: Context,
        type: SafetySmsEventType,
        statuses: List<SmsDispatchStatus>,
        incidentId: String
    ) {
        if (statuses.isEmpty() || statuses.any { !it.isResolved }) return
        val appContext = context.applicationContext
        val label = when (type) {
            SafetySmsEventType.EMERGENCY -> "긴급 문자"
            SafetySmsEventType.SOS -> "SOS 문자"
            SafetySmsEventType.DAILY -> "안부 미응답 문자"
        }
        val failedCount = statuses.count { it.state == SmsDispatchState.FAILED_FINAL }
        val title: String
        val body: String
        when (failedCount) {
            0 -> {
                title = "$label 발송 확인"
                body = "모든 보호자 문자 발송이 확인되었습니다."
            }
            statuses.size -> {
                title = "$label 발송 확인 필요"
                body = "보호자 ${statuses.size}명 모두 문자 발송을 확인하지 못했습니다. 직접 연락해 주세요."
            }
            else -> {
                title = "$label 일부 발송 미확인"
                body = "보호자 ${statuses.size}명 중 ${failedCount}명의 문자 발송을 확인하지 못했습니다. 직접 연락해 주세요."
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    SafetyNotificationCapability.ALERT_CHANNEL_ID,
                    "안전 확인 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { enableVibration(true) }
            )
        }
        if (!SafetyNotificationCapability.canPost(appContext)) return
        val confirmed = failedCount == 0
        if (confirmed) SafetyNotificationCapability.createStatusChannel(appContext)
        val notification = NotificationCompat.Builder(appContext,
            if (confirmed) SafetyNotificationCapability.STATUS_CHANNEL_ID else SafetyNotificationCapability.ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(
                PendingIntent.getActivity(
                    appContext, 0, Intent(appContext, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setPriority(if (confirmed) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setSilent(confirmed)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .build()
        NotificationManagerCompat.from(appContext).notify(
            incidentId, if (confirmed) CONFIRMED_NOTIFICATION_ID else NOTIFICATION_ID, notification
        )
    }
}
