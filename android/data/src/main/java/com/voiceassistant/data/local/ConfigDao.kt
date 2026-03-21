package com.voiceassistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    
    @Query("SELECT * FROM config WHERE key = :key")
    suspend fun get(key: String): ConfigEntity?
    
    @Query("SELECT * FROM config WHERE key = :key")
    fun getFlow(key: String): Flow<ConfigEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: ConfigEntity)
    
    @Query("DELETE FROM config WHERE key = :key")
    suspend fun delete(key: String)
}
