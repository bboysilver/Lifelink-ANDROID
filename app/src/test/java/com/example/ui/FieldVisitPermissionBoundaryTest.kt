package com.example.ui

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FieldVisitPermissionBoundaryTest {
    @Test fun counterOnlyDeviceHasAnActivitySensorAndAnAccurateSupportLabel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(SensorManager::class.java))
            .addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
        val environment = SafetyDiagnosticsReader.read(context)
        assertTrue(environment.hasActivitySensor)
        assertEquals("걸음 수", environment.activitySensorSummary)
    }
    @Test fun fieldPilotHasNoNewAndroidPermissionsOrNetworkPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val androidPermissions = info.requestedPermissions.orEmpty().filter { it.startsWith("android.permission.") }.toSet()
        assertEquals(setOf(
            "android.permission.SEND_SMS", "android.permission.READ_PHONE_STATE",
            "android.permission.ACTIVITY_RECOGNITION", "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_HEALTH", "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.VIBRATE", "android.permission.POST_NOTIFICATIONS",
            // Existing WorkManager merged permissions, also present in baseline 2.3.4.
            "android.permission.WAKE_LOCK", "android.permission.ACCESS_NETWORK_STATE"
        ), androidPermissions)
        assertFalse(info.requestedPermissions.orEmpty().contains("com.google.android.gms.permission.AD_ID"))
    }

    @Test fun airplaneModeIsReadWithoutAttemptingToChangeDeviceSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)
        assertTrue(SafetyDiagnosticsReader.read(context).airplaneModeEnabled)
        assertEquals(1, Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON))
        Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        assertFalse(SafetyDiagnosticsReader.read(context).airplaneModeEnabled)
    }

    @Test @Config(sdk = [24])
    fun androidSevenCanReadOfflineDiagnosticsAndBuildASharesheetWithoutNewPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(SafetyDiagnosticsReader.read(context))
        val chooser = fieldVisitShareIntent("technical summary only")
        assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        assertEquals("text/plain", send.type)
        assertEquals("technical summary only", send.getStringExtra(android.content.Intent.EXTRA_TEXT))
    }
}
