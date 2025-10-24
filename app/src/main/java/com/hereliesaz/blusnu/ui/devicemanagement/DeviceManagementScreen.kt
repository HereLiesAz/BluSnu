package com.hereliesaz.blusnu.ui.devicemanagement

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Found: ${state.devicesInCurrentScan}")
            Text("New: ${state.newDevicesInCurrentScan}")
            Text("Total: ${state.totalDevicesInDb}")
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
                    },
                    onLongClick = {
                        viewModel.toggleFavorite(device)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceRow(device: TargetDevice, isNew: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val textColor = if (isNew) MaterialTheme.colorScheme.primary else Color.Unspecified
    val starColor = if (device.isFavorite) Color.Yellow else Color.Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            if (device.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = starColor
                )
            }
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
