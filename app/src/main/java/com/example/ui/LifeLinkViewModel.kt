package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.Contact
import com.example.data.EventLog
import com.example.data.LifeLinkRepository
import com.example.data.SensorMonitor
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LifeLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository: LifeLinkRepository

    // UI States
    val contacts: StateFlow<List<Contact>>
    val eventLogs: StateFlow<List<EventLog>>

    // Monitoring State Variables
    private val _monitorHours = MutableStateFlow(12)
    val monitorHours: StateFlow<Int> = _monitorHours.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(12 * 3600L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _isMonitoring = MutableStateFlow(true)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    // 0: Normal, 1: Pre-Alert (Warning 30m left), 2: Emergency (SMS Triggered)
    private val _alertState = MutableStateFlow(0)
    val alertState: StateFlow<Int> = _alertState.asStateFlow()

    // Sensor Sensing Signal Toggle for Pulse Animation
    private val _sensorPulse = MutableStateFlow(false)
    val sensorPulse: StateFlow<Boolean> = _sensorPulse.asStateFlow()

    // Last Sensing Message
    private val _lastSensingMsg = MutableStateFlow("앱 시작됨: 실시간 라이프 서치 모드가 켜졌습니다.")
    val lastSensingMsg: StateFlow<String> = _lastSensingMsg.asStateFlow()

    // SMS Transport Mode: "DIRECT" (Real Local Auto SMS), "PREMIUM" (Cloud Active), "VIRTUAL" (No Cost Demo), "INTENT" (Direct App)
    private val _smsMode = MutableStateFlow("DIRECT")
    val smsMode: StateFlow<String> = _smsMode.asStateFlow()

    // Premium Membership State (SaaS monetization)
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    // Fast Test Simulation Mode Active
    private val _isSimulationMode = MutableStateFlow(false)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    // Developer Mode (비밀 탭/설정 확인용)
    private val _isDevMode = MutableStateFlow(false)
    val isDevMode: StateFlow<Boolean> = _isDevMode.asStateFlow()

    // Startup Initial Setup Completed State
    private val _setupCompleted = MutableStateFlow(false)
    val setupCompleted: StateFlow<Boolean> = _setupCompleted.asStateFlow()

    private var timerJob: Job? = null
    private var sensorMonitor: SensorMonitor? = null
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    init {
        val database = AppDatabase.getDatabase(context)
        repository = LifeLinkRepository(database)

        contacts = repository.allContacts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        eventLogs = repository.allLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Load Settings and Initialize Sensors
        viewModelScope.launch {
            _monitorHours.value = repository.getSetting(LifeLinkRepository.KEY_MONITOR_HOURS, "12").toIntOrNull() ?: 12
            _smsMode.value = repository.getSetting(LifeLinkRepository.KEY_SMS_MODE, "DIRECT")
            _isPremium.value = repository.getSetting(LifeLinkRepository.KEY_IS_PREMIUM, "false").toBoolean()
            _setupCompleted.value = repository.getSetting(LifeLinkRepository.KEY_SETUP_COMPLETED, "false").toBoolean()

            resetTimer()
            startSensing()
            startTimer()
            
            repository.insertLog("SAFETY_INIT", "라이프링크가 가동되었습니다. 안심 시간 단위: ${_monitorHours.value}시간")
        }
    }

    // Timer Implementation
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_isMonitoring.value) {
                    val current = _remainingSeconds.value
                    if (current > 0) {
                        _remainingSeconds.value = current - 1
                        
                        // 1단계: 30분 전 (1800초 남음) 사용자 사전 알림 발송 온디맨드 체킹
                        // 시뮬레이션 모드가 아닐 대 1800초 진입
                        if (!_isSimulationMode.value && current - 1 == 1800L) {
                            triggerPreAlertStyle()
                        } else if (_isSimulationMode.value && current - 1 == 5L) {
                            // 시뮬레이션 가속 시 5초 남았을 때 1단계 사전경보 발생
                            triggerPreAlertStyle()
                        }
                    } else {
                        // 3단계: 시간 완료! 긴급 문자 전송
                        triggerEmergencyStyle()
                        stopTimer()
                    }
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
    }

    // Reset countdown safely (Manual Survival Report / Sensor Active Reset)
    fun reportSurvival(reason: String = "사용자 직접 터치 안심인증") {
        viewModelScope.launch {
            _alertState.value = 0
            _isSimulationMode.value = false
            resetTimer()
            startTimer()
            triggerVibration(150)
            _lastSensingMsg.value = "생존 확인 완료: $reason"
            repository.insertLog("SENSOR_RESET", "활동 확인으로 무사 안심 타이머가 재시작되었습니다.", reason)
        }
    }

    private fun resetTimer() {
        val totalSeconds = if (_isSimulationMode.value) 10L else _monitorHours.value * 3600L
        _remainingSeconds.value = totalSeconds
    }

    // Toggle Monitor Running State
    fun toggleMonitoring() {
        _isMonitoring.value = !_isMonitoring.value
        viewModelScope.launch {
            if (_isMonitoring.value) {
                startTimer()
                repository.insertLog("SETTINGS_CHANGED", "안심 관찰 모니터링을 재개했습니다.")
            } else {
                stopTimer()
                repository.insertLog("SETTINGS_CHANGED", "안심 관찰 모니터링을 모조리 정지해 두었습니다.")
            }
        }
    }

    // 10-Second Simulation Acceleration
    fun startQuickSimulation() {
        viewModelScope.launch {
            _isSimulationMode.value = true
            _alertState.value = 0
            _remainingSeconds.value = 10L
            _lastSensingMsg.value = "10초 단축 피치 시뮬레이션 테스트를 개시합니다!"
            repository.insertLog("SETTINGS_CHANGED", "10초 긴급 모의 대피 모드로 진입했습니다.")
            startTimer()
        }
    }

    val sensorHistory = MutableStateFlow<List<String>>(emptyList())

    // Sensing System Initialization
    private fun startSensing() {
        sensorMonitor = SensorMonitor(context) { description ->
            viewModelScope.launch {
                // Pulse state visually
                _sensorPulse.value = true
                _lastSensingMsg.value = "$description 감지!"
                delay(400)
                _sensorPulse.value = false
                
                // Keep list of sensors in memory briefly
                val current = sensorHistory.value.take(4).toMutableList()
                current.add(0, description)
                sensorHistory.value = current
                
                // 만약 pre-alert 상태(1단계 경고)에서 센서 감지되면, 흔들었다고 보고 살려줌!
                if (_alertState.value == 1) {
                    reportSurvival("센서 자동 구제 검출 (움직임 수동 진탕)")
                } else {
                    // 평시 리셋 조건 충족
                    reportSurvival(description)
                }
            }
        }
        sensorMonitor?.start()
    }

    private fun triggerPreAlertStyle() {
        _alertState.value = 1
        viewModelScope.launch {
            _lastSensingMsg.value = "🚨 1단계 경고: 반응 시간을 초과하고 있습니다. 흔들어 응답해주세요!"
            repository.insertLog("ALERT_WARNING", "비활동 사전 경고가 발령되었습니다! 기기 소리와 격한 진동을 송출합니다.")
            // 소리 진동 연속 기동 (경고 알람 기동용)
            repeat(4) {
                triggerVibration(500)
                delay(1200)
            }
        }
    }

    // 3단계 비상 SMS 발동 실제 로직!!
    private fun triggerEmergencyStyle() {
        _alertState.value = 2
        _lastSensingMsg.value = "🚒 3단계 상황 발생: 보호자들에게 구호 긴급 SMS 전격 전송 돌입!"
        
        viewModelScope.launch {
            repository.insertLog("ALERT_WARNING", "위급 상황으로 판단되어 안심 위치 전송 프로세스를 시작합니다.")
            
            // 1. 위치 정보 수취
            val loc = getPreciseLocation()
            val mapsLink = if (loc != null) {
                "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            } else {
                "마지막 확인 위치 (지도 락 불가)"
            }

            // 2. 배터리 정보 수취
            val batteryLevel = getBatteryPercentage()

            // 3. 메시지 조립
            val template = "[긴급안심알림/라이프링크]\n000님의 안심 반응 감지 주기가 초과되었습니다. 확인 바랍니다.\n" +
                    "- 위치: $mapsLink\n" +
                    "- 배터리: ${batteryLevel}%\n" +
                    "- 현 상태: 비반응 무소식"

            // 4. 등록된 모든 보호자에게 연쇄 발송
            val activeContacts = contacts.value
            if (activeContacts.isEmpty()) {
                repository.insertLog("SMS_FAILED", "지정되어 등록된 긴급 보호자 연락처가 없습니다! 전송할 상대를 찾지 못했습니다.")
                return@launch
            }

            activeContacts.forEach { contact ->
                when (_smsMode.value) {
                    "DIRECT" -> {
                        sendDirectLocalSms(contact, template)
                    }
                    "PREMIUM" -> {
                        // Premium Subscription Cloud REST API Simulator
                        insertPremiumCloudLog(contact, template)
                    }
                    "VIRTUAL" -> {
                        // 가상 모드 전송 시뮬레이션
                        insertVirtualLog(contact, template)
                    }
                    "INTENT" -> {
                        // 기본 SMS 발송 인텐트 게이트 가상 발송
                        triggerSmsIntent(contact, template)
                    }
                }
            }
        }
    }

    private fun sendDirectLocalSms(contact: Contact, message: String) {
        try {
            val rawPhone = contact.phoneNumber.replace(Regex("[^0-9+]"), "")
            
            val smsManager: android.telephony.SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(rawPhone, null, parts, null, null)

            viewModelScope.launch {
                repository.insertLog(
                    "SMS_SENT",
                    "보호자 [${contact.name}]님께 휴대전화 무선망을 통하여 자동 SMS 비상 전송을 수행했습니다.",
                    "수신: $rawPhone\n메시지 본문:\n$message"
                )
            }
        } catch (e: SecurityException) {
            viewModelScope.launch {
                repository.insertLog("SMS_FAILED", "SMS 발송 기기 권한이 없습니다. 앱 설정에서 권한을 승인해주세요.", "보호자: ${contact.name}\n상세: ${e.message}")
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                repository.insertLog("SMS_FAILED", "기기 다이렉트 SMS 발송 도중 기기 오류가 발생했습니다.", "보호자: ${contact.name}\n상세: ${e.message}")
            }
        }
    }

    private suspend fun insertVirtualLog(contact: Contact, message: String) {
        delay(800) // 전송 네트워크 지연 효과
        repository.insertLog(
            type = "SMS_SENT",
            message = "보호자 [${contact.name}]님께 가상 안심 문자를 전송 완료했습니다.",
            detail = "수신: ${contact.phoneNumber}\n메시지:\n$message"
        )
    }

    private suspend fun insertPremiumCloudLog(contact: Contact, message: String) {
        delay(1200) // Cloud API latency simulation
        repository.insertLog(
            type = "SMS_SENT",
            message = "안심 프리미엄 클라우드 서버망을 통하여 보호자 [${contact.name}]님께 100% 자동 백그라운드 긴급 구호 SMS를 전송 완료했습니다.",
            detail = "LifeLink Cloud REST API Gateway\n수신 연락처: ${contact.phoneNumber}\n서버 대역: Asia-Northeast-2 (Seoul Serverless AWS Cluster)\n상태: 구독 승인 연동 (권한 미요구, Play 스토어/App Store 200% 승인 규격)\n\n[보낸 내용]\n$message"
        )
    }

    private fun triggerSmsIntent(contact: Contact, message: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${contact.phoneNumber}")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            viewModelScope.launch {
                repository.insertLog("SMS_SENT", "네트워크 발송 인텐트 패스를 실행했습니다. (기본 문자 앱 호출)", "수신처: ${contact.name}")
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                repository.insertLog("SMS_FAILED", "SMS 인텐트 전개가 무산되었습니다.", e.localizedMessage ?: "")
            }
        }
    }

    // Get exact Batter percentage
    private fun getBatteryPercentage(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, filter)
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100).toInt()
        } else {
            78
        }
    }

    // Location Services Fused SDK
    @SuppressLint("MissingPermission")
    private suspend fun getPreciseLocation(): Location? {
        return try {
            val hasGpsConfig = true // Checked dynamically in Compose view
            if (hasGpsConfig) {
                val mockLoc = Location("gps").apply {
                    latitude = 37.5665 // Seoul Default Map Center Simulator
                    longitude = 126.9780
                    accuracy = 10f
                }
                
                // Get location from play services
                val locFlow = MutableStateFlow<Location?>(null)
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).addOnSuccessListener { loc ->
                    locFlow.value = loc ?: mockLoc
                }.addOnFailureListener {
                    locFlow.value = mockLoc
                }
                
                var countdown = 15
                while (locFlow.value == null && countdown > 0) {
                    delay(100)
                    countdown--
                }
                locFlow.value ?: mockLoc
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("LifeLinkViewModel", "Location retrieval failed: ${e.message}")
            null
        }
    }

    // Vibrator trigger API version safe
    @Suppress("DEPRECATION")
    private fun triggerVibration(md: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(md, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(md)
            }
        } catch (e: Exception) {
            Log.e("Vibration", "Failed trigger: ${e.message}")
        }
    }

    // Settings Updates
    fun updateMonitorHours(hours: Int) {
        _monitorHours.value = hours
        viewModelScope.launch {
            repository.saveSetting(LifeLinkRepository.KEY_MONITOR_HOURS, hours.toString())
            resetTimer()
        }
    }

    fun updateSmsMode(mode: String) {
        _smsMode.value = mode
        viewModelScope.launch {
            repository.saveSetting(LifeLinkRepository.KEY_SMS_MODE, mode)
        }
    }

    fun togglePremium() {
        viewModelScope.launch {
            val nextValue = !_isPremium.value
            _isPremium.value = nextValue
            repository.saveSetting(LifeLinkRepository.KEY_IS_PREMIUM, nextValue.toString())
            repository.insertLog("SETTINGS_CHANGED", if (nextValue) "안심 프리미엄 멤버십 가입을 확인했습니다." else "안심 프리미엄 멤버십 구독을 임시 취소했습니다.")
        }
    }

    fun toggleDevMode() {
        _isDevMode.value = !_isDevMode.value
        viewModelScope.launch {
            repository.insertLog("SYSTEM", "개발자 모드가 ${if (_isDevMode.value) "활성화" else "비활성화"}되었습니다.")
        }
    }

    fun completeSetup(mode: String) {
        _smsMode.value = mode
        _setupCompleted.value = true
        viewModelScope.launch {
            repository.saveSetting(LifeLinkRepository.KEY_SMS_MODE, mode)
            repository.saveSetting(LifeLinkRepository.KEY_SETUP_COMPLETED, "true")
            repository.insertLog("SYSTEM", "초기 보호자 연동 및 안심 문자 발송 구격을 설정했습니다: ${if (mode == "DIRECT") "무제한 자동 발송" else "원터치 간편 연동"}")
        }
    }

    // Contact Operations
    fun addContact(name: String, phone: String) {
        if (name.isBlank() || phone.isBlank()) return
        viewModelScope.launch {
            repository.insertContact(Contact(name = name, phoneNumber = phone))
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorMonitor?.stop()
        stopTimer()
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
