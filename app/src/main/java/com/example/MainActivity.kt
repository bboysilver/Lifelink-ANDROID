package com.example

import android.os.Bundle
import android.os.SystemClock
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

class MainActivity : ComponentActivity() {
    private val monitoringStore by lazy { MonitoringStore(this) }
    private var lastInteractionElapsedMs = -1L

    override fun onUserInteraction() {
        super.onUserInteraction()
        val elapsedMs = SystemClock.elapsedRealtime()
        if (lastInteractionElapsedMs >= 0L && elapsedMs - lastInteractionElapsedMs < 30_000L) return
        if (monitoringStore.recordActivity(System.currentTimeMillis(), "라이프링크 화면 직접 조작")) {
            lastInteractionElapsedMs = elapsedMs
        }
    }

    private val viewModel: LifeLinkViewModel by viewModels {
        LifeLinkViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
