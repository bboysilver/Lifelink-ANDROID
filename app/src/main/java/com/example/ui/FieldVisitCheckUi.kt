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
import androidx.compose.runtime.mutableIntStateOf
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
    val baseline = rememberSaveable(saver = FieldVisitBaselineSaver) {
        captureFieldVisitBaseline(report, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT)
    }
    var operatorName by rememberSaveable { mutableStateOf(VisitOperatorLabel.NOT_RECORDED.name) }
    var testResults by rememberSaveable { mutableStateOf(List(VisitTestKind.entries.size) { VisitTestResult.NOT_RUN.name }) }
    var refreshCount by rememberSaveable { mutableIntStateOf(0) }
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
        Text(FIELD_VISIT_ACTIVITY_NOTICE)
        FieldVisitSectionTitle("점검 시작 전 기록 · 이 화면 진입 시 고정")
        Text("고정 시각: ${fieldVisitTime(baseline.capturedAtMs)}")
        Text("과거 기기 기록입니다. 이용자 조작이나 현재 정상 실행을 증명하지 않습니다. 새로 고침해도 바뀌지 않습니다.")
        Text(baseline.localFacts.joinToString("\n\n"), modifier = Modifier.testTag("field_baseline"))
        FieldVisitSectionTitle("점검 이후 앱 내 관찰")
        Text("정보는 앱 기록에 따라 갱신됩니다. 시각 차이만으로 누가 조작했는지 판단하지 않습니다.")
        if (diagnostics.checkedAtMs < baseline.observedAtMs) Text("기기 시각이 이전으로 바뀌어 전후 시간 비교는 미확인입니다.")
        Text(report.monitoringLabel, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("field_current_headline"))
        Text(fieldVisitLocalFacts(report).joinToString("\n\n"), modifier = Modifier.testTag("field_current"))
        Text("제조사의 수면 앱·절전 설정은 별도 확인이 필요합니다.")
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
        FieldVisitSectionTitle("점검 중 조작 구분 · 수동 선택")
        Text("Android는 실제 조작자를 알 수 없습니다. 직접 알고 있는 경우에만 표시하세요. 활동 기록의 주체를 자동으로 분류하지 않습니다.")
        FieldVisitChoice("조작 구분", VisitOperatorLabel.valueOf(operatorName).label, "field_operator",
            VisitOperatorLabel.entries.map { it.name to it.label }) { operatorName = it }
        FieldVisitChecklist(checklist) { index, result ->
            checklist = checklist.mapIndexed { position, current -> if (position == index) result.name else current }
        }
        FieldVisitSectionTitle("기기별 시험기록 · 수동 결론")
        Text("앱 ${BuildConfig.VERSION_NAME} / Android API ${Build.VERSION.SDK_INT} / 기종 ${Build.MODEL}")
        Text("결론은 기본 미실행입니다. 실제로 수행한 시험만 직접 표시하세요. 이 화면은 시험 문자·SOS·타이머를 실행하지 않습니다.")
        Text("수동 기록 ${testResults.count { it != VisitTestResult.NOT_RUN.name }}/${VisitTestKind.entries.size}건 · 합격률 아님",
            modifier = Modifier.testTag("field_test_count"))
        VisitTestKind.entries.forEachIndexed { index, kind ->
            Text("기대: ${kind.expected}")
            FieldVisitChoice(kind.label, VisitTestResult.valueOf(testResults[index]).label, "field_test_${kind.name}",
                VisitTestResult.entries.map { it.name to it.label }) { chosen ->
                testResults = testResults.mapIndexed { position, value -> if (position == index) chosen else value }
            }
        }
        Text("실제 차이·수신 시각·시험 조건은 동의한 담당자의 별도 내부 시험표에 기록하세요. 이름·전화번호·본문을 공유하지 마세요.")
        Text("현재 상태 다시 읽기: ${refreshCount}회 · 실제 시험 횟수 아님", modifier = Modifier.testTag("field_refresh_count"))
        FieldVisitAction("현재 상태 다시 읽기", "field_refresh") { refreshCount++; onRefresh() }
        Button(
            onClick = { shareDraft = fieldVisitSessionSummary(baseline, report, VisitOperatorLabel.valueOf(operatorName),
                testResults.map(VisitTestResult::valueOf), BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("field_preview")
        ) { Text("점검 요약 미리보기", fontSize = 18.sp) }
        Text("이용자의 허락을 받아 함께 확인해 주세요. 실제 대상자의 공식 돌봄·안전 확인을 이 점검으로 대체하지 마세요.")
    }
}

@Composable
private fun FieldVisitSectionTitle(title: String) = Text(title, fontSize = 20.sp,
    fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })

@Composable
private fun FieldVisitChoice(title: String, selected: String, tag: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)) {
            Text("$title: $selected")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(key); expanded = false },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_$key"))
            }
        }
    }
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
private fun FieldVisitAction(text: String, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)) { Text(text) }
}

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
