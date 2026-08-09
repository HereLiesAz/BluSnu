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
 *
 * @property lastResult The output string of the previously executed node.
 * @property targetDevice The target device resolved so far in the chain.
 * @property loopCounters Per-node iteration counters, keyed by [NodeId]. Maintained by the
 *                        executor so that [LoopNode] can enforce a bounded number of iterations.
 */
data class ExecutionContext(
    val lastResult: String? = null,
    val targetDevice: TargetDevice? = null,
    val loopCounters: Map<NodeId, Int> = emptyMap()
)

/**
 * Result returned FROM a node after execution.
 *
 * @property output Human-readable result/log line.
 * @property targetDevice The target device to forward to downstream nodes (if any).
 * @property nextPort The id of the single output port the executor should follow. When null,
 *                    the executor follows every outgoing connection (linear fan-out). Branching
 *                    nodes (e.g. [IfElseNode], [LoopNode]) set this to steer execution down one edge.
 */
data class ExecutionResult(
    val output: String,
    val targetDevice: TargetDevice? = null,
    val nextPort: String? = null
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
        // Basic condition evaluation: the branch is taken when a usable target device
        // has been resolved by an upstream node (or configured on this node). This models the
        // common "if we found/selected a target, continue the attack, else bail out" decision.
        // The executor follows only the matching output port ("true" / "false").
        val condition = (targetDevice ?: context.targetDevice) != null
        val branch = if (condition) "true" else "false"
        return ExecutionResult(
            output = "IfElseNode evaluated condition=$condition -> '$branch' branch",
            targetDevice = context.targetDevice,
            nextPort = branch
        )
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
    override val targetDevice: TargetDevice? = null,
    // Upper bound on the number of times the "loop_body" output is followed before the
    // loop exits via its "end" output. Keeps cyclic chains bounded (no infinite looping).
    val maxIterations: Int = 3
) : AttackNode {
    override val title: String = "Loop"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        delay(100)
        // Bounded loop counter: the executor tracks how many times this node's "loop_body"
        // edge has been traversed (via ExecutionContext.loopCounters). While the count is below
        // maxIterations we take the "loop_body" branch; once it is reached we exit via "end".
        val iteration = context.loopCounters[id] ?: 0
        return if (iteration < maxIterations) {
            ExecutionResult(
                output = "LoopNode iteration ${iteration + 1}/$maxIterations",
                targetDevice = context.targetDevice,
                nextPort = "loop_body"
            )
        } else {
            ExecutionResult(
                output = "LoopNode finished after $maxIterations iterations",
                targetDevice = context.targetDevice,
                nextPort = "end"
            )
        }
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
        // NOTE: This node is a simulation. The chain executor does not own a BluetoothScanner
        // instance, so this step does not perform a real scan; it reports a placeholder result
        // to demonstrate chain flow. Real scanning is driven separately by BluetoothScanner
        // from the scanning screens.
        return ExecutionResult("ScanBleNode (simulated): scan step reached", context.targetDevice)
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
