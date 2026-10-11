package com.example.ui

import android.Manifest
import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.Contact
import com.example.data.TestSmsVerification
import com.example.data.TestSmsVerificationState
import com.example.monitoring.SmsLine
import com.example.monitoring.SmsSetupState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SafetyPermissionsUiTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var context: Application

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).denyPermissions(Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun smsSwitchUpdatesWhileOtherPermissionsAreStillMissing() {
        composeRule.setContent {
            var permissions by remember { mutableStateOf(SafetyPermissions.read(context)) }
            MaterialTheme {
                SetupWizardDialog(
                    step = SetupStep.PERMISSIONS,
                    smsSetupState = SmsSetupState.Ready(SmsLine(1, 0, "SIM 1")),
                    contacts = listOf(Contact(1, "보호자", "01012345678")),
                    testSmsVerification = TestSmsVerification(TestSmsVerificationState.NOT_SENT, -1, "", ""),
                    monitorHours = 12,
                    smsGranted = permissions.sms,
                    phoneGranted = permissions.phone,
                    activityGranted = permissions.activity,
                    notificationGranted = permissions.notification,
                    onRefreshDevice = {}, onSelectSmsLine = {}, onAddContact = { _, _ -> },
                    onRequestSms = {
                        shadowOf(context).grantPermissions(Manifest.permission.SEND_SMS)
                        permissions = SafetyPermissions.read(context)
                    },
                    onRequestPhone = {}, onRequestActivity = {}, onRequestNotification = {},
                    onSendTestSms = {}, onSetHours = {}, onAdvance = {}, onComplete = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("4. 보호자 SMS 발송 권한 허용 필요")
            .performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithContentDescription("4. 보호자 SMS 발송 권한 허용됨").assertIsOn()
        composeRule.onNodeWithContentDescription("3. SIM 상태 확인 권한 허용 필요").assertIsOff()
        assertFalse(SafetyPermissions.read(context).allGranted)
    }

    @Test fun settingsGrantAndRevocationChangeTheIndividualSnapshot() {
        val denied = SafetyPermissions.read(context)
        shadowOf(context).grantPermissions(Manifest.permission.SEND_SMS)
        val granted = SafetyPermissions.read(context)
        assertNotEquals(denied, granted)
        assertFalse(denied.allGranted)
        assertFalse(granted.allGranted)
        shadowOf(context).denyPermissions(Manifest.permission.SEND_SMS)
        assertFalse(SafetyPermissions.read(context).sms)
    }
}
