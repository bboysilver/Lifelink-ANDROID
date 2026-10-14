package com.example

import android.os.Bundle
import android.os.SystemClock
import android.os.PowerManager
import android.app.KeyguardManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.LifeLinkApp
import com.example.ui.LifeLinkViewModel
import com.example.ui.LifeLinkViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import com.example.data.MonitoringStore
import com.example.monitoring.SafetyNotificationCapability

class MainActivity : ComponentActivity() {
    private val monitoringStore by lazy { MonitoringStore(this) }
    private var lastInteractionElapsedMs = -1L
    private var observedKeyguardLocked = false

    override fun onUserInteraction() {
        super.onUserInteraction()
        recordInteraction("라이프링크 화면 직접 조작")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (locked) observedKeyguardLocked = true
        // Require an observed lock/unlock transition, not just an automatic screen wake.
        if (hasFocus && observedKeyguardLocked &&
            getSystemService(PowerManager::class.java).isInteractive && !locked
        ) {
            observedKeyguardLocked = false
            recordInteraction("라이프링크 화면 사용 확인")
        }
    }

    override fun onPause() {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            observedKeyguardLocked = true
        }
        super.onPause()
    }

    private fun recordInteraction(reason: String) {
        val elapsedMs = SystemClock.elapsedRealtime()
        if (!ActivityInteractionPolicy.shouldRecord(lastInteractionElapsedMs, elapsedMs)) return
        if (monitoringStore.recordActivity(System.currentTimeMillis(), reason)) {
            lastInteractionElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    private val viewModel: LifeLinkViewModel by viewModels {
        LifeLinkViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SafetyNotificationCapability.cancelLegacyConfirmedNotifications(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LifeLinkApp(viewModel = viewModel)
                }
            }
        }
    }
}

internal object ActivityInteractionPolicy {
    fun shouldRecord(lastRecordedMs: Long, nowMs: Long): Boolean =
        lastRecordedMs < 0L || nowMs < lastRecordedMs || nowMs - lastRecordedMs >= 1_000L
}
