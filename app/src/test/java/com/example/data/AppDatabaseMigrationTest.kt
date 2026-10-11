package com.example.data

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {
    @Test
    fun versionTwoUpgradePreservesSmsSnapshotAndAllowsSeparateFamilyNote() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "lifelink-v2-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE contacts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, phoneNumber TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE event_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, type TEXT NOT NULL, message TEXT NOT NULL, detail TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE settings (`key` TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(`key`))")
                        AppDatabase.MIGRATION_1_2.migrate(db)
                        db.execSQL("INSERT INTO contacts VALUES (1, '보호자', '01012345678', 1000)")
                        db.execSQL("INSERT INTO settings VALUES ('monitor_hours', '12')")
                        db.execSQL("INSERT INTO event_logs VALUES (1, 1500, 'SMS_SENT', 'sent', '')")
                        db.execSQL("INSERT INTO safety_incidents VALUES ('sos:1000', 'sos', 1000, '사용자', '원래 문자', NULL, 1, 1001, NULL)")
                        db.execSQL("INSERT INTO safety_recipients VALUES ('sos:1000:1', 'sos:1000', 1, '보호자', '01012345678', 1, 'QUEUED', 1002)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            ).build()
        )
        helper.writableDatabase
        helper.close()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            val repository = LifeLinkRepository(migrated)
            assertEquals(1, migrated.contactDao().getContactCount())
            assertEquals("12", repository.getSetting("monitor_hours", ""))
            assertEquals(1500L, repository.lastSmsSentAt.first())
            assertEquals(FollowUpSaveResult.SAVED, repository.recordFamilyFollowUp("sos:1000", FamilyFollowUpStatus.CONFIRMED_SAFE, 2000L))
            val incident = SafetyIncidentRepository(migrated).get("sos:1000")!!
            assertEquals("원래 문자", incident.incident.message)
            assertEquals("QUEUED", incident.recipients.single().dispatchState)
            assertEquals("CONFIRMED_SAFE", repository.recentSafetyReviews.first().single().followUpStatus)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationFromVersionOnePreservesExistingUserDataAndCreatesIncidentTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val databaseName = "lifelink-migration-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER PRIMARY KEY " +
                                    "AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                                    "`phoneNumber` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                            )
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS `event_logs` (`id` INTEGER PRIMARY KEY " +
                                    "AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                                    "`type` TEXT NOT NULL, `message` TEXT NOT NULL, " +
                                    "`detail` TEXT NOT NULL)"
                            )
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, " +
                                    "`value` TEXT NOT NULL, PRIMARY KEY(`key`))"
                            )
                            db.execSQL(
                                "INSERT INTO contacts (id, name, phoneNumber, createdAt) " +
                                    "VALUES (1, '보호자', '01012345678', 1000)"
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )
        helper.writableDatabase
        helper.close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(1, migrated.contactDao().getContactCount())
            val incidents = SafetyIncidentRepository(migrated)
            incidents.getOrCreate(
                incidentId = "emergency:1000",
                type = "emergency",
                occurredAtMs = 1_000L,
                deviceAlias = "사용자 폰",
                message = "긴급 메시지",
                batteryPercent = 50,
                subscriptionId = 1,
                contacts = listOf(Contact(id = 1, name = "보호자", phoneNumber = "01012345678")),
                nowMs = 1_001L
            )
            assertTrue(incidents.pendingRecipients().isNotEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }
}
