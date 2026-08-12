package com.hereliesaz.blusnu.ui.lmpfuzzing

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
import com.hereliesaz.blusnu.data.LmpFuzzVector
import com.hereliesaz.blusnu.ui.components.ResultActions

/**
 * Screen for the LMP/Baseband Fuzzing module.
 *
 * Sends malformed LMP packets via external hardware (ESP32) or root binary
 * to test firmware-level vulnerability to BrakTooth-class denial-of-service attacks.
 *
 * Allows users to:
 * 1. Select a Classic/Dual target device.
 * 2. Pick a specific LMP fuzz vector or run the full suite.
 * 3. View real-time injection logs and crash detection results.
 *
 * @param viewModel State holder for LMP fuzzing logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LmpFuzzingScreen(viewModel: LmpFuzzingViewModel) {
    val devices by viewModel.devices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val selectedVector by viewModel.selectedVector.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()
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
                text = "LMP/Baseband Fuzzing",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Sends malformed LMP packets via external hardware (ESP32) or root binary to test firmware-level vulnerability to BrakTooth-class denial-of-service attacks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )

            // Device Selection Dropdown (Classic/Dual only).
            ExposedDropdownMenuBox(
                expanded = deviceExpanded,
                onExpandedChange = { deviceExpanded = !deviceExpanded }
            ) {
                TextField(
                    value = selectedDevice?.name ?: "Select Target Device",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Target Device (Classic/Dual)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = deviceExpanded,
                    onDismissRequest = { deviceExpanded = false }
                ) {
                    devices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name ?: device.macAddress) },
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

            // Fuzz Vector Selection Dropdown.
            ExposedDropdownMenuBox(
                expanded = vectorExpanded,
                onExpandedChange = { vectorExpanded = !vectorExpanded }
            ) {
                TextField(
                    value = selectedVector?.let { "${it.name}: ${it.description}" } ?: "Select Fuzz Vector",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fuzz Vector") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vectorExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isRunning)
                )
                ExposedDropdownMenu(
                    expanded = vectorExpanded,
                    onDismissRequest = { vectorExpanded = false }
                ) {
                    LmpFuzzVector.values().forEach { vector ->
                        DropdownMenuItem(
                            text = { Text("${vector.name}: ${vector.description}") },
                            onClick = {
                                viewModel.selectVector(vector)
                                vectorExpanded = false
                            }
                        )
                    }
                }
            }

            // Selected vector description.
            if (selectedVector != null) {
                Text(
                    text = selectedVector!!.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Terminal-style Log Output.
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
                    label = "LMP Fuzzing Results",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isRunning) {
                CircularProgressIndicator()
            }

            // Action Buttons.
            if (isRunning) {
                // Stop button when fuzzing is active.
                Button(
                    onClick = { viewModel.stopFuzzing() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("STOP FUZZING")
                }
            } else {
                // Run Selected Vector button.
                Button(
                    onClick = { viewModel.startFuzzing() },
                    enabled = selectedDevice != null && selectedVector != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RUN SELECTED VECTOR")
                }

                // Run Full Suite button.
                Button(
                    onClick = { viewModel.runFullSuite() },
                    enabled = selectedDevice != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RUN FULL SUITE")
                }
            }

            // Bottom Padding.
            Spacer(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.1f))
        }
    }
}
