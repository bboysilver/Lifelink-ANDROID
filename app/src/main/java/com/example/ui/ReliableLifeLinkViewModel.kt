package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Contact
import com.example.data.DailyCheckInPhase
import com.example.data.EventLog
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringStore
import com.example.monitoring.EmergencyMessageBuilder
import com.example.monitoring.EmergencySmsSender
import com.example.monitoring.MonitoringService
import com.example.monitoring.SmsDeviceManager
import com.example.monitoring.SmsDispatchStore
import com.example.monitoring.SmsQueueResult
import com.example.monitoring.SmsRetryPolicy
import com.example.monitoring.SmsSetupIssue
import com.example.monitoring.SmsSetupState
import com.example.monitoring.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LifeLinkViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>().applicationContext
    private val repository = LifeLinkRepository(AppDatabase.getDatabase(context))
    private val monitoringStore = MonitoringStore(context)
    private val smsDispatchStore = SmsDispatchStore(context)
    private val smsDeviceManager = SmsDeviceManager(context, monitoringStore)

    val contacts: StateFlow<List<Contact>> = repository.allContacts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
    val eventLogs: StateFlow<List<EventLog>> = repository.allLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _smsSetupState = MutableStateFlow<SmsSetupState>(
        SmsSetupState.Blocked(SmsSetupIssue.CHECKING)
    )
    val smsSetupState: StateFlow<SmsSetupState> = _smsSetupState.asStateFlow()

    private val _monitorHours = MutableStateFlow(12)
    val monitorHours: StateFlow<Int> = _monitorHours.asStateFlow()

    private val _dailyCheckInEnabled = MutableStateFlow(false)
    val dailyCheckInEnabled: StateFlow<Boolean> = _dailyCheckInEnabled.asStateFlow()

    private val _dailyCheckInHour = MutableStateFlow(9)
    val dailyCheckInHour: StateFlow<Int> = _dailyCheckInHour.asStateFlow()

    private val _dailyCheckInDue = MutableStateFlow(false)
    val dailyCheckInDue: StateFlow<Boolean> = _dailyCheckInDue.asStateFlow()

    private val _sosCountdownSeconds = MutableStateFlow(0)
    val sosCountdownSeconds: StateFlow<Int> = _sosCountdownSeconds.asStateFlow()
    private val _remainingSeconds = MutableStateFlow(12 * 3600L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _desiredMonitoring = MutableStateFlow(false)
    val desiredMonitoring: StateFlow<Boolean> = _desiredMonitoring.asStateFlow()

    private val _runtimeState = MutableStateFlow(MonitoringRuntimeState.STOPPED)
    val runtimeState: StateFlow<MonitoringRuntimeState> = _runtimeState.asStateFlow()

    private val _serviceError = MutableStateFlow("")
    val serviceError: StateFlow<String> = _serviceError.asStateFlow()

    private val _deviceAlias = MutableStateFlow("")
    val deviceAlias: StateFlow<String> = _deviceAlias.asStateFlow()

    private val _alertState = MutableStateFlow(0)
    val alertState: StateFlow<Int> = _alertState.asStateFlow()

    private val _sensorPulse = MutableStateFlow(false)
    val sensorPulse: StateFlow<Boolean> = _sensorPulse.asStateFlow()

    private val _lastSensingMsg = MutableStateFlow("활동 기록을 불러오는 중입니다.")
    val lastSensingMsg: StateFlow<String> = _lastSensingMsg.asStateFlow()

    private val _setupCompleted = MutableStateFlow(false)
    val setupCompleted: StateFlow<Boolean> = _setupCompleted.asStateFlow()

    private var tickerJob: Job? = null
    private var sosJob: Job? = null
    private var lastObservedActivityMs = 0L

    init {
        viewModelScope.launch {
            val savedHours = repository.getSetting(
                LifeLinkRepository.KEY_MONITOR_HOURS,
                monitoringStore.monitorHours.toString()
            ).toIntOrNull()?.coerceIn(6, 72) ?: 12
            _monitorHours.value = savedHours
            monitoringStore.monitorHours = savedHours

            val legacySetupCompleted = repository.getSetting(
                LifeLinkRepository.KEY_SETUP_COMPLETED,
                "false"
            ).toBoolean()
            if (legacySetupCompleted) monitoringStore.completeSetup()
            _setupCompleted.value = monitoringStore.isSetupCompleted
            monitoringStore.initializeDeadlineIfMissing()
            refreshSmsSetup()
            startTicker()
        }
    }

    fun ensureMonitoringStarted() {
        if (!monitoringStore.isSetupCompleted || !monitoringStore.desiredEnabled) return
        if (readySmsLineOrLog() == null) return
        startServiceWithoutReset()
    }

    fun startMonitoring() {
        if (!monitoringStore.isSetupCompleted || readySmsLineOrLog() == null) return
        monitoringStore.beginStart(reason = "사용자가 모니터링 시작")
        startServiceWithoutReset()
        viewModelScope.launch {
            repository.insertLog("SETTINGS_CHANGED", "안심 모니터링 시작을 요청했습니다.")
        }
        refreshUi()
    }

    fun restartMonitoring() {
        if (!monitoringStore.desiredEnabled || readySmsLineOrLog() == null) return
        monitoringStore.markServiceStarting()
        startServiceWithoutReset()
        refreshUi()
    }

    fun stopMonitoring() {
        MonitoringService.stop(context)
        viewModelScope.launch {
            repository.insertLog("SETTINGS_CHANGED", "안심 모니터링을 일시 중지했습니다.")
        }
        refreshUi()
    }

    fun reportSurvival(reason: String = "사용자 직접 무사 확인") {
        if (monitoringStore.dailyCheckInEnabled) monitoringStore.confirmDailyCheckIn()
        monitoringStore.resetDeadline(reason = reason)
        if (monitoringStore.desiredEnabled) {
            try {
                MonitoringService.reset(context, reason)
            } catch (error: RuntimeException) {
                monitoringStore.markServiceError(error.message ?: "활동 확인 전달에 실패했습니다.")
            }
        }
        viewModelScope.launch {
            repository.insertLog("SENSOR_RESET", "활동 확인으로 안심 마감시각을 갱신했습니다.", reason)
        }
        refreshUi()
    }

    fun updateDailyCheckIn(hour: Int?) {
        monitoringStore.dailyCheckInEnabled = hour != null
        if (hour != null) monitoringStore.dailyCheckInHour = hour
        viewModelScope.launch {
            repository.insertLog(
                "SETTINGS_CHANGED",
                if (hour == null) "매일 안부 확인을 사용하지 않습니다." else "매일 ${hour}시에 안부를 확인합니다."
            )
        }
        refreshUi()
    }

    fun reportDailySafe() {
        monitoringStore.confirmDailyCheckIn()
        reportSurvival("매일 안부 확인에서 괜찮음 응답")
    }

    fun requestDailyHelp() {
        if (beginSosCountdown()) {
            monitoringStore.confirmDailyCheckIn()
            refreshUi()
        }
    }

    fun startSosCountdown() {
        beginSosCountdown()
    }

    private fun beginSosCountdown(): Boolean {
        if (contacts.value.isEmpty()) {
            logValidationError("SOS를 보낼 보호자 연락처를 먼저 등록해 주세요.")
            return false
        }
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED || readySmsLineOrLog() == null
        ) return false
        if (sosJob?.isActive == true) return false
        val sendAtMs = System.currentTimeMillis() + SOS_COUNTDOWN_SECONDS * 1_000L
        if (monitoringStore.beginSos(sendAtMs) != sendAtMs) {
            logValidationError("이미 처리 중인 SOS 요청이 있습니다.")
            return false
        }
        try {
            // Start immediately so the persisted countdown survives the UI leaving the foreground.
            MonitoringService.triggerSos(context)
        } catch (error: RuntimeException) {
            monitoringStore.clearPendingSos()
            monitoringStore.markServiceError(error.message ?: "SOS 요청을 전달하지 못했습니다.")
            viewModelScope.launch {
                repository.insertLog("SYSTEM_ERROR", "SOS 요청을 전달하지 못했습니다.", error.message.orEmpty())
            }
            return false
        }

        sosJob = viewModelScope.launch {
            for (seconds in SOS_COUNTDOWN_SECONDS downTo 1) {
                _sosCountdownSeconds.value = seconds
                delay(1_000)
            }
            _sosCountdownSeconds.value = 0
            repository.insertLog("SOS_REQUESTED", "사용자가 SOS 문자 발송을 요청했습니다.")
        }
        return true
    }

    fun cancelSosCountdown() {
        if (sosJob?.isActive != true) return
        sosJob?.cancel()
        monitoringStore.clearPendingSos()
        _sosCountdownSeconds.value = 0
        viewModelScope.launch { repository.insertLog("SOS_CANCELLED", "사용자가 SOS 요청을 취소했습니다.") }
    }

    fun updateMonitorHours(hours: Int) {
        if (hours !in 6..72) {
            logValidationError("안심 시간은 6~72시간으로 설정해야 합니다.")
            return
        }
        _monitorHours.value = hours
        monitoringStore.monitorHours = hours
        if (monitoringStore.desiredEnabled) monitoringStore.resetDeadline(reason = "안심 시간 변경")
        viewModelScope.launch {
            repository.saveSetting(LifeLinkRepository.KEY_MONITOR_HOURS, hours.toString())
        }
        refreshUi()
    }

    fun updateDeviceAlias(alias: String) {
        monitoringStore.deviceAlias = alias
        _deviceAlias.value = monitoringStore.deviceAlias
        viewModelScope.launch {
            repository.insertLog("SETTINGS_CHANGED", "사용자 이름을 변경했습니다.")
        }
    }

    fun completeSetup() {
        monitoringStore.completeSetup()
        monitoringStore.setDesiredEnabled(true)
        _setupCompleted.value = true
        viewModelScope.launch {
            repository.saveSetting(LifeLinkRepository.KEY_SETUP_COMPLETED, "true")
            repository.insertLog("SAFETY_INIT", "초기 설정을 완료했습니다. 필수 권한 확인 후 모니터링을 시작합니다.")
        }
        refreshUi()
    }

    fun addContact(name: String, phone: String) {
        val normalizedPhone = phone.filter(Char::isDigit)
        when {
            name.isBlank() -> logValidationError("보호자 이름을 입력해 주세요.")
            normalizedPhone.length !in 8..15 -> logValidationError("올바른 보호자 전화번호를 입력해 주세요.")
            contacts.value.size >= 3 -> logValidationError("긴급 연락처는 최대 3명까지 등록할 수 있습니다.")
            else -> viewModelScope.launch {
                repository.insertContact(Contact(name = name.trim(), phoneNumber = normalizedPhone))
            }
        }
    }

    fun sendTestSms(contact: Contact) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            logValidationError("테스트 문자를 보내려면 문자 권한이 필요합니다.")
            return
        }
        val smsLine = readySmsLineOrLog() ?: return
        val nowMs = System.currentTimeMillis()
        val cooldownRemainingMs = smsDispatchStore.reserveTestSend(contact.id, nowMs)
        if (cooldownRemainingMs > 0L) {
            val seconds = (cooldownRemainingMs + 999L) / 1_000L
            logValidationError("테스트 문자는 ${seconds}초 뒤에 다시 보낼 수 있습니다.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val eventId = EmergencySmsSender.testEventId(nowMs, contact.id)
            try {
                val result = EmergencySmsSender(context).queue(
                    eventId = eventId,
                    contact = contact,
                    message = EmergencyMessageBuilder.buildTest(monitoringStore.deviceAlias),
                    subscriptionId = smsLine.subscriptionId,
                    retryPolicy = SmsRetryPolicy.ONE_SHOT,
                    nowMs = nowMs
                )
                repository.insertLog(
                    "SMS_TEST_QUEUED",
                    if (result == SmsQueueResult.QUEUED) {
                        "${contact.name} 보호자에게 1회 전용 테스트 문자를 요청했습니다."
                    } else {
                        "테스트 문자 발송 결과를 기다리고 있습니다."
                    }
                )
            } catch (error: Exception) {
                repository.insertLog(
                    "SMS_FAILED",
                    "${contact.name} 보호자 테스트 문자 요청에 실패했습니다.",
                    error.message ?: "알 수 없는 오류"
                )
            }
        }
    }
    fun deleteContact(contact: Contact) {
        viewModelScope.launch { repository.deleteContact(contact) }
    }

    fun clearAllLogs() {
        smsDispatchStore.clearAll()
        viewModelScope.launch { repository.clearLogs() }
    }

    fun refreshSmsSetup() {
        _smsSetupState.value = smsDeviceManager.inspect()
    }

    fun selectSmsLine(subscriptionId: Int) {
        monitoringStore.smsSubscriptionId = subscriptionId
        refreshSmsSetup()
        val state = _smsSetupState.value
        if (state is SmsSetupState.Ready) {
            viewModelScope.launch {
                repository.insertLog("SETTINGS_CHANGED", "긴급 문자 회선을 ${state.line.label}(으)로 설정했습니다.")
            }
            ensureMonitoringStarted()
        }
    }

    private fun readySmsLineOrLog(): com.example.monitoring.SmsLine? {
        refreshSmsSetup()
        val state = _smsSetupState.value
        if (state is SmsSetupState.Ready) return state.line
        monitoringStore.markServiceError(state.userMessage())
        logValidationError(state.userMessage())
        refreshUi()
        return null
    }

    private fun startServiceWithoutReset() {
        try {
            MonitoringService.start(context)
        } catch (error: RuntimeException) {
            monitoringStore.markServiceError(error.message ?: "백그라운드 모니터링을 시작하지 못했습니다.")
            viewModelScope.launch {
                repository.insertLog(
                    "SYSTEM_ERROR",
                    "백그라운드 모니터링을 시작하지 못했습니다.",
                    error.message ?: "알 수 없는 오류"
                )
            }
        }
    }

    private fun logValidationError(message: String) {
        viewModelScope.launch { repository.insertLog("SYSTEM_ERROR", message) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                refreshUi()
                delay(1_000)
            }
        }
    }

    private fun refreshUi() {
        val snapshot = monitoringStore.snapshot()
        _dailyCheckInEnabled.value = monitoringStore.dailyCheckInEnabled
        _dailyCheckInHour.value = monitoringStore.dailyCheckInHour
        _dailyCheckInDue.value = monitoringStore.dailyCheckInStatus().phase.let {
            it == DailyCheckInPhase.DUE || it == DailyCheckInPhase.OVERDUE
        }
        _remainingSeconds.value = snapshot.remainingSeconds
        _isMonitoring.value = snapshot.isRunning
        _desiredMonitoring.value = snapshot.desiredEnabled
        _runtimeState.value = snapshot.runtimeState
        _serviceError.value = snapshot.serviceError
        _deviceAlias.value = snapshot.deviceAlias
        _alertState.value = snapshot.alertState
        _lastSensingMsg.value = snapshot.lastActivityReason
        if (snapshot.lastActivityMs != 0L && snapshot.lastActivityMs != lastObservedActivityMs) {
            lastObservedActivityMs = snapshot.lastActivityMs
            viewModelScope.launch {
                _sensorPulse.value = true
                delay(400)
                _sensorPulse.value = false
            }
        }
    }

    private companion object {
        const val SOS_COUNTDOWN_SECONDS = 5
    }
}

class LifeLinkViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LifeLinkViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LifeLinkViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
