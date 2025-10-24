package com.hereliesaz.blusnu.ui.devicemanagement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.TargetDevice

@Composable
fun DeviceManagementScreen(viewModel: DeviceManagementViewModel) {
    val state by viewModel.state.collectAsState()
    var selectedDevice by remember { mutableStateOf<TargetDevice?>(null) }

    if (selectedDevice != null) {
        DeviceDetailsDialog(
            device = selectedDevice!!,
            vendor = state.vendor,
            onDismiss = { selectedDevice = null },
            onNotesChanged = { notes ->
                viewModel.updateDeviceNotes(selectedDevice!!, notes)
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = {
                if (state.isScanning) {
                    viewModel.stopScan()
                } else {
                    viewModel.startScan()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(if (state.isScanning) "Stop Scan" else "Start Scan")
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.devices) { device ->
                DeviceRow(
                    device = device,
                    isNew = device.lastSeen > state.scanStartTime,
                    onClick = {
                        selectedDevice = device
                        viewModel.onDeviceSelected(device)
                    }
                )
            }
        }
    }
}

@Composable
fun DeviceRow(device: TargetDevice, isNew: Boolean, onClick: () -> Unit) {
    val textColor = if (isNew) MaterialTheme.colorScheme.primary else Color.Unspecified

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = device.name ?: "Unknown Device",
                style = MaterialTheme.typography.titleMedium,
                color = textColor
            )
            Text(
                text = device.macAddress,
                style = MaterialTheme.typography.bodySmall,
                color = textColor
            )
        }
    }
}

@Composable
fun DeviceDetailsDialog(
    device: TargetDevice,
    vendor: String?,
    onDismiss: () -> Unit,
    onNotesChanged: (String) -> Unit
) {
    var notes by remember { mutableStateOf(device.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(device.name ?: "Unknown Device") },
        text = {
            Column {
                Text("MAC Address: ${device.macAddress}")
                Text("Vendor: ${vendor ?: "Unknown"}")
                Text("RSSI: ${device.rssi}")
                Text("Protocol: ${device.protocol}")
                Text("Last Seen: ${device.lastSeen}")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                        onNotesChanged(it)
                    },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
