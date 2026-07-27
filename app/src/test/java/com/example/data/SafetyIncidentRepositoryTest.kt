package com.example.data

import android.app.Application
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
class SafetyIncidentRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: SafetyIncidentRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SafetyIncidentRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun retrySnapshotIgnoresLaterContactAliasMessageAndSimChanges() = runBlocking {
        val original = Contact(id = 1, name = "첫 보호자", phoneNumber = "01011112222")
        database.contactDao().insertContact(original)
        val contactsAtIncident = database.contactDao().getAllContacts().first()

        repository.getOrCreate(
            incidentId = "emergency:1000",
            type = "emergency",
            occurredAtMs = 1_000L,
            deviceAlias = "어머니 폰",
            message = "최초 메시지 · 배터리 14%",
            batteryPercent = 14,
            subscriptionId = 10,
            contacts = contactsAtIncident,
            nowMs = 1_001L
        )

        database.contactDao().deleteContact(original)
        database.contactDao().insertContact(
            Contact(id = 2, name = "새 보호자", phoneNumber = "01099998888")
        )
        val changedContacts = database.contactDao().getAllContacts().first()
        val snapshot = repository.getOrCreate(
            incidentId = "emergency:1000",
            type = "emergency",
            occurredAtMs = 1_000L,
            deviceAlias = "변경된 별칭",
            message = "변경된 메시지",
            batteryPercent = 90,
            subscriptionId = 20,
            contacts = changedContacts,
            nowMs = 2_000L
        )

        assertEquals("어머니 폰", snapshot.incident.deviceAlias)
        assertEquals("최초 메시지 · 배터리 14%", snapshot.incident.message)
        assertEquals(14, snapshot.incident.batteryPercent)
        assertEquals(10, snapshot.incident.subscriptionId)
        assertEquals(listOf(1), snapshot.recipients.map { it.contactId })
        assertEquals("01011112222", snapshot.recipients.single().phoneNumber)
    }

    @Test
    fun completionKeepsAuditStateButRedactsPhoneAndLeavesNoPendingWork() = runBlocking {
        repository.getOrCreate(
            incidentId = "sos:2000",
            type = "sos",
            occurredAtMs = 2_000L,
            deviceAlias = "아버지 폰",
            message = "SOS",
            batteryPercent = null,
            subscriptionId = 7,
            contacts = listOf(Contact(id = 3, name = "보호자", phoneNumber = "01012345678")),
            nowMs = 2_001L
        )
        repository.updateRecipientStatus(
            eventId = "sos:2000:3",
            attemptCount = 3,
            dispatchState = "FAILED_FINAL",
            updatedAtMs = 3_000L
        )

        repository.completeAndRedact("sos:2000", completedAtMs = 3_001L)

        val completed = repository.get("sos:2000")
        assertNotNull(completed?.incident?.completedAtMs)
        assertEquals(3, completed?.recipients?.single()?.attemptCount)
        assertEquals("FAILED_FINAL", completed?.recipients?.single()?.dispatchState)
        assertEquals("", completed?.recipients?.single()?.phoneNumber)
        assertTrue(repository.pendingRecipients().isEmpty())

        repository.deleteCompletedBefore(3_002L)
        assertNull(repository.get("sos:2000"))
    }
}
