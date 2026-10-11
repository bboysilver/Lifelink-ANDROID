package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FamilyFollowUpTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LifeLinkRepository
    private lateinit var incidents: SafetyIncidentRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = LifeLinkRepository(db)
        incidents = SafetyIncidentRepository(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun createIncident(attempted: Boolean = true) {
        incidents.getOrCreate(
            "emergency:1000", "emergency", 1_000L, "사용자", "원래 문자", null, 1,
            listOf(Contact(id = 1, name = "보호자", phoneNumber = "01012345678")), nowMs = 1_001L
        )
        if (attempted) incidents.updateRecipientStatus("emergency:1000:1", 1, "QUEUED", 1_002L)
    }

    @Test fun followUpDoesNotCompleteIncidentOrChangeQueuedSms() = runBlocking {
        createIncident()
        assertEquals(FollowUpSaveResult.SAVED, repository.recordFamilyFollowUp("emergency:1000", FamilyFollowUpStatus.CONFIRMED_SAFE, 2_000L))
        val incident = incidents.get("emergency:1000")!!
        assertNull(incident.incident.completedAtMs)
        assertEquals("QUEUED", incident.recipients.single().dispatchState)
        assertEquals("원래 문자", incident.incident.message)
        assertEquals("CONFIRMED_SAFE", repository.recentSafetyReviews.first().single().followUpStatus)
    }

    @Test fun sameResultIsIdempotentAndDoesNotInventNewConfirmationTime() = runBlocking {
        createIncident()
        repository.recordFamilyFollowUp("emergency:1000", FamilyFollowUpStatus.CONTACTING, 2_000L)
        assertEquals(FollowUpSaveResult.UNCHANGED, repository.recordFamilyFollowUp("emergency:1000", FamilyFollowUpStatus.CONTACTING, 3_000L))
        assertEquals(2_000L, repository.recentSafetyReviews.first().single().followUpUpdatedAtMs)
        assertEquals(1, repository.allLogs.first().size)
    }

    @Test fun deletedAndUnattemptedIncidentsCannotBeMarkedConfirmed() = runBlocking {
        createIncident(attempted = false)
        assertTrue(repository.recentSafetyReviews.first().isEmpty())
        assertEquals(FollowUpSaveResult.EVENT_UNAVAILABLE, repository.recordFamilyFollowUp("emergency:1000", FamilyFollowUpStatus.CONFIRMED_SAFE))
        assertEquals(FollowUpSaveResult.EVENT_UNAVAILABLE, repository.recordFamilyFollowUp("missing", FamilyFollowUpStatus.CONFIRMED_SAFE))
        assertTrue(repository.allLogs.first().isEmpty())
    }

    @Test fun sentIsNotGuardianAcknowledgement() = runBlocking {
        createIncident()
        incidents.updateRecipientStatus("emergency:1000:1", 1, "SENT", 2_000L)
        val review = repository.recentSafetyReviews.first().single()
        assertEquals(1, review.sentRecipients)
        assertNull(review.followUpStatus)
    }

    @Test fun clearingRecordsKeepsActiveRetriesButRemovesManualResults() = runBlocking {
        createIncident()
        repository.recordFamilyFollowUp("emergency:1000", FamilyFollowUpStatus.CONTACTING, 2_000L)
        repository.clearLogs()
        assertNotNull(incidents.get("emergency:1000"))
        assertNull(repository.recentSafetyReviews.first().single().followUpStatus)
        assertTrue(repository.allLogs.first().isEmpty())
    }

    @Test fun retentionAndIncidentDeletionRemoveFollowUps() = runBlocking {
        createIncident()
        repository.recordFamilyFollowUp("emergency:1000", FamilyFollowUpStatus.CONTACTING, 2_000L)
        repository.deleteLogsBefore(2_001L)
        assertNull(db.safetyIncidentDao().getFollowUp("emergency:1000"))
        repository.recordFamilyFollowUp("emergency:1000", FamilyFollowUpStatus.CONFIRMED_SAFE, 3_000L)
        incidents.completeAndRedact("emergency:1000", 3_001L)
        incidents.deleteCompletedBefore(3_002L)
        assertNull(db.safetyIncidentDao().getFollowUp("emergency:1000"))
    }
}
