package com.example.monitoring

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuietNotificationChannelTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
    }

    @Test
    fun upgradeUsesNewUnbadgedChannelWithoutChangingExistingChannels() {
        manager.createNotificationChannel(NotificationChannel(
            "lifelink_monitoring", "legacy", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(true) })
        SafetyNotificationCapability.createMonitoringChannel(context)

        assertTrue(manager.getNotificationChannel("lifelink_monitoring").canShowBadge())
        assertFalse(manager.getNotificationChannel(SafetyNotificationCapability.MONITORING_CHANNEL_ID).canShowBadge())
    }

    @Test
    fun migrationPreservesBlockedMonitoringAndCompletionChannels() {
        listOf("lifelink_monitoring", "lifelink_safety_status").forEach { id ->
            manager.createNotificationChannel(NotificationChannel(id, "blocked", NotificationManager.IMPORTANCE_NONE))
        }
        SafetyNotificationCapability.createMonitoringChannel(context)
        SafetyNotificationCapability.createStatusChannel(context)

        listOf(SafetyNotificationCapability.MONITORING_CHANNEL_ID, SafetyNotificationCapability.STATUS_CHANNEL_ID)
            .forEach { id ->
                assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(id).importance)
                assertFalse(manager.getNotificationChannel(id).canShowBadge())
            }
    }

    @Test
    fun cleanupRemovesOldSuccessBadgesButKeepsFailuresAndPreWarnings() {
        fun notification(channel: String) = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info).build()
        manager.notify("emergency:1", SafetySmsStatusNotifier.CONFIRMED_NOTIFICATION_ID,
            notification("lifelink_safety_status"))
        manager.notify("sos:2", SafetySmsStatusNotifier.NOTIFICATION_ID,
            notification(SafetyNotificationCapability.ALERT_CHANNEL_ID))
        manager.notify(InactivityDeadlineTask.PRE_ALERT_NOTIFICATION_ID,
            notification(SafetyNotificationCapability.ALERT_CHANNEL_ID))

        SafetyNotificationCapability.cancelLegacyConfirmedNotifications(context)

        assertEquals(setOf(SafetySmsStatusNotifier.NOTIFICATION_ID, InactivityDeadlineTask.PRE_ALERT_NOTIFICATION_ID),
            manager.activeNotifications.map { it.id }.toSet())
    }
}
