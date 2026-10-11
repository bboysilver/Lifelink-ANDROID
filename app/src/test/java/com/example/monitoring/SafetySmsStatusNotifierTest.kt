package com.example.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SafetySmsStatusNotifierTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
    }

    @Test
    fun pendingAndEmptyBatchesDoNotClaimCompletion() {
        SafetySmsStatusNotifier.showCompletion(context, SafetySmsEventType.SOS, emptyList(), "sos:1000")
        SafetySmsStatusNotifier.showCompletion(
            context, SafetySmsEventType.SOS,
            listOf(status(SmsDispatchState.SENT), status(SmsDispatchState.FAILED_RETRYABLE)), "sos:1000"
        )

        assertNull(notification())
    }

    @Test
    fun confirmedSendAndDeliveryAreReportedAsSendConfirmation() {
        SafetySmsStatusNotifier.showCompletion(
            context, SafetySmsEventType.EMERGENCY,
            listOf(status(SmsDispatchState.SENT), status(SmsDispatchState.DELIVERED)), "emergency:1000"
        )

        val notification = checkNotNull(notification())
        assertEquals("긴급 문자 발송 확인", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("모든 보호자 문자 발송이 확인되었습니다.", notification.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(SafetyNotificationCapability.STATUS_CHANNEL_ID, notification.channelId)
        assertNull(notification.sound)
        assertNull(notification.vibrate)
        assertEquals(NotificationManager.IMPORTANCE_LOW,
            manager.getNotificationChannel(notification.channelId).importance)
        assertEquals(SafetySmsStatusNotifier.CONFIRMED_NOTIFICATION_ID, manager.activeNotifications.single().id)
    }

    @Test
    fun partialFailureNamesTheNumberOfUnconfirmedRecipients() {
        SafetySmsStatusNotifier.showCompletion(
            context, SafetySmsEventType.DAILY,
            listOf(status(SmsDispatchState.SENT), status(SmsDispatchState.FAILED_FINAL)), "daily:1000"
        )

        val notification = checkNotNull(notification())
        assertEquals("안부 미응답 문자 일부 발송 미확인", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("보호자 2명 중 1명의 문자 발송을 확인하지 못했습니다. 직접 연락해 주세요.",
            notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(SafetyNotificationCapability.ALERT_CHANNEL_ID, notification.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH,
            manager.getNotificationChannel(notification.channelId).importance)
    }

    @Test
    fun allFailedIsNotReportedAsPartialFailure() {
        SafetySmsStatusNotifier.showCompletion(
            context, SafetySmsEventType.SOS,
            listOf(status(SmsDispatchState.FAILED_FINAL), status(SmsDispatchState.FAILED_FINAL)), "sos:1000"
        )

        val notification = checkNotNull(notification())
        assertEquals("SOS 문자 발송 확인 필요", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("보호자 2명 모두 문자 발송을 확인하지 못했습니다. 직접 연락해 주세요.",
            notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun blockedSafetyChannelDoesNotPostOrGetReenabled() {
        manager.createNotificationChannel(NotificationChannel(
            SafetyNotificationCapability.ALERT_CHANNEL_ID, "안전 확인 알림", NotificationManager.IMPORTANCE_NONE
        ))

        SafetySmsStatusNotifier.showCompletion(
            context, SafetySmsEventType.SOS, listOf(status(SmsDispatchState.FAILED_FINAL)), "sos:1000"
        )

        assertNull(notification())
        assertEquals(NotificationManager.IMPORTANCE_NONE,
            manager.getNotificationChannel(SafetyNotificationCapability.ALERT_CHANNEL_ID).importance)
    }

    @Test
    fun sameIncidentUpdatesOnceButANewFailureKeepsItsOwnAlert() {
        val failed = listOf(status(SmsDispatchState.FAILED_FINAL))
        SafetySmsStatusNotifier.showCompletion(context, SafetySmsEventType.SOS, failed, "sos:1000")
        SafetySmsStatusNotifier.showCompletion(context, SafetySmsEventType.SOS, failed, "sos:1000")
        assertEquals(1, manager.activeNotifications.size)
        SafetySmsStatusNotifier.showCompletion(context, SafetySmsEventType.SOS, failed, "sos:2000")
        assertEquals(setOf("sos:1000", "sos:2000"), manager.activeNotifications.map { it.tag }.toSet())
    }

    @Test
    @Config(sdk = [24])
    fun completionIsSilentOnDevicesWithoutNotificationChannels() {
        SafetySmsStatusNotifier.showCompletion(context, SafetySmsEventType.SOS,
            listOf(status(SmsDispatchState.SENT)), "sos:1000")
        val notification = checkNotNull(notification())
        assertEquals(Notification.PRIORITY_LOW, notification.priority)
        assertEquals(0, notification.defaults and (Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE))
        assertNull(notification.sound)
        assertNull(notification.vibrate)
    }

    private fun notification(): Notification? = manager.activeNotifications.firstOrNull()?.notification

    private fun status(state: SmsDispatchState) = SmsDispatchStatus(state, 3, 3, 1_000L, 0L, 0)
}
