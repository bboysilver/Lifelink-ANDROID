package com.example.monitoring

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SmsStatusReceiverTest {
    private lateinit var context: Context
    private lateinit var store: SmsDispatchStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SmsDispatchStore.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = SmsDispatchStore(context)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun orderedBroadcastResultCodeIsPreservedForAsyncFailure() = runBlocking {
        val attempt = store.beginAttempt(EVENT_ID, totalParts = 1, nowMs = 1_000L)!!

        sendSentCallback(
            attempt = attempt,
            partIndex = 0,
            totalParts = 1,
            resultCode = SmsManager.RESULT_ERROR_NO_SERVICE
        )

        awaitState(SmsDispatchState.FAILED_RETRYABLE)
        assertEquals(SmsManager.RESULT_ERROR_NO_SERVICE, store.status(EVENT_ID).lastResultCode)
    }

    @Test
    fun multipartOrderedCallbacksBecomeSentAfterLastPart() = runBlocking {
        val attempt = store.beginAttempt(EVENT_ID, totalParts = 2, nowMs = 1_000L)!!

        sendSentCallback(attempt, partIndex = 0, totalParts = 2, resultCode = Activity.RESULT_OK)
        sendSentCallback(attempt, partIndex = 1, totalParts = 2, resultCode = Activity.RESULT_OK)

        awaitState(SmsDispatchState.SENT)
    }


    @Test
    fun dailyFailureSchedulesDurableRetryWork() = runBlocking {
        val eventId = "daily:200:1"
        val attempt = store.beginAttempt(eventId, totalParts = 1, nowMs = 1_000L)!!

        sendSentCallback(
            attempt = attempt,
            partIndex = 0,
            totalParts = 1,
            resultCode = SmsManager.RESULT_ERROR_NO_SERVICE,
            eventId = eventId
        )

        awaitState(SmsDispatchState.FAILED_RETRYABLE, eventId)
        val work = withTimeout(3_000L) {
            var scheduled = emptyList<androidx.work.WorkInfo>()
            while (scheduled.isEmpty()) {
                scheduled = WorkManager.getInstance(context)
                    .getWorkInfosByTag(DailyCheckInWorker.WORK_TAG)
                    .get(3, TimeUnit.SECONDS)
                if (scheduled.isEmpty()) delay(10L)
            }
            scheduled
        }
        assertFalse(work.isEmpty())
    }
    @Suppress("DEPRECATION")
    private fun sendSentCallback(
        attempt: Int,
        partIndex: Int,
        totalParts: Int,
        resultCode: Int,
        eventId: String = EVENT_ID
    ) {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            action = SmsStatusReceiver.ACTION_SMS_SENT
            putExtra(SmsStatusReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(SmsStatusReceiver.EXTRA_CONTACT_NAME, "보호자")
            putExtra(SmsStatusReceiver.EXTRA_PHONE_SUFFIX, "1234")
            putExtra(SmsStatusReceiver.EXTRA_ATTEMPT, attempt)
            putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, partIndex)
            putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, totalParts)
        }
        context.sendOrderedBroadcast(
            intent,
            null,
            NO_OP_RECEIVER,
            null,
            resultCode,
            null,
            null
        )
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun awaitState(expected: SmsDispatchState, eventId: String = EVENT_ID) {
        withTimeout(3_000L) {
            while (store.status(eventId).state != expected) delay(10L)
        }
    }

    companion object {
        private const val EVENT_ID = "emergency:200:1"
        private val NO_OP_RECEIVER = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = Unit
        }
    }
}
