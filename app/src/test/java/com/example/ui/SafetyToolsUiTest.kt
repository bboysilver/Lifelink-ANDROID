package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringSnapshot
import com.example.monitoring.SmsSetupIssue
import com.example.monitoring.SmsSetupState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp")
class SafetyToolsUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun simpleTimerHasReadableUnitsAtTwoHundredPercentText() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 48.dp)) {
                        SimpleRemainingTime(12 * 3600L + 34 * 60L + 56L)
                    }
                }
            }
        }
        listOf("12", "34", "56", "시간", "분", "초").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test fun simpleHomeAndCheckButtonAreOperableAtTwoHundredPercentText() {
        var enabled = false
        var checked = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme { SafetyToolsCard(false, { enabled = it }, { checked = true }) }
            }
        }
        composeRule.onNodeWithTag("simple_home_switch").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("open_diagnostics").assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(enabled && checked) }
    }

    @Test fun postAlertGuidanceIsReadableWithoutAFalseZeroCountdown() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                        InactivityCycleNotice(InactivityAlertNotice("활동 대기", "보호자의 안부 확인을 뜻하지 않습니다."))
                    }
                }
            }
        }
        composeRule.onNodeWithText("보호자의 안부 확인을 뜻하지 않습니다.").assertIsDisplayed()
        composeRule.onNodeWithText(INACTIVITY_CYCLE_GUIDANCE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("remaining_time_text").assertDoesNotExist()
    }

    @Test fun diagnosticDialogCanCloseAtTwoHundredPercentText() {
        var closed = false
        val now = 1_700_000_000_000L
        val snapshot = MonitoringSnapshot(false, MonitoringRuntimeState.STOPPED, "", 0L, 0L, 0, 0L, "기록 없음", 0L, "사용자")
        val report = SafetyDiagnosticReport(snapshot, DiagnosticEnvironment(false, false, false, false, false, BatteryPolicy.UNKNOWN), "설정 필요", now)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    SafetyDiagnosticsDialog(report, SmsSetupState.Blocked(SmsSetupIssue.NO_ACTIVE_SIM), null, {}, {}, { closed = true })
                }
            }
        }
        composeRule.onNodeWithText("확인").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(closed) }
    }

    @Test fun diagnosticShowsAutomaticEvidenceSeparatelyFromOpeningTheApp() {
        val now = 1_800_000_000_000L
        val snapshot = MonitoringSnapshot(
            true, MonitoringRuntimeState.RUNNING, "", now + 3_600_000, 3_600, 0,
            now, "라이프링크 화면 직접 조작", now, "사용자",
            lastAutomaticActivityMs = now - 60_000,
            lastAutomaticActivityReason = "걸음 수 증가 감지",
            lastAutomaticActivityApplied = true
        )
        val report = SafetyDiagnosticReport(snapshot,
            DiagnosticEnvironment(true, true, true, true, true, BatteryPolicy.OPTIMIZED), null, now)
        composeRule.setContent { MaterialTheme {
            SafetyDiagnosticsDialog(report, SmsSetupState.Blocked(SmsSetupIssue.NO_ACTIVE_SIM), null, {}, {}, {})
        } }
        composeRule.onNodeWithText("마지막 자동 감지 · 앱 조작과 별도").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("걸음 수 증가 감지").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(ACTIVITY_DETECTION_GUIDANCE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("확인").assertIsEnabled()
    }
}
