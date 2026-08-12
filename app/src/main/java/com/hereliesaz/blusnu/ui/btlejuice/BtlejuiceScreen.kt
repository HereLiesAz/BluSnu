package com.hereliesaz.blusnu.ui.btlejuice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.BtlejuiceState
import com.hereliesaz.blusnu.data.TargetDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BtlejuiceScreen(
    hardwareState: BtlejuiceHardwareState,
    btlejuiceState: BtlejuiceState,
    logs: List<String>,
    discoveredDevices: List<TargetDevice>,
    onConnectHardware: () -> Unit,
    onConnectDual: () -> Unit,
    onStartProxy: (TargetDevice?) -> Unit,
    onStopProxy: () -> Unit,
    gattTraffic: GattTraffic
) {
    var selectedDevice by remember { mutableStateOf<TargetDevice?>(null) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("BtleJuice", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            "BLE MITM proxy. Intercepts and modifies GATT traffic between a target device and its host. Requires external BtleJack hardware and USB dongle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Hardware status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Hardware: ${if (hardwareState.isConnected) "Connected" else "Disconnected"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!hardwareState.isConnected) {
                    Button(
                        onClick = onConnectHardware,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect BtleJack")
                    }
                } else {
                    Button(
                        onClick = onConnectDual,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect USB Dongle")
                    }
                }
            }
        }

        if (hardwareState.isConnected) {
            Spacer(modifier = Modifier.height(12.dp))

            // Device picker
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedDevice?.let { it.name ?: it.macAddress } ?: "Select a target device",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Target Device") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    discoveredDevices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name ?: device.macAddress) },
                            onClick = {
                                selectedDevice = device
                                expanded = false
                            }
                        )
                    }
                    if (discoveredDevices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No devices found") },
                            onClick = { expanded = false },
                            enabled = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                Button(
                    onClick = { onStartProxy(selectedDevice) },
                    enabled = selectedDevice != null && btlejuiceState == BtlejuiceState.IDLE,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start Proxy")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onStopProxy,
                    enabled = btlejuiceState != BtlejuiceState.IDLE,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop Proxy")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Traffic log
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Intercepted Traffic", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(gattTraffic.entries) { log ->
                        Text(log, style = MaterialTheme.typography.bodySmall)
                    }
                    if (gattTraffic.entries.isEmpty()) {
                        item {
                            Text(
                                "No traffic captured yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
