package com.hereliesaz.blusnu.ui.gattfuzzing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Screen for the GATT Fuzzing attack.
 *
 * Allows selection of a BLE target device and initiation of the fuzzing process.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GattFuzzingScreen(viewModel: GattFuzzingViewModel) {
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val status by viewModel.status.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "GATT Fuzzing involves sending random, malformed, or unexpected data to BLE characteristics to crash services or uncover unexpected behavior.\n\n" +
                       "This technique is effective against poorly tested IoT devices and smart wearables that do not properly validate input.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )

            // Device Selector.
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = selectedDevice?.name ?: "Select a target device",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    devices.forEach { device ->
                        // Visual cue for availability.
                        val isAvailable = System.currentTimeMillis() - device.lastSeen < 60000
                        val textColor = if (isAvailable) MaterialTheme.colorScheme.primary else Color.Unspecified
                        DropdownMenuItem(
                            text = { Text(device.name ?: "Unknown", color = textColor) },
                            onClick = {
                                viewModel.onDeviceSelected(device)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Action Button.
            Button(
                onClick = { viewModel.startAttack() },
                enabled = selectedDevice != null
            ) {
                Text("Start Fuzzing")
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Output.
            Text("Status: $status")
        }
    }
}
