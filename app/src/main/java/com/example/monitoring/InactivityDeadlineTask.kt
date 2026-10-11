package com.example.monitoring

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.DeadlineCalculator
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import com.example.data.SafetyIncidentRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InactivityDeadlineTask(
    context: Context,
    private val store: MonitoringStore = MonitoringStore(context.applicationContext),
    private val repository: LifeLinkRepository = LifeLinkRepository(AppDatabase.getDatabase(context)),
    private val incidents: SafetyIncidentRepository = SafetyIncidentRepository(AppDatabase.getDatabase(context))
) {
    private val appContext = context.applicationContext

    suspend fun run(
        expectedDeadlineMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ) = RUN_LOCK.withLock {
        store.rebaseAfterWallClockChange(nowMs, elapsedRealtimeMs)
        val snapshot = store.snapshot(nowMs)
        if (!snapshot.desiredEnabled || !store.isSetupCurrent || snapshot.deadlineMs <= 0L ||
            (expectedDeadlineMs != null && expectedDeadlineMs != snapshot.deadlineMs)
        ) return@withLock

        val deadlineMs = snapshot.deadlineMs
        val eventMs = store.inactivityEventMs
        if (store.wasEmergencyDispatched(eventMs)) return@withLock
        if (snapshot.remainingSeconds in 1..DeadlineCalculator.PRE_ALERT_SECONDS &&
            store.markPreAlert(deadlineMs) { showPreAlert(deadlineMs) }
        ) {
            repository.insertLog("ALERT_WARNING", "설정된 안심 시간 종료 30분 전 사전 알림을 표시했습니다.")
        }
        if (snapshot.remainingSeconds == 0L) {
            val battery = appContext.getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
            val batch = SafetyMessageDispatcher(appContext, store, repository, incidents) { log, title, body ->
                if (nowMs - lastBlockedAlertMs >= BLOCKED_RETRY_MS) {
                    lastBlockedAlertMs = nowMs
                    repository.insertLog("SMS_FAILED", log)
                    showStatus(title, body)
                }
            }.queue(
                type = SafetySmsEventType.EMERGENCY,
                occurredAtMs = eventMs,
                message = EmergencyMessageBuilder.build(
                    store.deviceAlias, battery, snapshot.lastActivityMs,
                    store.monitorHours.takeIf { store.debugMonitorMinutes == 0 }
                ),
                batteryPercent = battery,
                canCreateIncident = { store.desiredEnabled && store.inactivityEventMs == eventMs }
            )
            if (batch != null && batch.statuses.all { it.isResolved }) {
                store.markEmergency(eventMs)
                cancelPreAlert(appContext)
                SafetySmsStatusNotifier.showCompletion(appContext, SafetySmsEventType.EMERGENCY, batch.statuses,
                    "${SafetySmsEventType.EMERGENCY.wireName}:$eventMs")
            } else if (batch?.queuedAny == true) {
                val format = java.text.SimpleDateFormat("MM/dd HH:mm:ss", java.util.Locale.KOREA)
                repository.insertLog(
                    "INACTIVITY_DISPATCH",
                    "${store.monitorHours}시간 무활동 경보 발송 요청",
                    "마지막 활동: ${format.format(java.util.Date(snapshot.lastActivityMs))} " +
                        "(${snapshot.lastActivityReason}), 마감: ${format.format(java.util.Date(deadlineMs))}"
                )
                cancelPreAlert(appContext)
            }
        }
        InactivityDeadlineScheduler(appContext).ensureScheduled(nowMs)
    }

    private fun showPreAlert(deadlineMs: Long): Boolean {
        val safeIntent = PendingIntent.getBroadcast(
            appContext,
            1,
            Intent(appContext, InactivityDeadlineReceiver::class.java)
                .setAction(InactivityDeadlineReceiver.ACTION_SAFE)
                .putExtra(InactivityDeadlineScheduler.EXTRA_DEADLINE, deadlineMs),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return notify(
            builder("안전 확인이 필요합니다", "30분 안에 활동이 감지되거나 '무사합니다'를 누르면 자동 취소됩니다.")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .addAction(0, "무사합니다", safeIntent),
            PRE_ALERT_NOTIFICATION_ID
        )
    }

    private fun showStatus(title: String, body: String) {
        notify(builder(title, body).setCategory(NotificationCompat.CATEGORY_ERROR))
    }

    private fun builder(title: String, body: String): NotificationCompat.Builder =
        NotificationCompat.Builder(appContext, SafetyNotificationCapability.ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(
                PendingIntent.getActivity(
                    appContext, 0, Intent(appContext, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)

    @SuppressLint("MissingPermission")
    private fun notify(builder: NotificationCompat.Builder, id: Int = ALERT_NOTIFICATION_ID): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    SafetyNotificationCapability.ALERT_CHANNEL_ID, "안전 확인 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { enableVibration(true) }
            )
        }
        if (SafetyNotificationCapability.canPost(appContext)) {
            NotificationManagerCompat.from(appContext).notify(id, builder.build())
            return true
        }
        return false
    }

    companion object {
        private const val ALERT_NOTIFICATION_ID = 1002
        internal const val PRE_ALERT_NOTIFICATION_ID = 1006
        private const val BLOCKED_RETRY_MS = 5 * 60 * 1_000L
        private val RUN_LOCK = Mutex()
        private var lastBlockedAlertMs = 0L

        fun cancelPreAlert(context: Context) {
            NotificationManagerCompat.from(context).cancel(PRE_ALERT_NOTIFICATION_ID)
            // Pre-2.3.1 warnings shared the SMS status ID; never cancel an actual failure.
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter {
                it.id == ALERT_NOTIFICATION_ID && it.tag == null &&
                    it.notification.extras.getString(Notification.EXTRA_TITLE) == "안전 확인이 필요합니다"
            }.forEach { manager.cancel(it.tag, it.id) }
        }
    }
}
