package com.example.monitoring

import android.content.Context
import android.app.Activity
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsDispatchLifecycleTest {
    private lateinit var store: SmsDispatchStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(SmsDispatchStore.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = SmsDispatchStore(context)
    }

    @Test
    fun testSmsFailureIsFinalAfterOneAttempt() {
        val eventId = "test:100:1"
        val attempt = store.beginAttempt(
            eventId,
            totalParts = 1,
            policy = SmsRetryPolicy.ONE_SHOT,
            nowMs = 1_000L
        )!!

        val outcome = store.recordCallback(
            SmsCallbackStage.SENT,
            eventId,
            attempt,
            partIndex = 0,
            totalParts = 1,
            resultCode = SmsManager.RESULT_ERROR_NO_SERVICE,
            nowMs = 2_000L
        )

        assertEquals(SmsCallbackOutcome.FAILED_FINAL, outcome)
        assertEquals(1, store.status(eventId).maxAttempts)
        assertEquals(0L, store.status(eventId).retryAtMs)
    }

    @Test
    fun testSmsCooldownPreventsRapidDuplicateRequests() {
        assertEquals(0L, store.reserveTestSend(contactId = 7, nowMs = 10_000L))
        assertEquals(
            SmsDispatchStore.TEST_SMS_COOLDOWN_MS - 1_000L,
            store.reserveTestSend(contactId = 7, nowMs = 11_000L)
        )
        assertEquals(
            0L,
            store.reserveTestSend(
                contactId = 7,
                nowMs = 10_000L + SmsDispatchStore.TEST_SMS_COOLDOWN_MS
            )
        )
    }

    @Test
    fun oldDispatchStateIsPrunedAfterRetentionPeriod() {
        val oldEvent = "emergency:100:1"
        store.beginAttempt(oldEvent, totalParts = 1, nowMs = 1_000L)

        val removed = store.pruneExpired(1_000L + SmsDispatchStore.RETENTION_MS)

        assertEquals(1, removed)
        assertEquals(SmsDispatchState.NOT_QUEUED, store.status(oldEvent).state)
    }

    @Test
    fun retentionKeepsAllRecipientsOfAnUnfinishedIncidentIncludingSentOnes() {
        val sentEvent = "sos:100:1"
        val pendingEvent = "sos:100:2"
        val expiredTest = "test:100:3"
        val sentAttempt = store.beginAttempt(sentEvent, 1, nowMs = 1_000L)!!
        store.recordCallback(SmsCallbackStage.SENT, sentEvent, sentAttempt, 0, 1, Activity.RESULT_OK, 2_000L)
        store.beginAttempt(pendingEvent, 1, nowMs = 1_000L)
        store.beginAttempt(expiredTest, 1, SmsRetryPolicy.ONE_SHOT, 1_000L)

        val removed = store.pruneExpired(
            nowMs = 2_000L + SmsDispatchStore.RETENTION_MS,
            preserveEventIds = setOf(sentEvent, pendingEvent)
        )

        assertEquals(1, removed)
        assertEquals(SmsDispatchState.SENT, store.status(sentEvent).state)
        assertEquals(SmsDispatchState.QUEUED, store.status(pendingEvent).state)
        assertEquals(SmsDispatchState.NOT_QUEUED, store.status(expiredTest).state)
        assertEquals(1, store.status(sentEvent).attempt)
        assertEquals(null, store.beginAttempt(sentEvent, 1, nowMs = 2_001L + SmsDispatchStore.RETENTION_MS))
    }

    @Test
    fun retentionRemovesProtectedStatesAfterTheirIncidentCompletes() {
        val event = "sos:100:1"
        val attempt = store.beginAttempt(event, 1, nowMs = 1_000L)!!
        store.recordCallback(SmsCallbackStage.SENT, event, attempt, 0, 1, Activity.RESULT_OK, 2_000L)
        val expiredAt = 2_000L + SmsDispatchStore.RETENTION_MS
        assertEquals(0, store.pruneExpired(expiredAt, preserveEventIds = setOf(event)))

        assertEquals(1, store.pruneExpired(expiredAt))
        assertEquals(SmsDispatchState.NOT_QUEUED, store.status(event).state)
    }

    @Test
    fun clearAllRemovesDispatchAndCooldownState() {
        val eventId = "emergency:200:2"
        store.beginAttempt(eventId, totalParts = 1, nowMs = 2_000L)
        store.reserveTestSend(contactId = 2, nowMs = 2_000L)

        store.clearAll()

        assertEquals(SmsDispatchState.NOT_QUEUED, store.status(eventId).state)
        assertTrue(store.reserveTestSend(contactId = 2, nowMs = 2_001L) == 0L)
    }
}
