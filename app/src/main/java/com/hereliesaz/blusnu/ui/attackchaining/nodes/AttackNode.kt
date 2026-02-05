package com.hereliesaz.blusnu.ui.attackchaining.nodes

import androidx.compose.ui.geometry.Offset
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.delay

/**
 * Base interface for all nodes in the Attack Chain.
 */
interface AttackNode {
    val id: NodeId
    val position: Offset
    val inputs: List<NodeConnector>
    val outputs: List<NodeConnector>
    val name: String
    val title: String
    val targetDevice: TargetDevice?

    /**
     * Executes the node's logic.
     * @param context Data passed from the previous node.
     * @return Result data to pass to the next node.
     */
    suspend fun execute(context: ExecutionContext): ExecutionResult

    // Copy helper to update position in the immutable state flow.
    fun copy(position: Offset): AttackNode
    // Helper to update target config.
    fun withTarget(device: TargetDevice): AttackNode
}

/**
 * Context passed INTO a node during execution.
 */
data class ExecutionContext(
    val lastResult: String? = null,
    val targetDevice: TargetDevice? = null
)

/**
 * Result returned FROM a node after execution.
 */
data class ExecutionResult(
    val output: String,
    val targetDevice: TargetDevice? = null
)

// A unique identifier for a node.
typealias NodeId = String

// Represents a connection point (port) on a node.
data class NodeConnector(val id: String, val nodeId: NodeId)

// --- Node Implementations ---

/**
 * Starting point of any chain.
 */
data class StartNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = emptyList(),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("start", id)),
    override val name: String = "Start",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Start"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        delay(100)
        return ExecutionResult("StartNode executed", targetDevice)
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

/**
 * Simple conditional logic (Branching).
 */
data class IfElseNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("condition", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("true", id), NodeConnector("false", id)),
    override val name: String = "If/Else",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "If/Else"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        delay(100)
        // TODO: Implement actual condition evaluation logic.
        return ExecutionResult("IfElseNode executed", context.targetDevice)
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

/**
 * Pauses execution for a set duration.
 */
data class WaitNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("in", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("out", id)),
    override val name: String = "Wait",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Wait"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        delay(1000)
        return ExecutionResult("WaitNode executed", context.targetDevice)
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

/**
 * Loops execution N times or until condition met.
 */
data class LoopNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("start", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("loop_body", id), NodeConnector("end", id)),
    override val name: String = "Loop",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Loop"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        delay(100)
        // TODO: Implement loop counter logic in Executor.
        return ExecutionResult("LoopNode executed", context.targetDevice)
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

// --- Action Nodes ---

/**
 * Executes a BLE Scan.
 */
data class ScanBleNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("trigger", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("devices", id)),
    override val name: String = "Scan BLE Devices",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Scan BLE Devices"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        delay(2000)
        // TODO: Trigger actual BluetoothScanner.
        return ExecutionResult("ScanBleNode executed: Found 3 devices", null)
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

/**
 * Executes Bluesnarfing.
 */
data class BluesnarfNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("target_mac", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("data", id)),
    override val name: String = "Bluesnarf",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Bluesnarf"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        val target = targetDevice ?: context.targetDevice
        delay(3000)
        return if (target != null) {
            ExecutionResult("Bluesnarf executed on ${target.macAddress}", target)
        } else {
            ExecutionResult("Bluesnarf failed: No target", null)
        }
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

/**
 * Executes Keystroke Injection.
 */
data class KeystrokeInjectionNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("target_mac", id), NodeConnector("payload", id)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("success", id)),
    override val name: String = "Keystroke Injection",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Keystroke Injection"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        val target = targetDevice ?: context.targetDevice
        delay(1500)
        return if (target != null) {
            ExecutionResult("Keystroke Injection executed on ${target.macAddress}", target)
        } else {
            ExecutionResult("Keystroke Injection failed: No target", null)
        }
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}
