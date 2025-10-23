package com.hereliesaz.blusnu.data

import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingState
import com.hereliesaz.blusnu.ui.attackchaining.nodes.AttackNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.StartNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AttackChainExecutor {

    private val _output = MutableSharedFlow<String>()
    val output = _output.asSharedFlow()

    private var executionJob: Job? = null

    fun execute(state: AttackChainingState, scope: CoroutineScope) {
        executionJob?.cancel()
        executionJob = scope.launch(Dispatchers.Default) {
            val startNode = state.nodes.values.find { it is StartNode }
            if (startNode == null) {
                _output.emit("Error: No Start Node found.")
                return@launch
            }
            _output.emit("Execution started.")
            executeNode(startNode, state)
            _output.emit("Execution finished.")
        }
    }

    private suspend fun executeNode(node: AttackNode, state: AttackChainingState) {
        // Simple execution logic for now
        _output.emit("Executing node: ${node.name}")
        node.execute()

        // Find next connected node and execute it
        val connections = state.connections.filter { it.first.nodeId == node.id }
        for (connection in connections) {
            val nextNode = state.nodes[connection.second.nodeId]
            if (nextNode != null) {
                executeNode(nextNode as AttackNode, state)
            }
        }
    }

    fun stop() {
        executionJob?.cancel()
    }
}
