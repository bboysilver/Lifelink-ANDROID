package com.example.monitoring

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.DailyCheckInPhase
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class DailyCheckInWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val result = DailyCheckInTask(applicationContext).run()
        result.nextRunAtMs?.let { enqueueAt(applicationContext, result.dueAtMs, it) }
        Result.success()
    } catch (error: RuntimeException) {
        MonitoringStore(applicationContext).dailyCheckInError =
            error.message ?: "매일 안부 확인 작업을 처리하지 못했습니다."
        Result.retry()
    }

    companion object {
        private const val WORK_PREFIX = "lifelink-daily-check-in"
        internal const val WORK_TAG = "lifelink-daily-check-in-work"

        fun enqueueFromAlarm(context: Context, action: String) {
            val dueAtMs = MonitoringStore(context).dailyCheckInStatus().dueAtMs
            enqueue(
                context = context,
                uniqueName = "$WORK_PREFIX:alarm:$action:$dueAtMs",
                delayMs = 0L
            )
        }

        fun enqueueForSmsStatus(
            context: Context,
            eventId: String,
            outcome: SmsCallbackOutcome,
            retryAtMs: Long = 0L
        ) {
            val nowMs = System.currentTimeMillis()
            val delayMs = if (outcome == SmsCallbackOutcome.FAILED_RETRYABLE) {
                (retryAtMs - nowMs).coerceAtLeast(1_000L)
            } else {
                0L
            }
            enqueue(
                context = context,
                uniqueName = "$WORK_PREFIX:callback:$eventId:${outcome.name}:$retryAtMs",
                delayMs = delayMs
            )
        }

        fun enqueueAt(context: Context, dueAtMs: Long, runAtMs: Long) {
            val normalizedRunAtMs = (runAtMs / 1_000L) * 1_000L
            enqueue(
                context = context,
                uniqueName = "$WORK_PREFIX:follow-up:$dueAtMs:$normalizedRunAtMs",
                delayMs = (runAtMs - System.currentTimeMillis()).coerceAtLeast(1_000L)
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(WORK_TAG)
        }

        private fun enqueue(context: Context, uniqueName: String, delayMs: Long) {
            val request = OneTimeWorkRequestBuilder<DailyCheckInWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueName,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

internal data class DailyCheckInRunResult(
    val dueAtMs: Long,
    val nextRunAtMs: Long? = null
)

internal class DailyCheckInTask(
    context: Context,
    private val store: MonitoringStore = MonitoringStore(context.applicationContext),
    private val repository: LifeLinkRepository = LifeLinkRepository(
        AppDatabase.getDatabase(context.applicationContext)
    )
) {
    private val appContext = context.applicationContext

    suspend fun run(nowMs: Long = System.currentTimeMillis()): DailyCheckInRunResult {
        val status = store.dailyCheckInStatus(nowMs)
        return when (status.phase) {
            DailyCheckInPhase.DUE -> {
                if (!SafetyNotificationCapability.canPost(appContext)) {
                    skipBecauseNotificationsAreBlocked(status.dueAtMs)
                } else if (!store.wasDailyCheckInPrompted(status.dueAtMs)) {
                    store.dailyCheckInError = ""
                    store.markDailyCheckInPrompted(status.dueAtMs)
                    showPromptNotification()
                    repository.insertLog("DAILY_CHECK_IN", "오늘의 안부 확인을 요청했습니다.")
                }
                DailyCheckInRunResult(status.dueAtMs)
            }
            DailyCheckInPhase.OVERDUE -> {
                when {
                    !SafetyNotificationCapability.canPost(appContext) -> {
                        skipBecauseNotificationsAreBlocked(status.dueAtMs)
                        DailyCheckInRunResult(status.dueAtMs)
                    }
                    !store.wasDailyCheckInPrompted(status.dueAtMs) -> {
                        val deferredDueAtMs = store.deferDailyCheckInToNow(nowMs)
                        DailyCheckInScheduler(appContext).ensureScheduled(nowMs)
                        store.markDailyCheckInPrompted(deferredDueAtMs)
                        showPromptNotification()
                        repository.insertLog(
                            "DAILY_CHECK_IN",
                            "표시되지 않은 안부 확인을 지금부터 다시 시작했습니다."
                        )
                        DailyCheckInRunResult(deferredDueAtMs)
                    }
                    store.wasDailyCheckInAlerted(status.dueAtMs) ->
                        DailyCheckInRunResult(status.dueAtMs)
                    else -> dispatchMissedCheckIn(status.dueAtMs, nowMs)
                }
            }
            else -> DailyCheckInRunResult(status.dueAtMs)
        }
    }

    private suspend fun skipBecauseNotificationsAreBlocked(dueAtMs: Long) {
        val message = "알림이 꺼져 있어 안부 확인 문자를 보내지 않았습니다. 알림 권한과 채널을 확인해 주세요."
        store.dailyCheckInError = message
        store.advanceDailyCheckIn(dueAtMs)
        DailyCheckInScheduler(appContext).ensureScheduled()
        NotificationManagerCompat.from(appContext).cancel(DAILY_NOTIFICATION_ID)
        repository.insertLog("SYSTEM_ERROR", message)
    }

    private suspend fun dispatchMissedCheckIn(
        dueAtMs: Long,
        nowMs: Long
    ): DailyCheckInRunResult {
        val contacts = repository.allContacts.first().take(3)
        if (contacts.isEmpty()) {
            return blocked(
                dueAtMs,
                nowMs,
                "등록된 긴급 연락처가 없어 안부 미응답 문자를 보낼 수 없습니다."
            )
        }
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return blocked(
                dueAtMs,
                nowMs,
                "문자 권한이 없어 안부 미응답 문자를 보낼 수 없습니다."
            )
        }
        val smsSetup = SmsDeviceManager(appContext, store).inspect()
        if (smsSetup !is SmsSetupState.Ready) {
            return blocked(dueAtMs, nowMs, smsSetup.userMessage())
        }

        val sender = EmergencySmsSender(appContext)
        var queuedAny = false
        contacts.forEach { contact ->
            val eventId = EmergencySmsSender.dailyEventId(dueAtMs, contact.id)
            try {
                if (
                    sender.queue(
                        eventId = eventId,
                        contact = contact,
                        message = EmergencyMessageBuilder.buildDailyCheckInMissed(store.deviceAlias),
                        subscriptionId = smsSetup.line.subscriptionId
                    ) == SmsQueueResult.QUEUED
                ) {
                    queuedAny = true
                    repository.insertLog(
                        "SMS_QUEUED",
                        "${contact.name} 보호자 안부 미응답 문자 결과를 기다리고 있습니다."
                    )
                }
            } catch (error: Exception) {
                repository.insertLog(
                    "SMS_FAILED",
                    "${contact.name} 보호자 안부 미응답 문자 요청에 실패했습니다.",
                    error.message ?: "알 수 없는 오류"
                )
            }
        }

        val statuses = contacts.map {
            sender.status(EmergencySmsSender.dailyEventId(dueAtMs, it.id), nowMs)
        }
        if (statuses.all { it.isResolved }) {
            store.markDailyCheckInAlerted(dueAtMs)
            store.advanceDailyCheckIn(dueAtMs)
            store.dailyCheckInError = ""
            DailyCheckInScheduler(appContext).ensureScheduled(nowMs)
            NotificationManagerCompat.from(appContext).cancel(DAILY_NOTIFICATION_ID)
            showCompletionNotification(statuses.count { it.state == SmsDispatchState.FAILED_FINAL })
            return DailyCheckInRunResult(dueAtMs)
        }

        if (queuedAny) {
            showStatusNotification(
                "안부 미응답 문자 발송 확인 중",
                "통신사 결과를 확인하며 실패 시 최대 3회 다시 시도합니다."
            )
        }
        val nextRunAtMs = statuses
            .filterNot { it.isResolved }
            .map { smsStatus ->
                when (smsStatus.state) {
                    SmsDispatchState.QUEUED ->
                        smsStatus.updatedAtMs + SmsDispatchStore.CALLBACK_TIMEOUT_MS
                    SmsDispatchState.FAILED_RETRYABLE -> smsStatus.retryAtMs
                    else -> nowMs + BLOCKED_RETRY_MS
                }
            }
            .minOrNull()
            ?.coerceAtLeast(nowMs + 1_000L)
        return DailyCheckInRunResult(dueAtMs, nextRunAtMs)
    }

    private suspend fun blocked(
        dueAtMs: Long,
        nowMs: Long,
        message: String
    ): DailyCheckInRunResult {
        if (store.dailyCheckInError != message) {
            store.dailyCheckInError = message
            repository.insertLog("SMS_FAILED", message)
            showStatusNotification("안부 확인 문자 전송 대기", message)
        }
        return DailyCheckInRunResult(dueAtMs, nowMs + BLOCKED_RETRY_MS)
    }

    private fun showPromptNotification() {
        createAlertChannel()
        val safeIntent = PendingIntent.getService(
            appContext,
            2,
            Intent(appContext, MonitoringService::class.java)
                .setAction(MonitoringService.ACTION_DAILY_SAFE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(
            appContext,
            SafetyNotificationCapability.ALERT_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("오늘도 괜찮으신가요?")
            .setContentText("2시간 안에 안부를 알려 주세요.")
            .setContentIntent(launchAppIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .addAction(0, "괜찮아요", safeIntent)
            .build()
        notifyIfAllowed(DAILY_NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(failedCount: Int) {
        if (failedCount == 0) {
            showStatusNotification("안부 미응답 문자 발송 확인", "모든 보호자 문자 발송이 확인되었습니다.")
        } else {
            showStatusNotification(
                "안부 미응답 문자 일부 실패",
                "${failedCount}명의 보호자에게 3회 시도했지만 발송하지 못했습니다."
            )
        }
    }

    private fun showStatusNotification(title: String, body: String) {
        createAlertChannel()
        val notification = NotificationCompat.Builder(
            appContext,
            SafetyNotificationCapability.ALERT_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(launchAppIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(false)
            .build()
        notifyIfAllowed(ALERT_NOTIFICATION_ID, notification)
    }

    private fun launchAppIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        0,
        Intent(appContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    @SuppressLint("MissingPermission")
    private fun notifyIfAllowed(id: Int, notification: android.app.Notification) {
        if (SafetyNotificationCapability.canPost(appContext)) {
            NotificationManagerCompat.from(appContext).notify(id, notification)
        }
    }

    private fun createAlertChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                SafetyNotificationCapability.ALERT_CHANNEL_ID,
                "안전 확인 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "무활동 경고, 매일 안부 확인, SOS 문자 상태를 알립니다."
                enableVibration(true)
            }
        )
    }

    companion object {
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val DAILY_NOTIFICATION_ID = 1003
        private const val BLOCKED_RETRY_MS = 30 * 60 * 1_000L
    }
}