package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.monitoring.SmsDispatchState
import com.example.monitoring.SmsDispatchStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36])
class PreferenceWriteModeTest {
    private lateinit var context: Context
    private val preferences = mutableMapOf<String, RecordingPreferences>()

    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        listOf("lifelink_monitoring", SmsDispatchStore.FILE_NAME).forEach { name ->
            val delegate = base.getSharedPreferences(name, Context.MODE_PRIVATE)
            delegate.edit().clear().commit()
            preferences[name] = RecordingPreferences(delegate)
        }
        context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                preferences[name] ?: super.getSharedPreferences(name, mode)
        }
    }

    @Test fun smsAttemptIsCommittedSynchronouslyAndSurvivesStoreRecreation() {
        val store = SmsDispatchStore(context)
        assertEquals(1, store.beginAttempt("test:1000:1", 1, nowMs = 1_000))
        val recording = preferences.getValue(SmsDispatchStore.FILE_NAME)
        assertEquals(1, recording.commits)
        assertEquals(0, recording.applies)
        assertEquals(SmsDispatchState.QUEUED, SmsDispatchStore(context).status("test:1000:1").state)
    }

    @Test fun dailyConfigurationStillUsesDurableCommit() {
        val store = MonitoringStore(context)
        val due = store.configureDailyCheckIn(9, 1_700_000_000_000L)
        val recording = preferences.getValue("lifelink_monitoring")
        assertEquals(1, recording.commits)
        assertEquals(0, recording.applies)
        assertEquals(due, MonitoringStore(context).dailyNextDueAtMs)
    }

    @Test fun ordinaryPreferenceUpdatesRemainAsynchronous() {
        MonitoringStore(context).apply { monitorHours = 6; simpleHomeEnabled = true }
        val recording = preferences.getValue("lifelink_monitoring")
        assertEquals(0, recording.commits)
        assertEquals(2, recording.applies)
    }

    @Test fun booleanCommitResultsAreNotReplacedByInventedSuccess() {
        val store = MonitoringStore(context)
        val due = store.configureDailyCheckIn(9, 1_700_000_000_000L)
        store.beginSos(1_000)
        preferences.getValue("lifelink_monitoring").failCommit = true
        assertFalse(store.claimWatchdogAlert(123))
        assertFalse(store.markDailyCheckInPrompted(due, due, 0))
        assertFalse(store.advanceDailyCheckIn(due))
        assertFalse(store.cancelPendingSos())
        assertEquals(due, store.dailyNextDueAtMs)
        assertEquals(1_000L, store.pendingSosEventMs)
        preferences.getValue(SmsDispatchStore.FILE_NAME).failCommit = true
        assertFalse(SmsDispatchStore(context).clearAll())
    }

    private class RecordingPreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        var commits = 0
        var applies = 0
        var failCommit = false

        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor {
                override fun putString(key: String, value: String?) = apply { editor.putString(key, value) }
                override fun putStringSet(key: String, values: Set<String>?) = apply { editor.putStringSet(key, values) }
                override fun putInt(key: String, value: Int) = apply { editor.putInt(key, value) }
                override fun putLong(key: String, value: Long) = apply { editor.putLong(key, value) }
                override fun putFloat(key: String, value: Float) = apply { editor.putFloat(key, value) }
                override fun putBoolean(key: String, value: Boolean) = apply { editor.putBoolean(key, value) }
                override fun remove(key: String) = apply { editor.remove(key) }
                override fun clear() = apply { editor.clear() }
                override fun commit(): Boolean {
                    commits++
                    return !failCommit && editor.commit()
                }
                override fun apply() { applies++; editor.apply() }
            }
        }
    }
}
