package com.hereliesaz.blusnu.ui.attackchaining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.blusnu.ui.attackchaining.model.AttackNode
import java.util.UUID

@Composable
fun AttackChainingScreen(viewModel: AttackChainingViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddNodeDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedOutput by remember { mutableStateOf<Pair<UUID, UUID>?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    val portPositions = remember { mutableStateMapOf<UUID, Offset>() }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2D2D2D))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            uiState.connections.forEach { connection ->
                val start = portPositions[connection.fromOutputId]
                val end = portPositions[connection.toInputId]

                if (start != null && end != null) {
                    drawLine(
                        color = Color.Gray,
                        start = start,
                        end = end,
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
            }
        }

        uiState.nodes.forEach { node ->
            Node(
                node = node,
                onMove = { newPosition -> viewModel.moveNode(node.id, newPosition) },
                onOutputClick = { nodeId, outputId ->
                    selectedOutput = nodeId to outputId
                },
                onInputClick = { nodeId, inputId ->
                    selectedOutput?.let { (fromNodeId, fromOutputId) ->
                        viewModel.addConnection(fromNodeId, fromOutputId, nodeId, inputId)
                        selectedOutput = null
                    }
                },
                onPortPositioned = { portId, offset ->
                    portPositions[portId] = offset
                }
            )
        }

        FloatingActionButton(
            onClick = { showAddNodeDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Node")
        }

        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = Color.White)
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(text = { Text("Save Attack Chain") }, onClick = {
                showSaveDialog = true
                showMenu = false
            })
            DropdownMenuItem(text = { Text("Load Attack Chain") }, onClick = {
                showLoadDialog = true
                showMenu = false
            })
            DropdownMenuItem(text = { Text("Load Template: Smart Lock") }, onClick = {
                viewModel.loadTemplate("BLE Smart Lock Audit")
                showMenu = false
            })
            DropdownMenuItem(text = { Text("Load Template: Eavesdropping") }, onClick = {
                viewModel.loadTemplate("Opportunistic Eavesdropping")
                showMenu = false
            })
        }

        if (showAddNodeDialog) {
            AddNodeDialog(
                onDismiss = { showAddNodeDialog = false },
                onNodeSelected = { nodeType ->
                    viewModel.addNode(nodeType, Offset(100f, 100f)) // Default position
                    showAddNodeDialog = false
                }
            )
        }

        if (showSaveDialog) {
            SaveChainDialog(
                onDismiss = { showSaveDialog = false },
                onSave = { name ->
                    viewModel.saveAttackChain(name)
                    showSaveDialog = false
                }
            )
        }

        if (showLoadDialog) {
            LoadChainDialog(
                onDismiss = { showLoadDialog = false },
                onLoad = { name ->
                    viewModel.loadAttackChain(name)
                    showLoadDialog = false
                },
                savedChainNames = uiState.savedChainNames
            )
        }
    }
}

@Composable
fun Node(
    node: AttackNode,
    onMove: (Offset) -> Unit,
    onOutputClick: (UUID, UUID) -> Unit,
    onInputClick: (UUID, UUID) -> Unit,
    onPortPositioned: (UUID, Offset) -> Unit
) {
    var currentPosition by remember { mutableStateOf(node.position) }

    LaunchedEffect(node.position) {
        currentPosition = node.position
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(currentPosition.x.toInt(), currentPosition.y.toInt()) }
            .size(150.dp, 60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .padding(8.dp)
            .pointerInput(node.id) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentPosition += dragAmount
                    },
                    onDragEnd = {
                        onMove(currentPosition)
                    }
                )
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                node.inputs.forEach { input ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Green)
                            .clickable { onInputClick(node.id, input.id) }
                            .onGloballyPositioned {
                                onPortPositioned(input.id, it.localToRoot(Offset.Zero))
                            }
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = node.type,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                node.outputs.forEach { output ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                            .clickable { onOutputClick(node.id, output.id) }
                            .onGloballyPositioned {
                                onPortPositioned(output.id, it.localToRoot(Offset.Zero))
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun AddNodeDialog(
    onDismiss: () -> Unit,
    onNodeSelected: (String) -> Unit
) {
    val nodeTypes = listOf("Scan (BLE)", "Filter by Service", "Hijack Connection", "GATT Write", "Scan (Classic)", "Filter by Class", "BlueSpy Audio Record")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Node") },
        text = {
            LazyColumn {
                items(nodeTypes) { nodeType ->
                    Text(
                        text = nodeType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNodeSelected(nodeType) }
                            .padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun SaveChainDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Attack Chain") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Chain Name") }
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name) }) {
                Text("Save")
            }
        }
    )
}

@Composable
fun LoadChainDialog(
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit,
    savedChainNames: List<String>
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load Attack Chain") },
        text = {
            LazyColumn {
                items(savedChainNames) { name ->
                    Text(
                        text = name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoad(name) }
                            .padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {}
    )
}
