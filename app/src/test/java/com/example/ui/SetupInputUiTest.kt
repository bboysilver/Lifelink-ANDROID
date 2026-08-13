package com.example.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.example.data.TestSmsVerification
import com.example.data.TestSmsVerificationState
import com.example.monitoring.SmsLine
import com.example.monitoring.SmsSetupState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SetupInputUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun koreanNameAndPhoneDigitsCanBeEnteredWithoutInputRewrites() {
        composeRule.setContent {
            MaterialTheme {
                SetupWizardDialog(
                    step = SetupStep.CONTACT,
                    smsSetupState = SmsSetupState.Ready(
                        SmsLine(subscriptionId = 1, slotIndex = 0, label = "SIM 1")
                    ),
                    contacts = emptyList(),
                    testSmsVerification = TestSmsVerification(
                        state = TestSmsVerificationState.NOT_SENT,
                        contactId = -1,
                        eventId = "",
                        message = ""
                    ),
                    monitorHours = 12,
                    smsGranted = false,
                    phoneGranted = false,
                    activityGranted = false,
                    notificationGranted = false,
                    onRefreshDevice = {},
                    onSelectSmsLine = {},
                    onAddContact = { _, _ -> },
                    onRequestSms = {},
                    onRequestPhone = {},
                    onRequestActivity = {},
                    onRequestNotification = {},
                    onSendTestSms = {},
                    onSetHours = {},
                    onAdvance = {},
                    onComplete = {}
                )
            }
        }

        val nameField = composeRule.onNodeWithText("보호자 이름")
        val phoneField = composeRule.onNodeWithText("전화번호")
        nameField.performTextInput("홍길동")
        nameField.assertTextContains("홍길동")
        phoneField.performTextInput("01012345678")
        phoneField.assertTextContains("01012345678")
    }
}
