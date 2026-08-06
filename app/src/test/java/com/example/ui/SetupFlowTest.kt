package com.example.ui

import com.example.data.TestSmsVerificationState
import com.example.monitoring.SmsLine
import com.example.monitoring.SmsSetupIssue
import com.example.monitoring.SmsSetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupFlowTest {
    @Test
    fun unsupportedDeviceExplainsWhySosCannotRun() {
        val reason = SafetyReadiness.sosBlockReason(
            smsSetupState = SmsSetupState.Blocked(SmsSetupIssue.UNSUPPORTED_DEVICE),
            smsPermissionGranted = false,
            hasEmergencyContacts = true
        )

        assertEquals(
            "Wi-Fi 전용 기기에서는 SOS 문자를 보낼 수 없습니다. SMS가 가능한 기기를 사용해 주세요.",
            reason
        )
    }

    @Test
    fun monitoringRequiresSuccessfulTestSms() {
        val reason = SafetyReadiness.monitoringBlockReason(
            smsSetupState = readyState(),
            smsPermissionGranted = true,
            phonePermissionGranted = true,
            activityPermissionGranted = true,
            notificationPermissionGranted = true,
            hasEmergencyContacts = true,
            testSmsState = TestSmsVerificationState.FAILED
        )

        assertEquals("보호자 시험 문자 발송을 확인한 뒤 모니터링을 시작해 주세요.", reason)
    }

    @Test
    fun allVerifiedRequirementsAllowMonitoring() {
        assertNull(
            SafetyReadiness.monitoringBlockReason(
                smsSetupState = readyState(),
                smsPermissionGranted = true,
                phonePermissionGranted = true,
                activityPermissionGranted = true,
                notificationPermissionGranted = true,
                hasEmergencyContacts = true,
                testSmsState = TestSmsVerificationState.SUCCESS
            )
        )
    }

    private fun readyState(): SmsSetupState = SmsSetupState.Ready(
        SmsLine(subscriptionId = 1, slotIndex = 0, label = "SIM 1")
    )
    @Test
    fun legacySetupStepsCollapseIntoThreeOnboardingPages() {
        assertEquals(OnboardingPage.GUIDE, SetupStep.DEVICE.onboardingPage())
        assertEquals(OnboardingPage.GUIDE, SetupStep.INTRO.onboardingPage())
        assertEquals(OnboardingPage.PROTECTOR, SetupStep.CONTACT.onboardingPage())
        assertEquals(OnboardingPage.PROTECTOR, SetupStep.PERMISSIONS.onboardingPage())
        assertEquals(OnboardingPage.PROTECTOR, SetupStep.TEST_SMS.onboardingPage())
        assertEquals(OnboardingPage.MONITORING, SetupStep.MONITORING.onboardingPage())
    }
}
