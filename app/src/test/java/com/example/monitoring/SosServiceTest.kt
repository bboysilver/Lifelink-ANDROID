package com.example.monitoring

import android.Manifest
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.AppDatabase
import com.example.data.Contact
import com.example.data.LifeLinkRepository
import com.example.data.MonitoringStore
import com.example.data.SafetyIncidentRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SosServiceTest {
    private lateinit var context: Application
    private lateinit var store: MonitoringStore
    private lateinit var db: AppDatabase
    private lateinit var incidents: SafetyIncidentRepository
    private lateinit var task: SosDispatchTask

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        listOf("lifelink_monitoring", SmsDispatchStore.FILE_NAME).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        store = MonitoringStore(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        incidents = SafetyIncidentRepository(db)
        task = SosDispatchTask(context, store, LifeLinkRepository(db), incidents)
        db.contactDao().insertContact(Contact(id = 1, name = "guardian", phoneNumber = "01012345678"))
        shadowOf(context).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context).denyPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, true)
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING, true)
        shadowOf(context.getSystemService(TelephonyManager::class.java)).setIsSmsCapable(true)
        shadowOf(context.getSystemService(SubscriptionManager::class.java)).setActiveSubscriptionInfos(
            SubscriptionInfoBuilder.newBuilder().setId(1).setSimSlotIndex(0).setDisplayName("SIM").buildSubscriptionInfo()
        )
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun shortSosServiceDoesNotRequireHealthActivityPermission() {
        val event = store.beginSos(System.currentTimeMillis() + 5_000L)
        val controller = Robolectric.buildService(SosService::class.java).create()
        try {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE, controller.get().foregroundServiceType)
            assertFalse(store.desiredEnabled)
            assertEquals(Service.START_NOT_STICKY, controller.get().onStartCommand(Intent(SosService.ACTION_CANCEL)
                .putExtra(SosService.EXTRA_EVENT_MS, event), 0, 1))
            assertEquals(0L, store.sosEventMs)
        } finally { controller.destroy() }
    }

    @Test
    fun workerFallbackCanDispatchWithoutActivityPermissionOrHealthService() = runBlocking {
        val event = store.beginSos(System.currentTimeMillis())
        task.run(event)
        val status = SmsDispatchStore(context).status("sos:$event:1")
        assertEquals(1, status.attempt)
        assertEquals(SmsDispatchState.QUEUED, status.state)
        assertEquals(event, store.activeSosEventMs)
        assertFalse(store.desiredEnabled)
    }

    @Test
    fun cancelledAndStaleFallbacksCannotDispatchALaterSos() = runBlocking {
        val old = store.beginSos(System.currentTimeMillis())
        assertTrue(store.cancelPendingSos())
        task.run(old)
        assertNull(incidents.get("sos:$old"))
        val newer = store.beginSos(old + 1L)
        task.run(old, old + 10L)
        assertEquals(newer, store.pendingSosEventMs)
        assertEquals(0L, store.activeSosEventMs)
        assertNull(incidents.get("sos:$newer"))
    }

    @Test
    fun repeatedServiceAndWorkerEvaluationQueueEachRecipientOnlyOnce() = runBlocking {
        val event = store.beginSos(System.currentTimeMillis())
        task.run(event)
        task.run(event)
        assertEquals(1, SmsDispatchStore(context).status("sos:$event:1").attempt)
    }

    @Test
    fun missingSmsPermissionEndsSosWithVisibleFailureNotInfinitePending() = runBlocking {
        shadowOf(context).denyPermissions(Manifest.permission.SEND_SMS)
        val event = store.beginSos(System.currentTimeMillis())
        task.run(event)
        assertEquals(0L, store.sosEventMs)
        assertNull(incidents.get("sos:$event"))
    }

    @Test
    fun healthServiceFailureCannotEraseAnIndependentSos() {
        store.beginStart()
        val event = store.beginSos(System.currentTimeMillis() + 5_000L)
        val controller = Robolectric.buildService(MonitoringService::class.java).create()
        try {
            assertEquals(event, store.sosEventMs)
            assertFalse(store.snapshot().isRunning)
        } finally { controller.destroy() }
    }

    @Test
    fun shortServiceTimeoutLeavesPersistedSosAvailableForFallback() {
        val event = store.beginSos(System.currentTimeMillis() + 5_000L)
        val controller = Robolectric.buildService(SosService::class.java).create()
        try {
            controller.get().onTimeout(1)
            assertEquals(event, store.pendingSosEventMs)
            assertFalse(store.desiredEnabled)
        } finally { controller.destroy() }
    }
}
