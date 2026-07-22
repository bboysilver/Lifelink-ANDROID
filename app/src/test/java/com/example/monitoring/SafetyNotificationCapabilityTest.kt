package com.example.monitoring

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyNotificationCapabilityTest {
    @Test
    fun permissionAppAndChannelMustAllAllowNotifications() {
        assertTrue(SafetyNotificationCapability.isAvailable(true, true, null))
        assertFalse(SafetyNotificationCapability.isAvailable(false, true, null))
        assertFalse(SafetyNotificationCapability.isAvailable(true, false, null))
        assertFalse(
            SafetyNotificationCapability.isAvailable(
                true,
                true,
                NotificationManager.IMPORTANCE_NONE
            )
        )
    }
}