package com.hereliesaz.blusnu.data

import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingState
import com.hereliesaz.blusnu.ui.attackchaining.nodes.AttackNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.StartNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AttackChainExecutor {

    private val _output = MutableSharedFlow<String>()
    val output = _output.asSharedFlow()

    fun execute(state: AttackChainingState, scope: CoroutineScope) {
        scope.launch {
            val startNode = state.nodes.values.find { it is StartNode }
            if (startNode != null) {
                executeNode(startNode, state)
            } else {
                _output.emit("No start node found")
            }
        }
    }

    private suspend fun executeNode(node: AttackNode, state: AttackChainingState) {
        _output.emit("Executing node: ${node.name}")
        val result = node.execute()
        _output.emit("Result: $result")

        // Find the next node to execute
        val connections = state.connections.filter { it.first.nodeId == node.id }
        for (connection in connections) {
            val nextNode = state.nodes[connection.second.nodeId]
            if (nextNode != null) {
                executeNode(nextNode, state)
            }
        }
    }
}
