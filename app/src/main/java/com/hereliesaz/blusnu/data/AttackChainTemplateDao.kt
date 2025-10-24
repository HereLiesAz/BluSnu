package com.hereliesaz.blusnu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttackChainTemplateDao {

    @Query("SELECT * FROM attack_chain_templates")
    fun getAll(): Flow<List<AttackChainTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: AttackChainTemplate)
}
