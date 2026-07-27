package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "event_logs")
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val message: String,
    val detail: String = ""
)

@Entity(tableName = "settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "safety_incidents")
data class SafetyIncident(
    @PrimaryKey val id: String,
    val type: String,
    val occurredAtMs: Long,
    val deviceAlias: String,
    val message: String,
    val batteryPercent: Int?,
    val subscriptionId: Int,
    val createdAtMs: Long,
    val completedAtMs: Long? = null
)

@Entity(
    tableName = "safety_recipients",
    foreignKeys = [
        ForeignKey(
            entity = SafetyIncident::class,
            parentColumns = ["id"],
            childColumns = ["incidentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("incidentId")]
)
data class SafetyRecipient(
    @PrimaryKey val eventId: String,
    val incidentId: String,
    val contactId: Int,
    val name: String,
    val phoneNumber: String,
    val attemptCount: Int = 0,
    val dispatchState: String = "NOT_QUEUED",
    val updatedAtMs: Long
)
