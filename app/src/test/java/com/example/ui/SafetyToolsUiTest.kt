package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
