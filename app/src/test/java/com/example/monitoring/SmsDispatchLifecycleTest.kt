package com.example.monitoring

import android.content.Context
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
    fun clearAllRemovesDispatchAndCooldownState() {
        val eventId = "emergency:200:2"
        store.beginAttempt(eventId, totalParts = 1, nowMs = 2_000L)
        store.reserveTestSend(contactId = 2, nowMs = 2_000L)

        store.clearAll()

        assertEquals(SmsDispatchState.NOT_QUEUED, store.status(eventId).state)
        assertTrue(store.reserveTestSend(contactId = 2, nowMs = 2_001L) == 0L)
    }
}
