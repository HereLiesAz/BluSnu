package com.hereliesaz.blusnu.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_sessions")
data class SavedSession(
    @PrimaryKey val id: String,
    val name: String,
    val date: String
)
