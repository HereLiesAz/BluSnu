package com.hereliesaz.blusnu.ui.attackchaining.nodes

import androidx.compose.ui.geometry.Offset

// A unique identifier for a node
typealias NodeId = String

// Represents a connection point on a node
data class NodeConnector(val id: String, val nodeId: NodeId)

// Base interface for all nodes
interface AttackNode {
    val id: NodeId
    val position: Offset
    val inputs: List<NodeConnector>
    val outputs: List<NodeConnector>
    val title: String
    fun copy(position: Offset): AttackNode
}

// Logic Nodes
data class IfElseNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("condition", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("true", id), NodeConnector("false", id)),
    override val title: String = "If/Else"
) : AttackNode {
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
}

data class WaitNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("in", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("out", id)),
    override val title: String = "Wait"
) : AttackNode {
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
}

data class LoopNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("start", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("loop_body", id), NodeConnector("end", id)),
    override val title: String = "Loop"
) : AttackNode {
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
}

// Module Nodes (example)
data class ScanBleNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = emptyList(),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("devices", id)),
    override val title: String = "Scan BLE Devices"
) : AttackNode {
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
}

data class BluesnarfNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("target_mac", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("data", id)),
    override val title: String = "Bluesnarf"
) : AttackNode {
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
}

data class KeystrokeInjectionNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("target_mac", id), NodeConnector("payload", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("success", id)),
    override val title: String = "Keystroke Injection"
) : AttackNode {
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
}
