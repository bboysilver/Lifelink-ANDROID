package com.example.ui

import com.example.data.FamilyFollowUpStatus
import com.example.data.SafetyReviewItem
import com.example.monitoring.SmsDispatchState
import com.example.monitoring.SmsDispatchStore
import com.example.monitoring.SmsSetupState
import com.example.monitoring.SmsSetupIssue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal const val FIELD_VISIT_LIMITATION =
    "기기 내 기술 정보이며 이용자의 안전을 보장하지 않습니다. 실제 돌봄·119·24시간 관제를 대신하지 않습니다."
internal const val FIELD_VISIT_ACTIVITY_NOTICE =
    "점검 중에도 모니터링은 계속됩니다. 앱을 열거나 조작하면 기존 동작에 따라 타이머가 갱신될 수 있습니다. 진입 전 조작도 이미 반영됐을 수 있으며, 점검 결과나 메모는 이용자 안전 확인이 아닙니다."

internal enum class CheckResult(val label: String) {
    PASS("확인됨"), NEEDS_ACTION("조치 필요"), NOT_CHECKED("미확인")
}

internal enum class SmsObservationState(val label: String) {
    QUEUED("결과 대기 (QUEUED) · 발송 미확인"),
    SENT("발송 확인 (SENT) · 전달 미확인"),
    DELIVERED("전달 확인 (DELIVERED) · 열람 미확인"),
    FAILED_RETRYABLE("발송 확인 실패 (FAILED_RETRYABLE)"),
    FAILED_FINAL("발송 확인 실패 (FAILED_FINAL)"),
    UNKNOWN("미확인 (UNKNOWN)")
}

// Deliberately contains no identifiers, names, telephone numbers or message text.
internal data class SmsObservation(
    val isTest: Boolean,
    val state: SmsObservationState,
    val updatedAtMs: Long
)

internal fun smsObservation(
    isTest: Boolean,
    state: String,
    updatedAtMs: Long,
    resultCode: Int? = null
): SmsObservation = SmsObservation(
    isTest,
    if (resultCode == SmsDispatchStore.RESULT_CALLBACK_TIMEOUT) SmsObservationState.UNKNOWN else when (state) {
        SmsDispatchState.QUEUED.name -> SmsObservationState.QUEUED
        SmsDispatchState.SENT.name -> SmsObservationState.SENT
        SmsDispatchState.DELIVERED.name -> SmsObservationState.DELIVERED
        SmsDispatchState.FAILED_RETRYABLE.name -> SmsObservationState.FAILED_RETRYABLE
        SmsDispatchState.FAILED_FINAL.name -> SmsObservationState.FAILED_FINAL
        else -> SmsObservationState.UNKNOWN
    },
    updatedAtMs
)

internal fun SmsObservation?.stateAt(checkedAtMs: Long): SmsObservationState = when {
    this == null || updatedAtMs !in 1..checkedAtMs -> SmsObservationState.UNKNOWN
    state == SmsObservationState.QUEUED &&
        checkedAtMs - updatedAtMs >= SmsDispatchStore.CALLBACK_TIMEOUT_MS -> SmsObservationState.UNKNOWN
    else -> state
}

internal data class FieldVisitReport(
    val diagnostics: SafetyDiagnosticReport,
    val smsSetup: SmsSetupState,
    val contactCount: Int,
    val actualSms: SmsObservation?,
    val testSms: SmsObservation?
) {
    val smsReady: Boolean
        get() = smsSetup is SmsSetupState.Ready && diagnostics.environment.smsGranted &&
            diagnostics.environment.phoneGranted && !diagnostics.environment.airplaneModeEnabled

    val monitoringLabel: String
        get() = when {
            diagnostics.environment.airplaneModeEnabled -> "감지·문자 설정 확인 필요 (비행기 모드)"
            !smsReady || contactCount < 1 -> "문자·보호자 설정 확인 필요"
            diagnostics.environment.batteryPolicy == BatteryPolicy.RESTRICTED -> "백그라운드 제한 · 설정 확인 필요"
            else -> diagnostics.headline
        }

    val hasObservedActivity: Boolean
        get() = diagnostics.snapshot.lastActivityMs in 1..diagnostics.checkedAtMs

    val installationChecks: List<CheckResult>
        get() = listOf(
            if (contactCount > 0) CheckResult.PASS else CheckResult.NOT_CHECKED,
            when {
                smsReady -> CheckResult.PASS
                (smsSetup as? SmsSetupState.Blocked)?.issue == SmsSetupIssue.CHECKING -> CheckResult.NOT_CHECKED
                else -> CheckResult.NEEDS_ACTION
            },
            if (diagnostics.environment.smsGranted) CheckResult.PASS else CheckResult.NEEDS_ACTION,
            if (diagnostics.environment.activityGranted && diagnostics.environment.hasActivitySensor) CheckResult.PASS else CheckResult.NEEDS_ACTION,
            if (diagnostics.environment.notificationsGranted) CheckResult.PASS else CheckResult.NEEDS_ACTION,
            if (!diagnostics.environment.airplaneModeEnabled) CheckResult.PASS else CheckResult.NEEDS_ACTION,
            when (diagnostics.environment.batteryPolicy) {
                BatteryPolicy.UNRESTRICTED -> CheckResult.PASS
                BatteryPolicy.UNKNOWN -> CheckResult.NOT_CHECKED
                else -> CheckResult.NEEDS_ACTION
            }
        )
}

