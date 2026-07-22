package com.example.monitoring

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.data.MonitoringStore

class SmsSubscriptionMonitor(
    context: Context,
    private val onChanged: (SmsSetupState) -> Unit
) {
    private val appContext = context.applicationContext
    private val subscriptionManager = appContext.getSystemService(SubscriptionManager::class.java)
    private val deviceManager = SmsDeviceManager(appContext, MonitoringStore(appContext))
    private var started = false
    private val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() {
            onChanged(deviceManager.inspect())
        }
    }

    fun start(): Boolean = try {
        if (!started) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                subscriptionManager.addOnSubscriptionsChangedListener(
                    ContextCompat.getMainExecutor(appContext),
                    listener
                )
            } else {
                @Suppress("DEPRECATION")
                subscriptionManager.addOnSubscriptionsChangedListener(listener)
            }
            started = true
        }
        true
    } catch (_: RuntimeException) {
        false
    }

    fun stop() {
        if (!started) return
        subscriptionManager.removeOnSubscriptionsChangedListener(listener)
        started = false
    }
}
