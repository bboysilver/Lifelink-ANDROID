package com.example.ui

import androidx.compose.runtime.saveable.listSaver
import com.example.monitoring.SmsSetupState
import java.util.TimeZone

internal enum class VisitOperatorLabel(val label: String) {
    NOT_RECORDED("미기록 · 조작자 알 수 없음"),
    STAFF_REPORTED("직원·지원자 조작으로 직접 표시"),
    USER_REPORTED("이용자 조작으로 직접 표시"),
    UNKNOWN("구분할 수 없음으로 직접 표시")
}

internal enum class VisitTestKind(val label: String, val expected: String) {
    INSTALLATION("설치·설정", "권한·SIM·센서·알림·배터리 제한을 각각 확인"),
    TEST_SMS("시험 문자", "수신자 동의 후 기존 연락처 화면에서 수동 발송·실제 수신을 별도 확인"),
    INACTIVITY("무활동 경고", "마지막 유효 활동 이후 설정한 시간과 실제 발송·수신을 구분"),
    UNLOCK("PIN 잠금 해제·반복 걸음", "마지막 자동 감지 기록과 반영 여부를 확인"),
    DOZE("화면 OFF·Doze", "앱을 열지 않은 상태의 마감·발송·수신 지연을 기록"),
    RECOVERY("재부팅·업데이트", "기존 설정·사건·수신자 상태 보존과 서비스 복구를 확인"),
    SOS("SOS 취소", "기존5초 취소 기능을 별도로 시험하고 취소 후 발송이 없는지 확인")
}

internal enum class VisitTestResult(val label: String) {
    NOT_RUN("미실행"), EXPECTED("기대와 같음 · 담당자 수동 기록"),
    DIFFERENT("기대와 다름 · 조치 필요"), UNCONFIRMED("판단 불가 · 미확인")
}

// Only UI saved-state, never a Room row, monitoring preference or safety decision.
internal data class FieldVisitBaseline(
    val capturedAtMs: Long,
    val observedAtMs: Long,
    val localFacts: List<String>,
    val shareSummary: String
)

internal val FieldVisitBaselineSaver = listSaver<FieldVisitBaseline, String>(
    save = { listOf(it.capturedAtMs.toString(), it.observedAtMs.toString(), it.shareSummary) + it.localFacts },
    restore = { values ->
        val captured = values.getOrNull(0)?.toLongOrNull()
        val observed = values.getOrNull(1)?.toLongOrNull()
        if (captured == null || observed == null || values.size < 4) null
        else FieldVisitBaseline(captured, observed, values.drop(3), values[2])
    }
)

internal fun captureFieldVisitBaseline(
    report: FieldVisitReport,
    version: String,
    apiLevel: Int,
    capturedAtMs: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault()
): FieldVisitBaseline {
    // Evaluate liveness at capture, not at the time an old report was read.
    val checked = report.copy(diagnostics = report.diagnostics.copy(
        checkedAtMs = capturedAtMs
    ))
    val observedTime = report.diagnostics.checkedAtMs.takeIf { it in 1..capturedAtMs }
    val facts = listOf("원본 정보를 읽은 시각: ${observedTime?.let { fieldVisitTime(it, zone) } ?: "미확인 · 기기 시각 확인 필요"}") +
        fieldVisitLocalFacts(checked, zone).drop(1)
    return FieldVisitBaseline(capturedAtMs, report.diagnostics.checkedAtMs,
        facts, fieldVisitSummary(checked, version, apiLevel, "", zone = zone))
}

