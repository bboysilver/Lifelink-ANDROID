package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Contact
import com.example.data.EventLog
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import com.example.monitoring.MonitoringService
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

            if (_setupCompleted.value && monitoringStore.deadlineMs <= 0L) {
                monitoringStore.start(reason = "업데이트 후 모니터링 복구")
            } else {
                monitoringStore.initializeDeadlineIfMissing()
            }
            startTicker()
        }
    }

    fun ensureMonitoringStarted() {
        if (!monitoringStore.isSetupCompleted || !monitoringStore.isEnabled) return
        try {
            MonitoringService.start(context)
        } catch (error: Exception) {
            viewModelScope.launch {
                repository.insertLog(
                    "SYSTEM_ERROR",
                    "백그라운드 모니터링을 시작하지 못했습니다.",
                    error.message ?: "알 수 없는 오류"
                )
            }
        }
    }

    fun reportSurvival(reason: String = "사용자 직접 무사 확인") {
        val enabled = monitoringStore.isEnabled
        monitoringStore.resetDeadline(reason = reason, enable = enabled)
        if (enabled) MonitoringService.reset(context, reason)
        viewModelScope.launch {
            repository.insertLog("SENSOR_RESET", "활동 확인으로 안심 마감시각을 갱신했습니다.", reason)
        }
        refreshUi()
    }

    fun toggleMonitoring() {
        if (monitoringStore.isEnabled) {
            monitoringStore.stop()
            MonitoringService.stop(context)
            viewModelScope.launch {
                repository.insertLog("SETTINGS_CHANGED", "안심 모니터링을 일시 중지했습니다.")
            }
        } else {
            monitoringStore.start(reason = "사용자가 모니터링 재개")
            MonitoringService.start(context)
            viewModelScope.launch {
                repository.insertLog("SETTINGS_CHANGED", "안심 모니터링을 재개했습니다.")
            }
        }
        refreshUi()
    }

    fun updateMonitorHours(hours: Int) {
        if (hours !in 6..72) {
            viewModelScope.launch {
                repository.insertLog("SYSTEM_ERROR", "안심 시간은 6~72시간으로 설정해야 합니다.")
            }
            return
        }
        _monitorHours.value = hours
        monitoringStore.monitorHours = hours
        monitoringStore.resetDeadline(reason = "안심 시간 변경", enable = monitoringStore.isEnabled)
        viewModelScope.launch {
            repository.saveSetting(LifeLinkRepository.KEY_MONITOR_HOURS, hours.toString())
        }
        refreshUi()
    }

    fun completeSetup() {
        monitoringStore.completeSetup()
        monitoringStore.start(reason = "초기 설정 완료")
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

    fun deleteContact(contact: Contact) {
        viewModelScope.launch { repository.deleteContact(contact) }
    }

    fun clearAllLogs() {
        viewModelScope.launch { repository.clearLogs() }
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
        _isMonitoring.value = snapshot.enabled
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
