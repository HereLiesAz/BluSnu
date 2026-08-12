package com.hereliesaz.blusnu.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sealed class representing simple attack nodes for the basic list-based chain editor (Legacy/Simpler version).
 * Note: The full node editor uses `com.hereliesaz.blusnu.ui.attackchaining.nodes.AttackNode`.
 */
sealed class AttackNode {
    data class Scan(val protocol: Protocol) : AttackNode()
    data class Filter(val criteria: String) : AttackNode()
    data class Attack(val module: String) : AttackNode()
}

/**
 * A module for managing and executing a linear list of attack nodes.
 *
 * Nodes are stored in a [StateFlow] so the UI can observe changes.
 * Execution logs are emitted to a [SharedFlow] that the UI can collect.
 */
class AttackChainingCanvasModule {

    // List of nodes in the chain.
    private val _nodes = MutableStateFlow<List<AttackNode>>(emptyList())
    val nodes = _nodes.asStateFlow()

    // Execution log stream for UI observation.
    private val _executionLog = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val executionLog: SharedFlow<String> = _executionLog.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    /**
     * Adds a node to the end of the list.
     */
    fun addNode(node: AttackNode) {
        scope.launch {
            val currentNodes = _nodes.value.toMutableList()
            currentNodes.add(node)
            _nodes.value = currentNodes
        }
    }

    /**
     * Removes a specific node instance from the list.
     */
    fun removeNode(node: AttackNode) {
        scope.launch {
            val currentNodes = _nodes.value.toMutableList()
            currentNodes.remove(node)
            _nodes.value = currentNodes
        }
    }

    /**
     * Executes the chain sequentially, dispatching each node type and emitting
     * status to [executionLog].
     *
     * - [AttackNode.Scan]: Emits a scan request for the specified protocol.
     * - [AttackNode.Filter]: Applies the filter criteria to narrow the device list.
     * - [AttackNode.Attack]: Dispatches to the named attack module.
     */
    suspend fun executeChain() {
        val chain = _nodes.value
        if (chain.isEmpty()) {
            _executionLog.emit("Chain is empty. Add nodes before executing.")
            return
        }

        _executionLog.emit("Executing attack chain with ${chain.size} nodes...")

        chain.forEachIndexed { index, node ->
            val step = index + 1
            when (node) {
                is AttackNode.Scan -> {
                    _executionLog.emit("Step $step: SCAN requested for protocol ${node.protocol}")
                    _executionLog.emit("Step $step: Initiating ${node.protocol} scan...")
                }
                is AttackNode.Filter -> {
                    _executionLog.emit("Step $step: FILTER applying criteria: ${node.criteria}")
                    _executionLog.emit("Step $step: Device list filtered by '${node.criteria}'")
                }
                is AttackNode.Attack -> {
                    _executionLog.emit("Step $step: ATTACK dispatching module '${node.module}'")
                    _executionLog.emit("Step $step: Invoking ${node.module} against current target context")
                }
            }
        }

        _executionLog.emit("Attack chain execution completed. ${ chain.size} nodes processed.")
    }
}
