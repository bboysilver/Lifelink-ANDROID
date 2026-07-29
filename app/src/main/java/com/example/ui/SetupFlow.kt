package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Contact
import com.example.data.TestSmsVerification
import com.example.data.TestSmsVerificationState
import com.example.monitoring.SmsSetupIssue
import com.example.monitoring.SmsSetupState
import com.example.monitoring.userMessage

enum class SetupStep {
    DEVICE,
    INTRO,
    CONTACT,
    PERMISSIONS,
    TEST_SMS,
    MONITORING;

    companion object {
        fun fromStored(value: Int): SetupStep = entries.getOrElse(value) { DEVICE }
    }
}

internal object SafetyReadiness {
    fun monitoringBlockReason(
        smsSetupState: SmsSetupState,
        smsPermissionGranted: Boolean,
        phonePermissionGranted: Boolean,
        activityPermissionGranted: Boolean,
        notificationPermissionGranted: Boolean,
        hasEmergencyContacts: Boolean,
        testSmsState: TestSmsVerificationState
    ): String? {
        if (smsSetupState is SmsSetupState.Blocked) return smsSetupState.userMessage()
        if (!notificationPermissionGranted) return "사전 경고를 받으려면 알림 권한을 허용해 주세요."
        if (!activityPermissionGranted) return "활동을 확인하려면 활동 인식 권한을 허용해 주세요."
        if (!phonePermissionGranted) return "SMS 회선을 확인하려면 전화 상태 권한을 허용해 주세요."
        if (!smsPermissionGranted) return "보호자에게 문자를 보내려면 SMS 권한을 허용해 주세요."
        if (!hasEmergencyContacts) return "비상연락처를 최소 1명 등록해 주세요."
        if (testSmsState != TestSmsVerificationState.SUCCESS) {
            return "보호자 시험 문자 발송을 확인한 뒤 모니터링을 시작해 주세요."
        }
        return null
    }

    fun sosBlockReason(
        smsSetupState: SmsSetupState,
        smsPermissionGranted: Boolean,
        hasEmergencyContacts: Boolean
    ): String? {
        if (smsSetupState is SmsSetupState.Blocked) {
            return when (smsSetupState.issue) {
                SmsSetupIssue.UNSUPPORTED_DEVICE ->
                    "Wi-Fi 전용 기기에서는 SOS 문자를 보낼 수 없습니다. SMS가 가능한 기기를 사용해 주세요."
                SmsSetupIssue.NO_ACTIVE_SIM -> "활성화된 SMS 가능 SIM이 없습니다."
                else -> smsSetupState.userMessage()
            }
        }
        if (!hasEmergencyContacts) return "비상연락처를 먼저 등록해 주세요."
        if (!smsPermissionGranted) return "SOS 문자를 보내려면 SMS 권한을 허용해 주세요."
        return null
    }
}

