package com.example.monitoring

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

    @Suppress("DEPRECATION")
    private fun sendSentCallback(
        attempt: Int,
        partIndex: Int,
        totalParts: Int,
        resultCode: Int
    ) {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            action = SmsStatusReceiver.ACTION_SMS_SENT
            putExtra(SmsStatusReceiver.EXTRA_EVENT_ID, EVENT_ID)
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

    private suspend fun awaitState(expected: SmsDispatchState) {
        withTimeout(3_000L) {
            while (store.status(EVENT_ID).state != expected) delay(10L)
        }
    }

    companion object {
        private const val EVENT_ID = "emergency:200:1"
        private val NO_OP_RECEIVER = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = Unit
        }
    }
}
