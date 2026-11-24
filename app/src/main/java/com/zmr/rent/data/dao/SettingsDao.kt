package com.zmr.rent.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zmr.rent.data.entity.Setting
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<Setting>>

    @Upsert
    suspend fun upsertAll(settings: List<Setting>)
}