internal fun fieldVisitLocalFacts(report: FieldVisitReport, zone: TimeZone = TimeZone.getDefault()): List<String> {
    val diagnostics = report.diagnostics
    val snapshot = diagnostics.snapshot
    val now = diagnostics.checkedAtMs
    fun localTime(time: Long): String = if (time in 1..now) fieldVisitTime(time, zone) else "미확인 · 유효한 시각 없음"
    fun permission(granted: Boolean) = if (granted) "기기에서 허용 확인" else "조치 필요"
    return listOf(
        "정보를 읽은 시각: ${fieldVisitTime(now, zone)}",
        "기기 모니터링: ${report.monitoringLabel}",
        "설정 조건: ${report.installationChecks.count { it == CheckResult.PASS }}/${report.installationChecks.size}개 확인 · 실제 시험 합격 아님",
        "마지막 실제 서비스 응답: ${localTime(snapshot.lastHeartbeatMs)}",
        "서비스 응답 상태: ${if (diagnostics.heartbeatFresh) "관찰 시점 최근 응답 있음" else "미확인 · 실행을 보장할 수 없음"}",
        "마지막 전체 활동: ${localTime(snapshot.lastActivityMs)}",
        "마지막 자동 감지 · 앱 조작과 별도: ${localTime(snapshot.lastAutomaticActivityMs)}",
        "자동 감지 타이머 반영: ${when {
            snapshot.lastAutomaticActivityMs !in 1..now -> "미확인"
            snapshot.lastAutomaticActivityApplied -> "반영 기록 있음 · 안전 확인 아님"
            else -> "미반영 기록 · 더 최근 활동 등 별도 확인"
        }}",
        "등록 보호자: ${if (report.contactCount > 0) "${report.contactCount}명" else "미확인 · 현재 읽은 수0명"} · 이름·전화번호 비공개",
        "SMS 지원·선택 회선: ${when (val sms = report.smsSetup) {
            is SmsSetupState.Ready -> "SIM ${sms.line.slotIndex + 1} 설정 확인 · 실제 발송 미검증"
            is SmsSetupState.Blocked -> "설정 확인 필요 · 회선·권한 화면에서 확인"
        }}",
        "활동 센서: ${if (diagnostics.environment.hasActivitySensor) "지원 있음 · 실제 감지 시험 별도" else "지원 미확인 · 조치 필요"}",
        "활동 인식 권한: ${permission(diagnostics.environment.activityGranted)}",
        "SMS 발송 권한: ${permission(diagnostics.environment.smsGranted)}",
        "SIM 확인 권한: ${permission(diagnostics.environment.phoneGranted)}",
        "알림·안전 알림 채널: ${permission(diagnostics.environment.notificationsGranted)}",
        "비행기 모드: ${if (diagnostics.environment.airplaneModeEnabled) "켜짐 · 문자 발송 확인 필요" else "꺼짐 · 통신망·수신은 별도 확인"}",
        "배터리·백그라운드: ${batteryPolicyLabel(diagnostics.environment.batteryPolicy)}",
        "최근 실제 문자 · 수신자1명 결과: ${report.actualSms.stateAt(now).label}",
        "실제 문자 결과 기록 시각: ${localTime(report.actualSms?.updatedAtMs ?: 0L)}",
        "최근 시험 문자: ${report.testSms.stateAt(now).label}",
        "시험 문자 결과 기록 시각: ${localTime(report.testSms?.updatedAtMs ?: 0L)}"
    )
}

internal fun fieldVisitSessionSummary(
    baseline: FieldVisitBaseline,
    current: FieldVisitReport,
    operatorLabel: VisitOperatorLabel,
    testResults: List<VisitTestResult>,
    version: String,
    apiLevel: Int,
    zone: TimeZone = TimeZone.getDefault()
): String = buildString {
    appendLine("[점검 시작 전 기록 · 이 화면 진입 시 고정]")
    appendLine("고정 시각: ${fieldVisitTime(baseline.capturedAtMs, zone)}")
    appendLine("진입 전 앱 열기·조작이 이미 전체 활동에 반영됐을 수 있습니다.")
    appendLine(baseline.shareSummary)
    appendLine("\n[점검 이후 앱 내 관찰 · 미리보기 시 고정]")
    if (current.diagnostics.checkedAtMs < baseline.observedAtMs) {
        appendLine("기기 시각이 이전으로 바뀌어 전후 시간 비교는 미확인입니다.")
    }
    appendLine(fieldVisitSummary(current, version, apiLevel, "", zone = zone))
    appendLine("\n조작 구분(수동 선택): ${operatorLabel.label}")
    appendLine("Android가 실제 조작자를 식별한 결과가 아닙니다. 활동·문자 결과는 이용자 안전 확인이 아닙니다.")
    appendLine("[기기별 시험기록 · 담당자 수동 라벨]")
    VisitTestKind.entries.forEachIndexed { index, kind ->
        appendLine("${kind.label}: ${(testResults.getOrNull(index) ?: VisitTestResult.NOT_RUN).label}")
    }
    append("이 기록은 공식 동의·실기기 시험 합격·안전 인증이 아닙니다. 기종·정확한 활동·응답·문자 시각·담당자 신원은 공유하지 않습니다.")
}
