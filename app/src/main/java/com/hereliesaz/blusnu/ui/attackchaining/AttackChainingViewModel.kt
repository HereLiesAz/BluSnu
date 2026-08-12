package com.hereliesaz.blusnu.ui.attackchaining

import android.app.Application
import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.blusnu.data.AttackChainRepository
import com.hereliesaz.blusnu.data.AttackChainTemplates
import com.hereliesaz.blusnu.data.BluesnarfingModule
import com.hereliesaz.blusnu.data.KeystrokeInjectionModule
import com.hereliesaz.blusnu.ui.attackchaining.nodes.AttackNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.ChainServices
import com.hereliesaz.blusnu.ui.attackchaining.nodes.ConnectorRole
import com.hereliesaz.blusnu.ui.attackchaining.nodes.NodeConnector
import com.hereliesaz.blusnu.ui.attackchaining.nodes.NodeId
import kotlinx.coroutines.Job
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
 * @property isExecuting Whether a chain execution is currently running.
 * @property savedChainNames Names of all persisted chains for the Load dialog.
 */
data class AttackChainingState(
    val nodes: Map<NodeId, AttackNode> = emptyMap(),
    val connections: List<Pair<NodeConnector, NodeConnector>> = emptyList(),
    val logs: List<String> = emptyList(),
    val devices: List<TargetDevice> = emptyList(),
    val isExecuting: Boolean = false,
    val savedChainNames: Set<String> = emptySet()
)

/**
 * ViewModel for the Attack Chaining feature.
 *
 * Manages the graph data structure (Nodes and Edges) and delegates execution to [AttackChainExecutor].
 */
class AttackChainingViewModel(
    application: Application,
    private val repository: AttackChainRepository,
    private val deviceRepository: com.hereliesaz.blusnu.data.DeviceRepository,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val keystrokeInjectionModule: KeystrokeInjectionModule
) : AndroidViewModel(application) {

    private val executor = com.hereliesaz.blusnu.data.AttackChainExecutor()
    private val _uiState = MutableStateFlow(AttackChainingState())
    val uiState: StateFlow<AttackChainingState> = _uiState.asStateFlow()

    /** Tracks the currently running execution job so it can be cancelled (13.5). */
    private var executionJob: Job? = null

    /** Reason the last addConnection() call was rejected, or null. */
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

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

        // Load saved chain names for UI.
        refreshSavedChainNames()
    }

    private fun refreshSavedChainNames() {
        _uiState.update { it.copy(savedChainNames = repository.getAllAttackChainNames()) }
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
     * Adds a connection link with full validation:
     * - 13.1: Directionality enforcement (OUTPUT -> INPUT only).
     * - 13.6: Duplicate connection rejection.
     * - Self-loop prevention.
     *
     * When two connectors of the same role are tapped, the connection is normalized:
     * if both are INPUT or both OUTPUT, the connection is rejected with a message.
     */
    fun addConnection(from: NodeConnector, to: NodeConnector) {
        viewModelScope.launch {
            // Prevent self-loops.
            if (from.nodeId == to.nodeId) {
                _connectionError.value = "Cannot connect a node to itself"
                return@launch
            }

            // 13.1: Validate directionality -- one must be OUTPUT, the other INPUT.
            val (output, input) = when {
                from.role == ConnectorRole.OUTPUT && to.role == ConnectorRole.INPUT -> from to to
                from.role == ConnectorRole.INPUT && to.role == ConnectorRole.OUTPUT -> to to from
                else -> {
                    val sameRole = from.role.name.lowercase()
                    _connectionError.value = "Invalid connection: both connectors are $sameRole. Connect an output to an input."
                    return@launch
                }
            }

            _uiState.update { currentState ->
                // 13.6: Reject duplicate connections (same source output and destination input).
                val duplicate = currentState.connections.any {
                    it.first == output && it.second == input
                }
                if (duplicate) {
                    _connectionError.value = "Connection already exists"
                    return@update currentState
                }

                _connectionError.value = null
                val newConnections = currentState.connections.toMutableList()
                newConnections.add(output to input)
                currentState.copy(connections = newConnections)
            }
        }
    }

    /** Clears the last connection error after it has been shown. */
    fun clearConnectionError() {
        _connectionError.value = null
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

    /** 13.8: Persist the current chain state under the given name. */
    fun saveAttackChain(name: String) {
        repository.saveAttackChain(name, uiState.value)
        refreshSavedChainNames()
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
     * 13.5: Stores the Job so it can be cancelled. Guards against concurrent runs.
     */
    fun executeChain() {
        if (_uiState.value.isExecuting) return
        val services = mutableMapOf<String, Any>()
        bluetoothAdapter?.let { services[ChainServices.BLUETOOTH_ADAPTER] = it }
        services[ChainServices.BLUESNARFING_MODULE] = BluesnarfingModule()
        services[ChainServices.KEYSTROKE_INJECTION_MODULE] = keystrokeInjectionModule
        _uiState.update { it.copy(isExecuting = true) }
        executionJob = executor.execute(uiState.value, viewModelScope, services) {
            // Completion callback -- reset isExecuting.
            _uiState.update { it.copy(isExecuting = false) }
        }
    }

    /**
     * 13.5: Cancels the currently running chain execution.
     */
    fun cancelExecution() {
        executionJob?.cancel()
        executionJob = null
        _uiState.update { it.copy(isExecuting = false) }
    }
}
