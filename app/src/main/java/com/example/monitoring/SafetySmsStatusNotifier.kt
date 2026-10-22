package com.example.monitoring

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
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
        SafetyNotificationCapability.createAlertChannel(appContext)
        val confirmed = failedCount == 0
        if (confirmed) NotificationManagerCompat.from(appContext).cancel(incidentId, NOTIFICATION_ID)
        if (!SafetyNotificationCapability.canPost(appContext)) return
        if (confirmed) SafetyNotificationCapability.createStatusChannel(appContext)
        val guidance = if (type == SafetySmsEventType.EMERGENCY) {
            " 같은 무활동 경고는 새로 반복하지 않습니다. 문자 발송은 보호자의 안부 확인을 뜻하지 않습니다."
        } else ""
        val notification = NotificationCompat.Builder(appContext,
            if (confirmed) SafetyNotificationCapability.STATUS_CHANNEL_ID else SafetyNotificationCapability.ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body + guidance)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body + guidance))
            .setContentIntent(
                PendingIntent.getActivity(
                    appContext, 0, MainActivity.openIntent(appContext),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setPriority(if (confirmed) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setDefaults(if (confirmed) 0 else NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setSilent(confirmed)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(confirmed)
            .build()
        NotificationManagerCompat.from(appContext).notify(
            incidentId, if (confirmed) CONFIRMED_NOTIFICATION_ID else NOTIFICATION_ID, notification
        )
    }

    fun hasFailureNotice(context: Context, incidentId: String): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications.any {
            it.tag == incidentId && it.id == NOTIFICATION_ID
        }
}
