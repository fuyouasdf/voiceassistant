package com.voiceassistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [ConfigEntity::class, PlaylistEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add songData column to playlists table
                database.execSQL("ALTER TABLE playlists ADD COLUMN songData TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
