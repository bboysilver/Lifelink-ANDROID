package com.example.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyFollowUpStatus
import com.example.data.MonitoringRuntimeState
import com.example.data.SafetyReviewItem
import com.example.monitoring.SmsSetupState
import com.example.monitoring.userMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SimpleRemainingTime(seconds: Long) {
    val values = listOf(seconds / 3600L to "시간", seconds % 3600L / 60L to "분", seconds % 60L to "초")
    Row(
        modifier = Modifier.fillMaxWidth().testTag("remaining_time_text"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { (value, unit) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d".format(Locale.US, value), fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                Text(unit, fontSize = 16.sp)
            }
        }
    }
}

@Composable
internal fun InactivityCycleNotice(notice: InactivityAlertNotice) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("inactivity_cycle_notice")) {
        Text(notice.result, fontSize = 18.sp)
        Text(INACTIVITY_CYCLE_GUIDANCE, fontSize = 16.sp)
    }
}

@Composable
internal fun SafetyToolsCard(
    simpleHome: Boolean,
    onSimpleHomeChanged: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    var showResponseGuide by remember { mutableStateOf(false) }
    if (showResponseGuide) CaregiverResponseDialog { showResponseGuide = false }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("간편 홈", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("큰 버튼 중심으로 봅니다. 감지·문자 설정은 바뀌지 않습니다.", fontSize = 16.sp)
                }
                Switch(
                    checked = simpleHome,
                    onCheckedChange = onSimpleHomeChanged,
                    modifier = Modifier.testTag("simple_home_switch").semantics { contentDescription = "간편 홈" }
                )
            }
            Text("보호자 문자에는 발송 사유와 마지막 감지 활동 시각이 포함됩니다.", fontSize = 16.sp)
            OutlinedButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open_diagnostics")
            ) { Text("작동 상태 점검", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { showResponseGuide = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open_response_guide")
            ) { Text("경고 문자를 받았다면", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
internal fun CaregiverResponseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("보호자 대응 안내", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("문자만으로 응급상황이나 안전을 단정할 수 없습니다. 추가 문자를 기다리지 마세요.")
                Text("1. 지금 사용자에게 직접 전화해 안부를 확인해 주세요.")
                Text("2. 연락이 안 되면 미리 정한 다른 보호자·가까운 분과 연락하고 확인 담당자를 정해 주세요.")
                Text("3. 응급상황이 의심되면 기다리지 말고 119에 상황과 알고 있는 위치를 알려주세요. 상담원의 안내를 따라주세요.")
                Text("4. 실제 통화·대면으로 확인한 결과와 시각을 다른 보호자에게도 알려주세요.")
                Text("설치할 때 주 보호자와 가까운 예비 연락자를 미리 정하고 문자 수신·대응에 동의받으세요.")
                Text("앱은 자동 신고·출동을 요청하거나 문자 회신을 읽지 않습니다. 이 기기의 가족 확인 기록은 원격 보호자의 응답이 아니며 문자 재시도를 중지하지 않습니다.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text("확인") } }
    )
}

@Composable
internal fun SafetyDiagnosticsDialog(
    report: SafetyDiagnosticReport,
    smsState: SmsSetupState,
    lastSmsSentAtMs: Long?,
    onRefresh: () -> Unit,
    onRepairSetup: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFieldVisit: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var settingsError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("작동 상태 점검", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    report.headline,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (report.monitoringConfirmed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                )
                Text("이 점검은 기기 상태 확인이며 사용자의 안전을 보장하지 않습니다.")
                DiagnosticLine(
                    "활동 감지 서비스",
                    when {
                        report.monitoringConfirmed -> "실행 확인 · 센서 등록 후 서비스 응답 있음"
                        report.snapshot.isRunning && report.heartbeatFresh -> "서비스 응답 있음 · 아래 누락 설정 확인 필요"
                        report.snapshot.runtimeState == MonitoringRuntimeState.STARTING -> "시작 중 · 아직 실행 미확인"
                        !report.snapshot.desiredEnabled -> "꺼짐 · 무활동 감지 중단"
                        else -> report.snapshot.serviceError.ifBlank { "서비스 응답 확인 필요" }
                    }
                )
                DiagnosticLine("마지막 서비스 응답 (기기 내)", diagnosticTime(report.snapshot.lastHeartbeatMs))
                DiagnosticLine("마지막 감지 활동", diagnosticTime(report.snapshot.lastActivityMs))
                Text(report.snapshot.lastActivityReason)
                DiagnosticLine(
                    "마지막 자동 감지 · 앱 조작과 별도",
                    when {
                        report.snapshot.lastAutomaticActivityMs > report.checkedAtMs -> "기기 시각 확인 필요"
                        else -> diagnosticTime(report.snapshot.lastAutomaticActivityMs)
                    }
                )
                Text(report.snapshot.lastAutomaticActivityReason)
                if (report.snapshot.lastAutomaticActivityMs > 0L && !report.snapshot.lastAutomaticActivityApplied) {
                    Text("더 최근 활동이 있어 이 자동 신호로 타이머를 바꾸지 않았습니다.")
                }
                Text("앱을 열면 마지막 활동은 바뀔 수 있지만 위 자동 감지 기록은 덮어쓰지 않습니다.")
                Text("어떤 활동을 감지하나요?", fontWeight = FontWeight.Bold)
                Text(ACTIVITY_DETECTION_GUIDANCE)
                DiagnosticLine("마지막 문자 발송 확인 · 시험 문자 포함", diagnosticTime(lastSmsSentAtMs))
                Text("문자 발송·전달 확인은 보호자가 읽었거나 안부를 확인했다는 뜻이 아닙니다.")
                DiagnosticLine("활동 센서 지원", if (report.environment.hasActivitySensor) "센서 있음 · 실제 감지 이력은 위에서 확인" else "지원 센서 없음")
                Text(report.environment.activitySensorSummary)
                DiagnosticLine("활동 인식 권한", permissionLabel(report.environment.activityGranted))
                DiagnosticLine("SMS 발송 권한", permissionLabel(report.environment.smsGranted))
                DiagnosticLine("SIM 확인 권한", permissionLabel(report.environment.phoneGranted))
                DiagnosticLine("사전 경고 알림", permissionLabel(report.environment.notificationsGranted))
                DiagnosticLine("SMS 회선", smsState.userMessage())
                report.setupProblem?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = onRepairSetup, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("누락 설정 확인하기")
                    }
                }
                DiagnosticLine(
                    "배터리·백그라운드 설정",
                    when (report.environment.batteryPolicy) {
                        BatteryPolicy.RESTRICTED -> "백그라운드 제한됨 · 기기 설정을 확인해 주세요"
                        BatteryPolicy.OPTIMIZED -> "배터리 최적화 적용 중 · 작업이 늦어질 수 있습니다"
                        BatteryPolicy.UNRESTRICTED -> "시스템 배터리 최적화 제외됨"
                        BatteryPolicy.UNKNOWN -> "확인 불가 · 기기 설정에서 확인해 주세요"
                    }
                )
                Text("제조사의 절전·수면 앱 설정은 별도입니다. 앱이 설정을 자동으로 바꾸지 않습니다.")
                OutlinedButton(
                    onClick = { settingsError = !openBatterySettings(context) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("배터리 설정 열기") }
                if (settingsError) Text("설정 화면을 열 수 없습니다. 휴대전화 설정에서 LifeLink의 배터리 제한을 확인해 주세요.")
                Text("전원이 꺼지거나 앱을 강제 종료하면 이 기기에서 중단 알림을 보낼 수 없습니다. 보호자 원격 감시는 아직 제공하지 않습니다.")
                onOpenFieldVisit?.let { open ->
                    OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open_field_visit")) {
                        Text("현장 점검·요약 공유")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("확인") } },
        dismissButton = { TextButton(onClick = onRefresh) { Text("다시 점검") } }
    )
}

internal const val ACTIVITY_DETECTION_GUIDANCE =
    "모니터링이 실행 중일 때 잠금 해제 신호, 반복 걸음, 일정 시간 반복된 휴대전화 움직임을 확인합니다. " +
        "걸음은 한두 걸음만으로 갱신하지 않으며 걸음 수 센서는 첫 값 이후 증가를 확인합니다. " +
        "화면 점등만으로는 갱신하지 않습니다. 다른 앱의 모든 터치·통화·사용 이력을 읽지 않습니다. " +
        "다른 사람이 기기를 조작한 경우도 감지될 수 있으며 사람의 안전을 증명하지 않습니다."

@Composable
private fun DiagnosticLine(title: String, value: String) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(value, fontSize = 16.sp)
    }
}

