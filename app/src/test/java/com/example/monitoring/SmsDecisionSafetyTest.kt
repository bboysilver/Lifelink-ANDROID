package com.example.monitoring

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsDecisionSafetyTest {
    private lateinit var context: Context
    private lateinit var store: SmsDispatchStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SmsDispatchStore.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store = SmsDispatchStore(context)
    }

    @Test fun callbackTimeoutUsesElapsedTimeWithoutWaitingForTheClockBroadcast() {
        store.beginAttempt(EVENT, 1, nowMs = WALL, elapsedRealtimeMs = 1_000L)
        assertFalse(store.markQueuedTimeoutIfNeeded(EVENT, WALL + 3_600_000L, 2_000L))
        assertFalse(store.markQueuedTimeoutIfNeeded(EVENT, WALL - 3_600_000L, 120_999L))
        assertTrue(store.markQueuedTimeoutIfNeeded(EVENT, WALL - 3_599_999L, 121_000L))
        assertEquals(SmsDispatchState.FAILED_RETRYABLE, store.status(EVENT).state)
    }

    @Test fun retryUsesElapsedTimeInBothClockDirectionsWithoutARebaseCall() {
        val attempt = store.beginAttempt(EVENT, 1, nowMs = WALL, elapsedRealtimeMs = 1_000L)!!
        store.markQueueFailure(EVENT, attempt, -1, WALL, 1_000L)
        assertNull(store.beginAttempt(EVENT, 1, nowMs = WALL + 3_600_000L, elapsedRealtimeMs = 2_000L))
        assertNull(store.beginAttempt(EVENT, 1, nowMs = WALL - 3_600_000L, elapsedRealtimeMs = 300_999L))
        assertEquals(2, store.beginAttempt(EVENT, 1, nowMs = WALL - 3_599_999L, elapsedRealtimeMs = 301_000L))
    }

    @Test fun lastPartAndTerminalStateAreStoredTogether() {
        val attempt = store.beginAttempt(EVENT, 2)!!
        store.recordCallback(SmsCallbackStage.SENT, EVENT, attempt, 0, 2, Activity.RESULT_OK)
        val preferences = context.getSharedPreferences(SmsDispatchStore.FILE_NAME, Context.MODE_PRIVATE)
        val observed = mutableListOf<String?>()
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "part:SENT:$EVENT:$attempt:1") observed += prefs.getString("state:$EVENT", null)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        store.recordCallback(SmsCallbackStage.SENT, EVENT, attempt, 1, 2, Activity.RESULT_OK)
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        assertEquals(listOf(SmsDispatchState.SENT.name), observed)
    }

    @Test fun legacyInterruptedSentCommitIsReconciledBeforeTimeoutOrRetry() {
        seedInterruptedCommit(SmsCallbackStage.SENT)
        assertFalse(store.markQueuedTimeoutIfNeeded(EVENT, WALL + 1_000_000L, 1_001_000L))
        assertEquals(SmsDispatchState.SENT, store.status(EVENT).state)
        assertNull(store.beginAttempt(EVENT, 2, nowMs = WALL + 2_000_000L, elapsedRealtimeMs = 2_001_000L))
    }

    @Test fun legacyInterruptedDeliveryCommitIsReconciledWithoutReplayingACallback() {
        seedInterruptedCommit(SmsCallbackStage.DELIVERED)
        assertEquals(SmsDispatchState.DELIVERED, store.status(EVENT).state)
        assertEquals(0L, store.status(EVENT).retryAtMs)
    }

    private fun seedInterruptedCommit(stage: SmsCallbackStage) {
        val attempt = store.beginAttempt(EVENT, 2, nowMs = WALL, elapsedRealtimeMs = 1_000L)!!
        context.getSharedPreferences(SmsDispatchStore.FILE_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("part:${stage.name}:$EVENT:$attempt:0", true)
            .putBoolean("part:${stage.name}:$EVENT:$attempt:1", true)
            .commit()
    }

    companion object {
        private const val EVENT = "emergency:1700000000000:1"
        private const val WALL = 1_700_000_000_000L
    }
}
