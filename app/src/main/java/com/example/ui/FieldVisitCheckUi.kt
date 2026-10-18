package com.example.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.example.BuildConfig
import com.example.monitoring.SmsSetupState
import com.example.monitoring.userMessage
import java.util.Locale
import java.util.UUID

private val fieldChecklistItems = listOf(
    "consent" to "이용자·보호자 시험 참여 동의",
    "sim" to "활성 SIM과 기본 문자 앱 발송 확인",
    "recipient" to "시험 수신자·전화번호를 본인과 확인",
    "charges" to "실제 SMS 비용·긴급 문자 오인 가능성 안내",
    "power" to "충전·제조사 절전·배터리 제한 확인",
    "permissions" to "필수 권한·알림 설정 확인",
    "detection" to "잠금 해제·반복 활동 실제 감지 확인"
)

@Composable
internal fun FieldVisitCheckScreen(
    report: FieldVisitReport,
    onRefresh: () -> Unit,
    onOpenContacts: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var settingsError by rememberSaveable { mutableStateOf(false) }
    var shareDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var checklist by rememberSaveable { mutableStateOf(List(fieldChecklistItems.size) { CheckResult.NOT_CHECKED.name }) }
    val draft = shareDraft
    val temporaryAlias = rememberSaveable { "방문기기-${UUID.randomUUID().toString().take(6).uppercase(Locale.US)}" }
    if (draft != null) {
        FieldVisitSummaryPreview(
            summaryWithoutAlias = draft,
            temporaryAlias = temporaryAlias,
            onShare = { openFieldVisitSharesheet(context, it) },
            onDismiss = { shareDraft = null }
        )
        return
    }
    FieldVisitPage("현장 방문 · 기기 상태 점검", onDismiss) {
        val diagnostics = report.diagnostics
        Text("기술 검증 중 · 동의한 직원·자원봉사자용", fontWeight = FontWeight.Bold)
        Text(FIELD_VISIT_LIMITATION)
        Text(report.monitoringLabel, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        FieldVisitLine("정보 확인 시각", fieldVisitTime(diagnostics.checkedAtMs))
        FieldVisitLine("마지막 실제 서비스 응답", fieldVisitTime(diagnostics.snapshot.lastHeartbeatMs))
        Text(if (diagnostics.heartbeatFresh) "최근 서비스 응답 있음" else "최근 서비스 응답 미확인 · 실행을 보장할 수 없음")
        FieldVisitLine("마지막 관찰된 활동", if (report.hasObservedActivity) fieldVisitTime(diagnostics.snapshot.lastActivityMs) else "유효한 활동 시각 미확인")
        if (report.hasObservedActivity) Text(diagnostics.snapshot.lastActivityReason)
        Text("활동 신호는 사람의 안전 확인이 아닙니다.")
        FieldVisitLine("등록 보호자", "${report.contactCount}명 · 이름·전화번호 비공개")
        FieldVisitLine("SMS 지원·선택 회선", when (val sms = report.smsSetup) {
            is SmsSetupState.Ready -> "SIM ${sms.line.slotIndex + 1} 선택됨 · SMS 지원 회선 확인"
            is SmsSetupState.Blocked -> sms.userMessage()
        })
        Text(if (diagnostics.environment.airplaneModeEnabled) "비행기 모드 켜짐 · 이동통신사 문자 발송 확인 필요" else "통신망 연결·상대방 수신은 실제 시험으로 확인해 주세요.")
        FieldVisitLine("활동 센서", if (diagnostics.environment.hasActivitySensor) "센서 있음 · 위 감지 이력 별도 확인" else "지원 센서 없음 · 조치 필요")
        FieldVisitLine("활동 인식 권한", checkPermission(diagnostics.environment.activityGranted))
        FieldVisitLine("SMS 발송 권한", checkPermission(diagnostics.environment.smsGranted))
        FieldVisitLine("SIM 확인 권한", checkPermission(diagnostics.environment.phoneGranted))
        FieldVisitLine("알림·안전 알림 채널", checkPermission(diagnostics.environment.notificationsGranted))
        FieldVisitLine("배터리·백그라운드", batteryPolicyLabel(diagnostics.environment.batteryPolicy))
        Text("제조사의 수면 앱·절전 설정은 별도 확인이 필요합니다.")
        FieldVisitSmsLine("최근 실제 문자 · 수신자 1명 결과", report.actualSms, diagnostics.checkedAtMs)
        FieldVisitSmsLine("최근 시험 문자", report.testSms, diagnostics.checkedAtMs)
        Text("최근 결과 1건이 전체 보호자의 성공을 뜻하지 않습니다. 전달 미확인은 실패 확정도 아닙니다. SENT·DELIVERED 모두 실제 안부 확인과 다릅니다.")
        diagnostics.setupProblem?.let { Text("미해결 설정: $it", color = MaterialTheme.colorScheme.error) }
        if (report.smsSetup is SmsSetupState.Blocked) {
            Text("회선 선택은 이 화면을 닫고 홈의 문자 회선 설정에서 확인해 주세요. 지원하지 않는 기기에서는 모니터링을 시작할 수 없습니다.")
        }
        FieldVisitAction("앱 권한·알림 설정 열기", "field_app_settings") {
            settingsError = !openFieldVisitSettings(context, Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()
            ))
        }
        FieldVisitAction("배터리 설정 열기", "field_battery_settings") {
            settingsError = !openFieldVisitSettings(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        FieldVisitAction("보호자·시험 문자 화면 열기", "field_contacts") { onDismiss(); onOpenContacts() }
        Text("위 버튼은 설정 화면만 엽니다. 이 점검 화면에서 모니터링을 중지하거나 시험 문자를 자동 발송하지 않습니다.")
        if (settingsError) Text("설정을 열지 못했습니다. 휴대전화 설정에서 LifeLink 권한과 배터리 제한을 확인해 주세요.", color = MaterialTheme.colorScheme.error)
        Text(FIELD_VISIT_ACTIVITY_NOTICE)
        FieldVisitChecklist(checklist) { index, result ->
            checklist = checklist.mapIndexed { position, current -> if (position == index) result.name else current }
        }
        FieldVisitAction("다시 점검", "field_refresh", onRefresh)
        Button(
            onClick = { shareDraft = fieldVisitSummary(report, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, temporaryAlias) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("field_preview")
        ) { Text("점검 요약 미리보기", fontSize = 18.sp) }
        Text("이용자의 허락을 받아 함께 확인해 주세요. 실제 대상자의 공식 돌봄·안전 확인을 이 점검으로 대체하지 마세요.")
    }
}

@Composable
private fun FieldVisitSmsLine(title: String, observation: SmsObservation?, checkedAtMs: Long) {
    FieldVisitLine(title, observation.stateAt(checkedAtMs).label)
    val timestamp = observation?.updatedAtMs
    Text("최근 결과 기록 시각: ${if (timestamp != null && timestamp in 1..checkedAtMs) fieldVisitTime(timestamp) else "미확인"}")
}

@Composable
private fun FieldVisitChecklist(results: List<String>, onChange: (Int, CheckResult) -> Unit) {
    Text("시험 전 준비 체크리스트", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    Text("직접 확인한 상태만 선택하세요. 체크는 이 화면의 임시 표시이며 전송·타이머·안전 상태를 바꾸지 않습니다.")
    fieldChecklistItems.forEachIndexed { index, (key, label) ->
        var expanded by remember { mutableStateOf(false) }
        val result = CheckResult.valueOf(results[index])
        Column {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("check_$key")
            ) { Text("$label: ${result.label}") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CheckResult.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onChange(index, option); expanded = false },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("check_${key}_${option.name}")
                    )
                }
            }
        }
    }
    Text("실제 SMS 시험은 수신자 동의 후 연락처 화면에서 수동으로 실행하세요. 자동 검사나 CI는 실제 문자를 발송하지 않습니다.")
}

