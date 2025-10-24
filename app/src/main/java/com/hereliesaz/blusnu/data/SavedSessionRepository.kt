package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.Flow

class SavedSessionRepository(private val savedSessionDao: SavedSessionDao) {

    val allSessions: Flow<List<SavedSession>> = savedSessionDao.getAll()

    suspend fun insert(session: SavedSession) {
        savedSessionDao.insert(session)
    }
}
