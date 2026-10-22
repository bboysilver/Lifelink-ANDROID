package com.example.monitoring

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit

enum class SmsDispatchState {
    NOT_QUEUED,
    QUEUED,
    SENT,
    DELIVERED,
    FAILED_RETRYABLE,
    FAILED_FINAL
}

enum class SmsRetryPolicy(val maxAttempts: Int) {
    EMERGENCY(3),
    ONE_SHOT(1)
}

data class SmsDispatchStatus(
    val state: SmsDispatchState,
    val attempt: Int,
    val maxAttempts: Int,
    val updatedAtMs: Long,
    val retryAtMs: Long,
    val lastResultCode: Int
) {
    val isResolved: Boolean
        get() = state == SmsDispatchState.SENT ||
            state == SmsDispatchState.DELIVERED ||
            state == SmsDispatchState.FAILED_FINAL
}

enum class SmsCallbackStage { SENT, DELIVERED }

enum class SmsCallbackOutcome {
    PENDING,
    SENT,
    DELIVERED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    DELIVERY_UNCONFIRMED,
    IGNORED
}

@SuppressLint("ApplySharedPref")
class SmsDispatchStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private fun bootCount(): Int = Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun status(eventId: String): SmsDispatchStatus = synchronized(LOCK) {
        statusLocked(eventId)
    }

    fun beginAttempt(
        eventId: String,
        totalParts: Int,
        policy: SmsRetryPolicy = SmsRetryPolicy.EMERGENCY,
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): Int? = synchronized(LOCK) {
        require(totalParts > 0) { "SMS must contain at least one part" }
        val current = statusLocked(eventId)
        val canStart = current.state == SmsDispatchState.NOT_QUEUED ||
            (current.state == SmsDispatchState.FAILED_RETRYABLE &&
                pendingAgeLocked(eventId, current, nowMs, elapsedRealtimeMs) >= RETRY_DELAY_MS)
        if (!canStart) return@synchronized null

        val attempt = current.attempt + 1
        val eventIds = preferences.getStringSet(KEY_EVENT_IDS, emptySet()).orEmpty().toMutableSet()
        eventIds += eventId
        preferences.edit(commit = true) {
            putStringSet(KEY_EVENT_IDS, eventIds)
            putString(stateKey(eventId), SmsDispatchState.QUEUED.name)
            putInt(attemptKey(eventId), attempt)
            putInt(maxAttemptsKey(eventId), policy.maxAttempts)
            putInt(totalPartsKey(eventId), totalParts)
            putLong(updatedAtKey(eventId), nowMs)
            putLong(elapsedKey(eventId), elapsedRealtimeMs)
            putInt(bootKey(eventId), bootCount())
            putLong(retryAtKey(eventId), 0L)
            putInt(resultCodeKey(eventId), Activity.RESULT_OK)
        }
        attempt
    }

    fun markQueueFailure(
        eventId: String,
        attempt: Int,
        resultCode: Int,
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): SmsCallbackOutcome = synchronized(LOCK) {
        val current = statusLocked(eventId)
        if (current.attempt != attempt || current.state != SmsDispatchState.QUEUED) {
            return@synchronized SmsCallbackOutcome.IGNORED
        }
        markFailureLocked(eventId, attempt, resultCode, nowMs, elapsedRealtimeMs)
    }

    fun markQueuedTimeoutIfNeeded(
        eventId: String,
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): Boolean =
        synchronized(LOCK) {
            val current = statusLocked(eventId)
            if (
                current.state != SmsDispatchState.QUEUED ||
                pendingAgeLocked(eventId, current, nowMs, elapsedRealtimeMs) < CALLBACK_TIMEOUT_MS
            ) {
                return@synchronized false
            }
            markFailureLocked(eventId, current.attempt, RESULT_CALLBACK_TIMEOUT, nowMs, elapsedRealtimeMs)
            true
        }

    fun recordCallback(
        stage: SmsCallbackStage,
        eventId: String,
        attempt: Int,
        partIndex: Int,
        totalParts: Int,
        resultCode: Int,
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): SmsCallbackOutcome = synchronized(LOCK) {
        val current = statusLocked(eventId)
        if (
            current.state == SmsDispatchState.NOT_QUEUED ||
            current.state == SmsDispatchState.DELIVERED ||
            (current.state == SmsDispatchState.SENT && stage == SmsCallbackStage.SENT) ||
            current.attempt != attempt ||
            partIndex !in 0 until totalParts ||
            preferences.getInt(totalPartsKey(eventId), 0) != totalParts
        ) {
            return@synchronized SmsCallbackOutcome.IGNORED
        }

        // A timeout does not prove the SMS failed. Accept late success until a new
        // attempt starts, so an already sent message is not sent again.
        val alreadyFailed = current.state == SmsDispatchState.FAILED_RETRYABLE ||
            current.state == SmsDispatchState.FAILED_FINAL
        if (alreadyFailed && resultCode != Activity.RESULT_OK) {
            return@synchronized SmsCallbackOutcome.IGNORED
        }

        if (stage == SmsCallbackStage.SENT && resultCode != Activity.RESULT_OK) {
            return@synchronized markFailureLocked(eventId, attempt, resultCode, nowMs, elapsedRealtimeMs)
        }
        if (stage == SmsCallbackStage.DELIVERED && resultCode != Activity.RESULT_OK) {
            preferences.edit(commit = true) {
                putLong(updatedAtKey(eventId), nowMs)
                putLong(elapsedKey(eventId), elapsedRealtimeMs)
                putInt(bootKey(eventId), bootCount())
                putInt(resultCodeKey(eventId), resultCode)
            }
            return@synchronized SmsCallbackOutcome.DELIVERY_UNCONFIRMED
        }

        val confirmedPartKey = partKey(stage, eventId, attempt, partIndex)
        if (preferences.getBoolean(confirmedPartKey, false)) return@synchronized SmsCallbackOutcome.IGNORED
        val allPartsConfirmed = (0 until totalParts).all {
            it == partIndex || preferences.getBoolean(partKey(stage, eventId, attempt, it), false)
        }
        if (!allPartsConfirmed) {
            preferences.edit(commit = true) { putBoolean(confirmedPartKey, true) }
            return@synchronized SmsCallbackOutcome.PENDING
        }

        val newState = if (stage == SmsCallbackStage.SENT) {
            SmsDispatchState.SENT
        } else {
            SmsDispatchState.DELIVERED
        }
        preferences.edit(commit = true) {
            // The last part and terminal state must survive process death together.
            putBoolean(confirmedPartKey, true)
            putString(stateKey(eventId), newState.name)
            putLong(updatedAtKey(eventId), nowMs)
            putLong(elapsedKey(eventId), elapsedRealtimeMs)
            putInt(bootKey(eventId), bootCount())
            putLong(retryAtKey(eventId), 0L)
            putInt(resultCodeKey(eventId), resultCode)
        }
        if (newState == SmsDispatchState.SENT) SmsCallbackOutcome.SENT else SmsCallbackOutcome.DELIVERED
    }

    fun reserveTestSend(contactId: Int, nowMs: Long = System.currentTimeMillis()): Long =
        synchronized(LOCK) {
            val key = testThrottleKey(contactId)
            if (preferences.contains(key)) {
                val previous = preferences.getLong(key, 0L)
                if (nowMs < previous) {
                    preferences.edit(commit = true) { putLong(key, nowMs) }
                    return@synchronized TEST_SMS_COOLDOWN_MS
                }
                val remaining = TEST_SMS_COOLDOWN_MS - (nowMs - previous)
                if (remaining > 0L) return@synchronized remaining
            }
            preferences.edit(commit = true) { putLong(key, nowMs) }
            0L
        }

    fun pruneExpired(
        nowMs: Long = System.currentTimeMillis(),
        preserveEventIds: Set<String> = emptySet()
    ): Int = synchronized(LOCK) {
        pruneExpiredLocked(nowMs, preserveEventIds)
    }

    // Re-anchor only waiting intervals; IDs, attempts, multipart results and completed SMS stay intact.
    // A reboot or legacy record cannot prove elapsed time, so grant one bounded wait window.
    fun rebasePendingAfterClockChange(
        nowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
        afterReboot: Boolean = false
    ): List<String> = synchronized(LOCK) {
        val pending = preferences.getStringSet(KEY_EVENT_IDS, emptySet()).orEmpty().filter {
            statusLocked(it).state in setOf(SmsDispatchState.QUEUED, SmsDispatchState.FAILED_RETRYABLE)
        }
        if (pending.isEmpty()) return@synchronized emptyList()
        val boot = bootCount()
        preferences.edit(commit = true) {
            pending.forEach { eventId ->
                val current = statusLocked(eventId)
                val window = if (current.state == SmsDispatchState.QUEUED) CALLBACK_TIMEOUT_MS else RETRY_DELAY_MS
                val anchor = preferences.getLong(elapsedKey(eventId), -1L)
                val previousBoot = preferences.getInt(bootKey(eventId), -1)
                val sameBoot = !afterReboot && (boot < 0 || previousBoot < 0 || boot == previousBoot)
                val age = if (sameBoot && anchor >= 0L && elapsedRealtimeMs >= anchor) {
                    (elapsedRealtimeMs - anchor).coerceAtMost(window)
                } else 0L
                putLong(updatedAtKey(eventId), nowMs - age)
                putLong(retryAtKey(eventId), if (current.state == SmsDispatchState.FAILED_RETRYABLE) nowMs + window - age else 0L)
                putLong(elapsedKey(eventId), elapsedRealtimeMs - age)
                putInt(bootKey(eventId), boot)
            }
        }
        pending
    }

    // Share this editor with the per-part cleanup helper, then commit the batch once.
    @SuppressLint("UseKtx")
    fun clearResolved() = synchronized(LOCK) {
        val eventIds = preferences.getStringSet(KEY_EVENT_IDS, emptySet()).orEmpty()
        // Safety callbacks and retries can outlive visible history. Their non-PII
        // deduplication markers must remain until normal retention cleanup.
        val resolvedIds = eventIds.filter {
            statusLocked(it).isResolved && SafetySmsEvent.parse(it) == null
        }
        if (resolvedIds.isEmpty()) return@synchronized
        val editor = preferences.edit()
        resolvedIds.forEach { removeEventLocked(editor, it) }
        editor.putStringSet(KEY_EVENT_IDS, eventIds - resolvedIds.toSet()).commit()
    }

    // Preserve this API's actual Boolean commit result (KTX edit returns Unit).
    @SuppressLint("UseKtx")
    fun clearAll() = synchronized(LOCK) {
        preferences.edit().clear().commit()
    }

    private fun markFailureLocked(
        eventId: String,
        attempt: Int,
        resultCode: Int,
        nowMs: Long,
        elapsedRealtimeMs: Long
    ): SmsCallbackOutcome {
        val current = statusLocked(eventId)
        val retryable = attempt < current.maxAttempts
        val state = if (retryable) SmsDispatchState.FAILED_RETRYABLE else SmsDispatchState.FAILED_FINAL
        preferences.edit(commit = true) {
            putString(stateKey(eventId), state.name)
            putLong(updatedAtKey(eventId), nowMs)
            putLong(elapsedKey(eventId), elapsedRealtimeMs)
            putInt(bootKey(eventId), bootCount())
            putLong(retryAtKey(eventId), if (retryable) nowMs + RETRY_DELAY_MS else 0L)
            putInt(resultCodeKey(eventId), resultCode)
        }
        return if (retryable) SmsCallbackOutcome.FAILED_RETRYABLE else SmsCallbackOutcome.FAILED_FINAL
    }

    private fun pendingAgeLocked(
        eventId: String,
        current: SmsDispatchStatus,
        nowMs: Long,
        elapsedRealtimeMs: Long
    ): Long {
        val anchor = preferences.getLong(elapsedKey(eventId), -1L)
        val previousBoot = preferences.getInt(bootKey(eventId), -1)
        val boot = bootCount()
        val sameBoot = boot < 0 || previousBoot < 0 || boot == previousBoot
        val age = if (sameBoot && anchor >= 0L && elapsedRealtimeMs >= anchor) {
            elapsedRealtimeMs - anchor
        } else 0L
        val projectedUpdatedAtMs = nowMs - age
        // Guard the decision itself, not just TIME_SET/Worker recovery. A missing
        // or rebooted anchor receives one bounded wait, never an immediate retry.
        if (anchor < 0L || !sameBoot || elapsedRealtimeMs < anchor ||
            kotlin.math.abs(projectedUpdatedAtMs - current.updatedAtMs) > 1_000L
        ) {
            preferences.edit(commit = true) {
                putLong(updatedAtKey(eventId), projectedUpdatedAtMs)
                putLong(elapsedKey(eventId), elapsedRealtimeMs - age)
                putInt(bootKey(eventId), boot)
                putLong(retryAtKey(eventId), if (current.state == SmsDispatchState.FAILED_RETRYABLE) {
                    projectedUpdatedAtMs + RETRY_DELAY_MS
                } else 0L)
            }
        }
        return age
    }

    // The cleanup helper mutates one shared editor; the complete prune commits once.
    @SuppressLint("UseKtx")
    private fun pruneExpiredLocked(nowMs: Long, preserveEventIds: Set<String>): Int {
        val eventIds = preferences.getStringSet(KEY_EVENT_IDS, emptySet()).orEmpty()
        val expired = eventIds.filter { eventId ->
            val updatedAtMs = preferences.getLong(updatedAtKey(eventId), 0L)
            eventId !in preserveEventIds && updatedAtMs > 0L && nowMs - updatedAtMs >= RETENTION_MS
        }
        if (expired.isEmpty()) return 0

        val editor = preferences.edit()
        expired.forEach { removeEventLocked(editor, it) }
        editor.putStringSet(KEY_EVENT_IDS, eventIds - expired.toSet()).commit()
        return expired.size
    }

    private fun removeEventLocked(editor: android.content.SharedPreferences.Editor, eventId: String) {
        val attempts = preferences.getInt(attemptKey(eventId), 0)
        val totalParts = preferences.getInt(totalPartsKey(eventId), 0)
        for (attempt in 1..attempts) {
            for (partIndex in 0 until totalParts) {
                SmsCallbackStage.entries.forEach { stage ->
                    editor.remove(partKey(stage, eventId, attempt, partIndex))
                }
            }
        }
        editor
            .remove(stateKey(eventId))
            .remove(attemptKey(eventId))
            .remove(maxAttemptsKey(eventId))
            .remove(totalPartsKey(eventId))
            .remove(updatedAtKey(eventId))
            .remove(elapsedKey(eventId))
            .remove(bootKey(eventId))
            .remove(retryAtKey(eventId))
            .remove(resultCodeKey(eventId))
    }

    private fun statusLocked(eventId: String): SmsDispatchStatus {
        val stateName = preferences.getString(stateKey(eventId), null)
        var state = SmsDispatchState.entries.firstOrNull { it.name == stateName }
            ?: SmsDispatchState.NOT_QUEUED
        val attempt = preferences.getInt(attemptKey(eventId), 0)
        val parts = preferences.getInt(totalPartsKey(eventId), 0)
        if (state != SmsDispatchState.NOT_QUEUED && state != SmsDispatchState.DELIVERED && attempt > 0 && parts > 0) {
            // Heal pre-2.3.9 interrupted two-commit callbacks before any timeout or retry.
            val confirmed = listOf(SmsCallbackStage.DELIVERED, SmsCallbackStage.SENT).firstOrNull { stage ->
                (0 until parts).all { preferences.getBoolean(partKey(stage, eventId, attempt, it), false) }
            }
            val recoveredState = when (confirmed) {
                SmsCallbackStage.DELIVERED -> SmsDispatchState.DELIVERED
                SmsCallbackStage.SENT -> SmsDispatchState.SENT
                null -> state
            }
            if (recoveredState != state) {
                state = recoveredState
                preferences.edit(commit = true) {
                    putString(stateKey(eventId), state.name)
                    putLong(retryAtKey(eventId), 0L)
                    putInt(resultCodeKey(eventId), Activity.RESULT_OK)
                }
            }
        }
        return SmsDispatchStatus(
            state = state,
            attempt = attempt,
            maxAttempts = preferences.getInt(maxAttemptsKey(eventId), SmsRetryPolicy.EMERGENCY.maxAttempts),
            updatedAtMs = preferences.getLong(updatedAtKey(eventId), 0L),
            retryAtMs = preferences.getLong(retryAtKey(eventId), 0L),
            lastResultCode = preferences.getInt(resultCodeKey(eventId), Activity.RESULT_OK)
        )
    }

    private fun stateKey(eventId: String) = "state:$eventId"
    private fun attemptKey(eventId: String) = "attempt:$eventId"
    private fun maxAttemptsKey(eventId: String) = "max-attempts:$eventId"
    private fun totalPartsKey(eventId: String) = "parts:$eventId"
    private fun updatedAtKey(eventId: String) = "updated:$eventId"
    private fun elapsedKey(eventId: String) = "elapsed:$eventId"
    private fun bootKey(eventId: String) = "boot:$eventId"
    private fun retryAtKey(eventId: String) = "retry:$eventId"
    private fun resultCodeKey(eventId: String) = "result:$eventId"
    private fun testThrottleKey(contactId: Int) = "test-throttle:$contactId"
    private fun partKey(stage: SmsCallbackStage, eventId: String, attempt: Int, partIndex: Int) =
        "part:${stage.name}:$eventId:$attempt:$partIndex"

    companion object {
        const val FILE_NAME = "lifelink_sms_status"
        private const val KEY_EVENT_IDS = "event-ids"
        const val RETRY_DELAY_MS = 5 * 60 * 1_000L
        const val CALLBACK_TIMEOUT_MS = 2 * 60 * 1_000L
        const val TEST_SMS_COOLDOWN_MS = 60_000L
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1_000L
        const val MAX_ATTEMPTS = 3
        const val RESULT_CALLBACK_TIMEOUT = -10_001
        val LOCK = Any()

        fun isTestEvent(eventId: String): Boolean = eventId.startsWith("test:")

        fun isDailyEvent(eventId: String): Boolean = eventId.startsWith("daily:")
    }
}
