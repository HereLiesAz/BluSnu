package com.hereliesaz.blusnu.ui.badbluetooth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.ProfileConfusionMode
import com.hereliesaz.blusnu.ui.components.ResultActions

/**
 * Screen for BadBluetooth (Profile Confusion).
 *
 * Tests Bluetooth profile confusion by advertising benign profiles during
 * pairing, then adding privileged profiles (HID, PAN, audio) post-bonding
 * to check if the target auto-accepts without user re-authorization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadBluetoothScreen(viewModel: BadBluetoothViewModel) {
    val devices by viewModel.devices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var expanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header.
            Text(
                text = "BadBluetooth (Profile Confusion)",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Tests Bluetooth profile confusion -- advertises benign profiles " +
                        "during pairing, then adds privileged profiles (HID, PAN) " +
                        "post-bonding to check if the target auto-accepts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Device Selection.
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = selectedDevice?.let { device ->
                        device.name ?: "Unknown Device (${device.macAddress})"
                    } ?: "Select Target Device",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Target Device") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    devices.forEach { device ->
                        DropdownMenuItem(
                            text = {
                                Text(device.name ?: "Unknown Device (${device.macAddress})")
                            },
                            onClick = {
                                viewModel.selectDevice(device)
                                expanded = false
                            }
                        )
                    }
                    if (devices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No devices found -- run a scan first") },
                            onClick = { expanded = false },
                            enabled = false
                        )
                    }
                }
            }

            // Mode Selection.
            ExposedDropdownMenuBox(
                expanded = modeExpanded,
                onExpandedChange = { modeExpanded = !modeExpanded }
            ) {
                TextField(
                    value = "${selectedMode.name}: ${selectedMode.description}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Profile Confusion Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = modeExpanded,
                    onDismissRequest = { modeExpanded = false }
                ) {
                    ProfileConfusionMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text("${mode.name}: ${mode.description}") },
                            onClick = {
                                viewModel.selectMode(mode)
                                modeExpanded = false
                            }
                        )
                    }
                }
            }

            // Logs.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.DarkGray.copy(alpha = 0.3f))
                    .padding(8.dp)
            ) {
                LazyColumn(reverseLayout = true) {
                    items(logs.reversed()) { log ->
                        Text(text = log, style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                }
            }

            // Copy/Share result actions.
            if (logs.isNotEmpty()) {
                ResultActions(
                    resultText = logs.joinToString("\n"),
                    label = "BadBluetooth Results",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isRunning) {
                CircularProgressIndicator()
            }

            // Action buttons.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRunning) {
                    // Stop button
                    OutlinedButton(
                        onClick = { viewModel.stopAttack() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("STOP ATTACK")
                    }
                } else {
                    // Start button
                    Button(
                        onClick = { viewModel.startAttack() },
                        enabled = selectedDevice != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("RUN PROFILE CONFUSION")
                    }
                }
            }

            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}
