package com.hereliesaz.blusnu.ui.attackchaining

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.AttackChainRepository
import com.hereliesaz.blusnu.data.AttackChainTemplates
import com.hereliesaz.blusnu.ui.attackchaining.nodes.AttackNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.NodeConnector
import com.hereliesaz.blusnu.ui.attackchaining.nodes.NodeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AttackChainingState(
    val nodes: Map<NodeId, AttackNode> = emptyMap(),
    val connections: List<Pair<NodeConnector, NodeConnector>> = emptyList()
)

class AttackChainingViewModel(
    application: Application,
    private val repository: AttackChainRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AttackChainingState())
    val uiState: StateFlow<AttackChainingState> = _uiState.asStateFlow()

    init {
        loadAttackChain("default")
    }

    fun addNode(node: AttackNode) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val newNodes = currentState.nodes.toMutableMap()
                newNodes[node.id] = node
                currentState.copy(nodes = newNodes)
            }
        }
    }

    fun removeNode(nodeId: NodeId) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val newNodes = currentState.nodes.toMutableMap()
                newNodes.remove(nodeId)
                // Also remove any connections associated with this node
                val newConnections = currentState.connections.filterNot {
                    it.first.nodeId == nodeId || it.second.nodeId == nodeId
                }
                currentState.copy(nodes = newNodes, connections = newConnections)
            }
        }
    }

    fun addConnection(from: NodeConnector, to: NodeConnector) {
        viewModelScope.launch {
            if (from.nodeId != to.nodeId) {
                _uiState.update { currentState ->
                    val newConnections = currentState.connections.toMutableList()
                    newConnections.add(from to to)
                    currentState.copy(connections = newConnections)
                }
            }
        }
    }

    fun removeConnection(connection: Pair<NodeConnector, NodeConnector>) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val newConnections = currentState.connections.toMutableList()
                newConnections.remove(connection)
                currentState.copy(connections = newConnections)
            }
        }
    }

    fun updateNodePosition(nodeId: NodeId, newPosition: androidx.compose.ui.geometry.Offset) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val newNodes = currentState.nodes.toMutableMap()
                val nodeToUpdate = newNodes[nodeId]
                if (nodeToUpdate != null) {
                    newNodes[nodeId] = nodeToUpdate.copy(position = newPosition)
                    currentState.copy(nodes = newNodes)
                } else {
                    currentState
                }
            }
        }
    }

    fun saveAttackChain(name: String) {
        repository.saveAttackChain(name, uiState.value)
    }

    fun loadAttackChain(name: String) {
        val loadedState = repository.loadAttackChain(name)
        if (loadedState != null) {
            _uiState.value = loadedState
        }
    }

    fun loadTemplate(name: String) {
        val template = AttackChainTemplates.templates[name]
        if (template != null) {
            _uiState.value = template
        }
    }
}
