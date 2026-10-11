package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.monitoring.SafetyNotificationCapability

internal data class SafetyPermissions(
    val sms: Boolean,
    val phone: Boolean,
    val activity: Boolean,
    val notification: Boolean
) {
    val allGranted: Boolean get() = sms && phone && activity && notification

    companion object {
        fun read(context: Context): SafetyPermissions = SafetyPermissions(
            sms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED,
            phone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED,
            activity = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED,
            notification = SafetyNotificationCapability.canPost(context)
        )
    }
}
