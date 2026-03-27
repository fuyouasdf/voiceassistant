package com.voiceassistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ConfigEntity::class, PlaylistEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao
    abstract fun playlistDao(): PlaylistDao
}
