package com.hereliesaz.blusnu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSessionDao {

    @Query("SELECT * FROM saved_sessions")
    fun getAll(): Flow<List<SavedSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SavedSession)
}
