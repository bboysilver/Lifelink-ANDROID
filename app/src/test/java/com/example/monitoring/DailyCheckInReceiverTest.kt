package com.example.monitoring

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.MonitoringStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

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
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun dueAlarmEnqueuesDurableWorkWithoutStartingForegroundService() {
        val store = MonitoringStore(context)
        store.configureDailyCheckIn(hour = 9, nowMs = 1_000L)
        store.deferDailyCheckInToNow(nowMs = 2_000L)

        DailyCheckInReceiver().onReceive(
            context,
            Intent(DailyCheckInScheduler.ACTION_PROMPT)
        )

        val work = WorkManager.getInstance(context)
            .getWorkInfosByTag(DailyCheckInWorker.WORK_TAG)
            .get(3, TimeUnit.SECONDS)
        assertFalse(work.isEmpty())
        assertNull(shadowOf(context).nextStartedService)
    }
}