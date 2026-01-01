package com.hereliesaz.blusnu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDeviceDao {

    @Query("SELECT * FROM target_devices")
    fun getAll(): Flow<List<TargetDevice>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(device: TargetDevice): Long

    @Update
    suspend fun update(device: TargetDevice)

    @Query("SELECT * FROM target_devices WHERE macAddress = :macAddress")
    suspend fun getDevice(macAddress: String): TargetDevice?

    @Query("UPDATE target_devices SET notes = :notes WHERE macAddress = :macAddress")
    suspend fun updateNotes(macAddress: String, notes: String)

    @Query("UPDATE target_devices SET isFavorite = :isFavorite WHERE macAddress = :macAddress")
    suspend fun updateIsFavorite(macAddress: String, isFavorite: Boolean)
}
