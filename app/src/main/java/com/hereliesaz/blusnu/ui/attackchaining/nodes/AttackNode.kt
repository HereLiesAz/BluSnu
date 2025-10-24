package com.hereliesaz.blusnu.ui.attackchaining.nodes

import androidx.compose.ui.geometry.Offset
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.delay

// Start Node
data class StartNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = emptyList(),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("start", id)),
    override val name: String = "Start",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Start"
    override suspend fun execute(): String {
        delay(100)
        return "StartNode executed"
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

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
    val name: String
    val title: String
    val targetDevice: TargetDevice?
    suspend fun execute(): String
    fun copy(position: Offset): AttackNode
    fun withTarget(device: TargetDevice): AttackNode
}

// Logic Nodes
data class IfElseNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("condition", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("true", id), NodeConnector("false", id)),
    override val name: String = "If/Else",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "If/Else"
    override suspend fun execute(): String {
        delay(100)
        return "IfElseNode executed"
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

data class WaitNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("in", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("out", id)),
    override val name: String = "Wait",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Wait"
    override suspend fun execute(): String {
        delay(1000)
        return "WaitNode executed"
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

data class LoopNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("start", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("loop_body", id), NodeConnector("end", id)),
    override val name: String = "Loop",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Loop"
    override suspend fun execute(): String {
        delay(100)
        return "LoopNode executed"
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

// Module Nodes (example)
data class ScanBleNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = emptyList(),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("devices", id)),
    override val name: String = "Scan BLE Devices",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Scan BLE Devices"
    override suspend fun execute(): String {
        delay(2000)
        return "ScanBleNode executed"
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

data class BluesnarfNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("target_mac", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("data", id)),
    override val name: String = "Bluesnarf",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Bluesnarf"
    override suspend fun execute(): String {
        delay(3000)
        return "BluesnarfNode executed"
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

data class KeystrokeInjectionNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("target_mac", id), NodeConnector("payload", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("success", id)),
    override val name: String = "Keystroke Injection",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Keystroke Injection"
    override suspend fun execute(): String {
        delay(1500)
        return "KeystrokeInjectionNode executed"
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}
