package com.example

import android.app.Application
import android.content.Context
import android.app.KeyguardManager
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.MonitoringStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class MainActivityInteractionTest {
    private lateinit var context: Application
    private lateinit var store: MonitoringStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE).edit().clear().commit()
        store = MonitoringStore(context)
        store.beginStart(nowMs = System.currentTimeMillis() - 1_000L)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
    }

    @Test
    fun directInteractionResetsTimerWithoutAnotherUnlock() {
        val previousDeadline = store.deadlineMs
        ShadowSystemClock.advanceBy(Duration.ofHours(11))

        Robolectric.buildActivity(MainActivity::class.java).get().onUserInteraction()

        assertTrue(store.deadlineMs > previousDeadline)
        assertEquals("라이프링크 화면 직접 조작", store.snapshot().lastActivityReason)
    }

    @Test
    fun menuInteractionAfterTenSecondsResetsTheDeadlineAgain() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        activity.onUserInteraction()
        val deadline = store.deadlineMs
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))

        activity.onUserInteraction()

        assertTrue(store.deadlineMs > deadline)
        assertEquals(12 * 3_600L, store.snapshot().remainingSeconds)
    }

    @Test
    fun rapidTouchEventsAreCoalescedForOnlyOneSecond() {
        assertTrue(ActivityInteractionPolicy.shouldRecord(-1L, 10_000L))
        assertFalse(ActivityInteractionPolicy.shouldRecord(10_000L, 10_200L))
        assertFalse(ActivityInteractionPolicy.shouldRecord(10_000L, 10_999L))
        assertTrue(ActivityInteractionPolicy.shouldRecord(10_000L, 11_000L))
        assertTrue(ActivityInteractionPolicy.shouldRecord(10_000L, 20_000L))
    }

    @Test
    fun backwardsElapsedClockDoesNotPreventNewInteraction() {
        assertTrue(ActivityInteractionPolicy.shouldRecord(10_000L, 1_000L))
    }

    @Test
    fun returningToUnlockedAppResetsWithoutWaitingForATouchOrBroadcast() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val keyguard = shadowOf(context.getSystemService(KeyguardManager::class.java))
        keyguard.setKeyguardLocked(true)
        activity.onWindowFocusChanged(false)
        val deadline = store.deadlineMs
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        keyguard.setKeyguardLocked(false)

        activity.onWindowFocusChanged(true)

        assertTrue(store.deadlineMs > deadline)
        assertEquals("라이프링크 화면 사용 확인", store.snapshot().lastActivityReason)
    }

    @Test
    fun lockedScreenOrNonInteractiveFocusDoesNotResetActivity() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val deadline = store.deadlineMs
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        activity.onWindowFocusChanged(true)
        assertEquals(deadline, store.deadlineMs)

        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        activity.onWindowFocusChanged(true)
        assertEquals(deadline, store.deadlineMs)
    }

    @Test
    fun focusWithoutALockTransitionDoesNotCountAnAutomaticWakeAsActivity() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val deadline = store.deadlineMs
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))

        activity.onWindowFocusChanged(true)

        assertEquals(deadline, store.deadlineMs)
    }
}
