package com.example.monitoring

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.AppDatabase
import com.example.data.Contact
import com.example.data.SafetyIncidentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SafetySmsRetryWorkerTest {
    private lateinit var context: Application

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        context.getSharedPreferences(SmsDispatchStore.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(context).clearAllTables()
        }
    }

    @Test
    fun recoveryWorkerExecutesAndReschedulesAPendingImmutableIncident() = runBlocking {
        SafetyIncidentRepository(AppDatabase.getDatabase(context)).getOrCreate(
            incidentId = "emergency:5000",
            type = "emergency",
            occurredAtMs = 5_000L,
            deviceAlias = "테스트 기기",
            message = "저장된 메시지",
            batteryPercent = 20,
            subscriptionId = 1,
            contacts = listOf(Contact(id = 5, name = "보호자", phoneNumber = "01012345678")),
            nowMs = 5_001L
        )
        val worker = TestListenableWorkerBuilder<SafetySmsRetryWorker>(context).build()

        val result = worker.startWork().get(5, TimeUnit.SECONDS)

        assertTrue(result is ListenableWorker.Result.Success)
        val scheduled = WorkManager.getInstance(context)
            .getWorkInfosByTag(SafetySmsRetryWorker.WORK_TAG)
            .get(3, TimeUnit.SECONDS)
        assertTrue(scheduled.isNotEmpty())
    }
}
