package com.hereliesaz.blusnu.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attack_chain_templates")
data class AttackChainTemplate(
    @PrimaryKey val id: String,
    val name: String,
    val description: String
)
