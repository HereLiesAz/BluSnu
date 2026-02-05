package com.hereliesaz.blusnu.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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
 * A basic module for managing a linear list of attack nodes.
 *
 * This seems to be a simplified alternative or precursor to the full `AttackChainExecutor`.
 * It manages a list of nodes stored in a StateFlow and logs their simulated execution.
 */
class AttackChainingCanvasModule {

    // List of nodes in the chain.
    private val _nodes = MutableStateFlow<List<AttackNode>>(emptyList())
    val nodes = _nodes.asStateFlow()

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
     * Executes the chain sequentially by logging the steps.
     */
    fun executeChain() {
        Log.d("AttackChaining", "Executing attack chain:")
        _nodes.value.forEachIndexed { index, node ->
            Log.d("AttackChaining", "Step ${index + 1}: ${node}")
        }
        // In a real implementation, this would trigger the actual logic for each node type.
    }
}
