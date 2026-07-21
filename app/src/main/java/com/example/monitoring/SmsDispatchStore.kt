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

data class SmsDispatchStatus(
    val state: SmsDispatchState,
    val attempt: Int,
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

    fun beginAttempt(eventId: String, totalParts: Int, nowMs: Long = System.currentTimeMillis()): Int? =
        synchronized(LOCK) {
            require(totalParts > 0) { "SMS must contain at least one part" }
            val current = statusLocked(eventId)
            val canStart = current.state == SmsDispatchState.NOT_QUEUED ||
                (current.state == SmsDispatchState.FAILED_RETRYABLE && nowMs >= current.retryAtMs)
            if (!canStart) return@synchronized null

            val attempt = current.attempt + 1
            preferences.edit()
                .putString(stateKey(eventId), SmsDispatchState.QUEUED.name)
                .putInt(attemptKey(eventId), attempt)
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

        val partKey = partKey(stage, eventId, attempt, partIndex)
        if (preferences.getBoolean(partKey, false)) return@synchronized SmsCallbackOutcome.IGNORED
        preferences.edit().putBoolean(partKey, true).commit()
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

    private fun markFailureLocked(
        eventId: String,
        attempt: Int,
        resultCode: Int,
        nowMs: Long
    ): SmsCallbackOutcome {
        val retryable = attempt < MAX_ATTEMPTS
        val state = if (retryable) SmsDispatchState.FAILED_RETRYABLE else SmsDispatchState.FAILED_FINAL
        preferences.edit()
            .putString(stateKey(eventId), state.name)
            .putLong(updatedAtKey(eventId), nowMs)
            .putLong(retryAtKey(eventId), if (retryable) nowMs + RETRY_DELAY_MS else 0L)
            .putInt(resultCodeKey(eventId), resultCode)
            .commit()
        return if (retryable) SmsCallbackOutcome.FAILED_RETRYABLE else SmsCallbackOutcome.FAILED_FINAL
    }

    private fun statusLocked(eventId: String): SmsDispatchStatus {
        val stateName = preferences.getString(stateKey(eventId), null)
        val state = SmsDispatchState.entries.firstOrNull { it.name == stateName }
            ?: SmsDispatchState.NOT_QUEUED
        return SmsDispatchStatus(
            state = state,
            attempt = preferences.getInt(attemptKey(eventId), 0),
            updatedAtMs = preferences.getLong(updatedAtKey(eventId), 0L),
            retryAtMs = preferences.getLong(retryAtKey(eventId), 0L),
            lastResultCode = preferences.getInt(resultCodeKey(eventId), Activity.RESULT_OK)
        )
    }

    private fun stateKey(eventId: String) = "state:$eventId"
    private fun attemptKey(eventId: String) = "attempt:$eventId"
    private fun totalPartsKey(eventId: String) = "parts:$eventId"
    private fun updatedAtKey(eventId: String) = "updated:$eventId"
    private fun retryAtKey(eventId: String) = "retry:$eventId"
    private fun resultCodeKey(eventId: String) = "result:$eventId"
    private fun partKey(stage: SmsCallbackStage, eventId: String, attempt: Int, partIndex: Int) =
        "part:${stage.name}:$eventId:$attempt:$partIndex"

    companion object {
        const val FILE_NAME = "lifelink_sms_status"
        const val RETRY_DELAY_MS = 5 * 60 * 1_000L
        const val CALLBACK_TIMEOUT_MS = 2 * 60 * 1_000L
        const val MAX_ATTEMPTS = 3
        const val RESULT_CALLBACK_TIMEOUT = -10_001
        val LOCK = Any()
    }
}
