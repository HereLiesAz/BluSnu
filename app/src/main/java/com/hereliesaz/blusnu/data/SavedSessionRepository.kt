package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing Saved Sessions.
 */
class SavedSessionRepository(private val savedSessionDao: SavedSessionDao) {

    // Expose all sessions as a flow.
    val allSessions: Flow<List<SavedSession>> = savedSessionDao.getAll()

    /**
     * Save a session to the database.
     */
    suspend fun insert(session: SavedSession) {
        savedSessionDao.insert(session)
    }
}
