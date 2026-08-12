package com.hereliesaz.blusnu.ui.sweyntooth

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.hereliesaz.blusnu.data.SweynToothVector
import com.hereliesaz.blusnu.ui.components.ResultActions

/**
 * Screen for SweynTooth BLE Link Layer attack.
 *
 * Tests for SweynTooth BLE link layer vulnerabilities via external
 * nRF52/ESP32 hardware. Targets SoC firmware flaws affecting 480+
 * device models.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SweynToothScreen(viewModel: SweynToothViewModel) {
    val devices by viewModel.devices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val selectedVector by viewModel.selectedVector.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val hardwareConnected by viewModel.hardwareConnected.collectAsState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var deviceExpanded by remember { mutableStateOf(false) }
    var vectorExpanded by remember { mutableStateOf(false) }

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
                text = "SweynTooth (BLE Link Layer)",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Tests for SweynTooth BLE link layer vulnerabilities via external " +
                        "nRF52/ESP32 hardware. Targets SoC firmware flaws affecting 480+ " +
                        "device models.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Hardware Status Card.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (hardwareConnected) "Hardware: Connected" else "Hardware: Disconnected",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (hardwareConnected) {
                        OutlinedButton(
                            onClick = { viewModel.disconnectHardware() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect Hardware")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.connectHardware() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect Hardware")
                        }
                    }
                }
            }

            // Vector Selection Dropdown.
            ExposedDropdownMenuBox(
                expanded = vectorExpanded,
                onExpandedChange = { vectorExpanded = !vectorExpanded }
            ) {
                TextField(
                    value = selectedVector?.description ?: "Select Attack Vector",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Attack Vector") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vectorExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = vectorExpanded,
                    onDismissRequest = { vectorExpanded = false }
                ) {
                    SweynToothVector.entries.forEach { vector ->
                        DropdownMenuItem(
                            text = { Text(vector.description) },
                            onClick = {
                                viewModel.selectVector(vector)
                                vectorExpanded = false
                            }
                        )
                    }
                }
            }

            // Device Selection Dropdown.
            ExposedDropdownMenuBox(
                expanded = deviceExpanded,
                onExpandedChange = { deviceExpanded = !deviceExpanded }
            ) {
                TextField(
                    value = selectedDevice?.let { device ->
                        device.name ?: "Unknown Device (${device.macAddress})"
                    } ?: "Select Target Device",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Target Device") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = deviceExpanded,
                    onDismissRequest = { deviceExpanded = false }
                ) {
                    devices.forEach { device ->
                        DropdownMenuItem(
                            text = {
                                Text(device.name ?: "Unknown Device (${device.macAddress})")
                            },
                            onClick = {
                                viewModel.selectDevice(device)
                                deviceExpanded = false
                            }
                        )
                    }
                    if (devices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No devices found -- run a scan first") },
                            onClick = { deviceExpanded = false },
                            enabled = false
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
                    label = "SweynTooth Results",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isRunning) {
                CircularProgressIndicator()
            }

            // Action buttons: start/stop + full suite.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRunning) {
                    // Stop button.
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
                    // Start single vector button.
                    Button(
                        onClick = { viewModel.startAttack() },
                        enabled = selectedDevice != null && selectedVector != null && hardwareConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("RUN VECTOR")
                    }

                    // Full suite button.
                    Button(
                        onClick = { viewModel.startFullSuite() },
                        enabled = selectedDevice != null && hardwareConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("FULL SUITE")
                    }
                }
            }

            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}
