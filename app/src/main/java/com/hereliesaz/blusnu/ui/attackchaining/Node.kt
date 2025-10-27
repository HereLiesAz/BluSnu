package com.hereliesaz.blusnu.ui.attackchaining

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.TargetDevice
import com.hereliesaz.blusnu.ui.attackchaining.nodes.AttackNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.NodeConnector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Node(
    node: AttackNode,
    devices: List<TargetDevice>,
    onDelete: () -> Unit,
    onConnectorClick: (NodeConnector) -> Unit,
    onDeviceSelected: (TargetDevice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = node.title)
        Spacer(modifier = Modifier.size(8.dp))
        if(node.inputs.any { it.id == "target_mac" }) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = node.targetDevice?.name ?: "Select a target",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    devices.forEach { device ->
                        val isAvailable = System.currentTimeMillis() - device.lastSeen < 60000
                        val textColor = if (isAvailable) MaterialTheme.colorScheme.primary else Color.Unspecified
                        DropdownMenuItem(
                            text = { Text(device.name ?: "Unknown", color = textColor) },
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
