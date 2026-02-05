package com.hereliesaz.blusnu.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a saved session (snapshot of discovered devices).
 *
 * @property id Unique ID for the session.
 * @property name User-friendly name.
 * @property date ISO date string of creation.
 */
@Entity(tableName = "saved_sessions")
data class SavedSession(
    @PrimaryKey val id: String,
    val name: String,
    val date: String
)
