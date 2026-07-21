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
import com.example.data.EventLog
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringStore
import com.example.monitoring.EmergencyMessageBuilder
import com.example.monitoring.EmergencySmsSender
import com.example.monitoring.MonitoringService
import com.example.monitoring.SmsQueueResult
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

    private val _monitorHours = MutableStateFlow(12)
    val monitorHours: StateFlow<Int> = _monitorHours.asStateFlow()

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
            startTicker()
        }
    }

    fun ensureMonitoringStarted() {
        if (!monitoringStore.isSetupCompleted || !monitoringStore.desiredEnabled) return
        startServiceWithoutReset()
    }

    fun startMonitoring() {
        if (!monitoringStore.isSetupCompleted) return
        monitoringStore.beginStart(reason = "사용자가 모니터링 시작")
        startServiceWithoutReset()
        viewModelScope.launch {
            repository.insertLog("SETTINGS_CHANGED", "안심 모니터링 시작을 요청했습니다.")
        }
        refreshUi()
    }

    fun restartMonitoring() {
        if (!monitoringStore.desiredEnabled) return
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
        viewModelScope.launch(Dispatchers.IO) {
            val nowMs = System.currentTimeMillis()
            val eventId = EmergencySmsSender.testEventId(nowMs, contact.id)
            try {
                val result = EmergencySmsSender(context).queue(
                    eventId = eventId,
                    contact = contact,
                    message = EmergencyMessageBuilder.buildTest(monitoringStore.deviceAlias),
                    nowMs = nowMs
                )
                repository.insertLog(
                    "SMS_TEST_QUEUED",
                    if (result == SmsQueueResult.QUEUED) {
                        "${contact.name} 보호자에게 테스트 문자를 요청했습니다."
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
        viewModelScope.launch { repository.clearLogs() }
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
