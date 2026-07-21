package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmergencyRetryStoreTest {
    private lateinit var store: MonitoringStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE).edit().clear().commit()
        store = MonitoringStore(context)
    }

    @Test
    fun failedDispatchIsRetriedAfterFiveMinutes() {
        val deadline = 100L
        val firstAttempt = 1_000L

        assertTrue(store.canAttemptEmergency(deadline, firstAttempt))
        store.markEmergencyAttempt(deadline, firstAttempt)

        assertFalse(store.canAttemptEmergency(deadline, firstAttempt + 60_000L))
        assertTrue(
            store.canAttemptEmergency(
                deadline,
                firstAttempt + MonitoringStore.EMERGENCY_RETRY_INTERVAL_MS
            )
        )
    }

    @Test
    fun newDeadlineCanBeAttemptedImmediately() {
        store.markEmergencyAttempt(deadlineMs = 100L, nowMs = 1_000L)

        assertTrue(store.canAttemptEmergency(deadlineMs = 200L, nowMs = 1_001L))
    }
}
