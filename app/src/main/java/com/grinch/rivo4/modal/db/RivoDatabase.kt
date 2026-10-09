package com.grinch.rivo4.modal.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PrivateContactEntity::class,
        CallNoteEntity::class,
        CallbackReminderEntity::class,
        VobizCallRecordEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class RivoDatabase : RoomDatabase() {
    abstract fun privateContactDao(): PrivateContactDao
    abstract fun callNoteDao(): CallNoteDao
    abstract fun callbackReminderDao(): CallbackReminderDao
    abstract fun vobizCallRecordDao(): VobizCallRecordDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vobiz_call_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`callId` TEXT NOT NULL, " +
                        "`direction` TEXT NOT NULL, " +
                        "`number` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`startTime` INTEGER NOT NULL, " +
                        "`endTime` INTEGER NOT NULL, " +
                        "`durationSeconds` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`failureReason` TEXT NOT NULL, " +
                        "`did` TEXT NOT NULL, " +
                        "`recordingPath` TEXT NOT NULL)"
                )
            }
        }
    }
}
