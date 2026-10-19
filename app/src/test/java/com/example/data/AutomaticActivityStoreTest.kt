package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36])
class AutomaticActivityStoreTest {
    private lateinit var context: Context
    private lateinit var store: MonitoringStore
    private val now = 1_800_000_000_000L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE).edit().clear().commit()
        store = MonitoringStore(context)
        store.beginStart(now, elapsedRealtimeMs = 1_000)
    }

    @Test fun openingTheAppDoesNotOverwriteAutomaticEvidence() {
        assertTrue(store.recordAutomaticActivity(now + 1_000, "반복된 걸음 감지", now + 1_000, 2_000))
        assertTrue(store.recordActivity(now + 2_000, "라이프링크 화면 직접 조작", now + 2_000, 3_000))
        val snapshot = MonitoringStore(context).snapshot(now + 2_000)
        assertEquals(now + 2_000, snapshot.lastActivityMs)
        assertEquals(now + 1_000, snapshot.lastAutomaticActivityMs)
        assertEquals("반복된 걸음 감지", snapshot.lastAutomaticActivityReason)
        assertTrue(snapshot.lastAutomaticActivityApplied)
    }

    @Test fun delayedSensorEvidenceIsVisibleWithoutRollingBackTheTimer() {
        store.recordActivity(now + 10_000, "앱 직접 조작", now + 10_000, 11_000)
        val deadline = store.deadlineMs
        assertFalse(store.recordAutomaticActivity(now + 5_000, "걸음 수 증가 감지", now + 10_000, 11_000))
        val snapshot = store.snapshot(now + 10_000)
        assertEquals(deadline, snapshot.deadlineMs)
        assertEquals(now + 5_000, snapshot.lastAutomaticActivityMs)
        assertFalse(snapshot.lastAutomaticActivityApplied)
        store.recordAutomaticActivity(now + 4_000, "더 오래된 신호", now + 10_000, 11_000)
        assertEquals(now + 5_000, store.snapshot(now + 10_000).lastAutomaticActivityMs)
    }

    @Test fun invalidAndStoppedSignalsCannotCreateAutomaticEvidence() {
        assertFalse(store.recordAutomaticActivity(now + 1, "미래", now, 1_000))
        assertFalse(store.recordAutomaticActivity(0, "잘못된 시각", now, 1_000))
        store.stop(now)
        assertFalse(store.recordAutomaticActivity(now, "중지 후 신호", now, 1_000))
        assertEquals(0L, store.snapshot(now).lastAutomaticActivityMs)
    }

    @Test fun automaticEvidenceRebasesWithClockChangesWithoutChangingEventIdentity() {
        store.recordAutomaticActivity(now + 1_000, "잠금 해제", now + 1_000, 2_000)
        val event = store.inactivityEventMs
        assertTrue(store.rebaseAfterWallClockChange(now + 2_000 - 3_600_000, 3_000))
        val snapshot = store.snapshot(now + 2_000 - 3_600_000)
        assertEquals(now + 1_000 - 3_600_000, snapshot.lastAutomaticActivityMs)
        assertEquals(event, snapshot.inactivityEventMs)
    }

    @Test fun clearingDiagnosticEvidenceDoesNotResetSafetyState() {
        store.recordAutomaticActivity(now + 1_000, "걸음", now + 1_000, 2_000)
        val before = store.snapshot(now + 1_000)
        store.clearAutomaticActivity()
        val after = store.snapshot(now + 1_000)
        assertEquals(before.deadlineMs, after.deadlineMs)
        assertEquals(before.inactivityEventMs, after.inactivityEventMs)
        assertEquals(before.lastActivityMs, after.lastActivityMs)
        assertEquals(0L, after.lastAutomaticActivityMs)
    }

    @Test fun aFutureDiagnosticRecordCannotHideFreshValidSignals() {
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE).edit()
            .putLong("last_automatic_activity_ms", now + 3_600_000).commit()
        assertTrue(store.recordAutomaticActivity(now + 1_000, "잠금 해제", now + 1_000, 2_000))
        assertEquals(now + 1_000, store.snapshot(now + 1_000).lastAutomaticActivityMs)
    }
}
