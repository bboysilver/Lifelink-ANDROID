package com.example.monitoring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.Contact
import com.example.data.MonitoringStore
import com.example.data.SafetyIncidentRepository
import com.example.data.SafetyIncidentSnapshot
import com.example.data.SafetyRecipient
import java.util.concurrent.TimeUnit

internal enum class SafetySmsEventType(val wireName: String) {
    EMERGENCY("emergency"),
    SOS("sos"),
    DAILY("daily")
}

internal data class SafetySmsEvent(
    val type: SafetySmsEventType,
    val occurredAtMs: Long,
    val contactId: Int
) {
    val incidentId: String
        get() = "${type.wireName}:$occurredAtMs"

    companion object {
        fun parse(eventId: String): SafetySmsEvent? {
            val parts = eventId.split(':')
            if (parts.size != 3) return null
            val type = SafetySmsEventType.entries.firstOrNull { it.wireName == parts[0] }
                ?: return null
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
    override suspend fun doWork(): Result = try {
        val task = SafetySmsRetryTask(applicationContext)
        val requestedEventId = inputData.getString(KEY_EVENT_ID)
        val eventIds = requestedEventId?.let(::listOf) ?: task.pendingEventIds()
        eventIds.forEach { eventId ->
            task.run(eventId)?.let { nextRunAtMs ->
                enqueueAt(applicationContext, eventId, nextRunAtMs)
            }
        }
        Result.success()
    } catch (_: RuntimeException) {
        Result.retry()
    }

    companion object {
        private const val WORK_PREFIX = "lifelink-safety-sms"
        private const val RECOVERY_WORK_NAME = "$WORK_PREFIX:recovery"
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
            workManager(context)?.enqueueUniqueWork(
                "$WORK_PREFIX:$eventId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueRecovery(context: Context) {
            val request = OneTimeWorkRequestBuilder<SafetySmsRetryWorker>()
                .addTag(WORK_TAG)
                .build()
            workManager(context)?.enqueueUniqueWork(
                RECOVERY_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        private fun workManager(context: Context): WorkManager? = try {
            WorkManager.getInstance(context.applicationContext)
        } catch (error: IllegalStateException) {
            Log.e(WORK_TAG, "WorkManager is unavailable; the foreground loop will retry", error)
            null
        }
    }
}

internal class SafetySmsRetryTask(
    context: Context,
    private val store: MonitoringStore = MonitoringStore(context.applicationContext),
    private val incidents: SafetyIncidentRepository = SafetyIncidentRepository(
        AppDatabase.getDatabase(context.applicationContext)
    )
) {
    private val appContext = context.applicationContext

    suspend fun pendingEventIds(): List<String> =
        incidents.pendingRecipients().map(SafetyRecipient::eventId)

    suspend fun run(eventId: String, nowMs: Long = System.currentTimeMillis()): Long? {
        val event = SafetySmsEvent.parse(eventId) ?: return null
        val snapshot = incidents.get(event.incidentId) ?: return null
        if (snapshot.incident.completedAtMs != null) return null
        val recipient = snapshot.recipients.firstOrNull { it.eventId == eventId } ?: return null
        val sender = EmergencySmsSender(appContext)

        var status = sender.status(eventId, nowMs)
        incidents.recordStatus(eventId, status)
        if (status.isResolved) {
            finalizeIfResolved(event, snapshot, nowMs)
            return null
        }
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return nowMs + BLOCKED_RETRY_MS
        }

        try {
            sender.queue(
                eventId = eventId,
                contact = recipient.asContact(),
                message = snapshot.incident.message,
                subscriptionId = snapshot.incident.subscriptionId,
                nowMs = nowMs
            )
        } catch (error: RuntimeException) {
            Log.e(SafetySmsRetryWorker.WORK_TAG, "Unable to queue immutable safety incident", error)
        }
        status = sender.status(eventId, nowMs)
        incidents.recordStatus(eventId, status)
        finalizeIfResolved(event, snapshot, nowMs)
        return when (status.state) {
            SmsDispatchState.QUEUED -> status.updatedAtMs + SmsDispatchStore.CALLBACK_TIMEOUT_MS
            SmsDispatchState.FAILED_RETRYABLE -> status.retryAtMs
            SmsDispatchState.NOT_QUEUED -> nowMs + BLOCKED_RETRY_MS
            else -> null
        }
    }

    private suspend fun finalizeIfResolved(
        event: SafetySmsEvent,
        originalSnapshot: SafetyIncidentSnapshot,
        nowMs: Long
    ) {
        val snapshot = incidents.get(event.incidentId) ?: originalSnapshot
        val sender = EmergencySmsSender(appContext)
        val statuses = snapshot.recipients.map { recipient ->
            sender.status(recipient.eventId, nowMs).also { status ->
                incidents.recordStatus(recipient.eventId, status)
            }
        }
        if (statuses.isEmpty() || statuses.any { !it.isResolved }) return

        when (event.type) {
            SafetySmsEventType.EMERGENCY -> store.markEmergency(event.occurredAtMs)
            SafetySmsEventType.SOS -> store.completeActiveSos(event.occurredAtMs)
            SafetySmsEventType.DAILY -> {
                store.markDailyCheckInAlerted(event.occurredAtMs)
                store.advanceDailyCheckIn(event.occurredAtMs)
                store.dailyCheckInError = ""
                DailyCheckInScheduler(appContext).ensureScheduled(nowMs)
                NotificationManagerCompat.from(appContext).cancel(DailyCheckInTask.DAILY_NOTIFICATION_ID)
            }
        }
        incidents.completeAndRedact(event.incidentId, nowMs)
    }

    companion object {
        private const val BLOCKED_RETRY_MS = 30 * 60 * 1_000L
    }
}

internal fun SafetyRecipient.asContact(): Contact = Contact(
    id = contactId,
    name = name,
    phoneNumber = phoneNumber,
    createdAt = updatedAtMs
)

internal suspend fun SafetyIncidentRepository.recordStatus(
    eventId: String,
    status: SmsDispatchStatus
) {
    updateRecipientStatus(
        eventId = eventId,
        attemptCount = status.attempt,
        dispatchState = status.state.name,
        updatedAtMs = status.updatedAtMs
    )
}
