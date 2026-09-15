package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SafetyIncidentSnapshot(
    val incident: SafetyIncident,
    val recipients: List<SafetyRecipient>
)

class SafetyIncidentRepository(private val db: AppDatabase) {
    private val dao = db.safetyIncidentDao()

    suspend fun get(incidentId: String): SafetyIncidentSnapshot? = withContext(Dispatchers.IO) {
        val incident = dao.getIncident(incidentId) ?: return@withContext null
        SafetyIncidentSnapshot(incident, dao.getRecipients(incidentId))
    }

    suspend fun getOrCreate(
        incidentId: String,
        type: String,
        occurredAtMs: Long,
        deviceAlias: String,
        message: String,
        batteryPercent: Int?,
        subscriptionId: Int,
        contacts: List<Contact>,
        nowMs: Long = System.currentTimeMillis()
    ): SafetyIncidentSnapshot = withContext(Dispatchers.IO) {
        require(contacts.isNotEmpty()) { "Safety incident requires at least one recipient" }
        db.withTransaction {
            if (dao.getIncident(incidentId) == null) {
                dao.insertIncident(
                    SafetyIncident(
                        id = incidentId,
                        type = type,
                        occurredAtMs = occurredAtMs,
                        deviceAlias = deviceAlias,
                        message = message,
                        batteryPercent = batteryPercent,
                        subscriptionId = subscriptionId,
                        createdAtMs = nowMs
                    )
                )
                dao.insertRecipients(
                    contacts.map { contact ->
                        SafetyRecipient(
                            eventId = "$incidentId:${contact.id}",
                            incidentId = incidentId,
                            contactId = contact.id,
                            name = contact.name,
                            phoneNumber = contact.phoneNumber,
                            updatedAtMs = nowMs
                        )
                    }
                )
            }
            val incident = checkNotNull(dao.getIncident(incidentId))
            SafetyIncidentSnapshot(incident, dao.getRecipients(incidentId))
        }
    }

    suspend fun recipient(eventId: String): SafetyRecipient? = withContext(Dispatchers.IO) {
        dao.getRecipient(eventId)
    }

    suspend fun pendingRecipients(): List<SafetyRecipient> = withContext(Dispatchers.IO) {
        dao.getPendingRecipients()
    }

    suspend fun updateRecipientStatus(
        eventId: String,
        attemptCount: Int,
        dispatchState: String,
        updatedAtMs: Long
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val current = dao.getRecipient(eventId) ?: return@withTransaction
            if (attemptCount < current.attemptCount) return@withTransaction
            if (attemptCount == current.attemptCount) {
                if (updatedAtMs < current.updatedAtMs) return@withTransaction
                // Foreground and broadcast coroutines may persist snapshots out of
                // order, including within the same millisecond.
                if (current.dispatchState == "DELIVERED" && dispatchState != "DELIVERED") {
                    return@withTransaction
                }
                if (current.dispatchState == "SENT" && dispatchState !in listOf("SENT", "DELIVERED")) {
                    return@withTransaction
                }
                if (current.dispatchState != "QUEUED" && dispatchState == "QUEUED") {
                    return@withTransaction
                }
            }
            dao.updateRecipientStatus(eventId, attemptCount, dispatchState, updatedAtMs)
        }
    }

    suspend fun completeAndRedact(
        incidentId: String,
        completedAtMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        dao.completeAndRedact(incidentId, completedAtMs)
    }

    suspend fun deleteCompletedBefore(cutoffMs: Long): Int = withContext(Dispatchers.IO) {
        dao.deleteCompletedBefore(cutoffMs)
    }
}
