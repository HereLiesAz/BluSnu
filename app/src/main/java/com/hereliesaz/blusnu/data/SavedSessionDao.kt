package com.hereliesaz.blusnu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for SavedSession entities.
 */
@Dao
interface SavedSessionDao {

    /**
     * Get all saved sessions.
     */
    @Query("SELECT * FROM saved_sessions")
    fun getAll(): Flow<List<SavedSession>>

    /**
     * Insert or replace a session.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SavedSession)
}
