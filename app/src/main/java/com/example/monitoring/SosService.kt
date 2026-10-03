package com.example.monitoring

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import com.example.data.SafetyIncidentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** A user-requested SOS must not depend on the activity sensor's health-service permission. */
class SosService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var promotionFailed = false

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        try {
            ServiceCompat.startForeground(
                this, ONGOING_ID, notification(this, "SOS 문자 준비 중", "5초 안에 SOS를 취소할 수 있습니다.", true),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0
            )
        } catch (error: RuntimeException) {
            promotionFailed = true
            Log.e("LifeLinkSOS", "Immediate SOS service failed; persisted work will recover", error)
            showStatus(this, "SOS 문자 처리 대기", "즉시 전송 작업을 시작하지 못했습니다. 전송이 지연될 수 있으니 직접 연락해 주세요.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (promotionFailed) return START_NOT_STICKY
        if (intent?.action == ACTION_CANCEL) {
            val store = MonitoringStore(this)
            if (intent.getLongExtra(EXTRA_EVENT_MS, 0L) != store.pendingSosEventMs) return START_NOT_STICKY
            store.cancelPendingSos()
            job?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        val eventMs = intent?.getLongExtra(EXTRA_EVENT_MS, 0L) ?: 0L
        if (eventMs <= 0L || MonitoringStore(this).sosEventMs != eventMs) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive != true) {
            job = scope.launch {
                try {
                    delay((eventMs - System.currentTimeMillis()).coerceIn(0L, COUNTDOWN_MS))
                    // The countdown elapsed even if the user changed the wall clock.
                    SosDispatchTask(this@SosService).run(eventMs, maxOf(System.currentTimeMillis(), eventMs))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: RuntimeException) {
                    Log.e("LifeLinkSOS", "Immediate SOS dispatch failed; persisted work will recover", error)
                    showStatus(this@SosService, "SOS 문자 처리 대기", "전송 작업을 복구하고 있습니다. 긴급하면 보호자에게 직접 연락해 주세요.")
                } finally {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        job?.cancel()
        showStatus(this, "SOS 문자 처리 대기", "즉시 전송 작업이 중단되었습니다. 전송이 지연될 수 있으니 직접 연락해 주세요.")
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        internal const val EXTRA_EVENT_MS = "sos_event_ms"
        internal const val ACTION_CANCEL = "com.bboysilver.lifelink.action.CANCEL_MANUAL_SOS"
        internal const val COUNTDOWN_MS = 5_000L
        private const val ONGOING_ID = 1004

        fun start(context: Context) {
            val eventMs = MonitoringStore(context).sosEventMs
            if (eventMs <= 0L) return
            SosDispatchWorker.enqueue(context, eventMs)
            try {
                ContextCompat.startForegroundService(context, Intent(context, SosService::class.java)
                    .putExtra(EXTRA_EVENT_MS, eventMs))
            } catch (error: RuntimeException) {
                Log.e("LifeLinkSOS", "SOS foreground start rejected; persisted work will recover", error)
                showStatus(context, "SOS 문자 처리 대기", "즉시 전송 작업을 시작하지 못했습니다. 전송이 지연될 수 있으니 직접 연락해 주세요.")
            }
        }

        @SuppressLint("MissingPermission")
        internal fun showStatus(context: Context, title: String, body: String) {
            createChannel(context)
            if (SafetyNotificationCapability.canPost(context)) {
                NotificationManagerCompat.from(context).notify(
                    SafetySmsStatusNotifier.NOTIFICATION_ID, notification(context, title, body, false)
                )
            }
        }

        private fun notification(context: Context, title: String, body: String, cancellable: Boolean): Notification {
            val builder = NotificationCompat.Builder(context, SafetyNotificationCapability.ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title).setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            if (cancellable) {
                builder.setOngoing(true).addAction(0, "SOS 취소", PendingIntent.getService(
                    context, 4, Intent(context, SosService::class.java).setAction(ACTION_CANCEL)
                        .setData(android.net.Uri.parse("lifelink://cancel-sos/${MonitoringStore(context).pendingSosEventMs}"))
                        .putExtra(EXTRA_EVENT_MS, MonitoringStore(context).pendingSosEventMs),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
            }
            return builder.build()
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(SafetyNotificationCapability.ALERT_CHANNEL_ID,
                    "안전 확인 알림", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) })
        }
    }
}

class SosDispatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val eventMs = inputData.getLong(SosService.EXTRA_EVENT_MS, 0L)
        if (eventMs > 0L) SosDispatchTask(applicationContext).run(eventMs, maxOf(System.currentTimeMillis(), eventMs))
        Result.success()
    } catch (error: CancellationException) {
        throw error
    } catch (error: RuntimeException) {
        Log.e("LifeLinkSOS", "Persisted SOS dispatch failed", error)
        SosService.showStatus(applicationContext, "SOS 문자 처리 대기", "전송을 다시 시도하고 있습니다. 긴급하면 보호자에게 직접 연락해 주세요.")
        Result.retry()
    }

    companion object {
        internal fun enqueue(context: Context, eventMs: Long) {
            val request = OneTimeWorkRequestBuilder<SosDispatchWorker>()
                .setInputData(Data.Builder().putLong(SosService.EXTRA_EVENT_MS, eventMs).build())
                .setInitialDelay((eventMs - System.currentTimeMillis()).coerceIn(0L, SosService.COUNTDOWN_MS), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("lifelink-sos:$eventMs", ExistingWorkPolicy.KEEP, request)
        }
    }
}

internal class SosDispatchTask(
    context: Context,
    private val store: MonitoringStore = MonitoringStore(context.applicationContext),
    private val repository: LifeLinkRepository = LifeLinkRepository(AppDatabase.getDatabase(context)),
    private val incidents: SafetyIncidentRepository = SafetyIncidentRepository(AppDatabase.getDatabase(context))
) {
    private val appContext = context.applicationContext

    suspend fun run(expectedEventMs: Long, nowMs: Long = System.currentTimeMillis()) = LOCK.withLock {
        if (expectedEventMs <= 0L || store.sosEventMs != expectedEventMs) return@withLock
        val eventMs = store.claimPendingSos(nowMs, expectedEventMs) ?: return@withLock
        val batch = SafetyMessageDispatcher(appContext, store, repository, incidents) { log, title, body ->
            repository.insertLog("SMS_FAILED", log)
            SosService.showStatus(appContext, title, "$body 보호자에게 직접 연락해 주세요.")
            store.completeActiveSos(eventMs)
        }.queue(SafetySmsEventType.SOS, eventMs, EmergencyMessageBuilder.buildSos(store.deviceAlias, eventMs), null,
            canCreateIncident = { store.activeSosEventMs == eventMs }) ?: return@withLock
        if (batch.statuses.all { it.isResolved }) {
            store.completeActiveSos(eventMs)
            SafetySmsStatusNotifier.showCompletion(appContext, SafetySmsEventType.SOS, batch.statuses)
        } else {
            SafetySmsRetryWorker.enqueueRecovery(appContext)
            if (batch.queuedAny) SosService.showStatus(appContext, "SOS 문자 발송 확인 중", "통신사 결과를 확인하며 실패 시 최대 3회 다시 시도합니다.")
        }
    }

    companion object { private val LOCK = Mutex() }
}
