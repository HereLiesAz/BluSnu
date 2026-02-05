package com.hereliesaz.blusnu.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a pre-defined Attack Chain Template.
 *
 * Templates are immutable blueprints for workflows that are shipped with the app
 * or downloaded updates. They are stored in the Room database for structured access.
 *
 * @property id Unique identifier for the template.
 * @property name Display name of the template (e.g., "Smart Lock Audit").
 * @property description Detailed description of what the workflow does.
 */
@Entity(tableName = "attack_chain_templates")
data class AttackChainTemplate(
    @PrimaryKey val id: String,
    val name: String,
    val description: String
)
