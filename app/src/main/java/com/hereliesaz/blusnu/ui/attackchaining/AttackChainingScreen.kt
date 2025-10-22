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
import androidx.compose.material3.Button
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
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun AttackChainingScreen(viewModel: AttackChainingViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showAddNodeMenu by remember { mutableStateOf(false) }
    var selectedConnector by remember { mutableStateOf<NodeConnector?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            state.connections.forEach { (from, to) ->
                val fromNode = state.nodes[from.nodeId]
                val toNode = state.nodes[to.nodeId]
                if (fromNode != null && toNode != null) {
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

        state.nodes.values.forEach { node ->
            DraggableNode(
                node = node,
                onDrag = { dragAmount ->
                    viewModel.updateNodePosition(node.id, node.position + dragAmount)
                },
                onDelete = { viewModel.removeNode(node.id) },
                onConnectorClick = { connector ->
                    selectedConnector?.let {
                        viewModel.addConnection(it, connector)
                        selectedConnector = null
                    } ?: run {
                        selectedConnector = connector
                    }
                }
            )
        }

        FloatingActionButton(
            onClick = { showAddNodeMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Node")
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
        }
    }
}

@Composable
fun DraggableNode(
    node: AttackNode,
    onDrag: (Offset) -> Unit,
    onDelete: () -> Unit,
    onConnectorClick: (NodeConnector) -> Unit
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
            Row {
                // Input Connectors
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
                // Output Connectors
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
            Button(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}
