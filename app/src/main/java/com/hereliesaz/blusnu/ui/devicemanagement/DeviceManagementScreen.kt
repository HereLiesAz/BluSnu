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
import android.util.Log
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzTextBox
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.blusnu.data.TargetDevice

/**
 * Screen for managing discovered Bluetooth devices (Targets).
 *
 * Features:
 * - Scan for new devices (BLE + Classic).
 * - List view of results with real-time updates.
 * - Long-press to favorite/bookmark.
 * - Click to view details and add notes.
 * - Integration with external MAC lookup API.
 * - Integration with Vulnerability Correlation engine.
 */
@Composable
fun DeviceManagementScreen(
    viewModel: DeviceManagementViewModel,
    startScan: Boolean = false,
    onNavigateToBtlejuice: (TargetDevice) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var selectedDevice by remember { mutableStateOf<TargetDevice?>(null) }

    // Auto-start scan if argument provided (e.g. from Dashboard shortcut).
    LaunchedEffect(startScan) {
        if (startScan && !state.isScanning) {
            viewModel.startScan()
        }
    }

    // Modal dialog for detailed device view.
    if (selectedDevice != null) {
        DeviceDetailsDialog(
            device = selectedDevice!!,
            vendor = state.vendor,
            onDismiss = { selectedDevice = null },
            onNotesChanged = { notes ->
                viewModel.updateDeviceNotes(selectedDevice!!, notes)
            },
            onBtlejuiceClick = {
                onNavigateToBtlejuice(selectedDevice!!)
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Description.
        Text(
            text = "Manage and scan for Bluetooth devices.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )

        // Scan Control Button.
        Button(
            onClick = {
                Log.d("BLUSNU", "SCAN BUTTON CLICKED - isScanning=${state.isScanning}")
                if (state.isScanning) {
                    viewModel.stopScan()
                } else {
                    viewModel.startScan()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text(if (state.isScanning) "Stop Scan" else "Start Scan")
        }

        // Statistics Row.
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

        // Device List.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.devices) { device ->
                DeviceRow(
                    device = device,
                    // Highlight devices seen since scan started.
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

/**
 * List Item for a Target Device.
 */
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
            // Favorite indicator.
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

/**
 * Detailed view of a device.
 */
@Composable
fun DeviceDetailsDialog(
    device: TargetDevice,
    vendor: String?,
    onDismiss: () -> Unit,
    onNotesChanged: (String) -> Unit,
    onBtlejuiceClick: () -> Unit
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
                // Editable notes field.
                AzTextBox(
                    value = notes,
                    onValueChange = {
                        notes = it
                        onNotesChanged(it)
                    },
                    hint = "Notes",
                    onSubmit = {}, // Notes update live on change
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AzButton(onClick = onDismiss, text = "Close", shape = AzButtonShape.RECTANGLE)
        },
        dismissButton = {
            AzButton(onClick = onBtlejuiceClick, text = "Btlejuice", shape = AzButtonShape.RECTANGLE)
        }
    )
}
