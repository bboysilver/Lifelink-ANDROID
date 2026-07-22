package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventLogDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteBeforeRemovesOnlyExpiredLogs() = runTest {
        val dao = database.eventLogDao()
        dao.insertLog(EventLog(timestamp = 100L, type = "OLD", message = "old"))
        dao.insertLog(EventLog(timestamp = 200L, type = "KEEP", message = "keep"))

        assertEquals(1, dao.deleteBefore(150L))
        assertEquals(listOf("KEEP"), dao.getAllEventLogs().first().map { it.type })
    }
}