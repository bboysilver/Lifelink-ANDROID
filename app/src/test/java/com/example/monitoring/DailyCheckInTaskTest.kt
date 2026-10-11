package com.example.monitoring

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Contact
import com.example.data.DailyCheckInCalculator
import com.example.data.DailyCheckInPhase
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class DailyCheckInTaskTest {
    private lateinit var context: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: LifeLinkRepository
    private lateinit var store: MonitoringStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE)
            .edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LifeLinkRepository(database)
        store = MonitoringStore(context)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                SafetyNotificationCapability.ALERT_CHANNEL_ID,
                "test",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun automaticActivityBeforeLateMorningWorkerPreventsDailySms() = runBlocking {
        val due = store.configureDailyCheckIn(18, 1_700_000_000_000L)
        store.beginStart(due - 60_000L, elapsedRealtimeMs = 0L)
        store.markDailyCheckInPrompted(due, due, elapsedRealtimeMs = 60_000L)
        store.recordActivity(due + 60_000L, "unlock", due + 60_000L, elapsedRealtimeMs = 120_000L)

        val result = DailyCheckInTask(context, store, repository)
            .run(due + 13 * 60 * 60 * 1_000L, elapsedRealtimeMs = 60_000L + 13 * 60 * 60 * 1_000L)

        assertEquals(null, result.nextRunAtMs)
        assertEquals("", store.dailyCheckInError)
        assertEquals(DailyCheckInPhase.UPCOMING, store.dailyCheckInStatus(due + 13 * 60 * 60 * 1_000L).phase)
    }

    @Test
    fun delayedPromptGetsAFullTwoHourResponseWindow() = runBlocking {
        val dueAtMs = store.configureDailyCheckIn(hour = 18, nowMs = 1_700_000_000_000L)
        val displayedAtMs = dueAtMs + 60 * 60 * 1_000L

        DailyCheckInTask(context, store, repository).run(displayedAtMs, elapsedRealtimeMs = 0L)

        assertEquals(
            displayedAtMs + DailyCheckInCalculator.RESPONSE_WINDOW_MS,
            store.dailyResponseDeadlineAtMs
        )
        assertEquals(
            DailyCheckInPhase.DUE,
            store.dailyCheckInStatus(dueAtMs + DailyCheckInCalculator.RESPONSE_WINDOW_MS, 60 * 60 * 1_000L).phase
        )
    }

    @Test
    fun overduePromptThatWasNeverDisplayedRestartsFromNow() = runBlocking {
        val dueAtMs = store.configureDailyCheckIn(hour = 18, nowMs = 1_700_000_000_000L)
        val recoveredAtMs = dueAtMs + DailyCheckInCalculator.RESPONSE_WINDOW_MS + 1L

        DailyCheckInTask(context, store, repository).run(recoveredAtMs)

        assertEquals(recoveredAtMs, store.dailyNextDueAtMs)
        assertEquals(
            recoveredAtMs + DailyCheckInCalculator.RESPONSE_WINDOW_MS,
            store.dailyResponseDeadlineAtMs
        )
    }

    @Test
    @Config(sdk = [33])
    fun revokedNotificationPermissionSkipsThePromptAndRecordsAnError() = runBlocking {
        val dueAtMs = store.configureDailyCheckIn(hour = 18, nowMs = 1_700_000_000_000L)

        DailyCheckInTask(context, store, repository).run(dueAtMs)

        assertTrue(store.dailyCheckInError.contains("알림"))
        assertTrue(store.dailyNextDueAtMs > dueAtMs)
    }

    @Test
    fun noContactsKeepsTheMissedCheckInPendingForRetry() = runBlocking {
        val dueAtMs = store.configureDailyCheckIn(hour = 18, nowMs = 1_700_000_000_000L)
        store.markDailyCheckInPrompted(dueAtMs, dueAtMs, 0L)
        val overdueAtMs = dueAtMs + DailyCheckInCalculator.RESPONSE_WINDOW_MS

        val result = DailyCheckInTask(context, store, repository).run(overdueAtMs, elapsedRealtimeMs = DailyCheckInCalculator.RESPONSE_WINDOW_MS)

        assertTrue(store.dailyCheckInError.contains("긴급 연락처"))
        assertTrue(result.nextRunAtMs != null && result.nextRunAtMs!! > overdueAtMs)
        assertEquals(dueAtMs, store.dailyNextDueAtMs)
    }

    @Test
    fun revokedSmsPermissionKeepsTheMissedCheckInPendingForRetry() = runBlocking {
        database.contactDao().insertContact(Contact(id = 1, name = "보호자", phoneNumber = "01012345678"))
        val dueAtMs = store.configureDailyCheckIn(hour = 18, nowMs = 1_700_000_000_000L)
        store.markDailyCheckInPrompted(dueAtMs, dueAtMs, 0L)
        val overdueAtMs = dueAtMs + DailyCheckInCalculator.RESPONSE_WINDOW_MS

        val result = DailyCheckInTask(context, store, repository).run(overdueAtMs, elapsedRealtimeMs = DailyCheckInCalculator.RESPONSE_WINDOW_MS)

        assertTrue(store.dailyCheckInError.contains("문자 권한"))
        assertTrue(result.nextRunAtMs != null)
        assertEquals(dueAtMs, store.dailyNextDueAtMs)
    }

    @Test
    fun recoveryRestoresDailyPromptSilentlyAndDisablingItRemovesIt() = runBlocking {
        val due = store.configureDailyCheckIn(18, 1_700_000_000_000L)
        val manager = context.getSystemService(NotificationManager::class.java)
        val task = DailyCheckInTask(context, store, repository)
        task.run(due, elapsedRealtimeMs = 0L)
        assertEquals(1, manager.activeNotifications.size)
        task.run(due + 1_000L, restorePrompt = true, elapsedRealtimeMs = 1_000L)
        val restored = manager.activeNotifications.single().notification
        assertTrue(restored.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(Notification.GROUP_ALERT_SUMMARY, restored.groupAlertBehavior)
        assertEquals(0, restored.defaults and (Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE))
        store.configureDailyCheckIn(null, due + 2_000L)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun dailyCheckInIsOptInAndDoesNotSendOrNotifyByDefault() = runBlocking {
        val now = 1_700_000_000_000L
        store.beginStart(now, elapsedRealtimeMs = 0L)
        val result = DailyCheckInTask(context, store, repository).run(now + 24 * 60 * 60 * 1_000L)
        assertEquals(DailyCheckInPhase.DISABLED, store.dailyCheckInStatus().phase)
        assertEquals(null, result.nextRunAtMs)
        assertTrue(store.desiredEnabled)
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }
}
