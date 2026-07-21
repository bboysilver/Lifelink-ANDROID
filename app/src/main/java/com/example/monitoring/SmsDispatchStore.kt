package com.example.monitoring

import android.app.Activity
import android.content.Context

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

class SmsDispatchStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun status(eventId: String): SmsDispatchStatus = synchronized(LOCK) {
        statusLocked(eventId)
    }

    fun beginAttempt(
        eventId: String,
        totalParts: Int,
        policy: SmsRetryPolicy = SmsRetryPolicy.EMERGENCY,
        nowMs: Long = System.currentTimeMillis()
    ): Int? = synchronized(LOCK) {
        require(totalParts > 0) { "SMS must contain at least one part" }
        val current = statusLocked(eventId)
        val canStart = current.state == SmsDispatchState.NOT_QUEUED ||
            (current.state == SmsDispatchState.FAILED_RETRYABLE && nowMs >= current.retryAtMs)
        if (!canStart) return@synchronized null

        val attempt = current.attempt + 1
        val eventIds = preferences.getStringSet(KEY_EVENT_IDS, emptySet()).orEmpty().toMutableSet()
        eventIds += eventId
        preferences.edit()
            .putStringSet(KEY_EVENT_IDS, eventIds)
            .putString(stateKey(eventId), SmsDispatchState.QUEUED.name)
            .putInt(attemptKey(eventId), attempt)
            .putInt(maxAttemptsKey(eventId), policy.maxAttempts)
            .putInt(totalPartsKey(eventId), totalParts)
            .putLong(updatedAtKey(eventId), nowMs)
            .putLong(retryAtKey(eventId), 0L)
            .putInt(resultCodeKey(eventId), Activity.RESULT_OK)
            .commit()
        attempt
    }

    fun markQueueFailure(
        eventId: String,
        attempt: Int,
        resultCode: Int,
        nowMs: Long = System.currentTimeMillis()
    ): SmsCallbackOutcome = synchronized(LOCK) {
        if (statusLocked(eventId).attempt != attempt) return@synchronized SmsCallbackOutcome.IGNORED
        markFailureLocked(eventId, attempt, resultCode, nowMs)
    }

    fun markQueuedTimeoutIfNeeded(eventId: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        synchronized(LOCK) {
            val current = statusLocked(eventId)
            if (
                current.state != SmsDispatchState.QUEUED ||
                nowMs - current.updatedAtMs < CALLBACK_TIMEOUT_MS
            ) {
                return@synchronized false
            }
            markFailureLocked(eventId, current.attempt, RESULT_CALLBACK_TIMEOUT, nowMs)
            true
        }

    fun recordCallback(
        stage: SmsCallbackStage,
        eventId: String,
        attempt: Int,
        partIndex: Int,
        totalParts: Int,
        resultCode: Int,
        nowMs: Long = System.currentTimeMillis()
    ): SmsCallbackOutcome = synchronized(LOCK) {
        val current = statusLocked(eventId)
        if (
            current.state == SmsDispatchState.FAILED_RETRYABLE ||
            current.state == SmsDispatchState.FAILED_FINAL ||
            current.state == SmsDispatchState.DELIVERED ||
            (current.state == SmsDispatchState.SENT && stage == SmsCallbackStage.SENT) ||
            current.attempt != attempt ||
            partIndex !in 0 until totalParts ||
            preferences.getInt(totalPartsKey(eventId), 0) != totalParts
        ) {
            return@synchronized SmsCallbackOutcome.IGNORED
        }

        if (stage == SmsCallbackStage.SENT && resultCode != Activity.RESULT_OK) {
            return@synchronized markFailureLocked(eventId, attempt, resultCode, nowMs)
        }
        if (stage == SmsCallbackStage.DELIVERED && resultCode != Activity.RESULT_OK) {
            preferences.edit()
                .putLong(updatedAtKey(eventId), nowMs)
                .putInt(resultCodeKey(eventId), resultCode)
                .commit()
            return@synchronized SmsCallbackOutcome.DELIVERY_UNCONFIRMED
        }

        val confirmedPartKey = partKey(stage, eventId, attempt, partIndex)
        if (preferences.getBoolean(confirmedPartKey, false)) return@synchronized SmsCallbackOutcome.IGNORED
        preferences.edit().putBoolean(confirmedPartKey, true).commit()
        val allPartsConfirmed = (0 until totalParts).all {
            preferences.getBoolean(partKey(stage, eventId, attempt, it), false)
        }
        if (!allPartsConfirmed) return@synchronized SmsCallbackOutcome.PENDING

        val newState = if (stage == SmsCallbackStage.SENT) {
            SmsDispatchState.SENT
        } else {
            SmsDispatchState.DELIVERED
        }
        preferences.edit()
            .putString(stateKey(eventId), newState.name)
            .putLong(updatedAtKey(eventId), nowMs)
            .putInt(resultCodeKey(eventId), resultCode)
            .commit()
        if (newState == SmsDispatchState.SENT) SmsCallbackOutcome.SENT else SmsCallbackOutcome.DELIVERED
    }

    fun reserveTestSend(contactId: Int, nowMs: Long = System.currentTimeMillis()): Long =
        synchronized(LOCK) {
            val key = testThrottleKey(contactId)
            if (preferences.contains(key)) {
                val remaining = TEST_SMS_COOLDOWN_MS - (nowMs - preferences.getLong(key, 0L))
                if (remaining > 0L) return@synchronized remaining
            }
            preferences.edit().putLong(key, nowMs).commit()
            0L
        }

    fun pruneExpired(nowMs: Long = System.currentTimeMillis()): Int = synchronized(LOCK) {
        pruneExpiredLocked(nowMs)
    }

    fun clearAll() = synchronized(LOCK) {
        preferences.edit().clear().commit()
    }

    private fun markFailureLocked(
        eventId: String,
        attempt: Int,
        resultCode: Int,
        nowMs: Long
    ): SmsCallbackOutcome {
        val current = statusLocked(eventId)
        val retryable = attempt < current.maxAttempts
        val state = if (retryable) SmsDispatchState.FAILED_RETRYABLE else SmsDispatchState.FAILED_FINAL
        preferences.edit()
            .putString(stateKey(eventId), state.name)
            .putLong(updatedAtKey(eventId), nowMs)
            .putLong(retryAtKey(eventId), if (retryable) nowMs + RETRY_DELAY_MS else 0L)
            .putInt(resultCodeKey(eventId), resultCode)
            .commit()
        return if (retryable) SmsCallbackOutcome.FAILED_RETRYABLE else SmsCallbackOutcome.FAILED_FINAL
    }

    private fun pruneExpiredLocked(nowMs: Long): Int {
        val eventIds = preferences.getStringSet(KEY_EVENT_IDS, emptySet()).orEmpty()
        val expired = eventIds.filter { eventId ->
            val updatedAtMs = preferences.getLong(updatedAtKey(eventId), 0L)
            updatedAtMs > 0L && nowMs - updatedAtMs >= RETENTION_MS
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
            .remove(retryAtKey(eventId))
            .remove(resultCodeKey(eventId))
    }

    private fun statusLocked(eventId: String): SmsDispatchStatus {
        val stateName = preferences.getString(stateKey(eventId), null)
        val state = SmsDispatchState.entries.firstOrNull { it.name == stateName }
            ?: SmsDispatchState.NOT_QUEUED
        return SmsDispatchStatus(
            state = state,
            attempt = preferences.getInt(attemptKey(eventId), 0),
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
    }
}
