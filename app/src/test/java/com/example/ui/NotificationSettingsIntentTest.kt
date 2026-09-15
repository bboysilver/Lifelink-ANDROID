package com.example.ui

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotificationSettingsIntentTest {
    @Test
    @Config(sdk = [23, 25])
    fun olderDevicesUseSupportedAppDetailsScreen() {
        val intent = notificationSettingsIntent("com.bboysilver.lifelink")
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:com.bboysilver.lifelink", intent.data.toString())
    }

    @Test
    @Config(sdk = [26, 36])
    fun newerDevicesOpenAppNotificationSettings() {
        val intent = notificationSettingsIntent("com.bboysilver.lifelink")
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals("com.bboysilver.lifelink", intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }
}