@Composable
internal fun SetupWizardDialog(
    step: SetupStep,
    smsSetupState: SmsSetupState,
    contacts: List<Contact>,
    testSmsVerification: TestSmsVerification,
    monitorHours: Int,
    smsGranted: Boolean,
    phoneGranted: Boolean,
    activityGranted: Boolean,
    notificationGranted: Boolean,
    onRefreshDevice: () -> Unit,
    onSelectSmsLine: (Int) -> Unit,
    onAddContact: (String, String) -> Unit,
    onRequestSms: () -> Unit,
    onRequestPhone: () -> Unit,
    onRequestActivity: () -> Unit,
    onRequestNotification: () -> Unit,
    onSendTestSms: (Contact) -> Unit,
    onSetHours: (Int) -> Unit,
    onAdvance: (SetupStep) -> Unit,
    onComplete: () -> Unit
) {
    var consented by remember(step) { mutableStateOf(step != SetupStep.INTRO) }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    val permissionsReady = smsGranted && phoneGranted && activityGranted && notificationGranted
    val smsReady = smsSetupState is SmsSetupState.Ready

    AlertDialog(
        onDismissRequest = {},
        title = {
            Column {
                Text("안전 설정 ${step.ordinal + 1}/6", fontWeight = FontWeight.Bold)
                Text(setupTitle(step))
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (step) {
                    SetupStep.DEVICE -> {
                        Text("Life Link의 긴급 알림은 기기의 활성 SIM으로 실제 SMS를 보냅니다.")
                        Text(smsSetupState.userMessage(), fontWeight = FontWeight.Bold)
                        if (smsSetupState is SmsSetupState.Blocked &&
                            smsSetupState.issue == SmsSetupIssue.UNSUPPORTED_DEVICE
                        ) {
                            Text(
                                "이 기기는 이동통신사 SMS를 지원하지 않아 핵심 기능을 사용할 수 없습니다. " +
                                    "SMS가 가능한 스마트폰 또는 SIM 지원 태블릿에서 설치해 주세요."
                            )
                        }
                        OutlinedButton(onClick = onRefreshDevice, modifier = Modifier.fillMaxWidth()) {
                            Text("기기 상태 다시 확인")
                        }
                    }
                    SetupStep.INTRO -> {
                        Text("설정 시간 동안 유효한 활동이 없으면 등록한 보호자에게 SMS를 보냅니다.")
                        Text("119 신고나 의료기기를 대신하지 않으며, 전원 꺼짐·강제 종료·통신 장애에서는 작동하지 않을 수 있습니다.")
                        Text("위치는 수집하지 않습니다. 연락처, 활동 확인 시각과 문자 결과는 이 기기에만 저장됩니다.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = consented, onCheckedChange = { consented = it })
                            Text("개인정보 처리방침과 SMS 발송 안내에 동의합니다.")
                        }
                    }
                    SetupStep.CONTACT -> {
                        Text("문자를 받을 보호자를 최소 1명 등록해 주세요.")
                        OutlinedTextField(
                            value = contactName,
                            onValueChange = { contactName = it.take(30) },
                            label = { Text("보호자 이름") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = contactPhone,
                            onValueChange = { contactPhone = it.filter(Char::isDigit).take(15) },
                            label = { Text("전화번호") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                onAddContact(contactName, contactPhone)
                                if (contactName.isNotBlank() && contactPhone.length >= 8) {
                                    contactName = ""
                                    contactPhone = ""
                                }
                            },
                            enabled = contacts.size < 3 && contactName.isNotBlank() &&
                                contactPhone.length in 8..15,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("연락처 등록") }
                        Text("전화번호는 숫자 8~15자리로 입력해 주세요.")
                        contacts.forEach { Text("등록됨: ${it.name} · ${it.phoneNumber}") }
                    }
                    SetupStep.PERMISSIONS -> {
                        Text("각 권한은 표시된 안전 기능에만 사용합니다.")
                        SetupPermissionRow("1. 사전 경고 알림", notificationGranted, onRequestNotification)
                        SetupPermissionRow("2. 활동 인식", activityGranted, onRequestActivity)
                        SetupPermissionRow("3. SIM 상태 확인", phoneGranted, onRequestPhone)
                        SetupPermissionRow("4. 보호자 SMS 발송", smsGranted, onRequestSms)
                        Text(smsSetupState.userMessage(), fontWeight = FontWeight.Bold)
                        if (smsSetupState is SmsSetupState.Blocked &&
                            smsSetupState.issue in setOf(
                                SmsSetupIssue.SIM_SELECTION_REQUIRED,
                                SmsSetupIssue.SIM_CHANGED
                            )
                        ) {
                            smsSetupState.lines.forEach { line ->
                                OutlinedButton(
                                    onClick = { onSelectSmsLine(line.subscriptionId) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("${line.label} 사용") }
                            }
                        }
                    }
                    SetupStep.TEST_SMS -> {
                        Text("모니터링을 켜기 전에 보호자에게 실제 시험 문자 1건을 보냅니다.")
                        Text("시험 문자는 자동 재시도하지 않으며 60초 동안 다시 보낼 수 없습니다.")
                        contacts.forEach { contact ->
                            OutlinedButton(
                                onClick = { onSendTestSms(contact) },
                                enabled = testSmsVerification.state != TestSmsVerificationState.PENDING,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("${contact.name}에게 시험 문자 보내기") }
                        }
                        val message = testSmsVerification.message.ifBlank {
                            "아직 시험 문자 발송을 확인하지 않았습니다."
                        }
                        Text(message, fontWeight = FontWeight.Bold)
                    }
                    SetupStep.MONITORING -> {
                        Text("활동이 없을 때 보호자에게 알릴 시간을 선택해 주세요.")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(6, 12, 24).forEach { hours ->
                                FilterChip(
                                    selected = monitorHours == hours,
                                    onClick = { onSetHours(hours) },
                                    label = { Text("${hours}시간") }
                                )
                            }
                        }
                        Text("선택: ${monitorHours}시간")
                        Text("설정을 마치면 백그라운드 안전 모니터링이 시작됩니다.")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            val next = SetupStep.entries.getOrNull(step.ordinal + 1)
            Button(
                onClick = { if (next == null) onComplete() else onAdvance(next) },
                enabled = when (step) {
                    SetupStep.DEVICE -> smsSetupState !is SmsSetupState.Blocked ||
                        smsSetupState.issue in setOf(
                            SmsSetupIssue.PHONE_PERMISSION_REQUIRED,
                            SmsSetupIssue.SIM_SELECTION_REQUIRED,
                            SmsSetupIssue.SIM_CHANGED
                        )
                    SetupStep.INTRO -> consented
                    SetupStep.CONTACT -> contacts.isNotEmpty()
                    SetupStep.PERMISSIONS -> permissionsReady && smsReady
                    SetupStep.TEST_SMS -> testSmsVerification.state == TestSmsVerificationState.SUCCESS
                    SetupStep.MONITORING -> true
                }
            ) { Text(if (step == SetupStep.MONITORING) "안전 모니터링 시작" else "다음") }
        },
        dismissButton = {
            if (step.ordinal > 0) {
                TextButton(onClick = { onAdvance(SetupStep.entries[step.ordinal - 1]) }) {
                    Text("이전")
                }
            }
        }
    )
}

@Composable
private fun SetupPermissionRow(label: String, granted: Boolean, request: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label: ${if (granted) "허용됨" else "필요"}")
        if (!granted) TextButton(onClick = request) { Text("허용") }
    }
}

private fun setupTitle(step: SetupStep): String = when (step) {
    SetupStep.DEVICE -> "기기 호환성 확인"
    SetupStep.INTRO -> "기능과 한계 동의"
    SetupStep.CONTACT -> "비상연락처 등록"
    SetupStep.PERMISSIONS -> "필수 권한과 SIM 설정"
    SetupStep.TEST_SMS -> "보호자 시험 문자"
    SetupStep.MONITORING -> "안심 시간과 시작"
}