package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.Flow

class AttackChainTemplateRepository(private val attackChainTemplateDao: AttackChainTemplateDao) {

    val allTemplates: Flow<List<AttackChainTemplate>> = attackChainTemplateDao.getAll()

    suspend fun insert(template: AttackChainTemplate) {
        attackChainTemplateDao.insert(template)
    }
}
