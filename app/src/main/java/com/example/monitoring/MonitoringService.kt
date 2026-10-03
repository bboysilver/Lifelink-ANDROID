package com.example.monitoring

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.DailyCheckInPhase
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import com.example.data.SafetyIncidentRepository
import com.example.data.SensorMonitor
import com.example.data.SensorStartResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var store: MonitoringStore
    private lateinit var repository: LifeLinkRepository
    private lateinit var incidents: SafetyIncidentRepository
    private lateinit var sensorMonitor: SensorMonitor
    private lateinit var smsSubscriptionMonitor: SmsSubscriptionMonitor
    private var monitorJob: Job? = null
    private var lastMaintenanceMs = 0L
    private var lastHeartbeatWriteElapsedMs = 0L
    private var lastOngoingNotificationMinute = Long.MIN_VALUE
    private var startupFailed = false

    override fun onCreate() {
        super.onCreate()
        store = MonitoringStore(this)
        val database = AppDatabase.getDatabase(this)
        repository = LifeLinkRepository(database)
        incidents = SafetyIncidentRepository(database)
        SafetySmsRetryWorker.enqueueRecovery(this)
        DailyCheckInScheduler(this).ensureScheduled()

        val dailyStatus = store.dailyCheckInStatus()
        if (!store.desiredEnabled && !dailyStatus.needsResponse) {
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

        val requiresSms = store.desiredEnabled ||
            dailyStatus.phase == DailyCheckInPhase.OVERDUE
        if (requiresSms) {
            val smsSetup = SmsDeviceManager(this, store).inspect()
            if (smsSetup !is SmsSetupState.Ready) {
                failStartup(smsSetup.userMessage())
                return
            }
        }

        if (!ensureActivitySensors()) return

        if (!ensureSmsSubscriptionMonitor()) return
        if (store.desiredEnabled) {
            val nowMs = System.currentTimeMillis()
            store.markServiceRunning(nowMs)
            lastHeartbeatWriteElapsedMs = SystemClock.elapsedRealtime()
            MonitoringWatchdogWorker.ensureScheduled(this)
            MonitoringStatusNotifier.cancel(this)
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // stopSelf() in onCreate does not prevent delivery of this start command.
        if (startupFailed) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A service initially created for daily check-in can later be reused for monitoring.
        // Do not report RUNNING without registering its activity listeners.
        if (!ensureActivitySensors()) return START_NOT_STICKY
        if (!ensureSmsSubscriptionMonitor()) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_REPORT_SAFE -> confirmSafe("알림에서 무사 확인")
            ACTION_DAILY_SAFE -> confirmDailyCheckIn("매일 안부 알림에서 괜찮음 확인")
            ACTION_CANCEL_SOS -> store.cancelPendingSos()
            ACTION_TRIGGER_SOS -> Unit
            ACTION_RESET -> store.resetDeadline(
                reason = intent.getStringExtra(EXTRA_REASON) ?: "활동 확인"
            )
            ACTION_START -> {
                store.initializeDeadlineIfMissing()
                if (store.desiredEnabled) {
                    val nowMs = System.currentTimeMillis()
                    store.markServiceRunning(nowMs)
                    lastHeartbeatWriteElapsedMs = SystemClock.elapsedRealtime()
                    MonitoringWatchdogWorker.ensureScheduled(this)
                    MonitoringStatusNotifier.cancel(this)
                }
            }
        }

        val dailyNeedsWork = store.dailyCheckInStatus().needsResponse
        if (!store.desiredEnabled && !dailyNeedsWork) {
            stopSelf()
            return START_NOT_STICKY
        }
        startMonitorLoop()
        return if (store.desiredEnabled || hasPendingDailyDispatch()) {
            START_STICKY
        } else {
            START_NOT_STICKY
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureActivitySensors(): Boolean {
        if (!store.desiredEnabled) return true
        if (!::sensorMonitor.isInitialized) {
            sensorMonitor = SensorMonitor(this) { reason, activityAtMs ->
                store.recordActivity(activityAtMs, reason)
            }
        }
        if (sensorMonitor.start() != SensorStartResult.FAILED) return true
        failStartup("활동 센서를 시작할 수 없습니다. 기기를 다시 시작하거나 센서 권한을 확인해 주세요.")
        return false
    }

    private fun ensureSmsSubscriptionMonitor(): Boolean {
        if (!requiresSmsMonitoring() || ::smsSubscriptionMonitor.isInitialized) return true
        val setup = SmsDeviceManager(this, store).inspect()
        if (setup !is SmsSetupState.Ready) {
            failStartup(setup.userMessage())
            return false
        }
        smsSubscriptionMonitor = SmsSubscriptionMonitor(this) { state ->
            if (state !is SmsSetupState.Ready && requiresSmsMonitoring()) {
                val message = state.userMessage()
                startupFailed = true
                store.markServiceError(message)
                if (store.dailyCheckInStatus().phase == DailyCheckInPhase.OVERDUE) {
                    store.dailyCheckInError = message
                }
                serviceScope.launch {
                    repository.insertLog("SYSTEM_ERROR", "SIM 변경으로 안전 기능을 중단했습니다.", message)
                }
                showAlertNotification("SIM 상태 확인 필요", message)
                stopSelf()
            }
        }
        if (smsSubscriptionMonitor.start()) return true
        failStartup("SIM 변경 상태를 감시할 수 없습니다.")
        return false
    }

    override fun onDestroy() {
        if (::sensorMonitor.isInitialized) sensorMonitor.stop()
        if (::smsSubscriptionMonitor.isInitialized) smsSubscriptionMonitor.stop()
        monitorJob?.cancel()
        serviceScope.cancel()
        if (::store.isInitialized && store.desiredEnabled && !startupFailed) {
            val message = "모니터링 서비스가 예기치 않게 종료되었습니다."
            store.markServiceError(message)
            val snapshot = store.snapshot()
            if (store.claimWatchdogAlert(MonitoringWatchdogPolicy.alertToken(snapshot))) {
                MonitoringStatusNotifier.showUnexpectedStop(this, message)
            }
        }
        super.onDestroy()
    }

    private fun failStartup(message: String) {
        startupFailed = true
        store.markServiceError(message)
        if (store.dailyCheckInStatus().needsResponse) store.dailyCheckInError = message
        serviceScope.launch { repository.insertLog("SYSTEM_ERROR", message) }
        showAlertNotification("안전 기능 시작 실패", message)
        stopSelf()
    }

    private fun requiresSmsMonitoring(): Boolean =
        store.desiredEnabled ||
            store.dailyCheckInStatus().phase == DailyCheckInPhase.OVERDUE
    private fun startMonitorLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            if (store.desiredEnabled) {
                repository.insertLog("SAFETY_INIT", "백그라운드 안심 모니터링이 시작되었습니다.")
            }
            var firstPass = true
            while (
                isActive &&
                (
                    firstPass ||
                        store.desiredEnabled ||
                        hasPendingDailyDispatch()
                    )
            ) {
                firstPass = false
                runMaintenanceIfNeeded()
                if (store.desiredEnabled) {
                    val nowMs = System.currentTimeMillis()
                    val elapsedMs = SystemClock.elapsedRealtime()
                    if (MonitoringUpdatePolicy.shouldWriteHeartbeat(lastHeartbeatWriteElapsedMs, elapsedMs)) {
                        val issue = runtimeCapabilityIssue() ?: if (repository.getContactCount() == 0) {
                            "보호자 연락처가 없어 모니터링을 계속할 수 없습니다."
                        } else null
                        issue?.let { message ->
                            failRuntime(message)
                            return@launch
                        }
                        if (store.markHeartbeat(nowMs, elapsedMs)) {
                            InactivityDeadlineScheduler(this@MonitoringService).ensureScheduled(nowMs)
                        }
                        lastHeartbeatWriteElapsedMs = elapsedMs
                    }
                    evaluateInactivityDeadline()
                }
                evaluateDailyCheckIn()

                val shouldContinue = store.desiredEnabled ||
                    hasPendingDailyDispatch()
                if (!shouldContinue) break
                delay(CHECK_INTERVAL_MS)
            }
            if (!store.desiredEnabled) stopSelf()
        }
    }

    private fun hasPendingDailyDispatch(): Boolean {
        val status = store.dailyCheckInStatus()
        return status.phase == DailyCheckInPhase.OVERDUE &&
            store.wasDailyCheckInPrompted(status.dueAtMs) &&
            !store.wasDailyCheckInAlerted(status.dueAtMs)
    }
    private suspend fun evaluateInactivityDeadline() {
        updateOngoingNotification(store.snapshot().remainingSeconds)
        InactivityDeadlineTask(this, store, repository, incidents).run()
    }

    private suspend fun evaluateDailyCheckIn() {
        val result = DailyCheckInTask(this, store, repository).run()
        result.nextRunAtMs?.let { DailyCheckInWorker.enqueueAt(this, result.dueAtMs, it) }
    }
    private fun runtimeCapabilityIssue(): String? {
        if (!SafetyNotificationCapability.canPost(this)) {
            return "안전 알림이 꺼져 있어 모니터링을 계속할 수 없습니다."
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "활동 인식 권한이 해제되어 모니터링을 계속할 수 없습니다."
        }
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "SIM 상태 확인 권한이 해제되어 모니터링을 계속할 수 없습니다."
        }
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "문자 권한이 해제되어 모니터링을 계속할 수 없습니다."
        }
        return null
    }

    private fun failRuntime(message: String) {
        startupFailed = true
        store.markServiceError(message)
        serviceScope.launch { repository.insertLog("SYSTEM_ERROR", "안심 모니터링을 중단했습니다.", message) }
        showAlertNotification("모니터링 중단", message)
        stopSelf()
    }

    private fun confirmSafe(reason: String) {
        store.recordActivity(System.currentTimeMillis(), reason)
        NotificationManagerCompat.from(this).cancel(ALERT_NOTIFICATION_ID)
        serviceScope.launch {
            repository.insertLog("SENSOR_RESET", "사용자가 알림에서 무사함을 확인했습니다.")
        }
    }

    private fun confirmDailyCheckIn(reason: String) {
        val completedDueAtMs = store.confirmDailyCheckIn() ?: return
        DailyCheckInScheduler(this).ensureScheduled()
        if (store.desiredEnabled) store.resetDeadline(reason = reason)
        NotificationManagerCompat.from(this).cancel(DAILY_NOTIFICATION_ID)
        serviceScope.launch {
            repository.insertLog(
                "DAILY_CHECK_IN",
                "오늘의 안부를 확인했습니다.",
                "dueAtMs=$completedDueAtMs"
            )
        }
    }

    private suspend fun runMaintenanceIfNeeded(nowMs: Long = System.currentTimeMillis()) {
        if (nowMs - lastMaintenanceMs < MAINTENANCE_INTERVAL_MS) return
        SmsDispatchStore(this).pruneExpired(
            nowMs, preserveEventIds = incidents.pendingRecipients().map { it.eventId }.toSet()
        )
        repository.deleteLogsBefore(nowMs - SmsDispatchStore.RETENTION_MS)
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
        val message = error.message ?: "포그라운드 서비스를 시작하지 못했습니다."
        store.markServiceError(message)
        if (store.dailyCheckInStatus().needsResponse) store.dailyCheckInError = message
        serviceScope.launch {
            repository.insertLog(
                "SYSTEM_ERROR",
                "백그라운드 안전 기능 서비스를 시작하지 못했습니다.",
                message
            )
        }
        false
    }
    private fun buildOngoingNotification(remainingSeconds: Long): android.app.Notification {
        val builder = NotificationCompat.Builder(this, MONITORING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(
                when {
                    store.desiredEnabled -> "라이프링크 안심 모니터링 중"
                    else -> "매일 안부 확인 처리 중"
                }
            )
            .setContentText(
                when {
                    store.desiredEnabled -> "다음 안전 확인까지 ${formatRemaining(remainingSeconds)}"
                    else -> "안부 확인 상태를 처리하고 있습니다."
                }
            )
            .setContentIntent(launchAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        return builder.build()
    }
    private fun updateOngoingNotification(remainingSeconds: Long) {
        val minute = MonitoringUpdatePolicy.remainingMinute(remainingSeconds)
        if (minute == lastOngoingNotificationMinute) return
        lastOngoingNotificationMinute = minute
        notifyIfAllowed(MONITORING_NOTIFICATION_ID, buildOngoingNotification(remainingSeconds))
    }

    private fun showAlertNotification(title: String, body: String) {
        val notification = NotificationCompat.Builder(this, SafetyNotificationCapability.ALERT_CHANNEL_ID)
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

    @SuppressLint("MissingPermission")
    private fun notifyIfAllowed(id: Int, notification: android.app.Notification) {
        if (SafetyNotificationCapability.canPost(this)) {
            NotificationManagerCompat.from(this).notify(id, notification)
        }
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
                SafetyNotificationCapability.ALERT_CHANNEL_ID,
                "안전 확인 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "무활동 경고, 매일 안부 확인, SOS 문자 상태를 알립니다."
                enableVibration(true)
            }
        )
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
        const val ACTION_DAILY_SAFE = "com.bboysilver.lifelink.action.DAILY_SAFE"
        const val ACTION_TRIGGER_SOS = "com.bboysilver.lifelink.action.TRIGGER_SOS"
        const val ACTION_CANCEL_SOS = "com.bboysilver.lifelink.action.CANCEL_SOS"
        const val EXTRA_REASON = "reason"
        private const val MONITORING_CHANNEL_ID = "lifelink_monitoring"
        private const val MONITORING_NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val DAILY_NOTIFICATION_ID = 1003
        private const val CHECK_INTERVAL_MS = 15_000L
        private const val MAINTENANCE_INTERVAL_MS = 24 * 60 * 60 * 1_000L

        fun start(context: Context) {
            val store = MonitoringStore(context)
            if (!store.desiredEnabled) return
            MonitoringWatchdogWorker.ensureScheduled(context)
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

        fun stop(context: Context, notifyUser: Boolean = false) {
            MonitoringStore(context).stop()
            MonitoringWatchdogWorker.cancel(context)
            context.stopService(Intent(context, MonitoringService::class.java))
            if (notifyUser) MonitoringStatusNotifier.showUserStopped(context)
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
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java).setAction(ACTION_DAILY_SAFE)
            )
        }

        fun cancelDailyNotification(context: Context) {
            NotificationManagerCompat.from(context).cancel(DAILY_NOTIFICATION_ID)
        }
        fun triggerSos(context: Context) {
            SosService.start(context)
        }
    }
}
internal object MonitoringUpdatePolicy {
    const val HEARTBEAT_WRITE_INTERVAL_MS = 60_000L

    fun shouldWriteHeartbeat(lastWriteMs: Long, nowMs: Long): Boolean =
        lastWriteMs <= 0L || nowMs - lastWriteMs >= HEARTBEAT_WRITE_INTERVAL_MS

    fun remainingMinute(remainingSeconds: Long): Long =
        (remainingSeconds.coerceAtLeast(0L) + 59L) / 60L
}
