package com.example.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsCapabilityResolverTest {
    @Test
    fun android7Through12UseTheLegacyTelephonyFeature() {
        listOf(24, 28, 31, 32).forEach { sdk ->
            assertTrue(
                "SDK $sdk should support SMS with legacy telephony",
                SmsCapabilityResolver.isSupported(
                    sdkInt = sdk,
                    hasTelephonyFeature = true,
                    hasMessagingFeature = false,
                    telephonySmsCapable = true
                )
            )
        }
    }

    @Test
    fun android13AndLaterRequireTheMessagingFeature() {
        listOf(33, 35, 36).forEach { sdk ->
            assertFalse(
                SmsCapabilityResolver.isSupported(
                    sdkInt = sdk,
                    hasTelephonyFeature = true,
                    hasMessagingFeature = false,
                    telephonySmsCapable = true
                )
            )
            assertTrue(
                SmsCapabilityResolver.isSupported(
                    sdkInt = sdk,
                    hasTelephonyFeature = false,
                    hasMessagingFeature = true,
                    telephonySmsCapable = true
                )
            )
        }
    }

    @Test
    fun telephonyManagerMustAlsoReportSmsCapability() {
        assertFalse(
            SmsCapabilityResolver.isSupported(
                sdkInt = 36,
                hasTelephonyFeature = true,
                hasMessagingFeature = true,
                telephonySmsCapable = false
            )
        )
    }
}