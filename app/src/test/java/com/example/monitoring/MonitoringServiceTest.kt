package com.example.monitoring

import android.Manifest
import android.app.Application
import android.app.Service
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.MonitoringRuntimeState
import com.example.data.MonitoringStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class MonitoringServiceTest {
    private lateinit var context: Application
    private lateinit var store: MonitoringStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = MonitoringStore(context)
    }

    @Test
    fun startCommandAfterInitializationFailureCannotReportRunning() {
        store.beginStart()
        val controller = Robolectric.buildService(MonitoringService::class.java).create()
        try {
            val initializationError = store.snapshot().serviceError
            assertTrue(initializationError.isNotEmpty())

            val result = controller.get().onStartCommand(
                Intent(context, MonitoringService::class.java)
                    .setAction(MonitoringService.ACTION_START),
                0,
                1
            )

            assertEquals(Service.START_NOT_STICKY, result)
            assertEquals(MonitoringRuntimeState.ERROR, store.snapshot().runtimeState)
            assertFalse(store.snapshot().isRunning)
            assertEquals(initializationError, store.snapshot().serviceError)
            assertFalse(store.claimWatchdogAlert(MonitoringWatchdogPolicy.alertToken(store.snapshot())))
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun dailyOnlyServiceRegistersSensorsWhenMonitoringIsStartedLater() {
        prepareDailyOnlyService()
        configureSms()
        val controller = Robolectric.buildService(MonitoringService::class.java).create()
        val manager = controller.get().getSystemService(SensorManager::class.java)
        val motion = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowOf(manager).addSensor(motion)
        try {
            assertTrue(shadowOf(manager).listeners.isEmpty())
            store.beginStart(nowMs = System.currentTimeMillis() - 1_000L)

            val result = controller.get().onStartCommand(
                Intent(context, MonitoringService::class.java).setAction(MonitoringService.ACTION_START),
                0,
                1
            )

            assertEquals(Service.START_STICKY, result)
            assertTrue(store.snapshot().isRunning)
            assertTrue(shadowOf(manager).listeners.any { shadowOf(manager).hasListener(it, motion) })
            context.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("휴대전화 잠금 해제 감지", store.snapshot().lastActivityReason)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun dailyOnlyServiceCannotClaimMonitoringStartedWhenSensorsFail() {
        prepareDailyOnlyService()
        configureSms()
        val controller = Robolectric.buildService(MonitoringService::class.java).create()
        val manager = controller.get().getSystemService(SensorManager::class.java)
        shadowOf(manager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        shadowOf(manager).setForceListenersToFail(true)
        try {
            store.beginStart()

            val result = controller.get().onStartCommand(
                Intent(context, MonitoringService::class.java).setAction(MonitoringService.ACTION_START),
                0,
                1
            )

            assertEquals(Service.START_NOT_STICKY, result)
            assertFalse(store.snapshot().isRunning)
            assertTrue(store.snapshot().serviceError.contains("센서"))
        } finally {
            controller.destroy()
        }
    }

    private fun prepareDailyOnlyService() {
        val nowMs = System.currentTimeMillis()
        val dueAtMs = nowMs - 60_000L
        store.completeSetup()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE).edit()
            .putBoolean("daily_check_in_enabled", true)
            .putLong("daily_next_due_at_ms", dueAtMs)
            .putLong("daily_prompted_due_at_ms", dueAtMs)
            .putLong("daily_response_deadline_at_ms", nowMs + 3_600_000L)
            .commit()
    }

    @Test
    @Config(sdk = [24])
    fun requiredOngoingNotificationIsSilentOnLegacyAndroid() {
        prepareDailyOnlyService()
        val controller = Robolectric.buildService(MonitoringService::class.java).create()
        try {
            val notification = shadowOf(controller.get()).lastForegroundNotification
            assertEquals(Notification.PRIORITY_LOW, notification.priority)
            assertNull(notification.sound)
            assertNull(notification.vibrate)
            assertEquals(0, notification.defaults and (Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE))
        } finally { controller.destroy() }
    }

    @Test
    fun ongoingMonitoringDoesNotCreateAnUnreadBadge() {
        prepareDailyOnlyService()
        val controller = Robolectric.buildService(MonitoringService::class.java).create()
        try {
            val notification = shadowOf(controller.get()).lastForegroundNotification
            assertEquals(SafetyNotificationCapability.MONITORING_CHANNEL_ID, notification.channelId)
            val manager = context.getSystemService(NotificationManager::class.java)
            assertFalse(manager.getNotificationChannel(notification.channelId).canShowBadge())
        } finally { controller.destroy() }
    }

    @Test
    fun expiredCycleNotificationWaitsForActivityInsteadOfImplyingAnotherCountdown() {
        configureSms()
        store.completeSetup()
        store.monitorHours = 6
        store.beginStart(System.currentTimeMillis() - 7 * 3_600_000L, elapsedRealtimeMs = -1L)
        val sensors = context.getSystemService(SensorManager::class.java)
        shadowOf(sensors).addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        val controller = Robolectric.buildService(MonitoringService::class.java).create()
        try {
            val notification = shadowOf(controller.get()).lastForegroundNotification
            assertEquals("라이프링크 · 활동 확인 대기", notification.extras.getString(Notification.EXTRA_TITLE))
            assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)!!.contains("새 활동 후 설정 시간을 다시"))
            assertFalse(context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(notification.channelId).canShowBadge())
        } finally { controller.destroy() }
    }

    private fun configureSms() {
        shadowOf(context).grantPermissions(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACTIVITY_RECOGNITION,
            "com.bboysilver.lifelink.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        )
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, true)
        shadowOf(context.getSystemService(TelephonyManager::class.java)).setIsSmsCapable(true)
        shadowOf(context.getSystemService(SubscriptionManager::class.java)).setActiveSubscriptionInfos(
            SubscriptionInfoBuilder.newBuilder().setId(1).setSimSlotIndex(0)
                .setDisplayName("Test SIM").buildSubscriptionInfo()
        )
    }
}
