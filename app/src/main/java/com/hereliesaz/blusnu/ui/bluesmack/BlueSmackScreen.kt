package com.hereliesaz.blusnu.ui.bluesmack

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlueSmackScreen(viewModel: BlueSmackViewModel) {
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val status by viewModel.status.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val packetSize by viewModel.packetSize.collectAsState()
    val packetCount by viewModel.packetCount.collectAsState()
    val interfaceName by viewModel.interfaceName.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("BlueSmack", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            "L2CAP ping flood attack. Sends oversized l2ping packets to overwhelm the target device. Requires root.",
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
                value = selectedDevice?.let { it.name ?: it.macAddress } ?: "Select a target device",
                onValueChange = {},
                readOnly = true,
                label = { Text("Target Device") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                enabled = !isRunning,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name ?: device.macAddress) },
                        onClick = {
                            viewModel.onDeviceSelected(device)
                            expanded = false
                        }
                    )
                }
                if (devices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No devices found - run a scan first") },
                        onClick = { expanded = false },
                        enabled = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = packetSize,
            onValueChange = { viewModel.onPacketSizeChanged(it) },
            label = { Text("Packet Size (bytes)") },
            supportingText = { Text("Range: 1 - 65535") },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = packetCount,
            onValueChange = { viewModel.onPacketCountChanged(it) },
            label = { Text("Packet Count") },
            supportingText = { Text("Range: 1 - 100000") },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = interfaceName,
            onValueChange = { viewModel.onInterfaceNameChanged(it) },
            label = { Text("Interface Name") },
            supportingText = { Text("HCI interface (e.g. hci0). Alphanumeric only.") },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        )

        // Validation error message
        validationError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isRunning) {
            OutlinedButton(
                onClick = { viewModel.stopAttack() },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Attack")
            }
        } else {
            Button(
                onClick = { viewModel.startAttack() },
                enabled = selectedDevice != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Attack")
            }
        }

        if (isRunning) {
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator()
        }

        if (status.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
