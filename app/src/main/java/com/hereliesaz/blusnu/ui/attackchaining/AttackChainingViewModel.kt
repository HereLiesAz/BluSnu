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

import com.hereliesaz.blusnu.data.TargetDevice

/**
 * State for the Attack Chaining Canvas.
 *
 * @property nodes Map of NodeId to Node instance.
 * @property connections List of links between connectors.
 * @property logs Execution logs.
 * @property devices List of available devices (for node configuration).
 */
data class AttackChainingState(
    val nodes: Map<NodeId, AttackNode> = emptyMap(),
    val connections: List<Pair<NodeConnector, NodeConnector>> = emptyList(),
    val logs: List<String> = emptyList(),
    val devices: List<TargetDevice> = emptyList()
)

/**
 * ViewModel for the Attack Chaining feature.
 *
 * Manages the graph data structure (Nodes and Edges) and delegates execution to [AttackChainExecutor].
 */
class AttackChainingViewModel(
    application: Application,
    private val repository: AttackChainRepository,
    private val deviceRepository: com.hereliesaz.blusnu.data.DeviceRepository
) : AndroidViewModel(application) {

    private val executor = com.hereliesaz.blusnu.data.AttackChainExecutor()
    private val _uiState = MutableStateFlow(AttackChainingState())
    val uiState: StateFlow<AttackChainingState> = _uiState.asStateFlow()

    init {
        // Load default or blank state.
        loadAttackChain("default")
        if (_uiState.value.nodes.isEmpty()) {
            addNode(com.hereliesaz.blusnu.ui.attackchaining.nodes.StartNode(id = "start"))
        }

        // Collect execution logs.
        viewModelScope.launch {
            executor.output.collect { log ->
                _uiState.update { it.copy(logs = it.logs + log) }
            }
        }

        // Collect devices for dropdowns.
        viewModelScope.launch {
            deviceRepository.allDevices.collect { devices ->
                _uiState.update { it.copy(devices = devices) }
            }
        }
    }

    /**
     * Updates the target device for a specific node.
     */
    fun updateNodeTarget(nodeId: NodeId, device: TargetDevice) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val newNodes = currentState.nodes.toMutableMap()
                val nodeToUpdate = newNodes[nodeId]
                if (nodeToUpdate != null) {
                    newNodes[nodeId] = nodeToUpdate.withTarget(device)
                    currentState.copy(nodes = newNodes)
                } else {
                    currentState
                }
            }
        }
    }

    /**
     * Adds a new node to the graph.
     */
    fun addNode(node: AttackNode) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val newNodes = currentState.nodes.toMutableMap()
                newNodes[node.id] = node
                currentState.copy(nodes = newNodes)
            }
        }
    }

    /**
     * Removes a node and its connections.
     */
    fun removeNode(nodeId: NodeId) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val newNodes = currentState.nodes.toMutableMap()
                newNodes.remove(nodeId)
                // Also remove any connections associated with this node.
                val newConnections = currentState.connections.filterNot {
                    it.first.nodeId == nodeId || it.second.nodeId == nodeId
                }
                currentState.copy(nodes = newNodes, connections = newConnections)
            }
        }
    }

    /**
     * Adds a connection link.
     */
    fun addConnection(from: NodeConnector, to: NodeConnector) {
        viewModelScope.launch {
            // Prevent self-loops.
            if (from.nodeId != to.nodeId) {
                _uiState.update { currentState ->
                    val newConnections = currentState.connections.toMutableList()
                    newConnections.add(from to to)
                    currentState.copy(connections = newConnections)
                }
            }
        }
    }

    /**
     * Removes a connection link.
     */
    fun removeConnection(connection: Pair<NodeConnector, NodeConnector>) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val newConnections = currentState.connections.toMutableList()
                newConnections.remove(connection)
                currentState.copy(connections = newConnections)
            }
        }
    }

    /**
     * Updates the XY coordinates of a node (Drag and Drop).
     */
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

    /**
     * Loads a pre-defined template.
     */
    fun loadTemplate(name: String) {
        val template = AttackChainTemplates.templates[name]
        if (template != null) {
            _uiState.value = template
        }
    }

    /**
     * Runs the current graph.
     */
    fun executeChain() {
        executor.execute(uiState.value, viewModelScope)
    }
}
