package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.monitoring.SmsDispatchStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FieldVisitReadModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LifeLinkRepository
    private lateinit var incidents: SafetyIncidentRepository
    private lateinit var store: MonitoringStore
    private lateinit var dispatch: SmsDispatchStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE).edit().clear().commit()
        dispatch = SmsDispatchStore(context).apply { clearAll() }
        store = MonitoringStore(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = LifeLinkRepository(db)
        incidents = SafetyIncidentRepository(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun create(id: Long, state: String, updatedAt: Long) {
        incidents.getOrCreate("emergency:$id", "emergency", id, "private alias", "private message", null, 1,
            listOf(Contact(id = 1, name = "private guardian", phoneNumber = "01012345678")), nowMs = id)
        incidents.updateRecipientStatus("emergency:$id:1", 1, state, updatedAt)
    }

    @Test fun latestResultUsesResultTimeNotIncidentTimeAndDoesNotExposeContactData() = runBlocking {
        create(100, "SENT", 900)
        create(200, "QUEUED", 500)
        val result = repository.latestSmsRecord.first()!!
        assertEquals("emergency:100:1", result.eventId)
        assertEquals("SENT", result.dispatchState)
        assertEquals(900L, result.updatedAtMs)
        assertFalse(result.toString().contains("private"))
        assertFalse(result.toString().contains("01012345678"))
    }

    @Test fun sentAndDeliveredCountsAreDifferentFromHumanFollowUp() = runBlocking {
        create(100, "SENT", 101)
        var result = repository.recentSafetyReviews.first().single()
        assertEquals(1, result.sentRecipients)
        assertEquals(0, result.deliveredRecipients)
        assertNull(result.followUpStatus)
        incidents.updateRecipientStatus("emergency:100:1", 1, "DELIVERED", 102)
        result = repository.recentSafetyReviews.first().single()
        assertEquals(1, result.deliveredRecipients)
        assertNull(result.followUpStatus)
    }

    @Test fun followUpAndReadOnlyQueriesLeaveMonitoringSettingsAndRetryIntact() = runBlocking {
        store.monitorHours = 6
        store.beginStart(nowMs = 1_000, elapsedRealtimeMs = 0)
        create(100, "QUEUED", 101)
        dispatch.beginAttempt("emergency:100:1", 1, nowMs = 101)
        val deadline = store.deadlineMs
        val event = store.inactivityEventMs
        val status = dispatch.status("emergency:100:1")
        val original = incidents.get("emergency:100")!!
        repository.latestSmsRecord.first()
        repository.recordFamilyFollowUp("emergency:100", FamilyFollowUpStatus.CONFIRMED_SAFE, 200)
        assertEquals(6, store.monitorHours)
        assertEquals(deadline, store.deadlineMs)
        assertEquals(event, store.inactivityEventMs)
        assertEquals(status, dispatch.status("emergency:100:1"))
        assertEquals(original, incidents.get("emergency:100"))
    }

    @Test fun deletingVisibleHistoryDoesNotDeletePendingSmsAndRemovesCompletedSummary() = runBlocking {
        create(100, "SENT", 101)
        incidents.completeAndRedact("emergency:100", 102)
        repository.clearLogs()
        assertNull(repository.latestSmsRecord.first())
        create(200, "FAILED_RETRYABLE", 201)
        repository.recordFamilyFollowUp("emergency:200", FamilyFollowUpStatus.CONTACTING)
        repository.clearLogs()
        assertEquals("FAILED_RETRYABLE", repository.latestSmsRecord.first()!!.dispatchState)
        assertNull(repository.recentSafetyReviews.first().single().followUpStatus)
    }
}
