package com.example.monitoring

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.MonitoringStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DailyCheckInReceiverTest {
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun dueAlarmStartsDailyEvaluationWhenMonitoringIsStopped() {
        val store = MonitoringStore(context)
        store.configureDailyCheckIn(hour = 9, nowMs = 1_000L)
        store.deferDailyCheckInToNow(nowMs = 2_000L)

        DailyCheckInReceiver().onReceive(
            context,
            Intent(DailyCheckInScheduler.ACTION_PROMPT)
        )

        assertEquals(
            MonitoringService.ACTION_EVALUATE_DAILY,
            shadowOf(context).nextStartedService.action
        )
    }
}