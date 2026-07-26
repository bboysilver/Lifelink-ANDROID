package com.example.monitoring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

internal enum class SafetySmsEventType { EMERGENCY, SOS }

internal data class SafetySmsEvent(
    val type: SafetySmsEventType,
    val occurredAtMs: Long,
    val contactId: Int
) {
    companion object {
        fun parse(eventId: String): SafetySmsEvent? {
            val parts = eventId.split(':')
            if (parts.size != 3) return null
            val type = when (parts[0]) {
                "emergency" -> SafetySmsEventType.EMERGENCY
                "sos" -> SafetySmsEventType.SOS
                else -> return null
            }
            val occurredAtMs = parts[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
            val contactId = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return SafetySmsEvent(type, occurredAtMs, contactId)
        }
    }
}

class SafetySmsRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        return try {
            val nextRunAtMs = SafetySmsRetryTask(applicationContext).run(eventId)
            if (nextRunAtMs != null) enqueueAt(applicationContext, eventId, nextRunAtMs)
            Result.success()
        } catch (_: RuntimeException) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_PREFIX = "lifelink-safety-sms"
        internal const val WORK_TAG = "lifelink-safety-sms-work"
        private const val KEY_EVENT_ID = "event_id"

        fun enqueueAt(context: Context, eventId: String, runAtMs: Long) {
            if (SafetySmsEvent.parse(eventId) == null) return
            val request = OneTimeWorkRequestBuilder<SafetySmsRetryWorker>()
                .setInputData(Data.Builder().putString(KEY_EVENT_ID, eventId).build())
                .setInitialDelay(
                    (runAtMs - System.currentTimeMillis()).coerceAtLeast(1_000L),
                    TimeUnit.MILLISECONDS
                )
                .addTag(WORK_TAG)
                .build()
            val workManager = try {
                WorkManager.getInstance(context.applicationContext)
            } catch (error: IllegalStateException) {
                Log.e(WORK_TAG, "WorkManager is unavailable; the foreground loop will retry", error)
                return
            }
            workManager.enqueueUniqueWork(
                "$WORK_PREFIX:$eventId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

internal class SafetySmsRetryTask(
    context: Context,
    private val store: MonitoringStore = MonitoringStore(context.applicationContext),
    private val repository: LifeLinkRepository = LifeLinkRepository(
        AppDatabase.getDatabase(context.applicationContext)
    )
) {
    private val appContext = context.applicationContext

    suspend fun run(eventId: String, nowMs: Long = System.currentTimeMillis()): Long? {
        val event = SafetySmsEvent.parse(eventId) ?: return null
        val contacts = repository.allContacts.first().take(3)
        val contact = contacts.firstOrNull { it.id == event.contactId }
        if (contact == null) {
            finalizeIfResolved(event, contacts)
            return null
        }
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return nowMs + BLOCKED_RETRY_MS
        }
        val smsSetup = SmsDeviceManager(appContext, store).inspect()
        if (smsSetup !is SmsSetupState.Ready) return nowMs + BLOCKED_RETRY_MS

        val sender = EmergencySmsSender(appContext)
        val message = when (event.type) {
            SafetySmsEventType.EMERGENCY -> EmergencyMessageBuilder.build(store.deviceAlias, null)
            SafetySmsEventType.SOS -> EmergencyMessageBuilder.buildSos(
                store.deviceAlias,
                event.occurredAtMs
            )
        }
        sender.queue(
            eventId = eventId,
            contact = contact,
            message = message,
            subscriptionId = smsSetup.line.subscriptionId,
            nowMs = nowMs
        )
        val status = sender.status(eventId, nowMs)
        finalizeIfResolved(event, contacts)
        return when (status.state) {
            SmsDispatchState.QUEUED -> status.updatedAtMs + SmsDispatchStore.CALLBACK_TIMEOUT_MS
            SmsDispatchState.FAILED_RETRYABLE -> status.retryAtMs
            SmsDispatchState.NOT_QUEUED -> nowMs + BLOCKED_RETRY_MS
            else -> null
        }
    }

    private fun finalizeIfResolved(event: SafetySmsEvent, contacts: List<com.example.data.Contact>) {
        if (contacts.isEmpty()) return
        val sender = EmergencySmsSender(appContext)
        val allResolved = contacts.all { contact ->
            val id = when (event.type) {
                SafetySmsEventType.EMERGENCY ->
                    EmergencySmsSender.emergencyEventId(event.occurredAtMs, contact.id)
                SafetySmsEventType.SOS ->
                    EmergencySmsSender.sosEventId(event.occurredAtMs, contact.id)
            }
            sender.status(id).isResolved
        }
        if (!allResolved) return
        when (event.type) {
            SafetySmsEventType.EMERGENCY -> store.markEmergency(event.occurredAtMs)
            SafetySmsEventType.SOS -> store.completeActiveSos(event.occurredAtMs)
        }
    }

    companion object {
        private const val BLOCKED_RETRY_MS = 30 * 60 * 1_000L
    }
}