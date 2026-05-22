package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository for accessing Attack Chain Templates from the database.
 */
class AttackChainTemplateRepository(private val attackChainTemplateDao: AttackChainTemplateDao) {

    // Exposes the list of templates to the UI layer.
    val allTemplates: Flow<List<AttackChainTemplate>> = attackChainTemplateDao.getAll()

    /**
     * Inserts a new template into the database.
     */
    suspend fun insert(template: AttackChainTemplate) {
        attackChainTemplateDao.insert(template)
    }
}
