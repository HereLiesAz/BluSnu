package com.hereliesaz.blusnu.ui.spoofing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.ui.theme.BluSnuTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoofingScreen(
    state: SpoofingState = SpoofingState(),
    onMacAddressChanged: (String) -> Unit = {},
    onApplyClicked: () -> Unit = {},
    onDeviceSelected: (com.hereliesaz.blusnu.data.TargetDevice) -> Unit = {},
    onStartMitmAttack: () -> Unit = {}
) {
    var macAddress by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "Spoofing changes the local Bluetooth adapter's MAC address to impersonate another device or evade blocklists.\n\n" +
                       "Requires a rooted device and a Bluetooth chipset/driver that supports address modification. It may not work on all hardware.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = state.selectedDevice?.name ?: "Select a device to spoof",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    state.devices.forEach { device ->
                        val isAvailable = System.currentTimeMillis() - device.lastSeen < 60000
                        val textColor = if (isAvailable) MaterialTheme.colorScheme.primary else Color.Unspecified
                        DropdownMenuItem(
                            text = { Text(device.name ?: "Unknown", color = textColor) },
                            onClick = {
                                onDeviceSelected(device)
                                macAddress = device.macAddress
                                onMacAddressChanged(device.macAddress)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = macAddress,
                onValueChange = {
                    macAddress = it
                    onMacAddressChanged(it)
                },
                label = { Text("New MAC Address") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.isError
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onApplyClicked,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isError
            ) {
                Text("Apply")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status Log
            Text(
                "Status Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    reverseLayout = true
                ) {
                    items(state.logMessages.reversed()) { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onStartMitmAttack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start MITM Attack")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "MITM Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                ) {
                    items(state.mitmDevices) { device ->
                        Text(device.name ?: device.macAddress, fontWeight = FontWeight.Bold)
                        state.mitmLogs[device.macAddress]?.forEach { log ->
                            Text(log, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpoofingScreenPreview() {
    BluSnuTheme {
        SpoofingScreen(
            state = SpoofingState(
                logMessages = listOf("Ready.", "Applying new MAC address...", "Success!"),
                isError = false
            )
        )
    }
}
