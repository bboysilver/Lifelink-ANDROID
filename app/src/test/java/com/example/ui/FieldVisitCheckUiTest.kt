package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringSnapshot
import com.example.data.MonitoringStore
import com.example.data.SafetyReviewItem
import com.example.monitoring.SmsDispatchStore
import com.example.monitoring.SmsSetupIssue
import com.example.monitoring.SmsSetupState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp")
class FieldVisitCheckUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val now = 1_792_300_000_000L
    private val report = FieldVisitReport(
        SafetyDiagnosticReport(
            MonitoringSnapshot(false, MonitoringRuntimeState.STOPPED, "", 0L, 0L, 0, 0L, "기록 없음", 0L, "private name"),
            DiagnosticEnvironment(false, false, false, false, false, BatteryPolicy.UNKNOWN), "SIM·권한 확인 필요", now
        ), SmsSetupState.Blocked(SmsSetupIssue.NO_ACTIVE_SIM), 0, null, null
    )

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE).edit().clear().commit()
        SmsDispatchStore(context).clearAll()
    }

    @Test fun cancelPreviewDoesNotShareOrChangeMonitoringOrQueuedSms() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = MonitoringStore(context)
        store.beginStart(nowMs = now, elapsedRealtimeMs = 0)
        val dispatch = SmsDispatchStore(context)
        dispatch.beginAttempt("emergency:$now:1", 1, nowMs = now)
        val preferences = context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE)
        val before = preferences.all.toMap()
        val originalSms = dispatch.status("emergency:$now:1")
        var shared = false
        var shown by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                if (shown) FieldVisitSummaryPreview("sanitized summary", "방문기기-A0B1C2", { shared = true; true }, { shown = false })
            }
        }
        composeRule.onNodeWithTag("field_close").performClick()
        composeRule.runOnIdle {
            assertFalse(shared)
            assertEquals(before, preferences.all)
            assertEquals(originalSms, dispatch.status("emergency:$now:1"))
        }
    }

    @Test fun previewRequiresConsentAndAliasOptOutResetsConsentAtTwoHundredPercent() {
        var shared: String? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    FieldVisitSummaryPreview("sanitized summary", "방문기기-A0B1C2", { shared = it; true }, {})
                }
            }
        }
        composeRule.onNodeWithTag("field_alias").performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithTag("field_share").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("field_share_consent").performScrollTo().performClick()
        composeRule.onNodeWithTag("field_alias").performScrollTo().performClick()
        composeRule.onNodeWithTag("field_share_consent").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("field_share").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("field_share_consent").performScrollTo().performClick()
        composeRule.onNodeWithTag("field_share").performScrollTo().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals("sanitized summary", shared) }
    }

    @Test fun noSimNoPermissionsScreenAndChecklistAreScrollableAtTwoHundredPercent() {
        var closed = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme { FieldVisitCheckScreen(report, {}, {}, { closed = true }) }
            }
        }
        composeRule.onNodeWithTag("field_content").assertWidthIsAtLeast(200.dp)
        composeRule.onNodeWithText("문자·보호자 설정 확인 필요").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("field_app_settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("check_consent").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("확인됨").performClick()
        composeRule.onNodeWithText("이용자·보호자 시험 참여 동의: 확인됨").assertIsDisplayed()
        composeRule.onNodeWithTag("field_preview").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("field_close").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(closed) }
    }

    @Test fun checklistSurvivesPreviewCancelAndSavedStateRestoration() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { MaterialTheme { FieldVisitCheckScreen(report, {}, {}, {}) } }
        composeRule.onNodeWithTag("check_consent").performScrollTo().performClick()
        composeRule.onNodeWithTag("check_consent_NEEDS_ACTION").performClick()
        composeRule.onNodeWithTag("field_preview").performScrollTo().performClick()
        composeRule.onNodeWithTag("field_close").performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("이용자·보호자 시험 참여 동의: 조치 필요").performScrollTo().assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land")
    fun landscapePreviewRestoresConsentStateWithoutAutomaticallySharing() {
        val restoration = StateRestorationTester(composeRule)
        var shared = false
        restoration.setContent {
            MaterialTheme { FieldVisitSummaryPreview("summary", "방문기기-A0B1C2", { shared = true; true }, {}) }
        }
        composeRule.onNodeWithTag("field_share_consent").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("field_share").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("field_close").assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(shared) }
    }

    @Test fun failedChooserLaunchDoesNotClaimSharingOrReceipt() {
        composeRule.setContent {
            MaterialTheme { FieldVisitSummaryPreview("summary", "방문기기-A0B1C2", { false }, {}) }
        }
        composeRule.onNodeWithTag("field_share_consent").performScrollTo().performClick()
        composeRule.onNodeWithTag("field_share").performScrollTo().performClick()
        composeRule.onNodeWithText("공유창을 열 수 없습니다.", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun chooserContainsOnlyPlainTextWithoutRecipientsTrackingOrAttachments() {
        val chooser = fieldVisitShareIntent("sanitized summary")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("sanitized summary", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(setOf(Intent.EXTRA_TEXT), send.extras!!.keySet())
        assertNull(send.component)
        assertNull(send.`package`)
        assertNull(send.clipData)
        assertFalse(chooser.hasExtra(Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER))
    }

    @Test fun familyRecordSeparatesDeliveryAndLocalMemoAtLargeText() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    // Only this card's title/status lines are in view at first; no remote result is inferred.
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        FamilyReviewCard(SafetyReviewItem("private-id", "emergency", now, 1, 1, null, null), {})
                    }
                }
            }
        }
        composeRule.onNodeWithText("SMS 전달 확인: 0/1명", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("이 단말에 기록한 메모:", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun baselineSurvivesLiveUpdatesRefreshAndSavedStateRestoration() {
        val restoration = StateRestorationTester(composeRule)
        var current by mutableStateOf(report)
        var refreshes = 0
        restoration.setContent { MaterialTheme { FieldVisitCheckScreen(current, { refreshes++ }, {}, {}) } }
        val original = composeRule.onNodeWithTag("field_baseline").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        composeRule.runOnIdle {
            current = report.copy(diagnostics = report.diagnostics.copy(checkedAtMs = now + 60_000L,
                snapshot = report.diagnostics.snapshot.copy(lastActivityMs = now + 1_000L,
                    lastAutomaticActivityMs = now + 2_000L, lastHeartbeatMs = now + 3_000L)),
                actualSms = SmsObservation(false, SmsObservationState.SENT, now + 4_000L))
        }
        composeRule.onNodeWithTag("field_baseline").assertTextEquals(original)
        composeRule.onNodeWithTag("field_current").assertTextContains(fieldVisitTime(now + 2_000L), substring = true)
        composeRule.onNodeWithTag("field_refresh").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("field_baseline").assertTextEquals(original)
        composeRule.onNodeWithTag("field_refresh_count").assertTextContains("1회", substring = true)
        composeRule.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun manualLabelsAndTestCounterDoNotChangeSafetyStateOrSendAnything() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitoring = MonitoringStore(context)
        monitoring.beginStart(now, elapsedRealtimeMs = 1_000L)
        val dispatch = SmsDispatchStore(context)
        val eventId = "emergency:$now:1"
        dispatch.beginAttempt(eventId, 1)
        val preferences = context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE)
        val before = preferences.all.toMap()
        val smsBefore = dispatch.status(eventId)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { MaterialTheme { FieldVisitCheckScreen(report, {}, {}, {}) } }
        composeRule.onNodeWithTag("field_operator").performScrollTo().assertTextContains("미기록", substring = true).performClick()
        composeRule.onNodeWithTag("field_operator_STAFF_REPORTED").performClick()
        composeRule.onNodeWithTag("field_test_count").assertTextContains("0/7건", substring = true)
        composeRule.onNodeWithTag("field_test_INSTALLATION").performScrollTo().performClick()
        composeRule.onNodeWithTag("field_test_INSTALLATION_DIFFERENT").performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("field_operator").assertTextContains("직접 표시", substring = true)
        composeRule.onNodeWithTag("field_test_count").assertTextContains("1/7건", substring = true)
        composeRule.onNodeWithTag("field_preview").performScrollTo().performClick()
        composeRule.onNodeWithTag("field_share_text").assertTextContains("수동 선택", substring = true)
        composeRule.onNodeWithTag("field_share").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("field_close").performClick()
        composeRule.runOnIdle {
            assertEquals(before, preferences.all)
            assertEquals(smsBefore, dispatch.status(eventId))
            assertNull(org.robolectric.Shadows.shadowOf(context as android.app.Application).nextStartedActivity)
        }
    }

    @Test fun previewIsFrozenWhenLaterObservationsChangeAndCancelDoesNotUseClipboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("existing", "leave unchanged"))
        var current by mutableStateOf(report)
        composeRule.setContent { MaterialTheme { FieldVisitCheckScreen(current, {}, {}, {}) } }
        composeRule.onNodeWithTag("field_preview").performScrollTo().performClick()
        val original = composeRule.onNodeWithTag("field_share_text").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        composeRule.runOnIdle { current = report.copy(testSms = SmsObservation(true, SmsObservationState.DELIVERED, now)) }
        composeRule.onNodeWithTag("field_share_text").assertTextEquals(original)
        composeRule.onNodeWithTag("field_close").performClick()
        composeRule.runOnIdle { assertEquals("leave unchanged", clipboard.primaryClip!!.getItemAt(0).text.toString()) }
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land")
    fun manualLabelsAndCloseAreAccessibleInDarkLandscapeAtTwoHundredPercent() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme(colorScheme = darkColorScheme()) { FieldVisitCheckScreen(report, {}, {}, {}) }
            }
        }
        composeRule.onNodeWithTag("field_operator").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("field_operator_UNKNOWN").performClick()
        composeRule.onNodeWithTag("field_test_SOS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("field_preview").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("field_close").assertIsDisplayed()
    }

    @Test fun closingAndReopeningStartsANewInspectionRatherThanReusingOldLabels() {
        var current by mutableStateOf(report)
        var shown by mutableStateOf(true)
        composeRule.setContent { MaterialTheme {
            if (shown) FieldVisitCheckScreen(current, {}, {}, { shown = false })
        } }
        composeRule.onNodeWithTag("field_operator").performScrollTo().performClick()
        composeRule.onNodeWithTag("field_operator_STAFF_REPORTED").performClick()
        composeRule.onNodeWithTag("field_close").performClick()
        composeRule.runOnIdle {
            current = report.copy(testSms = SmsObservation(true, SmsObservationState.SENT, now))
            shown = true
        }
        composeRule.onNodeWithTag("field_operator").assertTextContains("미기록", substring = true)
        composeRule.onNodeWithTag("field_baseline").assertTextContains(SmsObservationState.SENT.label, substring = true)
        composeRule.onNodeWithTag("field_test_count").assertTextContains("0/7건", substring = true)
    }
}
