package com.hereliesaz.blusnu.ui.bluebugging

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluebuggingScreen(viewModel: BluebuggingViewModel) {
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val status by viewModel.status.collectAsState()
    val result by viewModel.result.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val customCommandResult by viewModel.customCommandResult.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var customCommand by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Bluebugging", style = MaterialTheme.typography.headlineSmall)
        // Finding 8.8: Removed false "Requires root." claim.
        // Finding 8.9: Rewritten to accurately describe implemented AT commands.
        Text(
            "Opens an RFCOMM channel via HSP/HFP to send AT commands (probe, list calls, " +
                "read phonebook, query status). Custom AT commands can also be sent below.",
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

        Spacer(modifier = Modifier.height(16.dp))

        if (!isRunning) {
            Button(
                onClick = { viewModel.startAttack() },
                enabled = selectedDevice != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Attack")
            }
        } else {
            Button(
                onClick = { viewModel.stopAttack() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Attack")
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

        if (result.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Result", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Finding 8.10: Custom AT command input
        Spacer(modifier = Modifier.height(24.dp))
        Text("Custom AT Command", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customCommand,
                onValueChange = { customCommand = it },
                label = { Text("AT Command") },
                placeholder = { Text("e.g. AT+CLCC") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    viewModel.sendCustomCommand(customCommand)
                },
                enabled = customCommand.isNotBlank()
            ) {
                Text("Send")
            }
        }

        if (customCommandResult.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Custom Command Response", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = customCommandResult,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