@Composable
internal fun FamilyReviewCard(
    item: SafetyReviewItem,
    onSelect: (FamilyFollowUpStatus) -> Unit
) {
    val status = FamilyFollowUpStatus.entries.firstOrNull { it.name == item.followUpStatus }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (item.type) {
                    "emergency" -> "무활동 알림"
                    "daily" -> "매일 안부 확인"
                    "sos" -> "SOS 도움 요청"
                    else -> "보호자 알림"
                },
                fontWeight = FontWeight.Bold, fontSize = 20.sp
            )
            Text("발생 시각: ${diagnosticTime(item.occurredAtMs)}")
            Text("SMS 발송 확인: ${item.sentRecipients}/${item.totalRecipients}명")
            Text("SMS 전달 확인: ${item.deliveredRecipients}/${item.totalRecipients}명 · 전달 미확인 ${item.totalRecipients - item.deliveredRecipients}명 · 열람 여부 알 수 없음")
            val memo = status?.label ?: if (item.followUpStatus == null) "미기록" else "알 수 없는 기록 · 확인 필요"
            Text("이 단말에 기록한 메모: $memo", fontWeight = FontWeight.Bold)
            item.followUpUpdatedAtMs?.let { Text("기록 시각: ${diagnosticTime(it)}") }
            FamilyFollowUpStatus.entries.forEach { option ->
                OutlinedButton(
                    onClick = { onSelect(option) },
                    enabled = status != option,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("기록: ${option.label}", fontSize = 18.sp) }
            }
        }
    }
}

internal fun diagnosticTime(timestampMs: Long?): String = if (timestampMs != null && timestampMs > 0) {
    SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA).format(Date(timestampMs))
} else {
    "기록 없음"
}

private fun permissionLabel(granted: Boolean) = if (granted) "허용됨" else "설정 필요"

private fun openBatterySettings(context: Context): Boolean = try {
    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    true
} catch (_: ActivityNotFoundException) {
    false
}
