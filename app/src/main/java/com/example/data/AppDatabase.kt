package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Contact::class,
        EventLog::class,
        AppSetting::class,
        SafetyIncident::class,
        SafetyRecipient::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun eventLogDao(): EventLogDao
    abstract fun settingDao(): SettingDao
    abstract fun safetyIncidentDao(): SafetyIncidentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifelink_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `safety_incidents` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `occurredAtMs` INTEGER NOT NULL,
                        `deviceAlias` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `batteryPercent` INTEGER,
                        `subscriptionId` INTEGER NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `completedAtMs` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `safety_recipients` (
                        `eventId` TEXT NOT NULL,
                        `incidentId` TEXT NOT NULL,
                        `contactId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `phoneNumber` TEXT NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `dispatchState` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`eventId`),
                        FOREIGN KEY(`incidentId`) REFERENCES `safety_incidents`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_safety_recipients_incidentId` " +
                        "ON `safety_recipients` (`incidentId`)"
                )
            }
        }
    }
}
