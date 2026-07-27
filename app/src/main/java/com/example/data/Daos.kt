package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY createdAt ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getContactCount(): Int
}

@Dao
interface EventLogDao {
    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllEventLogs(): Flow<List<EventLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: EventLog)

    @Query("DELETE FROM event_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM event_logs WHERE timestamp < :cutoffMs")
    suspend fun deleteBefore(cutoffMs: Long): Int
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun getSetting(key: String): AppSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: AppSetting)
}

@Dao
interface SafetyIncidentDao {
    @Query("SELECT * FROM safety_incidents WHERE id = :incidentId")
    suspend fun getIncident(incidentId: String): SafetyIncident?

    @Query("SELECT * FROM safety_recipients WHERE eventId = :eventId")
    suspend fun getRecipient(eventId: String): SafetyRecipient?

    @Query("SELECT * FROM safety_recipients WHERE incidentId = :incidentId ORDER BY eventId")
    suspend fun getRecipients(incidentId: String): List<SafetyRecipient>

    @Query(
        """
        SELECT safety_recipients.* FROM safety_recipients
        INNER JOIN safety_incidents
            ON safety_incidents.id = safety_recipients.incidentId
        WHERE safety_incidents.completedAtMs IS NULL
        ORDER BY safety_incidents.createdAtMs, safety_recipients.eventId
        """
    )
    suspend fun getPendingRecipients(): List<SafetyRecipient>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIncident(incident: SafetyIncident): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipients(recipients: List<SafetyRecipient>)

    @Query(
        """
        UPDATE safety_recipients
        SET attemptCount = :attemptCount,
            dispatchState = :dispatchState,
            updatedAtMs = :updatedAtMs
        WHERE eventId = :eventId
        """
    )
    suspend fun updateRecipientStatus(
        eventId: String,
        attemptCount: Int,
        dispatchState: String,
        updatedAtMs: Long
    )

    @Query(
        """
        UPDATE safety_incidents
        SET completedAtMs = :completedAtMs
        WHERE id = :incidentId AND completedAtMs IS NULL
        """
    )
    suspend fun markCompleted(incidentId: String, completedAtMs: Long)

    @Query("UPDATE safety_recipients SET phoneNumber = '' WHERE incidentId = :incidentId")
    suspend fun redactPhoneNumbers(incidentId: String)

    @Transaction
    suspend fun completeAndRedact(incidentId: String, completedAtMs: Long) {
        markCompleted(incidentId, completedAtMs)
        redactPhoneNumbers(incidentId)
    }

    @Query(
        "DELETE FROM safety_incidents WHERE completedAtMs IS NOT NULL AND completedAtMs < :cutoffMs"
    )
    suspend fun deleteCompletedBefore(cutoffMs: Long): Int
}
