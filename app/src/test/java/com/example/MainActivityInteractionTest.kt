package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MonitoringStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityInteractionTest {
    @Test
    fun directInteractionResetsTimerWithoutAnotherUnlock() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE).edit().clear().commit()
        val store = MonitoringStore(context)
        store.beginStart(System.currentTimeMillis() - 11 * 60 * 60 * 1_000L)
        val previousDeadline = store.deadlineMs

        Robolectric.buildActivity(MainActivity::class.java).get().onUserInteraction()

        assertTrue(store.deadlineMs > previousDeadline)
        assertEquals("라이프링크 화면 직접 조작", store.snapshot().lastActivityReason)
    }
}
