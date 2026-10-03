package com.example.monitoring

import android.app.Application
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.AppDatabase
import com.example.data.Contact
import com.example.data.MonitoringStore
import com.example.data.TestSmsVerificationState
import com.example.data.SafetyIncidentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SafetySmsRetryWorkerTest {
    private lateinit var context: Application

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        context.getSharedPreferences(SmsDispatchStore.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(context).clearAllTables()
        }
    }

    @Test
    fun testSmsWithoutCallbackEndsInFailureWithoutAnotherSend() = runBlocking {
        val eventId = "test:1000:1"
        val store = MonitoringStore(context)
        store.markTestSmsPending(1, eventId)
        val dispatch = SmsDispatchStore(context)
        dispatch.beginAttempt(eventId, 1, SmsRetryPolicy.ONE_SHOT, 1_000L)

        val next = SafetySmsRetryTask(context).run(eventId, 1_000L + SmsDispatchStore.CALLBACK_TIMEOUT_MS)

        assertNull(next)
        assertEquals(TestSmsVerificationState.FAILED, store.testSmsVerification.state)
        assertEquals(1, dispatch.status(eventId).attempt)
        assertEquals(SmsDispatchState.FAILED_FINAL, dispatch.status(eventId).state)
    }

    @Test
    fun recoveryWorkerExecutesAndReschedulesAPendingImmutableIncident() = runBlocking {
        SafetyIncidentRepository(AppDatabase.getDatabase(context)).getOrCreate(
            incidentId = "sos:5000",
            type = "sos",
            occurredAtMs = 5_000L,
            deviceAlias = "테스트 기기",
            message = "저장된 메시지",
            batteryPercent = 20,
            subscriptionId = 1,
            contacts = listOf(Contact(id = 5, name = "보호자", phoneNumber = "01012345678")),
            nowMs = 5_001L
        )
        val worker = TestListenableWorkerBuilder<SafetySmsRetryWorker>(context).build()

        val result = worker.startWork().get(5, TimeUnit.SECONDS)

        assertTrue(result is ListenableWorker.Result.Success)
        val scheduled = WorkManager.getInstance(context)
            .getWorkInfosByTag(SafetySmsRetryWorker.WORK_TAG)
            .get(3, TimeUnit.SECONDS)
        assertTrue(scheduled.isNotEmpty())
    }

    @Test
    fun failedDailySmsDoesNotRetryAfterAutomaticActivityConfirmation() = runBlocking {
        val store = MonitoringStore(context)
        val due = store.configureDailyCheckIn(18, 1_700_000_000_000L)
        store.beginStart(due - 60_000L, elapsedRealtimeMs = due - 60_000L)
        store.markDailyCheckInPrompted(due, due)
        val event = "daily:$due:1"
        val incidents = SafetyIncidentRepository(AppDatabase.getDatabase(context))
        incidents.getOrCreate("daily:$due", "daily", due, "test", "test", null, 1,
            listOf(Contact(id = 1, name = "guardian", phoneNumber = "01012345678")))
        val failedAt = due + 2 * 60 * 60 * 1_000L
        val dispatch = SmsDispatchStore(context)
        val attempt = dispatch.beginAttempt(event, 1, nowMs = failedAt)!!
        dispatch.markQueueFailure(event, attempt, android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE, failedAt)
        store.recordActivity(failedAt + 1_000L, "unlock", failedAt + 1_000L,
            elapsedRealtimeMs = failedAt + 1_000L)

        assertNull(SafetySmsRetryTask(context).run(event, failedAt + 5 * 60_000L))
        assertEquals(1, dispatch.status(event).attempt)
        assertTrue(incidents.get("daily:$due")!!.incident.completedAtMs != null)
    }

    @Test
    fun recoveryDoesNotStartAnUnsentInactivityAlertAfterNewActivity() = runBlocking {
        val store = MonitoringStore(context)
        store.beginStart(1_000L, elapsedRealtimeMs = 1_000L)
        val deadline = store.deadlineMs
        val incidents = SafetyIncidentRepository(AppDatabase.getDatabase(context))
        incidents.getOrCreate(
            "emergency:$deadline", "emergency", deadline, "test", "test", null, 1,
            listOf(Contact(id = 1, name = "guardian", phoneNumber = "01012345678"))
        )
        store.recordActivity(deadline + 1_000L, "unlock", deadline + 1_000L,
            elapsedRealtimeMs = deadline + 1_000L)

        assertNull(SafetySmsRetryTask(context).run("emergency:$deadline:1", deadline + 2_000L))
        assertEquals(0, SmsDispatchStore(context).status("emergency:$deadline:1").attempt)
        assertTrue(incidents.get("emergency:$deadline")!!.incident.completedAtMs != null)
    }

    @Test
    @Config(sdk = [28])
    fun recoveryPostsSuccessWithoutAForegroundService() = runBlocking {
        val incidents = SafetyIncidentRepository(AppDatabase.getDatabase(context))
        incidents.getOrCreate("sos:1000", "sos", 1_000L, "test", "test", null, 1,
            listOf(Contact(id = 1, name = "guardian", phoneNumber = "01012345678")))
        val eventId = "sos:1000:1"
        val dispatch = SmsDispatchStore(context)
        val attempt = dispatch.beginAttempt(eventId, 1, nowMs = 1_001L)!!
        dispatch.recordCallback(SmsCallbackStage.SENT, eventId, attempt, 0, 1, Activity.RESULT_OK, 2_000L)

        assertNull(SafetySmsRetryTask(context).run(eventId, 2_001L))

        val notification = context.getSystemService(NotificationManager::class.java)
            .activeNotifications.single { it.id == SafetySmsStatusNotifier.NOTIFICATION_ID }.notification
        assertEquals("SOS 문자 발송 확인", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(incidents.get("sos:1000")!!.incident.completedAtMs != null)
        assertEquals("", incidents.get("sos:1000")!!.recipients.single().phoneNumber)
    }

    @Test
    @Config(sdk = [28])
    fun recoveryPostsFinalFailureWithoutAForegroundService() = runBlocking {
        val incidents = SafetyIncidentRepository(AppDatabase.getDatabase(context))
        incidents.getOrCreate("sos:1000", "sos", 1_000L, "test", "test", null, 1,
            listOf(Contact(id = 1, name = "guardian", phoneNumber = "01012345678")))
        val eventId = "sos:1000:1"
        val dispatch = SmsDispatchStore(context)
        var nowMs = 2_000L
        repeat(SmsDispatchStore.MAX_ATTEMPTS) {
            val attempt = dispatch.beginAttempt(eventId, 1, nowMs = nowMs)!!
            dispatch.markQueueFailure(eventId, attempt, android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE, nowMs)
            nowMs += SmsDispatchStore.RETRY_DELAY_MS
        }

        assertNull(SafetySmsRetryTask(context).run(eventId, nowMs))

        val notification = context.getSystemService(NotificationManager::class.java)
            .activeNotifications.single { it.id == SafetySmsStatusNotifier.NOTIFICATION_ID }.notification
        assertEquals("SOS 문자 발송 확인 필요", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)!!.contains("모두"))
        assertTrue(incidents.get("sos:1000")!!.incident.completedAtMs != null)
        assertEquals(SmsDispatchState.FAILED_FINAL, dispatch.status(eventId).state)
    }

    @Test
    fun clockRebaseDoesNotCancelAnUnsentIncidentForTheSameActivityCycle() = runBlocking {
        val store = MonitoringStore(context)
        store.beginStart(1_000L, elapsedRealtimeMs = 1_000L)
        val eventMs = store.inactivityEventMs
        val incidents = SafetyIncidentRepository(AppDatabase.getDatabase(context))
        incidents.getOrCreate(
            "emergency:$eventMs", "emergency", eventMs, "test", "test", null, 1,
            listOf(Contact(id = 1, name = "guardian", phoneNumber = "01012345678"))
        )
        store.rebaseAfterWallClockChange(3_601_000L, elapsedRealtimeMs = 1_000L)

        // With SEND_SMS unavailable, the same incident remains pending for recovery.
        val nextRun = SafetySmsRetryTask(context).run("emergency:$eventMs:1", 3_601_000L)

        assertEquals(eventMs, store.inactivityEventMs)
        assertTrue(store.deadlineMs != eventMs)
        assertTrue(nextRun != null)
        assertNull(incidents.get("emergency:$eventMs")!!.incident.completedAtMs)
    }
}
