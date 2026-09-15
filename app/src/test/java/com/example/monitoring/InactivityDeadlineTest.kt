package com.example.monitoring

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
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
        store.beginStart(nowMs = now)
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
        task.run(store.deadlineMs, warningAt)
        task.run(store.deadlineMs, warningAt + 1_000L)
        assertTrue(store.wasPreAlerted(store.deadlineMs))
        assertEquals(store.deadlineMs, alarms.scheduledAlarms.single().triggerAtTime)
    }

    @Test
    fun stoppingCancelsWakeupAndStaleTaskCannotSend() = runBlocking {
        val deadline = store.deadlineMs
        InactivityDeadlineScheduler(context).ensureScheduled(now)
        store.stop(now + 1_000L)
        task.run(deadline, deadline + 1_000L)
        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        assertNull(incidents.get("emergency:$deadline"))
    }

    @Test
    fun resetMakesAnOldAlarmHarmless() = runBlocking {
        val old = store.deadlineMs
        store.resetDeadline(now + 60_000L, "safe")
        task.run(old, old + 1_000L)
        assertFalse(store.wasPreAlerted(store.deadlineMs))
        assertNull(incidents.get("emergency:$old"))
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
        task.run(deadline, deadline)
        assertTrue(store.wasEmergencyDispatched(deadline))
        assertEquals(1, dispatch.status(eventId).attempt)
        assertNotNull(incidents.get("emergency:$deadline")!!.incident.completedAtMs)
    }

    @Test
    fun safeActionWorksWithoutStartingAForegroundService() {
        val deadline = store.deadlineMs
        InactivityDeadlineReceiver().onReceive(context,
            Intent(InactivityDeadlineReceiver.ACTION_SAFE)
                .putExtra(InactivityDeadlineScheduler.EXTRA_DEADLINE, deadline)
        )
        assertNotEquals(deadline, store.deadlineMs)
        assertNull(shadowOf(context).nextStartedService)
    }
}
