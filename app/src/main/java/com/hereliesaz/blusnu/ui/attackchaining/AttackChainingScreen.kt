package com.hereliesaz.blusnu.ui.attackchaining

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.blusnu.data.TargetDevice
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
    var showOnboarding by rememberSaveable { mutableStateOf(true) }
    var logExpanded by rememberSaveable { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        Text(
            "Attack Chaining",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.3f, 3f)
                            panOffset += pan
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        state.connections.forEach { (from, to) ->
                            val fromNode = state.nodes[from.nodeId]
                            val toNode = state.nodes[to.nodeId]
                            if (fromNode != null && toNode != null) {
                                val fromConnectorPos = fromNode.position + Offset(
                                    150f,
                                    60f + fromNode.outputs.indexOf(from) * 40f
                                )
                                val toConnectorPos = toNode.position + Offset(
                                    0f,
                                    60f + toNode.inputs.indexOf(to) * 40f
                                )
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
                            devices = state.devices,
                            onDrag = { dragAmount ->
                                viewModel.updateNodePosition(
                                    node.id,
                                    node.position + dragAmount / scale
                                )
                            },
                            onDelete = { viewModel.removeNode(node.id) },
                            onConnectorClick = { connector ->
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
                }
            }

            // Collapsible log panel
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        )
                        .clickable { logExpanded = !logExpanded }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Execution Log (${state.logs.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        if (logExpanded) Icons.Filled.KeyboardArrowDown
                        else Icons.Filled.KeyboardArrowUp,
                        contentDescription = if (logExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(
                    visible = logExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(state.logs.size) {
                        if (state.logs.isNotEmpty()) {
                            listState.animateScrollToItem(state.logs.size - 1)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        items(state.logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                FloatingActionButton(
                    onClick = { showAddNodeMenu = true },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Node")
                }
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
                var showLoadTemplateMenu by remember { mutableStateOf(false) }
                Button(onClick = { showLoadTemplateMenu = true }) {
                    Text("Load Template")
                }
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
                Button(onClick = { viewModel.executeChain() }) {
                    Text("Run")
                }
            }

            // Onboarding overlay
            if (showOnboarding) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = { showOnboarding = false }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Dismiss"
                                        )
                                    }
                                }
                                Text(
                                    "Attack Chain Editor",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Build automated attack sequences by connecting nodes.\n\n" +
                                        "Tap + to add nodes (Scan, Bluesnarf, Wait, etc.)\n\n" +
                                        "Tap a green input or red output connector, then tap another connector to link them.\n\n" +
                                        "Drag nodes to reposition. Pinch to zoom, drag the canvas to pan.\n\n" +
                                        "Assign target devices to nodes that need them, then tap Run.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Start
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { showOnboarding = false }) {
                                    Text("Get Started")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .offset { IntOffset(node.position.x.roundToInt(), node.position.y.roundToInt()) }
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(16.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = node.title,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(8.dp))
            if (node.inputs.any { it.id == "target_mac" }) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = node.targetDevice?.name ?: "Select a target",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        devices.forEach { device ->
                            val isAvailable =
                                System.currentTimeMillis() - device.lastSeen < 60000
                            val textColor = if (isAvailable)
                                MaterialTheme.colorScheme.primary else Color.Unspecified
                            DropdownMenuItem(
                                text = {
                                    Text(device.name ?: "Unknown", color = textColor)
                                },
                                onClick = {
                                    onDeviceSelected(device)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            Row {
                // Input Connectors (with labels)
                Column(horizontalAlignment = Alignment.Start) {
                    node.inputs.forEach { connector ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = connector.id,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.size(60.dp))
                // Output Connectors (with labels)
                Column(horizontalAlignment = Alignment.End) {
                    node.outputs.forEach { connector ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = connector.id,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
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
            }
            Spacer(modifier = Modifier.size(8.dp))
            Button(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}
