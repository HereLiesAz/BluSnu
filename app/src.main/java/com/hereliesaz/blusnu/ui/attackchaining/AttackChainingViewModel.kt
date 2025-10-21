package com.hereliesaz.blusnu.ui.attackchaining

import android.app.Application
import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import com.hereliesaz.blusnu.ui.attackchaining.model.AttackNode
import com.hereliesaz.blusnu.ui.attackchaining.model.NodeConnection
import com.hereliesaz.blusnu.ui.attackchaining.model.NodeInput
import com.hereliesaz.blusnu.ui.attackchaining.model.NodeOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.reflect.Type
import java.util.UUID

// Represents the overall state of the Attack Chaining screen
data class AttackChainingUiState(
    val nodes: List<AttackNode> = emptyList(),
    val connections: List<NodeConnection> = emptyList()
)

class AttackChainingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AttackChainingUiState())
    val uiState: StateFlow<AttackChainingUiState> = _uiState.asStateFlow()

    private val gson = GsonBuilder()
        .registerTypeAdapter(UUID::class.java, UuidTypeAdapter())
        .create()

    fun addNode(type: String, position: Offset) {
        viewModelScope.launch {
            val newNode = createNodeFromType(type, position)
            _uiState.update { currentState ->
                currentState.copy(nodes = currentState.nodes + newNode)
            }
        }
    }

    fun removeNode(nodeId: UUID) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    nodes = currentState.nodes.filterNot { it.id == nodeId },
                    connections = currentState.connections.filterNot { it.fromNodeId == nodeId || it.toNodeId == nodeId }
                )
            }
        }
    }

    fun moveNode(nodeId: UUID, newPosition: Offset) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    nodes = currentState.nodes.map {
                        if (it.id == nodeId) it.copy(position = newPosition) else it
                    }
                )
            }
        }
    }

    fun addConnection(fromNodeId: UUID, fromOutputId: UUID, toNodeId: UUID, toInputId: UUID) {
        viewModelScope.launch {
            val newConnection = NodeConnection(
                fromNodeId = fromNodeId,
                fromOutputId = fromOutputId,
                toNodeId = toNodeId,
                toInputId = toInputId
            )
            _uiState.update { currentState ->
                currentState.copy(connections = currentState.connections + newConnection)
            }
        }
    }

    fun removeConnection(connectionId: UUID) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    connections = currentState.connections.filterNot { it.id == connectionId }
                )
            }
        }
    }

    fun saveAttackChain(name: String) {
        viewModelScope.launch {
            val json = gson.toJson(_uiState.value)
            val sharedPreferences = getApplication<Application>().getSharedPreferences("AttackChains", Context.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putString(name, json)
                apply()
            }
        }
    }

    fun loadAttackChain(name: String) {
        viewModelScope.launch {
            val sharedPreferences = getApplication<Application>().getSharedPreferences("AttackChains", Context.MODE_PRIVATE)
            val json = sharedPreferences.getString(name, null)
            if (json != null) {
                val type = object : TypeToken<AttackChainingUiState>() {}.type
                val loadedState = gson.fromJson<AttackChainingUiState>(json, type)
                _uiState.value = loadedState
            }
        }
    }

    fun loadTemplate(templateName: String) {
        viewModelScope.launch {
            val templateJson = getTemplateJson(templateName)
            val type = object : TypeToken<AttackChainingUiState>() {}.type
            val templateState = gson.fromJson<AttackChainingUiState>(templateJson, type)
            _uiState.value = templateState
        }
    }

    private fun getTemplateJson(templateName: String): String {
        return when (templateName) {
            "BLE Smart Lock Audit" -> generateSmartLockTemplate()
            "Opportunistic Eavesdropping" -> generateEavesdroppingTemplate()
            else -> ""
        }
    }

    private fun generateSmartLockTemplate(): String {
        val node1Id = UUID.randomUUID()
        val node2Id = UUID.randomUUID()
        val node3Id = UUID.randomUUID()

        val state = AttackChainingUiState(
            nodes = listOf(
                AttackNode(
                    id = node1Id,
                    type = "Scan (BLE)",
                    position = Offset(100f, 100f),
                    inputs = emptyList(),
                    outputs = listOf(NodeOutput(nodeId = node1Id, type = "List<TargetDevice>", label = "Devices"))
                ),
                AttackNode(
                    id = node2Id,
                    type = "Filter by Service",
                    position = Offset(400f, 100f),
                    inputs = listOf(
                        NodeInput(nodeId = node2Id, type = "List<TargetDevice>", label = "Devices In"),
                        NodeInput(nodeId = node2Id, type = "String", label = "Service UUID")
                    ),
                    outputs = listOf(NodeOutput(nodeId = node2Id, type = "List<TargetDevice>", label = "Devices Out"))
                ),
                AttackNode(
                    id = node3Id,
                    type = "GATT Write",
                    position = Offset(700f, 100f),
                    inputs = listOf(
                        NodeInput(nodeId = node3Id, type = "TargetDevice", label = "Target"),
                        NodeInput(nodeId = node3Id, type = "String", label = "Handle"),
                        NodeInput(nodeId = node3Id, type = "ByteArray", label = "Payload")
                    ),
                    outputs = emptyList()
                )
            ),
            connections = listOf(
                NodeConnection(
                    fromNodeId = node1Id,
                    fromOutputId = UUID.randomUUID(),
                    toNodeId = node2Id,
                    toInputId = UUID.randomUUID()
                )
            )
        )
        return gson.toJson(state)
    }

    private fun generateEavesdroppingTemplate(): String {
        val node1Id = UUID.randomUUID()
        val node2Id = UUID.randomUUID()
        val node3Id = UUID.randomUUID()

        val state = AttackChainingUiState(
            nodes = listOf(
                AttackNode(
                    id = node1Id,
                    type = "Scan (Classic)",
                    position = Offset(100f, 100f),
                    inputs = emptyList(),
                    outputs = listOf(NodeOutput(nodeId = node1Id, type = "List<TargetDevice>", label = "Devices"))
                ),
                AttackNode(
                    id = node2Id,
                    type = "Filter by Class",
                    position = Offset(400f, 100f),
                    inputs = listOf(
                        NodeInput(nodeId = node2Id, type = "List<TargetDevice>", label = "Devices In"),
                        NodeInput(nodeId = node2Id, type = "String", label = "Device Class")
                    ),
                    outputs = listOf(NodeOutput(nodeId = node2Id, type = "List<TargetDevice>", label = "Devices Out"))
                ),
                AttackNode(
                    id = node3Id,
                    type = "BlueSpy Audio Record",
                    position = Offset(700f, 100f),
                    inputs = listOf(
                        NodeInput(nodeId = node3Id, type = "TargetDevice", label = "Target"),
                        NodeInput(nodeId = node3Id, type = "Int", label = "Duration (s)")
                    ),
                    outputs = emptyList()
                )
            ),
            connections = listOf(
                NodeConnection(
                    fromNodeId = node1Id,
                    fromOutputId = UUID.randomUUID(),
                    toNodeId = node2Id,
                    toInputId = UUID.randomUUID()
                )
            )
        )
        return gson.toJson(state)
    }

    private fun createNodeFromType(type: String, position: Offset): AttackNode {
        val nodeId = UUID.randomUUID()
        return when (type) {
            "Scan (BLE)" -> AttackNode(
                id = nodeId,
                type = type,
                position = position,
                inputs = emptyList(),
                outputs = listOf(NodeOutput(nodeId = nodeId, type = "List<TargetDevice>", label = "Devices"))
            )
            "Filter by Service" -> AttackNode(
                id = nodeId,
                type = type,
                position = position,
                inputs = listOf(
                    NodeInput(nodeId = nodeId, type = "List<TargetDevice>", label = "Devices In"),
                    NodeInput(nodeId = nodeId, type = "String", label = "Service UUID")
                ),
                outputs = listOf(NodeOutput(nodeId = nodeId, type = "List<TargetDevice>", label = "Devices Out"))
            )
            "Hijack Connection" -> AttackNode(
                id = nodeId,
                type = type,
                position = position,
                inputs = listOf(NodeInput(nodeId = nodeId, type = "TargetDevice>", label = "Target")),
                outputs = listOf(NodeOutput(nodeId = nodeId, type = "Boolean", label = "Success"))
            )
            "GATT Write" -> AttackNode(
                id = nodeId,
                type = type,
                position = position,
                inputs = listOf(
                    NodeInput(nodeId = nodeId, type = "TargetDevice", label = "Target"),
                    NodeInput(nodeId = nodeId, type = "String", label = "Handle"),
                    NodeInput(nodeId = nodeId, type = "ByteArray", label = "Payload")
                ),
                outputs = emptyList()
            )
            else -> AttackNode(
                id = nodeId,
                type = "Unknown",
                position = position,
                inputs = emptyList(),
                outputs = emptyList()
            )
        }
    }
}

class UuidTypeAdapter : JsonSerializer<UUID>, JsonDeserializer<UUID> {
    override fun serialize(src: UUID?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src.toString())
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): UUID {
        return UUID.fromString(json?.asString)
    }
}
