package com.example.data

import androidx.room.Entity
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
    val type: String, // "SAFETY_INIT", "SENSOR_RESET", "ALERT_WARNING", "SMS_SENT", "SMS_FAILED", "SETTINGS_CHANGED"
    val message: String,
    val detail: String = ""
)

@Entity(tableName = "settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)
