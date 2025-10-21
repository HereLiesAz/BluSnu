package com.hereliesaz.blusnu.ui.dashboard

data class ActiveTask(
    val id: String,
    val name: String,
    val description: String
)

data class SavedSession(
    val id: String,
    val name: String,
    val date: String
)

data class AttackChainTemplate(
    val id: String,
    val name: String,
    val description: String
)
