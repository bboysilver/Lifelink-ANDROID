package com.example.monitoring

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
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
        nowMs: Long = System.currentTimeMillis()
    ) = RUN_LOCK.withLock {
        val snapshot = store.snapshot(nowMs)
        if (!snapshot.desiredEnabled || !store.isSetupCurrent || snapshot.deadlineMs <= 0L ||
            (expectedDeadlineMs != null && expectedDeadlineMs != snapshot.deadlineMs)
        ) return@withLock

        val deadlineMs = snapshot.deadlineMs
        if (store.wasEmergencyDispatched(deadlineMs)) return@withLock
        if (snapshot.remainingSeconds in 1..DeadlineCalculator.PRE_ALERT_SECONDS &&
            !store.wasPreAlerted(deadlineMs)
        ) {
            showPreAlert()
            store.markPreAlert(deadlineMs)
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
                occurredAtMs = deadlineMs,
                message = EmergencyMessageBuilder.build(store.deviceAlias, battery),
                batteryPercent = battery,
                canCreateIncident = { store.desiredEnabled && store.deadlineMs == deadlineMs }
            )
            if (batch != null && batch.statuses.all { it.isResolved }) {
                store.markEmergency(deadlineMs)
                val failed = batch.statuses.count { it.state == SmsDispatchState.FAILED_FINAL }
                if (failed == 0) {
                    showStatus("긴급 문자 발송 확인", "모든 보호자 문자 발송이 확인되었습니다.")
                } else {
                    showStatus("긴급 문자 일부 실패", "${failed}명의 보호자에게 3회 시도했지만 발송하지 못했습니다.")
                }
            } else if (batch?.queuedAny == true) {
                showStatus("긴급 문자 발송 확인 중", "통신사 결과를 확인하며 실패 시 최대 3회 다시 시도합니다.")
            }
        }
        InactivityDeadlineScheduler(appContext).ensureScheduled(nowMs)
    }

    private fun showPreAlert() {
        val safeIntent = PendingIntent.getBroadcast(
            appContext,
            1,
            Intent(appContext, InactivityDeadlineReceiver::class.java)
                .setAction(InactivityDeadlineReceiver.ACTION_SAFE)
                .putExtra(InactivityDeadlineScheduler.EXTRA_DEADLINE, store.deadlineMs),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notify(
            builder("안전 확인이 필요합니다", "30분 안에 '무사합니다'를 눌러 주세요.")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .addAction(0, "무사합니다", safeIntent)
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
            .setAutoCancel(false)

    @SuppressLint("MissingPermission")
    private fun notify(builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    SafetyNotificationCapability.ALERT_CHANNEL_ID, "안전 확인 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { enableVibration(true) }
            )
        }
        if (SafetyNotificationCapability.canPost(appContext)) {
            NotificationManagerCompat.from(appContext).notify(ALERT_NOTIFICATION_ID, builder.build())
        }
    }

    companion object {
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val BLOCKED_RETRY_MS = 5 * 60 * 1_000L
        private val RUN_LOCK = Mutex()
        private var lastBlockedAlertMs = 0L
    }
}
