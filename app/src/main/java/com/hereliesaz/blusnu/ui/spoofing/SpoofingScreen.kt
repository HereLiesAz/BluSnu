package com.hereliesaz.blusnu.ui.spoofing

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
    onStartMitmAttack: () -> Unit = {},
    onStopMitmAttack: () -> Unit = {},
    onRestoreOriginalMac: () -> Unit = {},
    onNameChanged: (String) -> Unit = {},
    onApplyNameClicked: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Identity Spoofing", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Change the Bluetooth adapter's MAC address or name to impersonate another device. Requires root.",
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
                value = state.selectedDevice?.let { it.name ?: it.macAddress } ?: "Select a device to clone",
                onValueChange = {},
                readOnly = true,
                label = { Text("Clone Identity From") },
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

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Select a scanned device above to copy its MAC address, or enter one manually below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        // MAC address input -- single source of truth from state.macAddress
        OutlinedTextField(
            value = state.macAddress,
            onValueChange = { onMacAddressChanged(it) },
            label = { Text("Spoof Adapter MAC To") },
            placeholder = { Text("XX:XX:XX:XX:XX:XX") },
            modifier = Modifier.fillMaxWidth(),
            isError = state.isError,
            supportingText = if (state.isError) {
                { Text("Format: XX:XX:XX:XX:XX:XX (colon-separated hex)") }
            } else null
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onApplyClicked,
                modifier = Modifier.weight(1f),
                enabled = !state.isError && state.macAddress.isNotBlank()
            ) {
                Text("Apply MAC")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onRestoreOriginalMac,
                modifier = Modifier.weight(1f),
                enabled = state.originalMac != null
            ) {
                Text("Restore Original")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Name spoofing section
        OutlinedTextField(
            value = state.spoofName,
            onValueChange = { onNameChanged(it) },
            label = { Text("Spoof Adapter Name") },
            placeholder = { Text("e.g. AirPods Pro") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onApplyNameClicked,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.spoofName.isNotBlank()
        ) {
            Text("Apply Name")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // MITM Attack controls
        if (state.isMitmRunning) {
            Button(
                onClick = onStopMitmAttack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop MITM Attack")
            }
        } else {
            Button(
                onClick = onStartMitmAttack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start MITM Attack")
            }
        }

        if (state.originalMac != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Original MAC: ${state.originalMac}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                if (state.mitmLogs.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("MITM Log", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(state.mitmLogs) { log ->
                        Text(log, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.logMessages.isEmpty() && state.mitmLogs.isEmpty()) {
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
                isError = false,
                originalMac = "AA:BB:CC:DD:EE:FF"
            )
        )
    }
}
