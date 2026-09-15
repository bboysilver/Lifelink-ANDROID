package com.example.monitoring

import android.app.Activity
import android.content.Context
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsDispatchStoreTest {
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
    fun multipartSmsIsSentOnlyAfterEveryPartSucceeds() {
        val attempt = store.beginAttempt(EVENT_ID, totalParts = 2, nowMs = 1_000L)!!

        val first = store.recordCallback(
            SmsCallbackStage.SENT,
            EVENT_ID,
            attempt,
            partIndex = 0,
            totalParts = 2,
            resultCode = Activity.RESULT_OK,
            nowMs = 2_000L
        )
        val second = store.recordCallback(
            SmsCallbackStage.SENT,
            EVENT_ID,
            attempt,
            partIndex = 1,
            totalParts = 2,
            resultCode = Activity.RESULT_OK,
            nowMs = 2_001L
        )

        assertEquals(SmsCallbackOutcome.PENDING, first)
        assertEquals(SmsCallbackOutcome.SENT, second)
        assertEquals(SmsDispatchState.SENT, store.status(EVENT_ID).state)
    }

    @Test
    fun sendFailureRetriesAfterFiveMinutes() {
        val firstAttempt = store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = 1_000L)!!

        val failure = store.recordCallback(
            SmsCallbackStage.SENT,
            EVENT_ID,
            firstAttempt,
            partIndex = 0,
            totalParts = 1,
            resultCode = SmsManager.RESULT_ERROR_NO_SERVICE,
            nowMs = 2_000L
        )

        assertEquals(SmsCallbackOutcome.FAILED_RETRYABLE, failure)
        assertNull(store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = 2_001L))
        assertEquals(
            2,
            store.beginAttempt(
                EVENT_ID,
                totalParts = 1,
                nowMs = 2_000L + SmsDispatchStore.RETRY_DELAY_MS
            )
        )
    }

    @Test
    fun thirdSendFailureBecomesFinal() {
        var nowMs = 1_000L
        repeat(SmsDispatchStore.MAX_ATTEMPTS) { index ->
            val attempt = store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = nowMs)!!
            val outcome = store.recordCallback(
                SmsCallbackStage.SENT,
                EVENT_ID,
                attempt,
                partIndex = 0,
                totalParts = 1,
                resultCode = SmsManager.RESULT_ERROR_RADIO_OFF,
                nowMs = nowMs + 1L
            )
            val expected = if (index == SmsDispatchStore.MAX_ATTEMPTS - 1) {
                SmsCallbackOutcome.FAILED_FINAL
            } else {
                SmsCallbackOutcome.FAILED_RETRYABLE
            }
            assertEquals(expected, outcome)
            nowMs += SmsDispatchStore.RETRY_DELAY_MS + 1L
        }

        assertEquals(SmsDispatchState.FAILED_FINAL, store.status(EVENT_ID).state)
    }

    @Test
    fun deliveryFailureDoesNotResendAnAlreadySentMessage() {
        val attempt = store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = 1_000L)!!
        store.recordCallback(
            SmsCallbackStage.SENT,
            EVENT_ID,
            attempt,
            partIndex = 0,
            totalParts = 1,
            resultCode = Activity.RESULT_OK,
            nowMs = 2_000L
        )

        val delivery = store.recordCallback(
            SmsCallbackStage.DELIVERED,
            EVENT_ID,
            attempt,
            partIndex = 0,
            totalParts = 1,
            resultCode = Activity.RESULT_CANCELED,
            nowMs = 3_000L
        )

        assertEquals(SmsCallbackOutcome.DELIVERY_UNCONFIRMED, delivery)
        assertEquals(SmsDispatchState.SENT, store.status(EVENT_ID).state)
        assertNull(store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = Long.MAX_VALUE))
    }

    @Test
    fun lateCallbackFromPreviousAttemptIsIgnored() {
        val firstAttempt = store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = 1_000L)!!
        store.markQueueFailure(EVENT_ID, firstAttempt, -1, nowMs = 2_000L)
        val secondAttempt = store.beginAttempt(
            EVENT_ID,
            totalParts = 1,
            nowMs = 2_000L + SmsDispatchStore.RETRY_DELAY_MS
        )!!

        val outcome = store.recordCallback(
            SmsCallbackStage.SENT,
            EVENT_ID,
            firstAttempt,
            partIndex = 0,
            totalParts = 1,
            resultCode = Activity.RESULT_OK,
            nowMs = 400_000L
        )

        assertEquals(SmsCallbackOutcome.IGNORED, outcome)
        assertEquals(secondAttempt, store.status(EVENT_ID).attempt)
        assertEquals(SmsDispatchState.QUEUED, store.status(EVENT_ID).state)
    }

    @Test
    fun clearingVisibleHistoryKeepsSuccessfulAndPendingSafetyRecipients() {
        val activeEventId = "emergency:200:2"
        store.beginAttempt(activeEventId, totalParts = 1, nowMs = 1_000L)
        val completedAttempt = store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = 1_000L)!!
        store.recordCallback(
            SmsCallbackStage.SENT,
            EVENT_ID,
            completedAttempt,
            partIndex = 0,
            totalParts = 1,
            resultCode = Activity.RESULT_OK,
            nowMs = 2_000L
        )

        store.clearResolved()

        assertEquals(SmsDispatchState.SENT, store.status(EVENT_ID).state)
        assertEquals(SmsDispatchState.QUEUED, store.status(activeEventId).state)
        assertNull(store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = 400_000L))
    }

    @Test
    fun lateSuccessAfterTimeoutCancelsRetryBeforeAnotherAttempt() {
        val attempt = store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = 1_000L)!!
        store.markQueuedTimeoutIfNeeded(EVENT_ID, 1_000L + SmsDispatchStore.CALLBACK_TIMEOUT_MS)

        val outcome = store.recordCallback(
            SmsCallbackStage.SENT, EVENT_ID, attempt, 0, 1, Activity.RESULT_OK,
            nowMs = 2_000L + SmsDispatchStore.CALLBACK_TIMEOUT_MS
        )

        assertEquals(SmsCallbackOutcome.SENT, outcome)
        assertEquals(0L, store.status(EVENT_ID).retryAtMs)
        assertNull(store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = 500_000L))
    }

    @Test
    fun lateMultipartSuccessPreservesEarlierConfirmedParts() {
        val attempt = store.beginAttempt(EVENT_ID, totalParts = 2, nowMs = 1_000L)!!
        store.recordCallback(SmsCallbackStage.SENT, EVENT_ID, attempt, 0, 2, Activity.RESULT_OK, 2_000L)
        store.markQueuedTimeoutIfNeeded(EVENT_ID, 1_000L + SmsDispatchStore.CALLBACK_TIMEOUT_MS)

        val outcome = store.recordCallback(
            SmsCallbackStage.SENT, EVENT_ID, attempt, 1, 2, Activity.RESULT_OK,
            nowMs = 2_000L + SmsDispatchStore.CALLBACK_TIMEOUT_MS
        )

        assertEquals(SmsCallbackOutcome.SENT, outcome)
        assertNull(store.beginAttempt(EVENT_ID, totalParts = 2, nowMs = 500_000L))
    }

    @Test
    fun lateDeliveryAfterFinalTimeoutStillConfirmsTheSentMessage() {
        val attempt = store.beginAttempt(EVENT_ID, 1, SmsRetryPolicy.ONE_SHOT, 1_000L)!!
        store.markQueuedTimeoutIfNeeded(EVENT_ID, 1_000L + SmsDispatchStore.CALLBACK_TIMEOUT_MS)

        val outcome = store.recordCallback(
            SmsCallbackStage.DELIVERED, EVENT_ID, attempt, 0, 1, Activity.RESULT_OK,
            nowMs = 2_000L + SmsDispatchStore.CALLBACK_TIMEOUT_MS
        )

        assertEquals(SmsCallbackOutcome.DELIVERED, outcome)
        assertEquals(SmsDispatchState.DELIVERED, store.status(EVENT_ID).state)
    }

    @Test
    fun queueExceptionCannotOverwriteAlreadyConfirmedSuccess() {
        val attempt = store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = 1_000L)!!
        store.recordCallback(SmsCallbackStage.SENT, EVENT_ID, attempt, 0, 1, Activity.RESULT_OK, 2_000L)

        assertEquals(
            SmsCallbackOutcome.IGNORED,
            store.markQueueFailure(EVENT_ID, attempt, EmergencySmsSender.RESULT_QUEUE_EXCEPTION, 2_001L)
        )
        assertEquals(SmsDispatchState.SENT, store.status(EVENT_ID).state)
    }

    @Test
    fun clearingHistoryStillRemovesFinishedTestState() {
        val eventId = "test:200:1"
        val attempt = store.beginAttempt(eventId, 1, SmsRetryPolicy.ONE_SHOT, 1_000L)!!
        store.recordCallback(SmsCallbackStage.SENT, eventId, attempt, 0, 1, Activity.RESULT_OK, 2_000L)

        store.clearResolved()

        assertEquals(SmsDispatchState.NOT_QUEUED, store.status(eventId).state)
    }
    companion object {
        private const val EVENT_ID = "emergency:100:1"
    }
}
