package com.example

import android.os.Bundle
import android.os.SystemClock
import android.os.PowerManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
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
    private var pendingUserEntry = false
    private var leftByUser = false
    private val entryState: ActivityEntryState by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingUserEntry = isUserOpenIntent(intent)
        recordConfirmedEntry()
    }

    override fun onUserLeaveHint() {
        leftByUser = true
        super.onUserLeaveHint()
    }

    override fun onResume() {
        // Record a user return before lifecycle observers restore an expired service.
        recordConfirmedEntry()
        super.onResume()
    }

    private fun recordConfirmedEntry() {
        if (!(pendingUserEntry || leftByUser || observedKeyguardLocked) ||
            !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked
        ) return
        pendingUserEntry = false
        leftByUser = false
        observedKeyguardLocked = false
        recordInteraction("라이프링크 직접 열기·복귀")
    }

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
        if (!entryState.creationHandled) {
            entryState.creationHandled = true
            pendingUserEntry = isUserOpenIntent(intent)
            // Compose can start recovery immediately; explicit opening is already activity.
            recordConfirmedEntry()
        }
        SafetyNotificationCapability.cancelLegacyConfirmedNotifications(this)
        SafetyNotificationCapability.createAlertChannel(this)
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

    companion object {
        const val ACTION_USER_OPEN = "com.bboysilver.lifelink.action.USER_OPEN"

        fun openIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).setAction(ACTION_USER_OPEN)

        private fun isUserOpenIntent(intent: Intent?): Boolean =
            intent?.action == ACTION_USER_OPEN ||
                (intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER))
    }
}

internal class ActivityEntryState : ViewModel() {
    var creationHandled = false
}

internal object ActivityInteractionPolicy {
    fun shouldRecord(lastRecordedMs: Long, nowMs: Long): Boolean =
        lastRecordedMs < 0L || nowMs < lastRecordedMs || nowMs - lastRecordedMs >= 1_000L
}
