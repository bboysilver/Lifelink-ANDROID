package com.example.monitoring

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.DeadlineCalculator
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import com.example.data.SensorMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var store: MonitoringStore
    private lateinit var repository: LifeLinkRepository
    private lateinit var sensorMonitor: SensorMonitor
    private var monitorJob: Job? = null
    private var lastBlockingAlertMs = 0L
    private var startupFailed = false

    override fun onCreate() {
        super.onCreate()
        store = MonitoringStore(this)
        repository = LifeLinkRepository(AppDatabase.getDatabase(this))
        if (!store.desiredEnabled) {
            stopSelf()
            return
        }
        store.markServiceStarting()
        createNotificationChannels()
        if (!startForegroundSafely()) {
            startupFailed = true
            stopSelf()
            return
        }
        sensorMonitor = SensorMonitor(this) { reason ->
            if (store.desiredEnabled) store.resetDeadline(reason = reason)
        }
        sensorMonitor.start()
        store.markServiceRunning()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REPORT_SAFE -> {
                store.resetDeadline(reason = "알림에서 무사 확인")
                NotificationManagerCompat.from(this).cancel(ALERT_NOTIFICATION_ID)
                serviceScope.launch {
                    repository.insertLog("SENSOR_RESET", "사용자가 알림에서 무사함을 확인했습니다.")
                }
            }
            ACTION_RESET -> store.resetDeadline(
                reason = intent.getStringExtra(EXTRA_REASON) ?: "활동 확인"
            )
            ACTION_START -> store.initializeDeadlineIfMissing()
        }

        if (!store.desiredEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        startMonitorLoop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::sensorMonitor.isInitialized) sensorMonitor.stop()
        monitorJob?.cancel()
        serviceScope.cancel()
        if (::store.isInitialized && store.desiredEnabled && !startupFailed) {
            store.markServiceError("모니터링 서비스가 종료되었습니다.")
        }
        super.onDestroy()
    }

    private fun startMonitorLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            repository.insertLog("SAFETY_INIT", "백그라운드 안심 모니터링이 시작되었습니다.")
            while (isActive && store.desiredEnabled) {
                store.markHeartbeat()
                evaluateDeadline()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun evaluateDeadline() {
        val snapshot = store.snapshot()
        updateOngoingNotification(snapshot.remainingSeconds)
        if (snapshot.deadlineMs <= 0L) return

        if (
            snapshot.remainingSeconds in 1..DeadlineCalculator.PRE_ALERT_SECONDS &&
            !store.wasPreAlerted(snapshot.deadlineMs)
        ) {
            store.markPreAlert(snapshot.deadlineMs)
            showPreAlertNotification()
            repository.insertLog(
                "ALERT_WARNING",
                "설정된 안심 시간 종료 30분 전 사전 알림을 표시했습니다."
            )
        }

        if (snapshot.remainingSeconds == 0L && !store.wasEmergencyDispatched(snapshot.deadlineMs)) {
            dispatchEmergency(snapshot.deadlineMs)
        }
    }

    private suspend fun dispatchEmergency(deadlineMs: Long) {
        val contacts = repository.allContacts.first().take(3)
        if (contacts.isEmpty()) {
            reportBlockingDispatchProblem(
                logMessage = "등록된 긴급 연락처가 없어 문자를 보낼 수 없습니다.",
                title = "긴급 연락처가 없습니다",
                body = "앱을 열어 긴급 연락처를 등록해 주세요."
            )
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            reportBlockingDispatchProblem(
                logMessage = "문자 권한이 없어 긴급 문자를 보낼 수 없습니다.",
                title = "문자 권한이 필요합니다",
                body = "앱 설정에서 문자 권한을 허용해 주세요."
            )
            return
        }

        val message = EmergencyMessageBuilder.build(store.deviceAlias, getBatteryPercentageOrNull())
        val sender = EmergencySmsSender(this)
        var queuedAny = false

        contacts.forEach { contact ->
            val eventId = EmergencySmsSender.emergencyEventId(deadlineMs, contact.id)
            try {
                if (sender.queue(eventId, contact, message) == SmsQueueResult.QUEUED) {
                    queuedAny = true
                    repository.insertLog(
                        "SMS_QUEUED",
                        "${contact.name} 보호자 문자 발송 결과를 기다리고 있습니다."
                    )
                }
            } catch (error: Exception) {
                repository.insertLog(
                    "SMS_FAILED",
                    "${contact.name} 보호자 문자 발송 요청에 실패했습니다.",
                    error.message ?: "알 수 없는 오류"
                )
            }
        }

        val statuses = contacts.map { contact ->
            sender.status(EmergencySmsSender.emergencyEventId(deadlineMs, contact.id))
        }
        if (statuses.all { it.isResolved }) {
            store.markEmergency(deadlineMs)
            val failedCount = statuses.count { it.state == SmsDispatchState.FAILED_FINAL }
            if (failedCount == 0) {
                showAlertNotification(
                    "긴급 문자 발송 확인",
                    "모든 보호자 문자 발송이 확인되었습니다."
                )
            } else {
                showAlertNotification(
                    "긴급 문자 일부 실패",
                    "${failedCount}명의 보호자에게 3회 시도했지만 발송하지 못했습니다."
                )
            }
        } else if (queuedAny) {
            showAlertNotification(
                "긴급 문자 발송 확인 중",
                "통신사 발송 결과를 확인하고 있으며 실패 시 최대 3회 다시 시도합니다."
            )
        }
    }

    private suspend fun reportBlockingDispatchProblem(logMessage: String, title: String, body: String) {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastBlockingAlertMs < BLOCKING_ALERT_INTERVAL_MS) return
        lastBlockingAlertMs = nowMs
        repository.insertLog("SMS_FAILED", logMessage)
        showAlertNotification(title, body)
    }

    private fun startForegroundSafely(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            MONITORING_NOTIFICATION_ID,
            buildOngoingNotification(store.snapshot().remainingSeconds),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else {
                0
            }
        )
        true
    } catch (error: RuntimeException) {
        store.markServiceError(error.message ?: "포그라운드 서비스를 시작하지 못했습니다.")
        serviceScope.launch {
            repository.insertLog(
                "SYSTEM_ERROR",
                "백그라운드 모니터링 서비스를 시작하지 못했습니다.",
                error.message ?: "서비스 시작 오류"
            )
        }
        false
    }

    private fun buildOngoingNotification(remainingSeconds: Long): android.app.Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, MONITORING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("라이프링크 안심 모니터링 중")
            .setContentText("다음 안전 확인까지 ${formatRemaining(remainingSeconds)}")
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateOngoingNotification(remainingSeconds: Long) {
        notifyIfAllowed(MONITORING_NOTIFICATION_ID, buildOngoingNotification(remainingSeconds))
    }

    private fun showPreAlertNotification() {
        val safeIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitoringService::class.java).setAction(ACTION_REPORT_SAFE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("안전 확인이 필요합니다")
            .setContentText("30분 안에 '무사합니다'를 눌러 주세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .addAction(0, "무사합니다", safeIntent)
            .build()
        notifyIfAllowed(ALERT_NOTIFICATION_ID, notification)
    }

    private fun showAlertNotification(title: String, body: String) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(false)
            .build()
        notifyIfAllowed(ALERT_NOTIFICATION_ID, notification)
    }

    private fun notifyIfAllowed(id: Int, notification: android.app.Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(this).notify(id, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                MONITORING_CHANNEL_ID,
                "안심 모니터링",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "활동 감지가 실행 중임을 표시합니다." }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "안전 확인 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "무활동 사전 경고와 긴급 문자 상태를 알립니다."
                enableVibration(true)
            }
        )
    }

    private fun getBatteryPercentageOrNull(): Int? {
        val manager = getSystemService(BatteryManager::class.java)
        val value = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return value.takeIf { it in 0..100 }
    }

    private fun formatRemaining(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
    }

    companion object {
        const val ACTION_START = "com.bboysilver.lifelink.action.START"
        const val ACTION_RESET = "com.bboysilver.lifelink.action.RESET"
        const val ACTION_REPORT_SAFE = "com.bboysilver.lifelink.action.REPORT_SAFE"
        const val EXTRA_REASON = "reason"
        private const val MONITORING_CHANNEL_ID = "lifelink_monitoring"
        private const val ALERT_CHANNEL_ID = "lifelink_safety_alerts"
        private const val MONITORING_NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val CHECK_INTERVAL_MS = 15_000L
        private const val BLOCKING_ALERT_INTERVAL_MS = 5 * 60 * 1_000L

        fun start(context: Context) {
            val store = MonitoringStore(context)
            if (!store.desiredEnabled) return
            store.markServiceStarting()
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MonitoringService::class.java).setAction(ACTION_START)
                )
            } catch (error: RuntimeException) {
                store.markServiceError(error.message ?: "서비스 시작 요청에 실패했습니다.")
                throw error
            }
        }

        fun stop(context: Context) {
            MonitoringStore(context).stop()
            context.stopService(Intent(context, MonitoringService::class.java))
        }

        fun reset(context: Context, reason: String) {
            if (!MonitoringStore(context).desiredEnabled) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java)
                    .setAction(ACTION_RESET)
                    .putExtra(EXTRA_REASON, reason)
            )
        }
    }
}
