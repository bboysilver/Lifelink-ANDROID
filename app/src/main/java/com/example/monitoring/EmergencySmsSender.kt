package com.example.monitoring

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.Contact

enum class SmsQueueResult { QUEUED, WAITING, ALREADY_RESOLVED, FAILED_FINAL }

class EmergencySmsSender(
    private val context: Context,
    private val dispatchStore: SmsDispatchStore = SmsDispatchStore(context),
    private val deviceManager: SmsDeviceManager = SmsDeviceManager(context)
) {
    fun queue(
        eventId: String,
        contact: Contact,
        message: String,
        subscriptionId: Int,
        retryPolicy: SmsRetryPolicy = SmsRetryPolicy.EMERGENCY,
        nowMs: Long = System.currentTimeMillis()
    ): SmsQueueResult {
        dispatchStore.markQueuedTimeoutIfNeeded(eventId, nowMs)
        val current = dispatchStore.status(eventId)
        if (current.state == SmsDispatchState.SENT || current.state == SmsDispatchState.DELIVERED) {
            return SmsQueueResult.ALREADY_RESOLVED
        }
        if (current.state == SmsDispatchState.FAILED_FINAL) return SmsQueueResult.FAILED_FINAL

        val phone = contact.phoneNumber.filter { it.isDigit() || it == '+' }
        require(phone.length >= 8) { "Invalid emergency contact number" }
        val manager = deviceManager.managerFor(subscriptionId)
        val parts = manager.divideMessage(message)
        val attempt = dispatchStore.beginAttempt(eventId, parts.size, retryPolicy, nowMs)
            ?: return SmsQueueResult.WAITING

        try {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            val deliveredIntents = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { partIndex ->
                sentIntents += statusIntent(
                    action = SmsStatusReceiver.ACTION_SMS_SENT,
                    eventId = eventId,
                    contact = contact,
                    attempt = attempt,
                    partIndex = partIndex,
                    totalParts = parts.size
                )
                deliveredIntents += statusIntent(
                    action = SmsStatusReceiver.ACTION_SMS_DELIVERED,
                    eventId = eventId,
                    contact = contact,
                    attempt = attempt,
                    partIndex = partIndex,
                    totalParts = parts.size
                )
            }
            manager.sendMultipartTextMessage(phone, null, parts, sentIntents, deliveredIntents)
            return SmsQueueResult.QUEUED
        } catch (error: Exception) {
            dispatchStore.markQueueFailure(eventId, attempt, RESULT_QUEUE_EXCEPTION, nowMs)
            throw error
        }
    }

    fun status(eventId: String, nowMs: Long = System.currentTimeMillis()): SmsDispatchStatus {
        dispatchStore.markQueuedTimeoutIfNeeded(eventId, nowMs)
        return dispatchStore.status(eventId)
    }

    private fun statusIntent(
        action: String,
        eventId: String,
        contact: Contact,
        attempt: Int,
        partIndex: Int,
        totalParts: Int
    ): PendingIntent {
        val phoneDigits = contact.phoneNumber.filter(Char::isDigit)
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(SmsStatusReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(SmsStatusReceiver.EXTRA_CONTACT_NAME, contact.name)
            putExtra(SmsStatusReceiver.EXTRA_PHONE_SUFFIX, phoneDigits.takeLast(4))
            putExtra(SmsStatusReceiver.EXTRA_ATTEMPT, attempt)
            putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, partIndex)
            putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, totalParts)
        }
        val requestCode = 31 * eventId.hashCode() + 17 * attempt + 7 * partIndex + action.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val RESULT_QUEUE_EXCEPTION = -10_002

        fun emergencyEventId(deadlineMs: Long, contactId: Int): String =
            "emergency:$deadlineMs:$contactId"

        fun sosEventId(requestedAtMs: Long, contactId: Int): String =
            "sos:$requestedAtMs:$contactId"

        fun dailyEventId(dueAtMs: Long, contactId: Int): String =
            "daily:$dueAtMs:$contactId"

        fun testEventId(nowMs: Long, contactId: Int): String = "test:$nowMs:$contactId"
    }
}
