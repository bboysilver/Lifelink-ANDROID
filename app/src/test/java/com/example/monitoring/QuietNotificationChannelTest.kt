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

    @Test
    fun allSafetyChannelsIncludingFailuresDisableBadges() {
        SafetyNotificationCapability.createMonitoringChannel(context)
        SafetyNotificationCapability.createStatusChannel(context)
        SafetyNotificationCapability.createAlertChannel(context)
        listOf(SafetyNotificationCapability.MONITORING_CHANNEL_ID,
            SafetyNotificationCapability.STATUS_CHANNEL_ID, SafetyNotificationCapability.ALERT_CHANNEL_ID)
            .forEach { assertFalse(manager.getNotificationChannel(it).canShowBadge()) }
        assertEquals(NotificationManager.IMPORTANCE_HIGH,
            manager.getNotificationChannel(SafetyNotificationCapability.ALERT_CHANNEL_ID).importance)
    }

    @Test
    fun importantLegacyAlertsAreMovedWithoutLosingContentOrActions() {
        val legacyId = "lifelink_safety_alerts"
        manager.createNotificationChannel(NotificationChannel(legacyId, "legacy", NotificationManager.IMPORTANCE_HIGH)
            .apply { setSound(null, null); enableVibration(false); setShowBadge(true) })
        val action = android.app.PendingIntent.getActivity(context, 1, com.example.MainActivity.openIntent(context),
            android.app.PendingIntent.FLAG_IMMUTABLE)
        manager.notify("sos:1", 1002, NotificationCompat.Builder(context, legacyId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SOS 문자 발송 확인 필요").setContentText("직접 연락해 주세요.")
            .addAction(0, "앱 열기", action).build())

        SafetyNotificationCapability.createAlertChannel(context)

        val moved = manager.activeNotifications.single()
        assertEquals("sos:1", moved.tag)
        assertEquals(1002, moved.id)
        assertEquals(SafetyNotificationCapability.ALERT_CHANNEL_ID, moved.notification.channelId)
        assertEquals("SOS 문자 발송 확인 필요", moved.notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals(1, moved.notification.actions.size)
        val channel = manager.getNotificationChannel(moved.notification.channelId)
        assertFalse(channel.canShowBadge())
        assertFalse(channel.shouldVibrate())
        org.junit.Assert.assertNull(channel.sound)
    }

    @Test
    fun blockedLegacySafetyChannelRemainsBlockedBeforeAndAfterMigration() {
        manager.createNotificationChannel(NotificationChannel("lifelink_safety_alerts", "blocked", NotificationManager.IMPORTANCE_NONE))
        assertFalse(SafetyNotificationCapability.canPost(context))
        SafetyNotificationCapability.createAlertChannel(context)
        assertFalse(SafetyNotificationCapability.canPost(context))
        assertEquals(NotificationManager.IMPORTANCE_NONE,
            manager.getNotificationChannel(SafetyNotificationCapability.ALERT_CHANNEL_ID).importance)
    }
}
