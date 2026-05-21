package com.hereliesaz.blusnu.ui.spoofing

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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("MAC Spoofing", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Change the Bluetooth adapter's MAC address to impersonate another device. Requires root.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Device picker
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = state.selectedDevice?.let { it.name ?: it.macAddress } ?: "Select a device to spoof",
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
                state.devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name ?: device.macAddress) },
                        onClick = {
                            onDeviceSelected(device)
                            macAddress = device.macAddress
                            onMacAddressChanged(device.macAddress)
                            expanded = false
                        }
                    )
                }
                if (state.devices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No devices found - run a scan first") },
                        onClick = { expanded = false },
                        enabled = false
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
            enabled = !state.isError && macAddress.isNotBlank()
        ) {
            Text("Apply MAC Address")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onStartMitmAttack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start MITM Attack")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Combined log
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            LazyColumn(modifier = Modifier.padding(12.dp)) {
                if (state.logMessages.isNotEmpty()) {
                    item {
                        Text("Status Log", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(state.logMessages) { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (state.mitmDevices.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("MITM Log", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(state.mitmDevices) { device ->
                        Text(device.name ?: device.macAddress, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        state.mitmLogs[device.macAddress]?.forEach { log ->
                            Text(log, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                if (state.logMessages.isEmpty() && state.mitmDevices.isEmpty()) {
                    item {
                        Text(
                            "No activity yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