@Composable
internal fun FieldVisitSummaryPreview(
    summaryWithoutAlias: String,
    temporaryAlias: String,
    onShare: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var includeAlias by rememberSaveable(summaryWithoutAlias) { mutableStateOf(false) }
    var consented by rememberSaveable(summaryWithoutAlias) { mutableStateOf(false) }
    var launchFailed by rememberSaveable { mutableStateOf(false) }
    val text = withVisitAlias(summaryWithoutAlias, temporaryAlias, includeAlias)
    FieldVisitPage("점검 요약 미리보기", onDismiss) {
        Text("아래 내용만 공유합니다. 이름·전화번호·정확한 활동 시각·SMS 본문은 포함하지 않습니다.")
        Text("기존 사용자 이름 대신 개인정보 없는 임시 점검 별칭만 선택할 수 있습니다.")
        FieldVisitConsentRow("임시 점검 별칭 포함 ($temporaryAlias)", includeAlias, "field_alias") {
            includeAlias = it
            consented = false
        }
        Text(text, modifier = Modifier.testTag("field_share_text"))
        Text("다음 공유창에서 받는 앱과 수신자를 직접 선택하세요. 선택한 외부 앱은 인터넷을 사용할 수 있으며, 전송 후에는 해당 앱에서 관리합니다. 앱은 수신·열람·조치 여부를 알 수 없습니다.")
        FieldVisitConsentRow("공유 내용과 외부 앱 이용을 확인했고 직접 공유합니다", consented, "field_share_consent") { consented = it }
        Button(
            onClick = {
                if (onShare(text)) onDismiss() else launchFailed = true
            },
            enabled = consented,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("field_share")
        ) { Text("동의하고 공유창 열기", fontSize = 18.sp) }
        if (launchFailed) Text("공유창을 열 수 없습니다. 전송 여부는 확인되지 않았습니다. 다시 시도하거나 취소해 주세요.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun FieldVisitConsentRow(label: String, checked: Boolean, tag: String, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FieldVisitPage(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).testTag("field_content"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    content()
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("field_close")) {
                    Text("닫기", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun FieldVisitLine(title: String, value: String) {
    Column {
        Text(title, fontWeight = FontWeight.Bold)
        Text(value)
    }
}

@Composable
private fun FieldVisitAction(text: String, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)) { Text(text) }
}

private fun checkPermission(granted: Boolean): String = if (granted) "허용·설정 확인됨" else "조치 필요"

internal fun fieldVisitShareIntent(text: String): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
    "점검 요약 공유 대상 선택"
)

private fun openFieldVisitSharesheet(context: Context, text: String): Boolean = try {
    context.startActivity(fieldVisitShareIntent(text))
    true
} catch (_: ActivityNotFoundException) {
    false
}

private fun openFieldVisitSettings(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
