package com.hereliesaz.blusnu.ui.bluesnarfing

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
import androidx.compose.runtime.LaunchedEffect
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
 * Screen for executing the Bluesnarfing attack (OBEX Phonebook theft).
 *
 * This UI allows the user to:
 * 1. Read an explanation of the attack.
 * 2. Select a target from the list of discovered Classic Bluetooth devices.
 * 3. Initiate the attack via the ViewModel.
 * 4. View the status (Connecting/Downloading) and the result (The stolen phonebook content).
 *
 * @param viewModel The state holder for this screen.
 * @param hasPermissions Boolean flag indicating if Bluetooth Connect permissions are granted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluesnarfingScreen(viewModel: BluesnarfingViewModel, hasPermissions: Boolean) {
    // Collecting state from ViewModel Flow.
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val status by viewModel.status.collectAsState()
    val result by viewModel.result.collectAsState()

    // Local state for dropdown menu visibility.
    var expanded by remember { mutableStateOf(false) }

    // Notify ViewModel about permission changes to update UI capability.
    LaunchedEffect(hasPermissions) {
        viewModel.onPermissionsResult(hasPermissions)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd // Content flows from top-right.
    ) {
        Column(horizontalAlignment = Alignment.End) {
            // Educational text explaining the attack vector.
            Text(
                text = "Bluesnarfing exploits OBEX (Object Exchange) over Bluetooth to steal contacts, calendar entries, and other data.\n\n" +
                       "This attack targets Classic Bluetooth. While effective on older feature phones and early smartphones, modern devices usually require explicit pairing or user confirmation for OBEX transfers.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )

            // Dropdown menu for Target Selection.
            // Filters only valid targets (Classic Protocol).
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
                        // Visual cue: Primary color if device seen recently (< 1 min), else gray.
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

            // Action button to trigger the attack logic.
            Button(onClick = { viewModel.startAttack() }, enabled = selectedDevice != null) {
                Text("Start Attack")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status Log.
            Text("Status: $status")

            Spacer(modifier = Modifier.height(8.dp))

            // Result Output (e.g., vCard content).
            Text("Result: $result")
        }
    }
}
