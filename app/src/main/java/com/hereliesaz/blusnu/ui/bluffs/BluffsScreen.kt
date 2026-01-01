package com.hereliesaz.blusnu.ui.bluffs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.BluffsMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluffsScreen(viewModel: BluffsViewModel) {
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val logs by viewModel.logs.collectAsState()
    var expandedDevice by remember { mutableStateOf(false) }
    var expandedMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "BLUFFS (CVE-2023-24023) Auditor\n\n" +
                   "Tests for Bluetooth Forward and Future Secrecy vulnerabilities by manipulating LMP parameters to force weak key derivation.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Device Selection
        ExposedDropdownMenuBox(
            expanded = expandedDevice,
            onExpandedChange = { expandedDevice = !expandedDevice }
        ) {
            TextField(
                value = selectedDevice?.name ?: "Select a target device",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDevice) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(
                expanded = expandedDevice,
                onDismissRequest = { expandedDevice = false }
            ) {
                devices.forEach { device ->
                    val isAvailable = System.currentTimeMillis() - device.lastSeen < 60000
                    val textColor = if (isAvailable) MaterialTheme.colorScheme.primary else Color.Unspecified
                    DropdownMenuItem(
                        text = { Text(device.name ?: "Unknown", color = textColor) },
                        onClick = {
                            viewModel.onDeviceSelected(device)
                            expandedDevice = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mode Selection
        ExposedDropdownMenuBox(
            expanded = expandedMode,
            onExpandedChange = { expandedMode = !expandedMode }
        ) {
            TextField(
                value = selectedMode.name,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMode) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(
                expanded = expandedMode,
                onDismissRequest = { expandedMode = false }
            ) {
                BluffsMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.name) },
                        onClick = {
                            viewModel.onModeSelected(mode)
                            expandedMode = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.startAttack() },
            enabled = selectedDevice != null
        ) {
            Text("Start Audit")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                Text(log)
            }
        }
    }
}
