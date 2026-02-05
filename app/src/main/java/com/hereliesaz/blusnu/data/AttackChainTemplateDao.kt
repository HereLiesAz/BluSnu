package com.hereliesaz.blusnu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for AttackChainTemplate entities.
 */
@Dao
interface AttackChainTemplateDao {

    /**
     * Retrieves all available templates as a Flow.
     */
    @Query("SELECT * FROM attack_chain_templates")
    fun getAll(): Flow<List<AttackChainTemplate>>

    /**
     * Inserts a template. Replaces if ID conflict occurs (useful for updating built-in templates).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: AttackChainTemplate)
}