internal fun fieldVisitTime(timestampMs: Long, zone: TimeZone = TimeZone.getDefault()): String =
    if (timestampMs > 0L) {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = zone }
            .format(Date(timestampMs)) + " [${zone.id}]"
    } else "기록 없음"

internal fun fieldVisitSummary(
    report: FieldVisitReport,
    version: String,
    apiLevel: Int,
    temporaryAlias: String,
    includeAlias: Boolean = false,
    zone: TimeZone = TimeZone.getDefault()
): String = withVisitAlias(buildString {
    appendLine("[LifeLink 기기 작동 상태 점검 · 기술 검증 중]")
    appendLine("점검 시각: ${fieldVisitTime(report.diagnostics.checkedAtMs, zone)}")
    appendLine("앱 버전: $version / Android API $apiLevel")
    appendLine("모니터링: ${when {
        !report.diagnostics.snapshot.desiredEnabled -> "꺼짐"
        report.diagnostics.monitoringConfirmed && report.smsReady && report.contactCount > 0 &&
            report.diagnostics.environment.batteryPolicy != BatteryPolicy.RESTRICTED -> "기기 실행 확인"
        else -> "실행 확인 필요"
    }}")
    appendLine("서비스 응답: ${if (report.diagnostics.heartbeatFresh) "최근 응답 있음" else "최근 응답 미확인"} (시각 비공개)")
    appendLine("최근 활동 관찰: ${if (report.hasObservedActivity) "기기 내 기록 있음" else "유효 기록 미확인"} (시간·이유 비공개)")
    appendLine("SMS 기능: ${if (report.smsReady) "SIM·권한 확인됨 / 통신망·실제 수신은 별도 확인" else "설정 확인 필요"}")
    appendLine("최근 실제 문자: ${report.actualSms.stateAt(report.diagnostics.checkedAtMs).label} (최근 수신자 1건, 전체 성공 아님)")
    appendLine("최근 시험 문자: ${report.testSms.stateAt(report.diagnostics.checkedAtMs).label}")
    appendLine("필수 설정: ${if (report.smsReady && report.diagnostics.setupProblem == null && report.diagnostics.environment.permissionsGranted && report.diagnostics.environment.hasActivitySensor && report.contactCount > 0) "기기에서 확인됨" else "확인 필요"}")
    appendLine("배터리 제한: ${batteryPolicyLabel(report.diagnostics.environment.batteryPolicy)}")
    appendLine("문자 발송·전달은 보호자의 열람이나 실제 안전 확인을 뜻하지 않습니다.")
    appendLine("전원 꺼짐·강제 종료·절전·통신 장애에서는 감지나 문자가 중단·지연될 수 있습니다.")
    appendLine("이 앱은 서버에 자동 전송하지 않습니다. 공유 대상은 사용자가 선택합니다.")
    append(FIELD_VISIT_LIMITATION)
}, temporaryAlias, includeAlias)

internal fun withVisitAlias(summaryWithoutAlias: String, temporaryAlias: String, include: Boolean): String =
    // Existing deviceAlias is a user-name field; never copy it into a share draft.
    if (include && Regex("방문기기-[A-F0-9]{6}").matches(temporaryAlias)) {
        "$summaryWithoutAlias\n임시 점검 별칭: $temporaryAlias"
    } else summaryWithoutAlias

internal fun batteryPolicyLabel(policy: BatteryPolicy): String = when (policy) {
    BatteryPolicy.UNRESTRICTED -> "시스템 최적화 제외됨 · 제조사 설정은 별도 확인"
    BatteryPolicy.OPTIMIZED -> "최적화 적용 중 · 작업 지연 가능"
    BatteryPolicy.RESTRICTED -> "백그라운드 제한됨 · 조치 필요"
    BatteryPolicy.UNKNOWN -> "미확인 · 기기 설정 확인 필요"
}

internal enum class FamilyReviewFilter(val label: String, val status: FamilyFollowUpStatus? = null) {
    ALL("전체"), NOT_RECORDED("미기록"),
    CONTACTING("연락 중", FamilyFollowUpStatus.CONTACTING),
    CONFIRMED_SAFE("직접 안전 확인", FamilyFollowUpStatus.CONFIRMED_SAFE),
    HELP_NEEDED("도움 필요", FamilyFollowUpStatus.HELP_NEEDED)
}

internal fun filterSafetyReviews(items: List<SafetyReviewItem>, filter: FamilyReviewFilter): List<SafetyReviewItem> =
    items.filter { item ->
        val known = FamilyFollowUpStatus.entries.firstOrNull { it.name == item.followUpStatus }
        when (filter) {
            FamilyReviewFilter.ALL -> true
            FamilyReviewFilter.NOT_RECORDED -> known == null
            else -> known == filter.status
        }
    }.sortedWith(compareByDescending<SafetyReviewItem> { it.occurredAtMs }.thenBy { it.incidentId })
