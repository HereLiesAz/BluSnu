package com.hereliesaz.blusnu.ui.keystrokeinjection

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeystrokeInjectionScreen(
    state: KeystrokeInjectionState,
    onAttemptAttack: () -> Unit,
    onSendKeystrokes: (String) -> Unit,
    onDeviceSelected: (com.hereliesaz.blusnu.data.TargetDevice) -> Unit
) {
    var textToSend by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Keystroke Injection", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Attempt silent Bluetooth pairing and inject keystrokes to the target device. For real HID keyboard/touchpad, use the HID screen instead.",
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
                value = state.selectedDevice?.let { it.name ?: it.macAddress } ?: "Select a target device",
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

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onAttemptAttack,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isPared && state.selectedDevice != null
        ) {
            Text(if (state.isPared) "Paired Successfully" else "Attempt Silent Pairing")
        }

        if (state.isPared) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textToSend,
                    onValueChange = { textToSend = it },
                    label = { Text("Text to send") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = { onSendKeystrokes(textToSend) },
                    enabled = textToSend.isNotEmpty()
                ) {
                    Text("Send")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Log", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.logMessages) { log ->
                        Text(log, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.logMessages.isEmpty()) {
                        item {
                            Text(
                                "No activity yet.",
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
