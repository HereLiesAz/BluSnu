package com.hereliesaz.blusnu.ui.attackchaining.model

import androidx.compose.ui.geometry.Offset
import java.util.UUID

// Represents a single node in the attack chain canvas
data class AttackNode(
    val id: UUID = UUID.randomUUID(),
    val type: String,
    val position: Offset,
    val inputs: List<NodeInput>,
    val outputs: List<NodeOutput>
)

// Represents an input port on a node
data class NodeInput(
    val id: UUID = UUID.randomUUID(),
    val nodeId: UUID,
    val type: String,
    val label: String
)

// Represents an output port on a node
data class NodeOutput(
    val id: UUID = UUID.randomUUID(),
    val nodeId: UUID,
    val type: String,
    val label: String
)
