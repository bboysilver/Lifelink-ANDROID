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

object MonitoringStatusNotifier {
    internal const val NOTIFICATION_ID = 1004

    fun showUnexpectedStop(context: Context, detail: String) {
        show(
            context = context,
            title = "모니터링이 중단되었습니다",
            body = "$detail 앱을 열어 다시 시작해 주세요."
        )
    }

    fun showUserStopped(context: Context) {
        show(
            context = context,
            title = "모니터링이 꺼졌습니다",
            body = "활동 감지와 무활동 긴급 문자가 중단되었습니다."
        )
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, title: String, body: String) {
        createChannel(context)
        if (!SafetyNotificationCapability.canPost(context)) return

        val openApp = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(
            context,
            SafetyNotificationCapability.ALERT_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(false)
            .addAction(0, "앱 열기", openApp)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                SafetyNotificationCapability.ALERT_CHANNEL_ID,
                "안전 확인 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "무활동 경고와 모니터링 중단 등 즉시 확인할 안전 상태를 알립니다."
                enableVibration(true)
            }
        )
    }
}
