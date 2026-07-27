package com.example.monitoring

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.DailyCheckInPhase
import com.example.data.MonitoringStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class TimeChangeReceiverTest {
    private lateinit var context: Application
    private lateinit var store: MonitoringStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = MonitoringStore(context)
        store.completeSetup()
    }

    @Test
    fun timezoneChangeRecalculatesAnUpcomingLocalCheckIn() {
        store.configureDailyCheckIn(hour = 9, nowMs = 1_700_000_000_000L)
        val oldDueAtMs = store.dailyNextDueAtMs

        TimeChangeReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))

        assertTrue(store.dailyNextDueAtMs > System.currentTimeMillis())
        assertTrue(store.dailyNextDueAtMs != oldDueAtMs)
    }

    @Test
    fun clockChangePreservesAnAlreadyDisplayedResponseWindow() {
        val nowMs = System.currentTimeMillis()
        val dueAtMs = nowMs - 30 * 60 * 1_000L
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("daily_check_in_enabled", true)
            .putInt("daily_check_in_hour", 9)
            .putLong("daily_next_due_at_ms", dueAtMs)
            .putLong("daily_prompted_due_at_ms", dueAtMs)
            .putLong("daily_response_deadline_at_ms", nowMs + 90 * 60 * 1_000L)
            .commit()

        TimeChangeReceiver().onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        assertEquals(dueAtMs, store.dailyNextDueAtMs)
        assertEquals(DailyCheckInPhase.DUE, store.dailyCheckInStatus().phase)

    }
}
