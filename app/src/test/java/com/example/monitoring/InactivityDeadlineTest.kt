package com.example.monitoring

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.Notification
import androidx.core.app.NotificationCompat
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Contact
import com.example.data.DeadlineCalculator
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import com.example.data.SafetyIncidentRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class InactivityDeadlineTest {
    private lateinit var context: Application
    private lateinit var store: MonitoringStore
    private lateinit var db: AppDatabase
    private lateinit var incidents: SafetyIncidentRepository
    private lateinit var task: InactivityDeadlineTask
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("lifelink_monitoring", "inactivity_alarm", SmsDispatchStore.FILE_NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        store = MonitoringStore(context)
        store.completeSetup()
        store.beginStart(nowMs = now, elapsedRealtimeMs = 0L)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        incidents = SafetyIncidentRepository(db)
        task = InactivityDeadlineTask(context, store, LifeLinkRepository(db), incidents)
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun serviceAndAlarmShareOnePreAlertAndThenScheduleTheDeadline() = runBlocking {
        val scheduler = InactivityDeadlineScheduler(context)
        scheduler.ensureScheduled(now)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val warningAt = store.deadlineMs - DeadlineCalculator.PRE_ALERT_SECONDS * 1_000L
        assertEquals(warningAt, alarms.scheduledAlarms.single().triggerAtTime)
        task.run(store.deadlineMs, warningAt, warningAt - now)
        task.run(store.deadlineMs, warningAt + 1_000L, warningAt + 1_000L - now)
        assertTrue(store.wasPreAlerted(store.deadlineMs))
        assertEquals(store.deadlineMs, alarms.scheduledAlarms.single().triggerAtTime)
    }

    @Test
    fun stoppingCancelsWakeupAndStaleTaskCannotSend() = runBlocking {
        val deadline = store.deadlineMs
        InactivityDeadlineScheduler(context).ensureScheduled(now)
        store.stop(now + 1_000L)
        task.run(deadline, deadline + 1_000L, deadline + 1_000L - now)
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        assertNull(incidents.get("emergency:$deadline"))
    }

    @Test
    fun resetMakesAnOldAlarmHarmless() = runBlocking {
        val old = store.deadlineMs
        store.resetDeadline(now + 60_000L, "safe", elapsedRealtimeMs = 60_000L)
        task.run(old, old + 1_000L, old + 1_000L - now)
        assertFalse(store.wasPreAlerted(store.deadlineMs))
        assertNull(incidents.get("emergency:$old"))
    }

    @Test
    fun activityBetweenSnapshotAndDispatchCannotAttachAnExpiredDeadlineToTheNewCycle() = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, true)
        shadowOf(context.getSystemService(TelephonyManager::class.java)).setIsSmsCapable(true)
        shadowOf(context.getSystemService(SubscriptionManager::class.java)).setActiveSubscriptionInfos(
            SubscriptionInfoBuilder.newBuilder().setId(1).setSimSlotIndex(0).setDisplayName("SIM").buildSubscriptionInfo()
        )
        db.contactDao().insertContactIfAllowed(Contact(name = "guardian", phoneNumber = "01012345678"))
        val oldEvent = store.inactivityEventMs
        val checkAt = store.deadlineMs + 1_000L
        val racingTask = InactivityDeadlineTask(context, store, LifeLinkRepository(db), incidents,
            readSnapshot = { at ->
                store.snapshot(at).also { store.recordActivity(at, "unlock", at, at - now) }
            })

        racingTask.run(nowMs = checkAt, elapsedRealtimeMs = checkAt - now)

        assertNotEquals(oldEvent, store.inactivityEventMs)
        assertNull(incidents.get("emergency:$oldEvent"))
        assertNull(incidents.get("emergency:${store.inactivityEventMs}"))
        assertFalse(store.wasEmergencyDispatched(store.inactivityEventMs))
        assertTrue(store.snapshot(checkAt).remainingSeconds > 0L)
    }

    @Test
    fun confirmedSmsAtDeadlineIsFinalizedWithoutSendingAgain() = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.SEND_SMS)
        val deadline = store.deadlineMs
        val eventId = "emergency:$deadline:1"
        incidents.getOrCreate(
            "emergency:$deadline", "emergency", deadline, "test", "test", 30, 1,
            listOf(Contact(id = 1, name = "guardian", phoneNumber = "01012345678"))
        )
        val dispatch = SmsDispatchStore(context)
        val attempt = dispatch.beginAttempt(eventId, 1)!!
        dispatch.recordCallback(SmsCallbackStage.SENT, eventId, attempt, 0, 1, Activity.RESULT_OK)
        task.run(deadline, deadline, deadline - now)
        assertTrue(store.wasEmergencyDispatched(deadline))
        assertEquals(1, dispatch.status(eventId).attempt)
        assertNotNull(incidents.get("emergency:$deadline")!!.incident.completedAtMs)
        task.run(deadline, deadline + 6 * 3_600_000L, deadline + 6 * 3_600_000L - now)
        task.run(deadline, deadline + 12 * 3_600_000L, deadline + 12 * 3_600_000L - now)
        assertEquals(1, dispatch.status(eventId).attempt)
        assertEquals(deadline, store.deadlineMs)
        assertEquals(now, store.snapshot().lastActivityMs)
        assertNull(incidents.get("emergency:${deadline + 6 * 3_600_000L}"))
        assertTrue(store.snapshot(deadline + 12 * 3_600_000L).inactivityAlertHandled)
    }

    @Test
    fun completedAlarmIsCancelledAndOnlyNewActivitySchedulesTheNextCycle() {
        val scheduler = InactivityDeadlineScheduler(context)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        scheduler.ensureScheduled(now)
        assertFalse(alarms.scheduledAlarms.isEmpty())
        store.markEmergency(store.inactivityEventMs)
        assertTrue(alarms.scheduledAlarms.isEmpty())
        scheduler.ensureScheduled(store.deadlineMs + 6 * 3_600_000L)
        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertTrue(store.recordActivity(now + 1_000L, "unlock", now + 1_000L, 1_000L))
        assertFalse(alarms.scheduledAlarms.isEmpty())
        assertFalse(store.snapshot(now + 1_000L).inactivityAlertHandled)
    }

    @Test
    fun safeActionWorksWithoutStartingAForegroundService() {
        store.beginStart(nowMs = System.currentTimeMillis() - 1_000L)
        val deadline = store.deadlineMs
        InactivityDeadlineReceiver().onReceive(context,
            Intent(InactivityDeadlineReceiver.ACTION_SAFE)
                .putExtra(InactivityDeadlineScheduler.EXTRA_DEADLINE, deadline)
        )
        assertNotEquals(deadline, store.deadlineMs)
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun automaticActivityRemovesOnlyItsWarningAndStaleWorkCannotRestoreIt() = runBlocking {
        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = store.deadlineMs
        val warningAt = deadline - DeadlineCalculator.PRE_ALERT_SECONDS * 1_000L
        task.run(deadline, warningAt, warningAt - now)
        assertNotNull(manager.activeNotifications.single { it.id == InactivityDeadlineTask.PRE_ALERT_NOTIFICATION_ID })
        manager.notify(SafetySmsStatusNotifier.NOTIFICATION_ID,
            NotificationCompat.Builder(context, SafetyNotificationCapability.ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("SOS failure").build())

        store.recordActivity(warningAt + 1_000L, "unlock", warningAt + 1_000L, warningAt + 1_000L - now)
        task.run(deadline, warningAt + 2_000L, warningAt + 2_000L - now)

        assertEquals(listOf(SafetySmsStatusNotifier.NOTIFICATION_ID), manager.activeNotifications.map { it.id })
        var stalePosted = false
        assertFalse(store.markPreAlert(deadline) { stalePosted = true; true })
        assertFalse(stalePosted)
    }

    @Test
    fun preAlertIsImportantOnceAndStoppingRemovesIt() = runBlocking {
        val manager = context.getSystemService(NotificationManager::class.java)
        val warningAt = store.deadlineMs - DeadlineCalculator.PRE_ALERT_SECONDS * 1_000L
        task.run(store.deadlineMs, warningAt, warningAt - now)
        val warning = manager.activeNotifications.single().notification
        assertEquals(SafetyNotificationCapability.ALERT_CHANNEL_ID, warning.channelId)
        assertTrue(warning.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        task.run(store.deadlineMs, warningAt + 1_000L, warningAt + 1_000L - now)
        assertEquals(1, manager.activeNotifications.size)
        store.stop(warningAt + 2_000L)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun activityAlsoClearsAnOldVersionWarningWithoutClearingTaggedSmsFailures() {
        val manager = context.getSystemService(NotificationManager::class.java)
        fun notification(title: String) = NotificationCompat.Builder(context, SafetyNotificationCapability.ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).build()
        manager.notify(1002, notification("안전 확인이 필요합니다"))
        manager.notify("sos:1000", 1002, notification("SOS 문자 발송 확인 필요"))
        store.recordActivity(now + 1_000L, "unlock", now + 1_000L, 1_000L)
        assertEquals(listOf("sos:1000"), manager.activeNotifications.map { it.tag })
    }

    @Test
    @Config(sdk = [24])
    fun legacyPreAlertRequestsSoundAndVibration() = runBlocking {
        val warningAt = store.deadlineMs - DeadlineCalculator.PRE_ALERT_SECONDS * 1_000L
        task.run(store.deadlineMs, warningAt, warningAt - now)
        val warning = context.getSystemService(NotificationManager::class.java).activeNotifications.single().notification
        val alertDefaults = Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE
        assertEquals(alertDefaults, warning.defaults and alertDefaults)
    }
}
