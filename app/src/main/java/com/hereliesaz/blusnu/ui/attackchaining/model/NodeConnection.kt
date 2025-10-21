package com.hereliesaz.blusnu.ui.attackchaining.model

import java.util.UUID

// Represents a connection between an output port of one node and an input port of another
data class NodeConnection(
    val id: UUID = UUID.randomUUID(),
    val fromNodeId: UUID,
    val fromOutputId: UUID,
    val toNodeId: UUID,
    val toInputId: UUID
)
