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
import com.example.data.Contact
import com.example.data.DailyCheckInPhase
import com.example.data.DeadlineCalculator
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import com.example.data.SensorMonitor
import com.example.data.SensorStartResult
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
    private lateinit var smsSubscriptionMonitor: SmsSubscriptionMonitor
    private var monitorJob: Job? = null
    private var lastBlockingAlertMs = 0L
    private var lastMaintenanceMs = 0L
    private var startupFailed = false

    override fun onCreate() {
        super.onCreate()
        store = MonitoringStore(this)
        repository = LifeLinkRepository(AppDatabase.getDatabase(this))
        SmsDispatchStore(this).pruneExpired()
        if (!store.desiredEnabled && store.pendingSosEventMs <= 0L) {
            stopSelf()
            return
        }
        if (store.desiredEnabled) store.markServiceStarting()
        createNotificationChannels()
        if (!startForegroundSafely()) {
            startupFailed = true
            stopSelf()
            return
        }

        val smsSetup = SmsDeviceManager(this, store).inspect()
        if (smsSetup !is SmsSetupState.Ready) {
            failStartup(smsSetup.userMessage())
            return
        }

        if (store.desiredEnabled) {
            sensorMonitor = SensorMonitor(this) { reason ->
                if (store.desiredEnabled) store.resetDeadline(reason = reason)
            }
            if (sensorMonitor.start() == SensorStartResult.FAILED) {
                failStartup("활동 센서를 시작할 수 없습니다. 기기를 다시 시작하거나 센서 권한을 확인해 주세요.")
                return
            }
        }

        smsSubscriptionMonitor = SmsSubscriptionMonitor(this) { state ->
            if (
                state !is SmsSetupState.Ready &&
                (store.desiredEnabled || store.pendingSosEventMs > 0L)
            ) {
                val message = state.userMessage()
                startupFailed = true
                store.markServiceError(message)
                serviceScope.launch {
                    repository.insertLog("SYSTEM_ERROR", "SIM 변경으로 모니터링을 중단했습니다.", message)
                }
                showAlertNotification("SIM 상태 확인 필요", message)
                if (!store.desiredEnabled) store.clearPendingSos()
                stopSelf()
            }
        }
        if (!smsSubscriptionMonitor.start()) {
            failStartup("SIM 변경 상태를 감시할 수 없습니다.")
            return
        }
        if (store.desiredEnabled) store.markServiceRunning()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REPORT_SAFE -> confirmSafe("알림에서 무사 확인")
            ACTION_DAILY_SAFE -> confirmDailyCheckIn("매일 안부 알림에서 괜찮음 확인")
            ACTION_TRIGGER_SOS -> {
                store.beginSos()
                serviceScope.launch { dispatchPendingSos() }
            }
            ACTION_RESET -> store.resetDeadline(
                reason = intent.getStringExtra(EXTRA_REASON) ?: "활동 확인"
            )
            ACTION_START -> store.initializeDeadlineIfMissing()
        }

        if (!store.desiredEnabled && store.pendingSosEventMs <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        startMonitorLoop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::sensorMonitor.isInitialized) sensorMonitor.stop()
        if (::smsSubscriptionMonitor.isInitialized) smsSubscriptionMonitor.stop()
        monitorJob?.cancel()
        serviceScope.cancel()
        if (::store.isInitialized && store.desiredEnabled && !startupFailed) {
            store.markServiceError("모니터링 서비스가 종료되었습니다.")
        }
        super.onDestroy()
    }

    private fun failStartup(message: String) {
        startupFailed = true
        store.markServiceError(message)
        if (!store.desiredEnabled) store.clearPendingSos()
        serviceScope.launch { repository.insertLog("SYSTEM_ERROR", message) }
        showAlertNotification("모니터링 시작 실패", message)
        stopSelf()
    }

    private fun startMonitorLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            if (store.desiredEnabled) {
                repository.insertLog("SAFETY_INIT", "백그라운드 안심 모니터링이 시작되었습니다.")
            }
            while (isActive && (store.desiredEnabled || store.pendingSosEventMs > 0L)) {
                if (store.desiredEnabled) {
                    store.markHeartbeat()
                    evaluateDailyCheckIn()
                    evaluateInactivityDeadline()
                    runMaintenanceIfNeeded()
                }
                dispatchPendingSos()
                delay(if (store.pendingSosEventMs > 0L) SOS_CHECK_INTERVAL_MS else CHECK_INTERVAL_MS)
            }
            if (!store.desiredEnabled) stopSelf()
        }
    }

    private suspend fun evaluateInactivityDeadline() {
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
            dispatchInactivityAlert(snapshot.deadlineMs)
        }
    }

    private suspend fun evaluateDailyCheckIn() {
        val status = store.dailyCheckInStatus()
        when (status.phase) {
            DailyCheckInPhase.DUE -> if (!store.wasDailyCheckInPrompted(status.dayStartMs)) {
                store.markDailyCheckInPrompted(status.dayStartMs)
                showDailyCheckInNotification()
                repository.insertLog("DAILY_CHECK_IN", "오늘의 안부 확인을 요청했습니다.")
            }
            DailyCheckInPhase.OVERDUE -> if (!store.wasDailyCheckInAlerted(status.dayStartMs)) {
                dispatchDailyCheckInAlert(status.dayStartMs)
            }
            else -> Unit
        }
    }

    private suspend fun dispatchInactivityAlert(deadlineMs: Long) {
        val batch = queueSafetyMessage(
            message = EmergencyMessageBuilder.build(store.deviceAlias, getBatteryPercentageOrNull()),
            eventIdFor = { EmergencySmsSender.emergencyEventId(deadlineMs, it.id) }
        ) ?: return
        if (batch.statuses.all { it.isResolved }) {
            store.markEmergency(deadlineMs)
            showCompletionNotification("긴급 문자", batch.statuses)
        } else if (batch.queuedAny) {
            showRetryNotification("긴급 문자 발송 확인 중")
        }
    }

    private suspend fun dispatchDailyCheckInAlert(dayStartMs: Long) {
        val batch = queueSafetyMessage(
            message = EmergencyMessageBuilder.buildDailyCheckInMissed(store.deviceAlias),
            eventIdFor = { EmergencySmsSender.dailyEventId(dayStartMs, it.id) }
        ) ?: return
        if (batch.statuses.all { it.isResolved }) {
            store.markDailyCheckInAlerted(dayStartMs)
            showCompletionNotification("안부 미응답 문자", batch.statuses)
        } else if (batch.queuedAny) {
            showRetryNotification("안부 미응답 문자 발송 확인 중")
        }
    }

    private suspend fun dispatchPendingSos() {
        val eventMs = store.pendingSosEventMs
        if (eventMs <= 0L || eventMs > System.currentTimeMillis()) return
        val batch = queueSafetyMessage(
            message = EmergencyMessageBuilder.buildSos(store.deviceAlias, eventMs),
            eventIdFor = { EmergencySmsSender.sosEventId(eventMs, it.id) }
        ) ?: return
        if (batch.statuses.all { it.isResolved }) {
            store.clearPendingSos()
            showCompletionNotification("SOS 문자", batch.statuses)
        } else if (batch.queuedAny) {
            showRetryNotification("SOS 문자 발송 확인 중")
        }
    }

    private suspend fun queueSafetyMessage(
        message: String,
        eventIdFor: (Contact) -> String
    ): SmsBatch? {
        val contacts = repository.allContacts.first().take(3)
        if (contacts.isEmpty()) {
            reportBlockingDispatchProblem(
                "등록된 긴급 연락처가 없어 문자를 보낼 수 없습니다.",
                "긴급 연락처가 없습니다",
                "앱을 열어 긴급 연락처를 등록해 주세요."
            )
            return null
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            reportBlockingDispatchProblem(
                "문자 권한이 없어 보호자 문자를 보낼 수 없습니다.",
                "문자 권한이 필요합니다",
                "앱 설정에서 문자 권한을 허용해 주세요."
            )
            return null
        }
        val smsSetup = SmsDeviceManager(this, store).inspect()
        if (smsSetup !is SmsSetupState.Ready) {
            reportBlockingDispatchProblem(
                smsSetup.userMessage(),
                "문자 발송 환경 확인 필요",
                smsSetup.userMessage()
            )
            return null
        }

        val sender = EmergencySmsSender(this)
        var queuedAny = false
        contacts.forEach { contact ->
            try {
                if (
                    sender.queue(
                        eventId = eventIdFor(contact),
                        contact = contact,
                        message = message,
                        subscriptionId = smsSetup.line.subscriptionId
                    ) == SmsQueueResult.QUEUED
                ) {
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
        return SmsBatch(
            statuses = contacts.map { sender.status(eventIdFor(it)) },
            queuedAny = queuedAny
        )
    }

    private fun showCompletionNotification(label: String, statuses: List<SmsDispatchStatus>) {
        val failedCount = statuses.count { it.state == SmsDispatchState.FAILED_FINAL }
        if (failedCount == 0) {
            showAlertNotification("$label 발송 확인", "모든 보호자 문자 발송이 확인되었습니다.")
        } else {
            showAlertNotification("$label 일부 실패", "${failedCount}명의 보호자에게 3회 시도했지만 발송하지 못했습니다.")
        }
    }

    private fun showRetryNotification(title: String) {
        showAlertNotification(title, "통신사 결과를 확인하며 실패 시 최대 3회 다시 시도합니다.")
    }

    private suspend fun reportBlockingDispatchProblem(logMessage: String, title: String, body: String) {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastBlockingAlertMs < BLOCKING_ALERT_INTERVAL_MS) return
        lastBlockingAlertMs = nowMs
        repository.insertLog("SMS_FAILED", logMessage)
        showAlertNotification(title, body)
    }

    private fun confirmSafe(reason: String) {
        store.resetDeadline(reason = reason)
        NotificationManagerCompat.from(this).cancel(ALERT_NOTIFICATION_ID)
        serviceScope.launch {
            repository.insertLog("SENSOR_RESET", "사용자가 알림에서 무사함을 확인했습니다.")
        }
    }

    private fun confirmDailyCheckIn(reason: String) {
        store.confirmDailyCheckIn()
        store.resetDeadline(reason = reason)
        NotificationManagerCompat.from(this).cancel(DAILY_NOTIFICATION_ID)
        serviceScope.launch { repository.insertLog("DAILY_CHECK_IN", "오늘의 안부를 확인했습니다.") }
    }

    private fun runMaintenanceIfNeeded(nowMs: Long = System.currentTimeMillis()) {
        if (nowMs - lastMaintenanceMs < MAINTENANCE_INTERVAL_MS) return
        SmsDispatchStore(this).pruneExpired(nowMs)
        lastMaintenanceMs = nowMs
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

    private fun buildOngoingNotification(remainingSeconds: Long): android.app.Notification =
        NotificationCompat.Builder(this, MONITORING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(
                if (store.desiredEnabled) "라이프링크 안심 모니터링 중" else "SOS 문자 준비 중"
            )
            .setContentText(
                if (store.desiredEnabled) {
                    "다음 안전 확인까지 ${formatRemaining(remainingSeconds)}"
                } else {
                    "5초 안에 취소하지 않으면 보호자에게 문자를 보냅니다."
                }
            )
            .setContentIntent(launchAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

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
            .setContentIntent(launchAppIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .addAction(0, "무사합니다", safeIntent)
            .build()
        notifyIfAllowed(ALERT_NOTIFICATION_ID, notification)
    }

    private fun showDailyCheckInNotification() {
        val safeIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MonitoringService::class.java).setAction(ACTION_DAILY_SAFE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
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

    private fun showAlertNotification(title: String, body: String) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
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
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

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
                description = "무활동 경고, 매일 안부 확인, SOS 문자 상태를 알립니다."
                enableVibration(true)
            }
        )
    }

    private fun getBatteryPercentageOrNull(): Int? {
        val manager = getSystemService(BatteryManager::class.java)
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    }

    private fun formatRemaining(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
    }

    private data class SmsBatch(
        val statuses: List<SmsDispatchStatus>,
        val queuedAny: Boolean
    )

    companion object {
        const val ACTION_START = "com.bboysilver.lifelink.action.START"
        const val ACTION_RESET = "com.bboysilver.lifelink.action.RESET"
        const val ACTION_REPORT_SAFE = "com.bboysilver.lifelink.action.REPORT_SAFE"
        const val ACTION_DAILY_SAFE = "com.bboysilver.lifelink.action.DAILY_SAFE"
        const val ACTION_TRIGGER_SOS = "com.bboysilver.lifelink.action.TRIGGER_SOS"
        const val EXTRA_REASON = "reason"
        private const val MONITORING_CHANNEL_ID = "lifelink_monitoring"
        private const val ALERT_CHANNEL_ID = "lifelink_safety_alerts"
        private const val MONITORING_NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val DAILY_NOTIFICATION_ID = 1003
        private const val CHECK_INTERVAL_MS = 15_000L
        private const val SOS_CHECK_INTERVAL_MS = 1_000L
        private const val BLOCKING_ALERT_INTERVAL_MS = 5 * 60 * 1_000L
        private const val MAINTENANCE_INTERVAL_MS = 24 * 60 * 60 * 1_000L

        fun start(context: Context) {
            val store = MonitoringStore(context)
            if (!store.desiredEnabled) return
            val smsSetup = SmsDeviceManager(context, store).inspect()
            if (smsSetup !is SmsSetupState.Ready) {
                store.markServiceError(smsSetup.userMessage())
                return
            }
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

        fun confirmDailyCheckIn(context: Context) {
            if (!MonitoringStore(context).desiredEnabled) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java).setAction(ACTION_DAILY_SAFE)
            )
        }

        fun triggerSos(context: Context) {
            if (MonitoringStore(context).pendingSosEventMs <= 0L) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java).setAction(ACTION_TRIGGER_SOS)
            )
        }
    }
}
