package com.hereliesaz.blusnu.ui.attackchaining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.ui.attackchaining.nodes.AttackNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.BluesnarfNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.IfElseNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.KeystrokeInjectionNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.LoopNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.NodeConnector
import com.hereliesaz.blusnu.ui.attackchaining.nodes.ScanBleNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.WaitNode
import com.hereliesaz.blusnu.ui.components.ScreenTitle
import java.util.UUID
import kotlin.math.roundToInt

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzRoller
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.blusnu.data.TargetDevice

/**
 * Screen for the visual Attack Chain Editor.
 *
 * This screen provides a canvas where users can:
 * 1. Add nodes (Attack Modules or Logic).
 * 2. Drag nodes to position them.
 * 3. Connect nodes to define the execution flow.
 * 4. Save/Load/Execute the workflow.
 */
@Composable
fun AttackChainingScreen(viewModel: AttackChainingViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showAddNodeMenu by remember { mutableStateOf(false) }

    // Tracks a connector that was clicked to start a link.
    var selectedConnector by remember { mutableStateOf<NodeConnector?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Visually chain attacks and automate workflows.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            // Draw connections (Lines) between nodes.
            Canvas(modifier = Modifier.fillMaxSize()) {
                state.connections.forEach { (from, to) ->
                    val fromNode = state.nodes[from.nodeId]
                    val toNode = state.nodes[to.nodeId]
                    if (fromNode != null && toNode != null) {
                        // Calculate connector positions relative to node position.
                        val fromConnectorPos = fromNode.position + Offset(150f, 60f + fromNode.outputs.indexOf(from) * 40f)
                        val toConnectorPos = toNode.position + Offset(0f, 60f + toNode.inputs.indexOf(to) * 40f)
                        drawLine(
                            color = Color.Gray,
                            start = fromConnectorPos,
                            end = toConnectorPos,
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }

            // Draw Nodes.
            state.nodes.values.forEach { node ->
                DraggableNode(
                    node = node,
                    devices = state.devices,
                    onDrag = { dragAmount ->
                        viewModel.updateNodePosition(node.id, node.position + dragAmount)
                    },
                    onDelete = { viewModel.removeNode(node.id) },
                    onConnectorClick = { connector ->
                        // Link creation logic.
                        selectedConnector?.let {
                            viewModel.addConnection(it, connector)
                            selectedConnector = null
                        } ?: run {
                            selectedConnector = connector
                        }
                    },
                    onDeviceSelected = { device ->
                        viewModel.updateNodeTarget(node.id, device)
                    }
                )
            }

            // Execution Logs (Bottom Left).
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.LightGray, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    items(state.logs) { log ->
                        Text(text = log)
                    }
                }
            }

            // Controls (Bottom Right).
            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                // Add Node FAB.
                FloatingActionButton(
                    onClick = { showAddNodeMenu = true },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Node")
                }

                // Add Node Menu.
                DropdownMenu(
                    expanded = showAddNodeMenu,
                    onDismissRequest = { showAddNodeMenu = false }
                ) {
                    DropdownMenuItem(text = { Text("Scan BLE") }, onClick = {
                        viewModel.addNode(ScanBleNode(id = UUID.randomUUID().toString()))
                        showAddNodeMenu = false
                    })
                    DropdownMenuItem(text = { Text("Bluesnarf") }, onClick = {
                        viewModel.addNode(BluesnarfNode(id = UUID.randomUUID().toString()))
                        showAddNodeMenu = false
                    })
                    DropdownMenuItem(text = { Text("Keystroke Injection") }, onClick = {
                        viewModel.addNode(KeystrokeInjectionNode(id = UUID.randomUUID().toString()))
                        showAddNodeMenu = false
                    })
                    DropdownMenuItem(text = { Text("If/Else") }, onClick = {
                        viewModel.addNode(IfElseNode(id = UUID.randomUUID().toString()))
                        showAddNodeMenu = false
                    })
                    DropdownMenuItem(text = { Text("Wait") }, onClick = {
                        viewModel.addNode(WaitNode(id = UUID.randomUUID().toString()))
                        showAddNodeMenu = false
                    })
                    DropdownMenuItem(text = { Text("Loop") }, onClick = {
                        viewModel.addNode(LoopNode(id = UUID.randomUUID().toString()))
                        showAddNodeMenu = false
                    })
                }
                Spacer(modifier = Modifier.size(16.dp))

                // Template Loader.
                var showLoadTemplateMenu by remember { mutableStateOf(false) }
                AzButton(onClick = { showLoadTemplateMenu = true }, text = "Load Template", shape = AzButtonShape.RECTANGLE)
                DropdownMenu(
                    expanded = showLoadTemplateMenu,
                    onDismissRequest = { showLoadTemplateMenu = false }
                ) {
                    DropdownMenuItem(text = { Text("Simple Scan") }, onClick = {
                        viewModel.loadTemplate("Simple Scan")
                        showLoadTemplateMenu = false
                    })
                    DropdownMenuItem(text = { Text("Snarf and Inject") }, onClick = {
                        viewModel.loadTemplate("Snarf and Inject")
                        showLoadTemplateMenu = false
                    })
                }
                Spacer(modifier = Modifier.size(16.dp))

                // Run Button.
                AzButton(onClick = { viewModel.executeChain() }, text = "Run", shape = AzButtonShape.RECTANGLE)
            }
        }
    }
}

/**
 * A draggable card representing a single node in the graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraggableNode(
    node: AttackNode,
    devices: List<TargetDevice>,
    onDrag: (Offset) -> Unit,
    onDelete: () -> Unit,
    onConnectorClick: (NodeConnector) -> Unit,
    onDeviceSelected: (TargetDevice) -> Unit
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(node.position.x.roundToInt(), node.position.y.roundToInt()) }
            .background(Color.LightGray, RoundedCornerShape(8.dp))
            .padding(16.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = node.title)
            Spacer(modifier = Modifier.size(8.dp))

            // If the node requires a target MAC input, show a selector.
            if(node.inputs.any { it.id == "target_mac" }) {
                val deviceOptions = devices.map { it.name ?: it.macAddress }
                val selectedOption = node.targetDevice?.let { it.name ?: it.macAddress }
                                     ?: if (devices.isEmpty()) "No devices" else "Select a target"

                AzRoller(
                    options = deviceOptions,
                    selectedOption = selectedOption,
                    onOptionSelected = { selectedName ->
                        val device = devices.find { (it.name ?: it.macAddress) == selectedName }
                        if (device != null) {
                            onDeviceSelected(device)
                        }
                    },
                    hint = "Select a target",
                    enabled = devices.isNotEmpty()
                )
            }

            // Connectors (Inputs Left, Outputs Right).
            Row {
                // Input Connectors.
                Column(horizontalAlignment = Alignment.Start) {
                    node.inputs.forEach { connector ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color.Green, CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        onConnectorClick(connector)
                                    }
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.size(100.dp))
                // Output Connectors.
                Column(horizontalAlignment = Alignment.End) {
                    node.outputs.forEach { connector ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color.Red, CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        onConnectorClick(connector)
                                    }
                                }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            AzButton(onClick = onDelete, text = "Delete", shape = AzButtonShape.RECTANGLE)
        }
    }
}
