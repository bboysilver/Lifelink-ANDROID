package com.example.monitoring

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.example.data.Contact

class EmergencySmsSender(private val context: Context) {
    fun send(deadlineMs: Long, contact: Contact, message: String): Boolean {
        val eventId = "$deadlineMs-${contact.id}"
        val statusPreferences = context.getSharedPreferences(STATUS_FILE, Context.MODE_PRIVATE)
        val dispatchKey = "dispatch:$eventId"
        synchronized(LOCK) {
            if (statusPreferences.getBoolean(dispatchKey, false)) return false
            statusPreferences.edit().putBoolean(dispatchKey, true).commit()
        }

        try {
            val phone = contact.phoneNumber.filter { it.isDigit() || it == '+' }
            require(phone.length >= 8) { "Invalid emergency contact number" }
            val manager = context.getSystemService(SmsManager::class.java)
            val parts = manager.divideMessage(message)
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            val deliveredIntents = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { partIndex ->
                sentIntents += statusIntent(
                    action = SmsStatusReceiver.ACTION_SMS_SENT,
                    eventId = eventId,
                    contact = contact,
                    partIndex = partIndex,
                    totalParts = parts.size
                )
                deliveredIntents += statusIntent(
                    action = SmsStatusReceiver.ACTION_SMS_DELIVERED,
                    eventId = eventId,
                    contact = contact,
                    partIndex = partIndex,
                    totalParts = parts.size
                )
            }
            manager.sendMultipartTextMessage(phone, null, parts, sentIntents, deliveredIntents)
            return true
        } catch (error: Exception) {
            statusPreferences.edit().remove(dispatchKey).apply()
            throw error
        }
    }

    private fun statusIntent(
        action: String,
        eventId: String,
        contact: Contact,
        partIndex: Int,
        totalParts: Int
    ): PendingIntent {
        val phoneDigits = contact.phoneNumber.filter(Char::isDigit)
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(SmsStatusReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(SmsStatusReceiver.EXTRA_CONTACT_NAME, contact.name)
            putExtra(SmsStatusReceiver.EXTRA_PHONE_SUFFIX, phoneDigits.takeLast(4))
            putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, partIndex)
            putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, totalParts)
        }
        val requestCode = 31 * eventId.hashCode() + 17 * partIndex + action.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val STATUS_FILE = "lifelink_sms_status"
        val LOCK = Any()
    }
}
