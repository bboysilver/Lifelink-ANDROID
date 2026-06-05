package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LifeLinkRepository(private val db: AppDatabase) {

    val allContacts: Flow<List<Contact>> = db.contactDao().getAllContacts()
    val allLogs: Flow<List<EventLog>> = db.eventLogDao().getAllEventLogs()

    // Preferences / Settings Keys
    companion object {
        const val KEY_MONITOR_HOURS = "monitor_hours"
        const val KEY_SMS_MODE = "sms_mode" // "VIRTUAL", "INTENT", "DIRECT", "PREMIUM"
        const val KEY_IS_PREMIUM = "is_premium"
    }

    // Settings
    suspend fun getSetting(key: String, defaultValue: String): String = withContext(Dispatchers.IO) {
        db.settingDao().getSetting(key)?.value ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        db.settingDao().saveSetting(AppSetting(key, value))
        insertLog("SETTINGS_CHANGED", "설정 수정을 반영했습니다", "$key -> $value")
    }

    // Contacts
    suspend fun insertContact(contact: Contact) = withContext(Dispatchers.IO) {
        db.contactDao().insertContact(contact)
        insertLog("SAFETY_INIT", "보호자 긴급 연락처를 등록했습니다", "${contact.name} (${contact.phoneNumber})")
    }

    suspend fun deleteContact(contact: Contact) = withContext(Dispatchers.IO) {
        db.contactDao().deleteContact(contact)
        insertLog("SAFETY_INIT", "보호자 연락처를 삭제했습니다", "${contact.name} (${contact.phoneNumber})")
    }

    suspend fun getContactCount(): Int = withContext(Dispatchers.IO) {
        db.contactDao().getContactCount()
    }

    // Logs
    suspend fun insertLog(type: String, message: String, detail: String = "") = withContext(Dispatchers.IO) {
        db.eventLogDao().insertLog(EventLog(type = type, message = message, detail = detail))
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        db.eventLogDao().clearLogs()
    }
}
